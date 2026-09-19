"""
统一的 Java 微服务调用客户端。
- 生产模式：通过 POST /customer-service/agent/command/execute 调用命令。
- MOCK 模式（MOCK_JAVA=true）：无需真实后端，返回模拟数据，便于离线开发联调。
- call_java_transfer：调用转人工接口 POST /customer-service/agent/ai-transfer。
- 任何网络/解析异常都被捕获，转换为结构化错误，绝不让 AI 服务崩溃。
"""
import contextvars
import httpx

import config

# 线程安全上下文，由 tool_node 注入
_ctx_user_id = contextvars.ContextVar("ctx_user_id", default="")
_ctx_session_id = contextvars.ContextVar("ctx_session_id", default="")


def set_java_context(user_id: str, session_id: str):
    """在 tool_node 中调用，注入当前会话上下文。"""
    _ctx_user_id.set(user_id)
    _ctx_session_id.set(session_id)


async def call_java(command_name: str, params: dict = None) -> dict:
    """调用 Java 命令接口，返回 dict。失败时返回 {"_error": True, ...}。"""
    if config.MOCK_JAVA:
        return _mock_command(command_name, params or {})

    user_id = _ctx_user_id.get()
    session_id = _ctx_session_id.get()

    url = f"{config.JAVA_GATEWAY_URL}/customer-service/agent/command/execute"
    body = {
        "commandName": command_name,
        "targetUserId": user_id,
        "sessionId": session_id,
        "params": params or {},
    }
    try:
        async with httpx.AsyncClient(timeout=config.JAVA_HTTP_TIMEOUT) as client:
            resp = await client.post(
                url,
                headers={"X-User-Id": "ai_agent", "Content-Type": "application/json"},
                json=body,
            )
            resp.raise_for_status()
            try:
                data = resp.json()
            except ValueError:
                return {"_error": True, "message": "后端返回非 JSON 数据", "command": command_name}
            return _unwrap_response(data, command_name)
    except httpx.HTTPStatusError as e:
        return {"_error": True, "message": f"后端返回错误状态码 {e.response.status_code}", "command": command_name}
    except httpx.HTTPError as e:
        return {"_error": True, "message": f"无法连接后端服务：{e}", "command": command_name}
    except Exception as e:  # noqa: BLE001
        return {"_error": True, "message": f"调用后端时发生异常：{e}", "command": command_name}


async def call_java_transfer(session_id: str, user_id: str, reason: str = "") -> dict:
    """调用 Java 转人工接口。"""
    if config.MOCK_JAVA:
        return {
            "code": 200, "message": "已转接人工客服",
            "data": {"sessionId": session_id, "assignedAgentId": "1", "status": "assigned", "reason": reason or "AI 无法处理"},
        }

    url = f"{config.JAVA_GATEWAY_URL}/customer-service/agent/ai-transfer"
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.post(
                url,
                json={"sessionId": session_id, "userId": user_id, "reason": reason or "AI 无法处理该问题"},
            )
            resp.raise_for_status()
            try:
                return resp.json()
            except ValueError:
                return {"_error": True, "message": "转人工接口返回非 JSON 数据"}
    except httpx.HTTPError as e:
        return {"_error": True, "message": f"转人工接口调用失败：{e}"}
    except Exception as e:  # noqa: BLE001
        return {"_error": True, "message": f"转人工接口异常：{e}"}


def _unwrap_response(data: dict, command_name: str) -> dict:
    """解包 Java 统一响应 {code, message, data: {success, message, data, displayType, needApproval}}。

    注意：code=200 不代表操作成功，必须检查 data.success 字段。
    """
    if data.get("code") != 200:
        return {"_error": True, "message": data.get("message", "未知错误"), "command": command_name}
    inner = data.get("data") or {}
    if isinstance(inner, dict):
        if inner.get("success") is False:
            return {"_error": True, "message": inner.get("message", "未知错误"), "command": command_name}
        if inner.get("needApproval"):
            return inner
        if "data" in inner:
            result = inner["data"]
            if isinstance(result, dict):
                result["_displayType"] = inner.get("displayType", "")
            return result
    return inner


# --------------------------------------------------------------------------- #
# MOCK 数据
# --------------------------------------------------------------------------- #
def _mock_command(command_name: str, params: dict) -> dict:
    user_id = _ctx_user_id.get()
    return _MOCK_MAP.get(command_name, lambda p: {"_error": True, "message": f"未匹配的 MOCK 命令：{command_name}"})(params, user_id)


def _mock_query_order(params, _uid):
    oid = params.get("orderId", "unknown")
    return {
        "orderId": oid, "status": "DELIVERING", "statusText": "配送中",
        "amount": 38.5, "shopName": "老王家黄焖鸡米饭",
        "items": [{"name": "黄焖鸡米饭", "price": 22.0, "qty": 1},
                  {"name": "可乐", "price": 6.5, "qty": 2}],
        "address": "XX市XX区XX路123号", "createTime": "2026-09-05 10:42:11",
    }


def _mock_query_order_logistics(params, _uid):
    return {
        "status": "DELIVERING", "statusText": "配送中",
        "riderName": "李师傅", "riderPhone": "138****8888",
        "estimatedDelivery": "2026-09-05 11:15:00",
        "timeline": [
            {"time": "10:42", "text": "商家已接单"},
            {"time": "10:55", "text": "骑手已取餐"},
            {"time": "11:02", "text": "配送中，距您 1.2km"},
        ],
    }


def _mock_list_user_orders(_params, user_id):
    return {
        "userId": user_id, "total": 12, "list": [
            {"orderId": "2096213091871096832", "statusText": "配送中", "amount": 38.5},
            {"orderId": "2096213091871096001", "statusText": "已送达", "amount": 25.0},
        ],
    }


def _mock_query_product(params, _uid):
    pid = params.get("productId", "unknown")
    return {"productId": pid, "name": "招牌黄焖鸡米饭", "price": 22.0,
            "description": "精选三黄鸡，秘制酱料，配米饭与时蔬。", "sales": 2300}


def _mock_search_product(params, _uid):
    kw = params.get("keyword", "")
    return {"keyword": kw, "list": [
        {"productId": "p1001", "name": f"{kw}套餐", "price": 29.9},
        {"productId": "p1002", "name": f"招牌{kw}", "price": 22.0},
    ]}


def _mock_query_user_info(_params, user_id):
    return {"userId": user_id, "userName": "张三", "phone": "139****1234",
            "memberLevel": "金卡会员", "balance": 12.5}


def _mock_modify_address(params, _uid):
    return {"orderId": params.get("orderId"), "newAddress": params.get("newAddress"),
            "status": "success", "message": "收货地址已修改"}


def _mock_refund_order(params, _uid):
    return {"needApproval": True, "approvalId": "APR_20260906_001",
            "message": "此操作需要审批，已生成审批单"}


def _mock_cancel_order(params, _uid):
    return {"needApproval": True, "approvalId": "APR_20260906_002",
            "message": "此操作需要审批，已生成审批单"}


_MOCK_MAP = {
    "queryOrder": _mock_query_order,
    "queryOrderLogistics": _mock_query_order_logistics,
    "listUserOrders": _mock_list_user_orders,
    "queryProduct": _mock_query_product,
    "searchProduct": _mock_search_product,
    "queryUserInfo": _mock_query_user_info,
    "modifyAddress": _mock_modify_address,
    "refundOrder": _mock_refund_order,
    "cancelOrder": _mock_cancel_order,
}