"""
非侵入式调试日志模块（AOP 风格）
通过 LangGraph 的 astream_events 事件流拦截所有节点、LLM 调用、工具调用，
不修改 nodes.py / builder.py 的任何业务代码。

用法：
    from graph.debug import ainvoke_with_logging
    result = await ainvoke_with_logging(graph, state)
"""

import json
import logging
import time
from typing import Any

# ---------------------------------------------------------------------------
# 配置日志格式
# ---------------------------------------------------------------------------
logger = logging.getLogger("graph.debug")
logger.setLevel(logging.DEBUG)
if not logger.handlers:
    h = logging.StreamHandler()
    h.setFormatter(logging.Formatter(
        "[%(asctime)s] %(levelname)-5s [%(name)s] %(message)s",
        datefmt="%H:%M:%S",
    ))
    logger.addHandler(h)

# 同时开启 langchain / langgraph 内部日志（INFO 级别，不会太吵）
for name in ("langchain", "langgraph", "langchain_core"):
    lc = logging.getLogger(name)
    lc.setLevel(logging.DEBUG)
    if not lc.handlers:
        lc.addHandler(h)

# ---------------------------------------------------------------------------
# 节点名 → 中文标签
# ---------------------------------------------------------------------------
NODE_LABEL = {
    "intent": "意图识别",
    "tool":   "工具调用",
    "rag":    "知识库检索",
    "reply":  "回复生成",
}


def _ts() -> float:
    return time.time()


def _short(s: Any, maxlen: int = 200) -> str:
    """截断字符串，避免日志爆炸"""
    text = str(s)
    if len(text) <= maxlen:
        return text
    return text[:maxlen] + f"...(共{len(text)}字符)"


def _safe_dict(output: Any) -> dict:
    """将 astream_events 的 output 转为 dict。
    支持 dict、JSON 字符串、Python 字典字符串三种格式。
    """
    if isinstance(output, dict):
        return output
    if isinstance(output, str):
        try:
            return json.loads(output)
        except (json.JSONDecodeError, TypeError):
            try:
                import ast
                result = ast.literal_eval(output)
                return result if isinstance(result, dict) else {}
            except (ValueError, SyntaxError):
                return {"_raw": output}
    return {}


