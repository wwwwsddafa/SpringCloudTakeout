"""
LangGraph 各节点实现：
  - intent_node : 意图识别（LLM 分类）
  - tool_node   : 根据意图，让 LLM 自主选择并调用 Java 微服务工具
  - rag_node    : 从知识库检索 FAQ
  - reply_node  : 综合上下文生成最终回复（支持流式）
  - router_node : 判断是否需要转人工
"""
import json

from langchain_core.messages import SystemMessage, HumanMessage, ToolMessage

import config
from model import get_chat_model
from tools import ALL_TOOLS, TOOL_MAP, format_tool_results
from tools.java_client import set_java_context
from rag.retriever import search_knowledge


INTENT_LIST = [
    "order_query", "order_modify", "order_cancel", "refund",
    "product_query", "account_query", "faq", "chitchat", "transfer_human",
]

INTENT_SYSTEM = """你是一个外卖平台 AI 客服的意图识别模块。
分析用户消息（可结合历史对话），判断其意图类型，只输出 JSON，不要任何解释。

意图类型：
- order_query：查询订单状态、物流、配送进度、订单列表等（只读操作）
- order_modify：修改订单信息，如修改收货地址、修改备注等（写操作）
- order_cancel：取消订单（用户在未支付时想取消）
- refund：退单、退款、退货、要求退钱（用户在已支付后想退款）
- product_query：查询商品信息、搜索/推荐商品
- account_query：查询用户账户信息（会员等级、余额、手机号等）
- faq：通用问题（退换货政策、配送范围/时效、支付、优惠券、账户操作等）
- chitchat：问候、闲聊、无明确诉求
- transfer_human：明确要求转接人工客服

分类规则：
- 用户说"改地址""修改收货地址""换个地址" → order_modify
- 用户说"取消订单""不想要了"（未支付场景） → order_cancel
- 用户说"退款""退钱""退单""退货" → refund
- 用户说"到哪了""物流""订单状态" → order_query
- 用户说"转人工""人工客服" → transfer_human

输出格式（仅输出 JSON）：
{"intent": "xxx", "confidence": 0.95}
"""

REPLY_SYSTEM = """你是"XX外卖"平台的 AI 客服助手。请遵循以下回复规范：
- 使用简体中文，语气友好、专业、简洁。
- 只回答用户当前的问题，不要一次性回答多个不相关的问题。
- 知识库中可能有多条参考，请只选取与用户问题最相关的一条来回复。
- 涉及订单/物流/商品等具体信息时，用自然流畅的语言描述，不要直接粘贴原始 JSON。
- 如果信息不足（如缺少订单号），主动、礼貌地向用户追问。
- 不要编造平台未提供的信息；不确定时建议用户联系人工客服。
- 回复控制在 1-3 段，重点突出。"""


# --------------------------------------------------------------------------- #
# 工具函数
# --------------------------------------------------------------------------- #
def _format_history(history):
    if not history:
        return "（无）"
    parts = []
    for m in history[-6:]:
        role = "用户" if m.get("role") == "user" else "客服"
        parts.append(f"{role}：{m.get('content', '')}")
    return "\n".join(parts)


def _extract_json(text):
    """从模型输出中稳健地提取 JSON（兼容 ```json 代码块与多余文本）。"""
    if isinstance(text, list):
        text = "".join(getattr(p, "text", str(p)) for p in text)
    text = (text or "").strip()
    if "```" in text:
        block = text.split("```")[1]
        if block.startswith("json"):
            block = block[4:]
        text = block.strip()
    try:
        return json.loads(text)
    except Exception:
        start, end = text.find("{"), text.rfind("}")
        if start != -1 and end != -1:
            try:
                return json.loads(text[start:end + 1])
            except Exception:
                return {}
    return {}


def _text(content):
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(getattr(p, "text", str(p)) for p in content)
    return str(content)


# --------------------------------------------------------------------------- #
# 节点
# --------------------------------------------------------------------------- #
async def intent_node(state: dict) -> dict:
    prompt = (
        f"历史对话：\n{_format_history(state.get('history', []))}\n\n"
        f"用户当前消息：{state['user_input']}"
    )
    try:
        llm = get_chat_model()
        resp = await llm.ainvoke([SystemMessage(content=INTENT_SYSTEM), HumanMessage(content=prompt)])
        data = _extract_json(_text(resp.content))
        intent = data.get("intent", "faq")
        conf = float(data.get("confidence", 0.5))
        if intent not in INTENT_LIST:
            intent = "faq"
    except Exception:  # noqa: BLE001
        intent, conf = "faq", 0.4
    return {"intent": intent, "intent_confidence": conf}


