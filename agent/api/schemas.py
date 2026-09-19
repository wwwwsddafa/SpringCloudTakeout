"""请求 / 响应数据模型（Pydantic）。"""
from typing import List, Dict, Any, Optional

from pydantic import BaseModel


class ChatMessage(BaseModel):
    role: str          # "user" | "assistant"
    content: str


class ChatRequest(BaseModel):
    session_id: str
    user_id: str
    user_name: str
    message: str
    history: List[ChatMessage] = []


class ChatResponse(BaseModel):
    session_id: str
    reply: str
    # action: {"type": "none" | "transfer_to_human", "reason": "..."}
    action: Dict[str, str] = {"type": "none", "reason": ""}
    # tool_calls: [{ "tool": str, "result": Any }]
    tool_calls: List[Dict[str, Any]] = []
