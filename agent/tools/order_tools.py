"""订单相关工具：查询订单、物流、用户订单列表、修改地址、退单/取消。"""
from langchain_core.tools import tool

from tools.java_client import call_java


@tool
async def query_order(order_id: str) -> dict:
    """查询订单详情。输入订单号，返回订单状态、金额、商品清单、配送地址等。"""
    return await call_java("queryOrder", {"orderId": order_id})


@tool
async def query_order_logistics(order_id: str) -> dict:
    """查询订单物流/配送状态。输入订单号，返回配送状态、骑手信息、预计送达时间、时间线。"""
    return await call_java("queryOrderLogistics", {"orderId": order_id})


@tool
async def list_user_orders() -> dict:
    """查询当前用户的订单列表。无需参数，自动获取当前用户最近的订单。"""
    return await call_java("listUserOrders", {})


@tool
async def modify_address(order_id: str, new_address: str) -> dict:
    """修改订单收货地址。输入订单号和新地址，返回修改结果。"""
    return await call_java("modifyAddress", {"orderId": order_id, "newAddress": new_address})


@tool
async def refund_order(order_id: str) -> dict:
    """
    提交退单申请。调用后端退单接口，通常需要人工审批。
    返回结果中 needApproval=true 表示已生成审批单，需人工客服处理。
    """
    return await call_java("refundOrder", {"orderId": order_id})


@tool
async def cancel_order(order_id: str) -> dict:
    """
    提交取消订单申请。调用后端取消接口，通常需要人工审批。
    返回结果中 needApproval=true 表示已生成审批单，需人工客服处理。
    """
    return await call_java("cancelOrder", {"orderId": order_id})