# --------------------------------------------------------------------------- #
# tool_node 系统提示词 — 注入完整业务规则（来自知识库）
# --------------------------------------------------------------------------- #
TOOL_SYSTEM = """你是XX外卖平台的AI客服助手，负责通过工具调用处理用户请求。

## 可用工具
- query_order：查询订单详情（订单状态、金额、地址、商品清单等）
- query_order_logistics：查询订单配送物流（骑手信息、预计送达、时间线）
- list_user_orders：查询当前用户的订单列表（无需参数）
- modify_address：修改订单收货地址（参数：order_id, new_address）
- refund_order：提交退单申请（参数：order_id）
- cancel_order：取消订单（参数：order_id）
- query_product：查询商品详情（参数：product_id）
- search_product：搜索商品（参数：keyword）
- query_user_info：查询用户信息（无需参数）

## 核心原则
1. 只能通过工具获取数据，严禁编造任何订单号、金额、状态、地址等信息。
2. 如果用户没有提供必要信息（如订单号），先向用户追问，不要猜测。
3. 所有修改类操作（modify_address、cancel_order、refund_order）必须先查询订单状态，再决定能否执行。

## 订单状态一览（按顺序）
待支付 → 待确认 → 备餐中 → 待取餐 → 配送中 → 已送达 → 已完成
（还有：已取消、退款中、已退款）

## 修改地址规则
1. 用户必须提供订单号 + 新地址，缺一不可。缺少时先追问用户。
2. 调用 query_order 查询订单当前状态。
3. 根据状态判断：
   - 待支付 / 待确认 /已支付→ 允许修改，调用 modify_address
   - 配送中 / 已送达 / 已完成 → 不允许修改，
     回复用户：「订单已进入{状态}阶段，无法修改地址。如需帮助，可联系人工客服。」
4. 绝对禁止在未查询订单状态的情况下直接调用 modify_address。

## 取消订单规则
1. 用户必须提供订单号。
2. 调用 query_order 查询订单状态。
3. 仅「待支付」状态可取消 → 调用 cancel_order。
4. 其他状态 → 回复用户：「订单状态为{状态}，无法直接取消。如已支付请使用退单功能，如需帮助可联系人工客服。」
5. 已取消的订单 → 告知用户订单已经取消，无需重复操作。

## 退单退款规则
1. 用户必须提供订单号。
2. 调用 query_order 查询订单状态。
3. 可退单的状态：待确认 / 备餐中 / 待取餐 / 配送中 / 已完成 → 调用 refund_order。
4. 待支付状态 → 订单尚未支付，无需退款，必须调用 cancel_order 取消订单。不要只回复文字说明。
5. 已取消 / 已退单 / 退款中 → 告知用户无法重复退单。

## 查询类操作
- 查订单状态、物流：直接调用 query_order 或 query_order_logistics。
- 用户没给订单号但想查订单：先调用 list_user_orders 获取列表，再根据用户选择的订单号查询。
- 查商品：直接调用 query_product 或 search_product。
- 查用户信息：直接调用 query_user_info。

## 重要提示
- 如果用户要求执行操作（取消、退款、修改地址），必须调用对应的工具函数，不要只生成文字回复而不调用工具。
- new_address 参数必须从用户原始消息中提取，严禁使用 query_order 返回的旧地址作为新地址。
- 如果查询结果显示订单状态不允许操作，直接回复用户说明原因，不要强行调用修改工具。
- 每次只调用当前步骤需要的工具，不要一次调用多个无关工具。"""


async def tool_node(state: dict) -> dict:
    set_java_context(state["user_id"], state["session_id"])
    llm = get_chat_model().bind_tools(ALL_TOOLS)
    user_msg = (
        f"用户昵称：{state.get('user_name') or '用户'}\n"
        f"用户ID：{state['user_id']}\n"
        f"用户意图：{state.get('intent', '')}\n"
        f"历史对话：\n{_format_history(state.get('history', []))}\n\n"
        f"用户当前消息：{state['user_input']}"
    )
    messages = [SystemMessage(content=TOOL_SYSTEM), HumanMessage(content=user_msg)]
    tool_results = dict(state.get("tool_results", {}))

    for _ in range(3):  # 简单 agent 循环，最多 3 轮工具调用
        resp = await llm.ainvoke(messages)
        tool_calls = getattr(resp, "tool_calls", None)
        if not tool_calls:
            break
        messages.append(resp)
        for tc in tool_calls:
            name = tc["name"]
            args = tc.get("args", {}) or {}
            tool = TOOL_MAP.get(name)
            if not tool:
                result = {"_error": True, "message": f"未知工具：{name}"}
            else:
                try:
                    result = await tool.ainvoke(args)
                except Exception as e:  # noqa: BLE001
                    result = {"_error": True, "message": f"工具执行失败：{e}"}
            tool_results[name] = result
            messages.append(ToolMessage(content=str(result), tool_call_id=tc["id"]))

    return {"tool_results": tool_results}


