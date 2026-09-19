#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本地 Mock 服务（仅用于验证 auto_order.py 是否跑得通，不参与真实压测）

它按后端真实契约实现了全部接口，包括：
  - 注册 / 登录（含 test- 前缀验证码绕过）
  - 商品列表 / 详情 / 点赞数 / 搜索
  - 购物车增改查（X-User-Id 隔离，Redis 语义用内存字典模拟）
  - 下单（**严格复刻 OrderServiceImpl 的 BigDecimal 金额校验**）+ 确认支付
  - 客服 WebSocket /ws/chat

用法::

    python tools/mock_server.py --http-port 28080 --ws-port 28081

然后在另一个终端::

    python auto_order.py --gateway http://127.0.0.1:28080 --ws-port 28081 \
        --accounts 20 --workers 20 --target-orders 60 --runtime 3 --reset-progress

跑完直接 Ctrl+C 关掉 Mock 服务即可。
"""

from __future__ import annotations

import argparse
import asyncio
import json
import random
import time
from decimal import Decimal
from typing import Any, Dict, List

from aiohttp import web, WSMsgType


class Store:
    """内存版存储，语义对齐后端（MySQL 用户表 + Redis 购物车/订单）。"""

    def __init__(self) -> None:
        self.users: Dict[str, Dict[str, Any]] = {}          # username -> user
        self.tokens: Dict[str, str] = {}                    # token -> userId
        self.carts: Dict[str, Dict[str, Dict[str, Any]]] = {}   # userId -> {fid: item}
        self.orders: Dict[str, Dict[str, Any]] = {}         # roid -> order
        self.counter = 1000
        self.lock = asyncio.Lock()
        self.stats = {
            "register": 0, "login": 0, "product_list": 0, "product_detail": 0,
            "like_count": 0, "search": 0, "cart_add": 0, "cart_update": 0,
            "cart_list": 0, "order_create": 0, "order_confirm": 0,
            "ws_connect": 0, "amount_mismatch": 0, "auth_fail": 0,
            "reviews": 0, "review_stars": 0,
            "like_submit": 0, "like_net": 0, "dislike_net": 0, "like_toggle_off": 0,
        }
        self._uv_seen: set = set()                          # 用于模拟 UV 去重
        self._reviewed: set = set()                         # (userId, fid) 已评价集合
        self._likes: Dict[str, int] = {}                    # "userId|fid" -> likeType(1/-1/0)

    def next_id(self, prefix: str) -> str:
        self.counter += 1
        return f"{prefix}{self.counter:016d}"


STORE = Store()

FOODS: List[Dict[str, Any]] = [
    {"fid": "F0000000000001", "fname": "麻辣香锅", "normprice": 38.00, "realprice": 28.80,
     "category": "川菜", "detail": "麻辣鲜香", "fphoto": "http://img/1.jpg,http://img/2.jpg,"},
    {"fid": "F0000000000002", "fname": "宫保鸡丁", "normprice": 32.00, "realprice": 25.50,
     "category": "川菜", "detail": "酸甜微辣", "fphoto": "http://img/3.jpg,"},
    {"fid": "F0000000000003", "fname": "十三香小龙虾", "normprice": 128.00, "realprice": 99.99,
     "category": "小龙虾", "detail": "十三香配方", "fphoto": "http://img/4.jpg,"},
    {"fid": "F0000000000004", "fname": "蒜蓉生蚝", "normprice": 66.00, "realprice": 0.10,
     "category": "海鲜", "detail": "现开现烤", "fphoto": "http://img/5.jpg,"},
    {"fid": "F0000000000005", "fname": "手打柠檬茶", "normprice": 18.00, "realprice": 12.35,
     "category": "饮品", "detail": "手打更清爽", "fphoto": "http://img/6.jpg,"},
]


def ok(data: Any = None, msg: str = "success") -> web.Response:
    return web.json_response({"code": 200, "msg": msg, "data": data})


def fail(code: int, msg: str, http_status: int = 200) -> web.Response:
    return web.json_response({"code": code, "msg": msg}, status=http_status)


async def read_json(request: web.Request) -> Dict[str, Any]:
    try:
        return await request.json()
    except Exception:
        return {}


def require_user(request: web.Request) -> str:
    """对齐后端：网关会注入 X-User-Id，这里校验客户端有没有传。"""
    uid = request.headers.get("X-User-Id")
    if not uid:
        raise web.HTTPUnauthorized(text="missing X-User-Id")
    return uid


def require_token(request: web.Request) -> str:
    auth = request.headers.get("Authorization") or ""
    if not auth.startswith("Bearer "):
        raise web.HTTPUnauthorized(text="missing token")
    token = auth[7:]
    uid = STORE.tokens.get(token)
    if not uid:
        STORE.stats["auth_fail"] += 1
        raise web.HTTPUnauthorized(text="invalid token")
    return uid


# ---------------- 用户 ----------------
async def h_register(request: web.Request) -> web.Response:
    STORE.stats["register"] += 1
    body = await read_json(request)
    username = body.get("username") or ""
    if not username:
        return fail(-1001, "用户名不能为空")
    if username in STORE.users:
        return fail(-1005, "用户名已存在")
    if not str(body.get("captchaKey") or "").startswith("test-"):
        return fail(-1003, "验证码错误")
    user_id = STORE.next_id("U")
    user = {"userId": user_id, "username": username,
            "email": body.get("email"), "avatar": None, "role": "USER"}
    STORE.users[username] = user
    return ok(user)


async def h_login(request: web.Request) -> web.Response:
    STORE.stats["login"] += 1
    body = await read_json(request)
    username = body.get("username") or ""
    if not str(body.get("captchaKey") or "").startswith("test-"):
        return fail(-1003, "验证码错误")
    user = STORE.users.get(username)
    if not user or body.get("password") != "123456":
        return fail(-1004, "用户名或密码错误")
    token = f"mock.{user['userId']}.{int(time.time() * 1000)}"
    STORE.tokens[token] = user["userId"]
    return ok({"token": token, "userInfo": {"userId": user["userId"],
                                            "username": username, "role": "USER",
                                            "jti": token[-12:]}})


# ---------------- 商品 ----------------
async def h_product_list(request: web.Request) -> web.Response:
    STORE.stats["product_list"] += 1
    data = []
    for f in FOODS:
        d = dict(f)
        d["status"] = 1
        d["likeCount"] = 3
        d["dislikeCount"] = 0
        data.append(d)
    return ok(data)


async def h_product_detail(request: web.Request) -> web.Response:
    STORE.stats["product_detail"] += 1
    fid = request.match_info["fid"]
    for f in FOODS:
        if f["fid"] == fid:
            d = dict(f)
            d["status"] = 1
            return ok(d)
    return fail(404, "商品不存在或已下架")


async def h_like_count(request: web.Request) -> web.Response:
    STORE.stats["like_count"] += 1
    return ok({"likeCount": 3, "dislikeCount": 0})


# ---------------- 搜索 ----------------
async def h_search(request: web.Request) -> web.Response:
    require_token(request)   # 对齐真实后端：搜索需登录鉴权（修复前 search 会 401）
    STORE.stats["search"] += 1
    kw = request.query.get("keyword") or ""
    records = [f for f in FOODS if kw and kw in f["fname"]]
    return ok({"total": len(records), "page": 1, "size": 10, "records": records})


# ---------------- 购物车 ----------------
async def h_cart_add(request: web.Request) -> web.Response:
    STORE.stats["cart_add"] += 1
    uid = require_user(request)
    body = await read_json(request)
    fid = body.get("fid")
    if not fid:
        return fail(400, "fid 不能为空")
    cart = STORE.carts.setdefault(uid, {})
    num = int(body.get("num") or 1)
    if fid in cart:
        cart[fid]["num"] += num
    else:
        cart[fid] = {"fid": fid, "fname": body.get("fname"),
                     "realprice": body.get("realprice"),
                     "fphoto": body.get("fphoto"), "num": num}
    return ok("添加成功")


async def h_cart_list(request: web.Request) -> web.Response:
    STORE.stats["cart_list"] += 1
    uid = require_user(request)
    return ok(list(STORE.carts.get(uid, {}).values()))


async def h_cart_update(request: web.Request) -> web.Response:
    STORE.stats["cart_update"] += 1
    uid = require_user(request)
    fid = request.query.get("fid")
    num = request.query.get("num")
    cart = STORE.carts.get(uid, {})
    if fid not in cart:
        return fail(-2001, "购物车中不存在该商品")
    cart[fid]["num"] = int(num or 1)
    return ok("更新成功")


# ---------------- 订单 ----------------
async def h_order_create(request: web.Request) -> web.Response:
    STORE.stats["order_create"] += 1
    uid = require_user(request)
    require_token(request)
    body = await read_json(request)

    cart = STORE.carts.get(uid) or {}
    if not cart:
        return fail(-3001, "购物车为空")

    # !! 严格复刻 OrderServiceImpl.doCreateOrder 的金额校验 !!
    server_original = Decimal("0")
    items = []
    for it in cart.values():
        price = Decimal(str(it["realprice"]))
        num = int(it["num"])
        server_original += price * Decimal(num)
        items.append({"fid": it["fid"], "fname": it["fname"],
                      "dealprice": float(price), "num": num})

    front_original = Decimal(str(body.get("originalAmount")))
    front_expect = Decimal(str(body.get("expectAmount")))
    # 对齐后端 BigDecimal.compareTo 的数值比较语义
    if server_original != front_original:
        STORE.stats["amount_mismatch"] += 1
        return fail(-3002, f"PRICE_CHANGED: server={server_original} front={front_original}")
    if server_original != front_expect:
        STORE.stats["amount_mismatch"] += 1
        return fail(-3003, f"AMOUNT_MISMATCH: server={server_original} front={front_expect}")

    roid = STORE.next_id("O")
    order = {
        "roid": roid, "userid": uid, "uname": f"用户_{uid[:8]}",
        "address": body.get("address"), "tel": body.get("tel"),
        "orderTime": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "deliveryType": body.get("deliveryType") or "now",
        "payment": body.get("payment") or "alipay", "ps": body.get("ps"),
        "status": 0, "tradeno": f"ORD{int(time.time() * 1000)}",
        "payTime": None, "cancelTime": None, "items": items,
        "totalAmount": float(server_original), "discountAmount": 0.0,
        "payAmount": float(server_original),
    }
    STORE.orders[roid] = order
    STORE.carts.pop(uid, None)      # 后端 createOrder 会自动清空购物车
    return ok(order)


async def h_order_confirm(request: web.Request) -> web.Response:
    STORE.stats["order_confirm"] += 1
    require_token(request)
    roid = request.match_info["roid"]
    order = STORE.orders.get(roid)
    if not order:
        return fail(-3010, "订单不存在")
    if order["status"] != 0:
        return fail(-3011, "订单状态不允许支付")
    order["status"] = 1
    order["payTime"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    return ok("支付成功")


# ---------------- 客服 WebSocket ----------------
async def h_ws_chat(request: web.Request) -> web.WebSocketResponse:
    STORE.stats["ws_connect"] += 1
    ws = web.WebSocketResponse()
    await ws.prepare(request)
    uid = request.query.get("userId") or ""
    user_name = request.query.get("userName") or f"用户_{uid[:8]}"
    # 模拟后端：连上就推历史 + AI 欢迎语，之后保持连接等客户端断开
    await ws.send_json({"type": "system", "content": f"{user_name} 已接入人工客服"})
    await ws.send_json({"type": "ai", "content": "您好，我是智能客服小助手，请问有什么可以帮您？"})
    try:
        async for msg in ws:
            if msg.type == WSMsgType.ERROR:
                break
    except asyncio.CancelledError:
        raise
    except Exception:
        pass
    return ws


async def h_stats(request: web.Request) -> web.Response:
    return web.json_response({
        "stats": STORE.stats,
        "users": len(STORE.users),
        "orders": len(STORE.orders),
        "orders_paid": sum(1 for o in STORE.orders.values() if o["status"] == 1),
        "orders_unpaid": sum(1 for o in STORE.orders.values() if o["status"] == 0),
        "carts_open": len(STORE.carts),
    })


async def h_revoke_tokens(request: web.Request) -> web.Response:
    """测试用：把已签发的 Token 全部作废，用来验证客户端的 401 自动重登。"""
    n = len(STORE.tokens)
    STORE.tokens.clear()
    return web.json_response({"revoked": n})


async def h_ops_pv_track(request: web.Request) -> web.Response:
    """模拟 res-operations 的 /ops/pv/track 埋点接口。"""
    STORE.stats["pv_track"] = STORE.stats.get("pv_track", 0) + 1
    body = await read_json(request)
    pt = (body or {}).get("pageType") or "OTHER"
    fid = (body or {}).get("fid")
    key = f"pv:{pt}" + (f":{fid}" if fid else "")
    STORE.stats[key] = STORE.stats.get(key, 0) + 1
    uid = request.headers.get("X-User-Id")
    if uid:
        STORE.stats["uv_users"] = STORE.stats.get("uv_users", 0) + (1 if uid not in STORE._uv_seen else 0)
        STORE._uv_seen.add(uid)
    return ok("ok")


async def h_review(request: web.Request) -> web.Response:
    """模拟 res-product 的 POST /product/review（multipart 表单）。

    对齐真实后端的关键行为：
      - 需要 X-User-Id（真实后端缺该头时返回 -1「系统内部错误」）
      - starRating 必须在 1-5，否则 -3101
      - 一人一菜只能评一次，重复返回 -3103
    """
    uid = request.headers.get("X-User-Id")
    if not uid:
        return fail(-1, "系统内部错误")
    form = await request.post()
    fid = form.get("fid")
    try:
        star = int(form.get("starRating"))
    except (TypeError, ValueError):
        star = 0
    if star < 1 or star > 5:
        return fail(-3101, "星级评分必须在1-5之间")
    if (uid, fid) in STORE._reviewed:
        return fail(-3103, "您已经评价过该商品了")
    STORE._reviewed.add((uid, fid))
    STORE.stats["reviews"] += 1
    STORE.stats["review_stars"] += star
    return ok("评价成功")


async def h_like(request: web.Request) -> web.Response:
    """模拟 res-product 的 POST /product/like（**开关式**，与真实后端一致）。

    同一 (userId, fid) 用相同 likeType 再调一次 → 取消并扣减计数。
    所以 like_toggle_off > 0 就说明脚本重复点赞了（正常应为 0）。
    """
    uid = request.headers.get("X-User-Id")
    if not uid:
        return fail(-1, "系统内部错误")
    fid = request.query.get("fid")
    try:
        like_type = int(request.query.get("likeType"))
    except (TypeError, ValueError):
        like_type = 0
    if like_type not in (1, -1):
        return fail(-3006, "参数错误，likeType必须为1(赞)或-1(踩)")

    STORE.stats["like_submit"] += 1
    key = f"{uid}|{fid}"
    old = STORE._likes.get(key, 0)

    if old == like_type:                      # 同类型重复调用 → 取消
        STORE._likes[key] = 0
        STORE.stats["like_toggle_off"] += 1
        STORE.stats["like_net" if like_type == 1 else "dislike_net"] -= 1
        return ok({"status": "已取消"})

    STORE._likes[key] = like_type
    if like_type == 1:
        STORE.stats["like_net"] += 1
        if old == -1:
            STORE.stats["dislike_net"] -= 1
    else:
        STORE.stats["dislike_net"] += 1
        if old == 1:
            STORE.stats["like_net"] -= 1
    return ok({"status": "ok"})


def build_http_app() -> web.Application:
    app = web.Application()
    r = app.router
    r.add_post("/user/register", h_register)
    r.add_post("/user/login", h_login)
    r.add_get("/product/list", h_product_list)
    r.add_get("/product/detail/{fid}", h_product_detail)
    r.add_get("/product/like/{fid}/count", h_like_count)
    r.add_get("/search/food", h_search)
    r.add_post("/cart/add", h_cart_add)
    r.add_get("/cart/list", h_cart_list)
    r.add_post("/cart/update", h_cart_update)
    r.add_post("/order/create", h_order_create)
    r.add_post("/order/confirm/{roid}", h_order_confirm)
    r.add_post("/ops/pv/track", h_ops_pv_track)
    r.add_post("/product/review", h_review)
    r.add_post("/product/like", h_like)
    r.add_route("*", "/__stats", h_stats)
    r.add_route("*", "/__revoke_tokens", h_revoke_tokens)
    return app


def build_ws_app() -> web.Application:
    app = web.Application()
    app.router.add_get("/ws/chat", h_ws_chat)
    return app


async def amain(http_port: int, ws_port: int) -> None:
    http_runner = web.AppRunner(build_http_app())
    await http_runner.setup()
    await web.TCPSite(http_runner, "127.0.0.1", http_port).start()

    ws_runner = web.AppRunner(build_ws_app())
    await ws_runner.setup()
    await web.TCPSite(ws_runner, "127.0.0.1", ws_port).start()

    print(f"[mock] HTTP  http://127.0.0.1:{http_port}   (网关模拟)")
    print(f"[mock] WS    ws://127.0.0.1:{ws_port}/ws/chat")
    print(f"[mock] 统计  http://127.0.0.1:{http_port}/__stats")
    try:
        await asyncio.Event().wait()
    finally:
        await http_runner.cleanup()
        await ws_runner.cleanup()


def main() -> None:
    p = argparse.ArgumentParser(description="auto_order.py 的本地 Mock 后端")
    p.add_argument("--http-port", type=int, default=28080)
    p.add_argument("--ws-port", type=int, default=28081)
    args = p.parse_args()
    try:
        asyncio.run(amain(args.http_port, args.ws_port))
    except KeyboardInterrupt:
        print("\n[mock] 已停止")


if __name__ == "__main__":
    main()
