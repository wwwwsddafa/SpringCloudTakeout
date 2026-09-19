"""
聊天接口：
  POST /api/chat        非流式
  POST /api/chat/stream SSE 流式（打字机）

请求体见 api.schemas.ChatRequest。
"""
import json

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from graph.builder import graph
from graph.debug import ainvoke_with_logging  # 非侵入式调试日志
from api.schemas import ChatRequest, ChatResponse

router = APIRouter()


def _build_state(req: ChatRequest) -> dict:
    return {
        "session_id": req.session_id,
        "user_id": req.user_id,
        "user_name": req.user_name,
        "messages": [],
        "history": [{"role": m.role, "content": m.content} for m in req.history],
        "user_input": req.message,
        "intent": "",
        "intent_confidence": 0.0,
        "tool_results": {},
        "retrieved_docs": [],
        "final_reply": "",
        "need_human": False,
        "transfer_reason": "",
    }


def _tool_calls_list(tool_results: dict) -> list:
    return [{"tool": k, "result": v} for k, v in (tool_results or {}).items()]


def _action(result: dict) -> dict:
    need = bool(result.get("need_human"))
    return {
        "type": "transfer_to_human" if need else "none",
        "reason": result.get("transfer_reason", "") if need else "",
    }


@router.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest) -> ChatResponse:
    """非流式聊天：返回完整回复 + 转人工标记 + 工具调用结果。"""
    state = _build_state(req)
    try:
        result = await ainvoke_with_logging(graph, state)
    except Exception as e:  # noqa: BLE001
        return ChatResponse(
            session_id=req.session_id,
            reply=f"服务暂时异常：{e}",
            action={"type": "none", "reason": ""},
        )

    return ChatResponse(
        session_id=req.session_id,
        reply=result.get("final_reply") or "（暂无回复）",
        action=_action(result),
        tool_calls=_tool_calls_list(result.get("tool_results")),
    )


@router.post("/chat/stream")
async def chat_stream(req: ChatRequest):
    """SSE 流式聊天：逐 token 推送，结尾以 done 事件携带工具结果与转人工标记。"""
    state = _build_state(req)

    async def generate():
        final = {}
        try:
            async for event in graph.astream_events(state, version="v2"):
                kind = event.get("event")
                meta = event.get("metadata", {})

                # 仅捕获 reply 节点的流式输出（避免 intent/tool 节点的 token 泄漏）
                if kind == "on_chat_model_stream" and meta.get("langgraph_node") == "reply":
                    chunk = event["data"]["chunk"]
                    content = chunk.content if isinstance(chunk.content, str) else ""
                    if content:
                        yield f"event: token\ndata: {json.dumps({'content': content}, ensure_ascii=False)}\n\n"

                # 图运行结束时的完整状态（node=None 的 on_chain_end 给出最终全量状态）
                elif kind == "on_chain_end" and not meta.get("langgraph_node"):
                    final = event["data"].get("output", {}) or {}
        except Exception as e:  # noqa: BLE001
            yield f"event: error\ndata: {json.dumps({'message': str(e)}, ensure_ascii=False)}\n\n"
            return

        # 兜底：若未捕获到最终状态（个别版本事件差异），补跑一次拿到结果
        if not final:
            try:
                final = await graph.ainvoke(state) or {}
            except Exception:  # noqa: BLE001
                final = {}

        done = {
            "session_id": req.session_id,
            "tool_calls": _tool_calls_list(final.get("tool_results")),
            "action": _action(final),
        }
        yield f"event: done\ndata: {json.dumps(done, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"X-Accel-Buffering": "no", "Cache-Control": "no-cache"},
    )