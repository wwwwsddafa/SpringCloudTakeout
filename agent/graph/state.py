"""
LangGraph 状态定义。
意图：订单查询 / 退单 / 商品查询 / 账户查询 / 知识问答 / 闲聊 / 转人工。
"""
from typing import TypedDict, Annotated, List, Dict, Any, Optional

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


class CustomerServiceState(TypedDict):
    # ---- 会话元数据 ----
    session_id: str
    user_id: str
    user_name: str

    # ---- 对话历史（LangGraph 用 add_messages 自动合并）----
    messages: Annotated[List[BaseMessage], add_messages]

    # 原始历史（来自 Java 端的最近 N 轮，仅作上下文，不进入 messages 合并）
    history: List[Dict[str, str]]

    # ---- 当前用户输入 ----
    user_input: str

    # ---- 意图识别结果 ----
    intent: str            # order_query / refund / product_query / account_query / faq / chitchat / transfer_human
    intent_confidence: float

    # ---- 工具调用结果 ----
    tool_results: Dict[str, Any]

    # ---- RAG 检索结果 ----
    retrieved_docs: List[str]

    # ---- 最终回复 ----
    final_reply: str

    # ---- 转人工 ----
    need_human: bool
    transfer_reason: str
