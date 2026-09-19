"""
工具聚合层：
- ALL_TOOLS：所有可供 LLM 调用的工具列表（用于 bind_tools）
- TOOL_MAP：名称 → 工具对象（用于按名执行）
- format_tool_results：把工具结果转成易读文本，供 reply_node 使用
"""
from tools.order_tools import (
    query_order,
    query_order_logistics,
    list_user_orders,
    modify_address,
    refund_order,
    cancel_order,
)
from tools.product_tools import query_product, search_product
from tools.user_tools import query_user_info

ALL_TOOLS = [
    query_order,
    query_order_logistics,
    list_user_orders,
    modify_address,
    query_product,
    search_product,
    query_user_info,
    refund_order,
    cancel_order,
]

TOOL_MAP = {t.name: t for t in ALL_TOOLS}


def format_tool_results(tool_results: dict) -> str:
    """将 {tool_name: result} 转换为可读文本。"""
    if not tool_results:
        return ""
    lines = []
    for name, result in tool_results.items():
        if isinstance(result, dict) and result.get("_error"):
            lines.append(f"[{name}] 调用失败：{result.get('message')}")
        else:
            lines.append(f"[{name}] {result}")
    return "\n".join(lines)