# ---------------------------------------------------------------------------
# 核心：基于 astream_events 的日志拦截器
# ---------------------------------------------------------------------------
async def ainvoke_with_logging(graph, state: dict) -> dict:
    """
    等价于 graph.ainvoke(state)，但会拦截并打印所有节点事件。
    对上游调用方（api/chat.py）完全透明。
    """
    overall_start = _ts()
    final_output: dict = {}

    # 跟踪当前活跃节点，用于计算耗时。key = node_name
    node_start: dict[str, float] = {}
    # 用 set 去重：LangGraph 内部会多次触发同一节点的 on_chain_start/end
    _seen_end: set = set()
    _node_started: set = set()
    # 跟踪 LLM 调用序号
    llm_call_counts: dict[str, int] = {}

    logger.info("=" * 60)
    logger.info("【Graph 开始】session=%s user=%s msg=%s",
                state.get("session_id", "?"),
                state.get("user_name", "?"),
                _short(state.get("user_input", ""), 80))

    try:
        async for event in graph.astream_events(state, version="v2"):
            kind = event.get("event")
            meta = event.get("metadata", {})
            node_name = meta.get("langgraph_node", "")
            node_label = NODE_LABEL.get(node_name, node_name)

            # ================================================================
            # 1. 节点开始（去重：只记录第一次）
            # ================================================================
            if kind == "on_chain_start" and node_name:
                if node_name in _node_started:
                    continue
                _node_started.add(node_name)
                node_start[node_name] = _ts()
                logger.info("┌─ [%s] 节点开始 ──────────────────────────────", node_label)

            # ================================================================
            # 2. 节点结束（去重：只记录第一次）
            # ================================================================
            elif kind == "on_chain_end" and node_name:
                if node_name in _seen_end:
                    continue
                output = _safe_dict(event.get("data", {}).get("output", {}))

                # 跳过 LLM 链的内部 on_chain_end 事件（output 不含节点特有字段）
                if node_name == "intent" and "intent" not in output:
                    continue
                if node_name == "tool" and "tool_results" not in output:
                    continue
                if node_name == "rag" and "retrieved_docs" not in output:
                    continue
                if node_name == "reply" and "final_reply" not in output:
                    continue

                _seen_end.add(node_name)
                start_time = node_start.get(node_name, _ts())
                elapsed = (_ts() - start_time) * 1000

                if node_name == "intent":
                    logger.info("│  [%s] 识别结果: intent=%s confidence=%.2f",
                                node_label,
                                output.get("intent", "?"),
                                output.get("intent_confidence", 0))
                elif node_name == "tool":
                    tool_results = output.get("tool_results", {})
                    if isinstance(tool_results, dict):
                        logger.info("│  [%s] 工具调用结果: %d 个工具被调用",
                                    node_label, len(tool_results))
                        for tname, tresult in tool_results.items():
                            tr = _safe_dict(tresult)
                            ok = not tr.get("_error")
                            status = "成功" if ok else "失败"
                            logger.info("│    → %s: %s %s",
                                        tname, status,
                                        _short(tr.get("message", "") if not ok else ""))
                            # 详细打印工具返回数据（关键字段）
                            if ok and tr:
                                logger.info("│      %s 详情: %s", tname, _short(json.dumps(tr, ensure_ascii=False), 600))
                    else:
                        logger.info("│  [%s] 工具调用结果: %s", node_label, _short(tool_results))
                elif node_name == "rag":
                    docs = output.get("retrieved_docs", [])
                    logger.info("│  [%s] 命中 %d 条", node_label, len(docs))
                    for i, doc in enumerate(docs):
                        logger.debug("│    #%d: %s", i + 1, _short(doc))
                elif node_name == "reply":
                    reply_text = output.get("final_reply", "")
                    need_human = output.get("need_human", False)
                    logger.info("│  [%s] 需要转人工: %s", node_label, need_human)
                    logger.info("│  [%s] 回复内容: %s", node_label, _short(reply_text))

                logger.info("└─ [%s] 节点结束 耗时=%dms", node_label, int(elapsed))

            # ================================================================
            # 3. LLM 调用开始（记录 prompt）
            # ================================================================
            elif kind == "on_chat_model_start":
                context = f"({node_label})" if node_name else ""
                key = node_name or "__global__"
                idx = llm_call_counts.get(key, 0) + 1
                llm_call_counts[key] = idx
                logger.info("  [LLM#%d %s] 调用开始", idx, context)
                # 打印 system prompt 摘要
                messages = event.get("data", {}).get("input", {}).get("messages", [])
                for m in messages:
                    if isinstance(m, dict):
                        role = m.get("type", m.get("role", "?"))
                        content = m.get("content", "")
                        if role == "system":
                            logger.debug("  [LLM#%d %s] system: %s", idx, context, _short(content, 120))
                        elif role == "human":
                            logger.debug("  [LLM#%d %s] human: %s", idx, context, _short(content, 120))
                        elif role == "tool":
                            logger.debug("  [LLM#%d %s] tool: %s", idx, context, _short(str(content), 120))

            # ================================================================
            # 4. LLM 调用结束（记录 token 用量）
            # ================================================================
            elif kind == "on_chat_model_end":
                context = f"({node_label})" if node_name else ""
                key = node_name or "__global__"
                idx = llm_call_counts.get(key, 0)
                output = event.get("data", {}).get("output", {})
                # token 用量
                usage = None
                if hasattr(output, "response_metadata"):
                    usage = output.response_metadata.get("token_usage", {})
                if usage:
                    logger.info("  [LLM#%d %s] 调用结束 | input=%d output=%d total=%d tokens",
                                idx, context,
                                usage.get("prompt_tokens", 0),
                                usage.get("completion_tokens", 0),
                                usage.get("total_tokens", 0))
                else:
                    # 尝试从 AIMessage 获取
                    if hasattr(output, "usage_metadata"):
                        um = output.usage_metadata
                        logger.info("  [LLM#%d %s] 调用结束 | input=%d output=%d total=%d tokens",
                                    idx, context,
                                    um.get("input_tokens", 0),
                                    um.get("output_tokens", 0),
                                    um.get("total_tokens", 0))
                    else:
                        logger.info("  [LLM#%d %s] 调用结束", idx, context)

                # 打印 tool_calls 决策
                if hasattr(output, "tool_calls") and output.tool_calls:
                    for tc in output.tool_calls:
                        logger.info("  [LLM#%d %s] → 决定调用工具: %s(%s)",
                                    idx, context, tc.get("name", "?"),
                                    _short(tc.get("args", {})))

            # ================================================================
            # 5. 工具调用开始
            # ================================================================
            elif kind == "on_tool_start":
                tool_name = event.get("name", "?")
                tool_input = event.get("data", {}).get("input", {})
                logger.info("  [Tool] %s 开始 | 参数: %s", tool_name, _short(tool_input))

            # ================================================================
            # 6. 工具调用结束
            # ================================================================
            elif kind == "on_tool_end":
                tool_name = event.get("name", "?")
                tool_output = _safe_dict(event.get("data", {}).get("output", {}))
                ok = not tool_output.get("_error")
                logger.info("  [Tool] %s 结束 | 成功=%s | 结果: %s",
                            tool_name, ok, _short(json.dumps(tool_output, ensure_ascii=False), 500))

            # ================================================================
            # 7. Graph 整体结束（捕获最终状态）
            # ================================================================
            elif kind == "on_chain_end" and not node_name:
                final_output = _safe_dict(event.get("data", {}).get("output", {}))

    except Exception:
        logger.exception("Graph 执行异常")

    total_elapsed = (_ts() - overall_start) * 1000
    logger.info("【Graph 完成】总耗时=%dms", int(total_elapsed))
    logger.info("=" * 60)

    return final_output