async def rag_node(state: dict) -> dict:
    docs = search_knowledge(state["user_input"], top_k=config.RAG_TOP_K)
    return {"retrieved_docs": [d["content"] for d in docs]}


async def reply_node(state: dict) -> dict:
    intent = state.get("intent", "faq")
    tr = state.get("tool_results", {})

    # ---- 明确转人工 ----
    if intent == "transfer_human":
        return {
            "final_reply": "您的问题需要人工客服处理，正在为您转接，请稍候...",
            "need_human": True,
            "transfer_reason": "用户明确要求转接人工客服",
            "tool_results": tr,
        }

    # ---- 退单/退款 ----
    if intent == "refund":
        refund_result = tr.get("refund_order")
        if refund_result and isinstance(refund_result, dict):
            if refund_result.get("_error"):
                return {
                    "final_reply": f"退单申请失败：{refund_result.get('message', '未知错误')}，请稍后重试或联系人工客服。",
                    "tool_results": tr,
                }
            if refund_result.get("needApproval"):
                return {
                    "final_reply": refund_result.get("message", "已生成审批单，等待人工客服处理。"),
                    "need_human": True,
                    "transfer_reason": "退单需人工客服审批",
                    "tool_results": tr,
                }
        # 未调用 refund_order（如待支付状态，应走 cancel_order），由正常回复流程处理

    # ---- 取消订单 ----
    if intent == "order_cancel":
        cancel_result = tr.get("cancel_order")
        if cancel_result and isinstance(cancel_result, dict):
            if cancel_result.get("_error"):
                return {
                    "final_reply": f"取消订单失败：{cancel_result.get('message', '未知错误')}，请稍后重试或联系人工客服。",
                    "tool_results": tr,
                }
            if cancel_result.get("needApproval"):
                return {
                    "final_reply": cancel_result.get("message", "已生成审批单，等待人工客服处理。"),
                    "need_human": True,
                    "transfer_reason": "取消订单需人工客服审批",
                    "tool_results": tr,
                }
            # 取消成功
            return {
                "final_reply": "已为您取消该订单，退款将按原支付方式退回。",
                "tool_results": tr,
            }
        # 未调用 cancel_order（状态不允许），由正常回复流程处理

    # ---- 修改地址 ----
    if intent == "order_modify":
        modify_result = tr.get("modify_address")
        if modify_result and isinstance(modify_result, dict):
            if modify_result.get("_error"):
                return {
                    "final_reply": f"修改地址失败：{modify_result.get('message', '未知错误')}，请稍后重试或联系人工客服。",
                    "tool_results": tr,
                }
            # 成功：_unwrap_response 已统一保证 success=true 必存在
            if modify_result.get("success") is True:
                new_addr = modify_result.get("newAddress", "新地址")
                return {
                    "final_reply": f"已为您将订单收货地址修改为「{new_addr}」，请注意查收。",
                    "tool_results": tr,
                }
            fail_msg = modify_result.get("message", "当前订单状态不支持修改地址")
            return {
                "final_reply": f"修改地址失败：{fail_msg}。如需帮助，可联系人工客服。",
                "tool_results": tr,
            }
        # 未调用 modify_address（状态不允许），由正常回复流程处理

    # ---- 正常回复流程（order_query / product_query / account_query / faq / chitchat） ----
    ctx = []
    if tr:
        ctx.append("【工具查询结果】\n" + format_tool_results(tr))
    if state.get("retrieved_docs"):
        ctx.append("【知识库参考】\n" + "\n\n".join(state["retrieved_docs"]))

    prompt = (
        f"用户昵称：{state.get('user_name') or '用户'}\n"
        f"历史对话：\n{_format_history(state.get('history', []))}\n"
        f"用户当前消息：{state['user_input']}\n"
    )
    if ctx:
        prompt += "\n" + "\n\n".join(ctx) + "\n"
    prompt += "\n请基于以上信息生成客服回复。"

    llm = get_chat_model()
    resp = await llm.ainvoke([SystemMessage(content=REPLY_SYSTEM), HumanMessage(content=prompt)])
    reply = _text(resp.content)

    # 工具全部失败 → 标记转人工
    if tr and all(isinstance(v, dict) and v.get("_error") for v in tr.values()):
        return {
            "final_reply": "抱歉，查询服务暂时不可用，已为您转接人工客服，请稍候。",
            "need_human": True,
            "transfer_reason": "后端服务调用失败",
            "tool_results": tr,
        }

    return {"final_reply": reply, "tool_results": tr}


def router_node(state: dict) -> str:
    """返回路由分支：end / transfer_human。两者最终都到 END，但 transfer 会带 need_human 标记。"""
    if state.get("need_human"):
        return "transfer_human"
    if state.get("intent_confidence", 1.0) < 0.6:
        return "transfer_human"
    return "end"