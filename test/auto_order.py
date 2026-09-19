#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
自动化下单压测脚本
================================================================
按 docs/auto-order-script-spec.md 实现：

  注册测试账号 -> 登录拿 Token -> 预加载商品 -> 分配设备类型/行为分群
  -> 并发执行四类 Worker（浏览 / 下单不支付 / 下单并支付 / 客服模拟）

用法::

    python auto_order.py                       # 按 config.py 全量运行
    python auto_order.py --accounts 20 --runtime 10 --workers 20
    python auto_order.py --target-orders 500   # 只跑 500 笔下单就停
    python auto_order.py --ramp                # 并发梯度压测模式（1/10/20/50）
    python auto_order.py --reset-progress      # 忽略断点，从头开始
    python auto_order.py --selftest            # 离线自检（不发任何请求）

输出文件（与脚本同目录）：
    progress.json           断点续传进度
    auto-order.log          汇总日志
    auto-order-error.log    错误日志
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
import random
import sys
import time
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any, Dict, List, Optional, Sequence
from urllib.parse import urlencode

import aiohttp
from tqdm import tqdm

import config as cfg

try:  # websockets 缺失时只影响客服模拟 Worker，不影响主流程
    import websockets
except Exception:  # pragma: no cover
    websockets = None


# ============================================================
# 路径与编码
# ============================================================
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROGRESS_PATH = os.path.join(BASE_DIR, cfg.PROGRESS_FILE)
LOG_PATH = os.path.join(BASE_DIR, cfg.LOG_FILE)
ERROR_LOG_PATH = os.path.join(BASE_DIR, cfg.ERROR_LOG_FILE)

# 保持控制台原有编码（Windows 下通常是 GBK），只把错误策略改成 replace，
# 避免个别字符直接把脚本打挂。
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(errors="replace")
    except Exception:
        pass


log = logging.getLogger("auto_order")
error_log = logging.getLogger("auto_order.error")

# 行为分群标识
B_BROWSE = "browse"
B_ORDER_UNPAID = "order_unpaid"
B_ORDER_PAID = "order_paid"
B_CS = "cs"

BEHAVIOR_LABEL = {
    B_BROWSE: "浏览型",
    B_ORDER_UNPAID: "下单不支付",
    B_ORDER_PAID: "下单并支付",
    B_CS: "客服模拟",
}

DEVICE_ORDER = ("DESKTOP", "MOBILE", "TABLET")


class _TqdmLogHandler(logging.Handler):
    """把 WARNING 及以上的日志安全地打在进度条上方，而不是把进度条冲乱。"""

    def emit(self, record: logging.LogRecord) -> None:
        try:
            tqdm.write(self.format(record), file=sys.stdout)
        except Exception:
            pass


def setup_logging() -> None:
    if log.handlers:  # 避免重复初始化
        return
    log.setLevel(logging.DEBUG)
    log.propagate = False
    error_log.setLevel(logging.WARNING)
    error_log.propagate = False

    fmt = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s", "%Y-%m-%d %H:%M:%S")

    main_fh = logging.FileHandler(LOG_PATH, mode="a", encoding="utf-8")
    main_fh.setLevel(logging.INFO)
    main_fh.setFormatter(fmt)

    err_fh = logging.FileHandler(ERROR_LOG_PATH, mode="a", encoding="utf-8")
    err_fh.setLevel(logging.WARNING)
    err_fh.setFormatter(fmt)

    console = _TqdmLogHandler()
    console.setLevel(logging.WARNING)
    console.setFormatter(logging.Formatter("[%(levelname)s] %(message)s"))

    log.addHandler(main_fh)
    log.addHandler(console)
    error_log.addHandler(err_fh)
    error_log.addHandler(console)


def say(msg: str = "") -> None:
    """带进度条保护的控制台输出。"""
    try:
        tqdm.write(msg, file=sys.stdout)
    except Exception:
        print(msg)


# ============================================================
# 工具函数
# ============================================================
def random_tel() -> str:
    return random.choice(cfg.TEL_PREFIX_POOL) + "".join(random.choices("0123456789", k=8))


def first_photo(fphoto: Optional[str]) -> str:
    """fphoto 形如 'http://a,http://b,'，取第一张。"""
    if not fphoto:
        return ""
    for part in str(fphoto).split(","):
        part = part.strip()
        if part:
            return part
    return ""


def calc_amount(items: Sequence[Dict[str, Any]]) -> Decimal:
    """按后端口径计算购物车总金额：SUM(realprice × num)。

    后端 OrderServiceImpl 用 BigDecimal 做 compareTo 精确比较，
    所以这里全程用 Decimal，绝不能先转 float 再相加
    （否则会出现 144.00000000000003 之类的误差导致 PRICE_CHANGED）。
    """
    total = Decimal("0")
    for it in items:
        price = Decimal(str(it.get("realprice", "0")))
        num = int(it.get("num") or 0)
        total += price * Decimal(num)
    return total


def amount_to_json_number(value: Decimal) -> float:
    """Decimal -> JSON 数字（float 的 repr 可无损往返，Jackson 解析为 BigDecimal）。"""
    return float(value)


def username_of(index: int) -> str:
    return f"{cfg.ACCOUNT_PREFIX}{index:03d}"


def weighted_choice(options: Sequence[str], weights: Sequence[float]) -> str:
    return random.choices(list(options), weights=list(weights), k=1)[0]


# ============================================================
# 异常
# ============================================================
class Unauthorized(Exception):
    """HTTP 401 / Token 失效。"""


class RetryableError(Exception):
    """网络抖动、5xx、限流等，可以退避重试。"""


class ApiError(Exception):
    """后端返回业务错误码（code != 200）或 4xx，重试无意义。"""

    def __init__(self, code: Any, msg: str, path: str = ""):
        self.code = code
        self.msg = msg
        self.path = path
        super().__init__(f"[{code}] {msg} ({path})")


class BusinessSkip(Exception):
    """本次迭代无法继续（如购物车为空），跳过这一轮即可。"""


# 这些业务码属于「可预期的跳过」，不算错误、不告警：
#   -2003 请勿重复提交订单 —— 后端用户级下单锁被占（多为下单超时重试撞上）
#   -2004 购物车为空，无法下单 —— 下单超时重试，而服务端其实已建单并清空购物车
#   -3004 只有购买过该商品的用户才能评价 —— 未支付过该菜，正常跳过
#   -3103 您已经评价过该商品了 —— 一人一菜只能评一次，正常跳过
EXPECTED_SKIP_CODES = {-2003, -2004, -3004, -3103}


# ============================================================
# 统计
# ============================================================
class Stats:
    def __init__(self) -> None:
        self.browse_pv = 0
        self.orders_unpaid = 0
        self.orders_paid = 0
        self.cs_connects = 0
        self.reviews = 0
        self.likes = 0
        self.errors = 0
        self.requests = 0
        self.retries = 0
        self.device_pv: Dict[str, int] = {d: 0 for d in DEVICE_ORDER}
        self.device_orders: Dict[str, int] = {d: 0 for d in DEVICE_ORDER}
        self.started_at = time.monotonic()
        self.qps = 0.0

    @property
    def total_orders(self) -> int:
        return self.orders_unpaid + self.orders_paid

    def add_pv(self, device: str) -> None:
        self.browse_pv += 1
        self.device_pv[device] = self.device_pv.get(device, 0) + 1

    def add_order(self, device: str, paid: bool) -> int:
        if paid:
            self.orders_paid += 1
        else:
            self.orders_unpaid += 1
        self.device_orders[device] = self.device_orders.get(device, 0) + 1
        return self.total_orders

    def refresh_qps(self, elapsed: float) -> None:
        self.qps = self.requests / elapsed if elapsed > 0 else 0.0

    def to_progress(self) -> Dict[str, Any]:
        return {
            "completed": self.total_orders,
            "failed": self.errors,
            "lastUpdate": time.strftime("%Y-%m-%dT%H:%M:%S"),
            # ---- 以下为便于恢复统计口径的扩展字段 ----
            "browse_pv": self.browse_pv,
            "orders_unpaid": self.orders_unpaid,
            "orders_paid": self.orders_paid,
            "cs_connects": self.cs_connects,
            "reviews": self.reviews,
            "likes": self.likes,
            "requests": self.requests,
            "device_pv": dict(self.device_pv),
            "device_orders": dict(self.device_orders),
        }

    def load_progress(self, data: Dict[str, Any]) -> None:
        self.browse_pv = int(data.get("browse_pv", 0) or 0)
        self.orders_unpaid = int(data.get("orders_unpaid", 0) or 0)
        self.orders_paid = int(data.get("orders_paid", 0) or 0)
        self.cs_connects = int(data.get("cs_connects", 0) or 0)
        self.reviews = int(data.get("reviews", 0) or 0)
        self.likes = int(data.get("likes", 0) or 0)
        self.errors = int(data.get("failed", 0) or 0)
        self.requests = int(data.get("requests", 0) or 0)
        for k, v in (data.get("device_pv") or {}).items():
            self.device_pv[k] = int(v)
        for k, v in (data.get("device_orders") or {}).items():
            self.device_orders[k] = int(v)


def save_progress(stats: Stats) -> None:
    tmp = PROGRESS_PATH + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(stats.to_progress(), f, ensure_ascii=False, indent=2)
        os.replace(tmp, PROGRESS_PATH)
    except Exception as exc:  # pragma: no cover
        error_log.warning("写入进度文件失败: %s", exc)


def load_progress() -> Optional[Dict[str, Any]]:
    if not os.path.exists(PROGRESS_PATH):
        return None
    try:
        with open(PROGRESS_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as exc:  # pragma: no cover
        error_log.warning("读取进度文件失败，忽略: %s", exc)
        return None


# ============================================================
# 全局 QPS 限流（令牌桶，MAX_QPS<=0 时不限速）
# ============================================================
class QpsLimiter:
    def __init__(self, qps: int) -> None:
        self.qps = float(qps or 0)
        self._tokens = self.qps
        self._updated = time.monotonic()
        self._lock = asyncio.Lock()

    async def acquire(self) -> None:
        if self.qps <= 0:
            return
        async with self._lock:
            now = time.monotonic()
            self._tokens = min(self.qps, self._tokens + (now - self._updated) * self.qps)
            self._updated = now
            if self._tokens >= 1:
                self._tokens -= 1
                return
            wait = (1 - self._tokens) / self.qps
            self._tokens -= 1  # 预支
        await asyncio.sleep(wait)


# ============================================================
# 账号
# ============================================================
@dataclass
class Account:
    username: str
    password: str
    token: Optional[str] = None
    user_id: Optional[str] = None
    device: str = "DESKTOP"
    ua: str = ""
    behavior: str = B_BROWSE
    reviewed_fids: set = field(default_factory=set)   # 本账号已评价过的菜品，避免重复提交
    liked_fids: set = field(default_factory=set)      # 本账号已赞/踩过的菜品（后端开关式，重复调用会取消）

    @property
    def ready(self) -> bool:
        return bool(self.token and self.user_id)


# ============================================================
# HTTP 客户端
# ============================================================
class HttpClient:
    def __init__(self, session: aiohttp.ClientSession, stats: Stats) -> None:
        self.session = session
        self.stats = stats
        self.limiter = QpsLimiter(cfg.MAX_QPS)
        self._relogin_lock = asyncio.Lock()

    # ---------- 底层 ----------
    async def _send(self, method: str, url: str, headers: Dict[str, str],
                    params: Optional[Dict[str, Any]],
                    body: Optional[Any], path: str,
                    form: Optional[Any] = None) -> Dict[str, Any]:
        await self.limiter.acquire()
        try:
            req_kwargs: Dict[str, Any] = {
                "headers": headers, "params": params, "allow_redirects": False,
            }
            if form is not None:
                # multipart/form-data：aiohttp.FormData 会自动带 boundary，
                # 不能手动设置 Content-Type（否则 boundary 丢失）
                req_kwargs["data"] = form
            else:
                req_kwargs["json"] = body
            async with self.session.request(method, url, **req_kwargs) as resp:
                text = await resp.text()
                self.stats.requests += 1
                if resp.status == 401:
                    raise Unauthorized(f"401 {text[:120]}")
                if resp.status == 403:
                    raise ApiError(403, f"无权限: {text[:120]}", path)
                if resp.status >= 500 or resp.status == 429:
                    raise RetryableError(f"HTTP {resp.status}: {text[:160]}")
                if resp.status != 200:
                    raise ApiError(resp.status, f"HTTP {resp.status}: {text[:160]}", path)
                try:
                    return json.loads(text)
                except json.JSONDecodeError as exc:
                    raise RetryableError(f"响应不是 JSON: {text[:160]}") from exc
        except (aiohttp.ClientError, asyncio.TimeoutError) as exc:
            raise RetryableError(f"{type(exc).__name__}: {exc}") from exc

    async def relogin(self, account: Account) -> bool:
        """Token 失效时重新登录。"""
        async with self._relogin_lock:
            try:
                await login_one(self, account)
                log.info("Token 重新获取成功: %s", account.username)
                return True
            except Exception as exc:
                error_log.warning("重新登录失败: %s -> %s", account.username, exc)
                return False

    # ---------- 高层 ----------
    async def call(
        self,
        method: str,
        path: str,
        *,
        account: Optional[Account] = None,
        auth: bool = False,
        user_id: bool = False,
        body: Optional[Any] = None,
        params: Optional[Dict[str, Any]] = None,
        expect_business_ok: bool = True,
        retryable: bool = True,
        form: Optional[Any] = None,
    ) -> Dict[str, Any]:
        """发一个请求并解包统一响应体。

        expect_business_ok=True  -> code != 200 抛 ApiError
        expect_business_ok=False -> 原样返回整个响应体（用于探测"用户名已存在"）
        retryable=True  -> 网络抖动/超时会自动重试（适用于 GET 等幂等请求）
        retryable=False -> 超时不重试（适用于 /order/create 等非幂等请求，
                           避免"重发导致重复下单"）
        form=aiohttp.FormData() -> 以 multipart/form-data 提交（用于 /product/review）
        """
        url = cfg.GATEWAY_URL + path
        attempt = 0
        relogin_done = False

        while True:
            attempt += 1
            headers: Dict[str, str] = {"Accept": "application/json"}
            if account is not None and account.ua:
                headers["User-Agent"] = account.ua
            if auth:
                if account is None or not account.token:
                    raise ApiError(401, "缺少 token，无法发起认证请求", path)
                headers["Authorization"] = f"Bearer {account.token}"
            if user_id:
                if account is None or not account.user_id:
                    raise ApiError(400, "缺少 X-User-Id", path)
                headers["X-User-Id"] = account.user_id
            if body is not None and form is None:
                headers["Content-Type"] = "application/json"

            try:
                payload = await self._send(method, url, headers, params, body, path, form)
            except Unauthorized as exc:
                # Token 过期：重登一次后重试
                if auth and account is not None and not relogin_done:
                    relogin_done = True
                    self.stats.retries += 1
                    log.info("收到 401，尝试重新登录: %s", account.username)
                    if await self.relogin(account):
                        continue
                self.stats.errors += 1
                error_log.warning("认证失败: %s %s -> %s", method, path, exc)
                raise ApiError(401, "认证失败", path) from exc
            except RetryableError as exc:
                # 非幂等请求（如 /order/create）超时不重试：重发可能造成重复下单
                if not retryable:
                    raise BusinessSkip(f"非幂等请求超时放弃重试: {exc}")
                if attempt >= cfg.MAX_RETRIES:
                    self.stats.errors += 1
                    error_log.warning("重试耗尽: %s %s -> %s", method, path, exc)
                    raise
                self.stats.retries += 1
                delay = cfg.RETRY_BACKOFF_BASE * (2 ** (attempt - 1))
                log.debug("请求失败将重试(%d/%d): %s %s -> %s",
                          attempt, cfg.MAX_RETRIES, method, path, exc)
                await asyncio.sleep(delay)
                continue

            code = payload.get("code")
            if code != 200:
                if expect_business_ok:
                    if code in EXPECTED_SKIP_CODES:
                        # 可预期的业务拒绝（重复提交/购物车为空）：跳过本轮即可，
                        # 不计入错误数、不写 error 日志，交由 Worker 按 info 处理
                        raise BusinessSkip(f"[{code}] {payload.get('msg')} ({path})")
                    self.stats.errors += 1
                    raise ApiError(code, str(payload.get("msg")), path)
                return payload
            return payload


# ============================================================
# 账号准备
# ============================================================
def assign_devices(accounts: List[Account]) -> Dict[str, int]:
    """按 DESKTOP/MOBILE/TABLET 占比给每个账号分配一个固定设备 + UA。"""
    n = len(accounts)
    n_desktop = round(n * cfg.DEVICE_DESKTOP_RATIO)
    n_mobile = round(n * cfg.DEVICE_MOBILE_RATIO)
    n_tablet = max(0, n - n_desktop - n_mobile)
    bucket = ["DESKTOP"] * n_desktop + ["MOBILE"] * n_mobile + ["TABLET"] * n_tablet
    while len(bucket) < n:
        bucket.append("DESKTOP")
    bucket = bucket[:n]
    random.shuffle(bucket)

    for acct, device in zip(accounts, bucket):
        acct.device = device
        acct.ua = random.choice(cfg.UA_POOL[device])

    return {d: sum(1 for a in accounts if a.device == d) for d in DEVICE_ORDER}


def assign_behaviors(accounts: List[Account]) -> Dict[str, List[Account]]:
    """按行为占比切分账号（设备与行为独立交叉随机）。"""
    pool = accounts[:]
    random.shuffle(pool)
    n = len(pool)
    n_browse = round(n * cfg.BEHAVIOR_BROWSE_RATIO)
    n_unpaid = round(n * cfg.BEHAVIOR_ORDER_UNPAID_RATIO)
    n_paid = round(n * cfg.BEHAVIOR_ORDER_PAID_RATIO)
    n_cs = max(0, n - n_browse - n_unpaid - n_paid)

    groups = {
        B_BROWSE: pool[:n_browse],
        B_ORDER_UNPAID: pool[n_browse:n_browse + n_unpaid],
        B_ORDER_PAID: pool[n_browse + n_unpaid:n_browse + n_unpaid + n_paid],
        B_CS: pool[n_browse + n_unpaid + n_paid:n_browse + n_unpaid + n_paid + n_cs],
    }
    for behavior, members in groups.items():
        for acct in members:
            acct.behavior = behavior
    return groups


async def login_one(http: HttpClient, account: Account) -> None:
    body = {
        "username": account.username,
        "password": account.password,
        "captchaKey": cfg.CAPTCHA_KEY,
        "captcha": cfg.CAPTCHA_VALUE,
    }
    payload = await http.call("POST", "/user/login", account=account, body=body)
    data = payload.get("data") or {}
    token = data.get("token")
    info = data.get("userInfo") or {}
    user_id = info.get("userId")
    if not token or not user_id:
        raise ApiError(-1, f"登录响应缺少 token/userId: {str(payload)[:200]}", "/user/login")
    account.token = token
    account.user_id = user_id


async def register_one(http: HttpClient, account: Account, index: int, total: int) -> str:
    body = {
        "username": account.username,
        "password": account.password,
        "email": f"{account.username}{cfg.ACCOUNT_EMAIL_SUFFIX}",
        "captchaKey": cfg.CAPTCHA_KEY,
        "captcha": cfg.CAPTCHA_VALUE,
    }
    try:
        payload = await http.call("POST", "/user/register", account=account,
                                  body=body, expect_business_ok=False)
    except ApiError as exc:
        # 兼容后端把业务异常直接映射成非 200 HTTP 状态的情况
        if "已存在" in str(exc.msg) or exc.code in (-1005, -1002):
            log.info("注册 [%d/%d] %s -> 已存在", index, total, account.username)
            return "exists"
        raise

    code = payload.get("code")
    msg = str(payload.get("msg") or "")
    if code == 200:
        result = "ok"
    elif "已存在" in msg or code in (-1005, -1002):
        result = "exists"
    else:
        result = "fail"
    log.info("注册 [%d/%d] %s -> %s (%s)", index, total, account.username, result, msg)
    return result


async def prepare_accounts(http: HttpClient, accounts: List[Account], stats: Stats) -> List[Account]:
    total = len(accounts)

    # ---- 1.1 批量注册 ----
    say(f"[1/5] 注册 {total} 个测试账号 ...")
    n_ok = n_exists = n_fail = 0
    for i, acct in enumerate(accounts, start=1):
        try:
            result = await register_one(http, acct, i, total)
        except Exception as exc:
            result = "fail"
            error_log.warning("注册异常 %s: %s", acct.username, exc)
            stats.errors += 1
        if result == "ok":
            n_ok += 1
        elif result == "exists":
            n_exists += 1
        else:
            n_fail += 1
    say(f"      新注册 {n_ok} 个 | 已存在 {n_exists} 个 | 失败 {n_fail} 个")

    # ---- 1.2 批量登录拿 Token ----
    say("[2/5] 登录获取 Token ...")
    ok_accounts: List[Account] = []
    for acct in accounts:
        try:
            await login_one(http, acct)
            ok_accounts.append(acct)
        except Exception as exc:
            error_log.warning("登录失败 %s: %s", acct.username, exc)
            stats.errors += 1
    say(f"      {len(ok_accounts)}/{total} 登录成功")
    if not ok_accounts:
        raise RuntimeError(
            "没有任何可用账号。请确认 Nacos / Redis / MySQL / res-gateway / res-userservice "
            "均已启动，且 CaptchaServiceImpl 的 test- 前缀绕过已生效"
        )
    return ok_accounts


# ============================================================
# 业务接口封装
# ============================================================
async def load_products(http: HttpClient, ua: str = "") -> List[Dict[str, Any]]:
    probe = Account(username="_preloader", password="", ua=ua)
    payload = await http.call("GET", "/product/list", account=probe)
    data = payload.get("data") or []
    return [p for p in data if p.get("fid") and p.get("realprice") is not None]


async def track_pv(http: HttpClient, acct: Account, page_type: str,
                   fid: Optional[str] = None) -> None:
    """向运营统计模块打 PV/UV 埋点。

    后端 res-operations 的 PageViewService.track 靠这个接口把浏览事件写进 Redis：
      - 浏览量(PV)  来自 pvDailyTotal 计数
      - 访客/独立访客(UV) 与 活跃用户 来自 uvDaily（仅当请求带登录身份 X-User-Id 时计入）
      - 设备分布 由后端按 User-Agent 解析（脚本的随机 UA 会自动归类 desktop/mobile/tablet）
      - 每道菜浏览量 来自 pvDailyProduct（pageType=PRODUCT_DETAIL 且带 fid）
    聚合任务每 5 分钟把 Redis 刷进 MySQL 日汇总表，后台才会显示。
    注意：这是「尽力而为」的埋点，失败不计入业务错误（不影响下单主流程）。
    """
    body = {"pageType": page_type, "pageUrl": f"/{page_type.lower()}"}
    if fid:
        body["fid"] = fid
        body["pageUrl"] = f"/product/{fid}"
    try:
        # auth=True 带 Bearer；user_id=True 带 X-User-Id。
        # 网关若要求鉴权：会用 token 身份注入 X-User-Id（UV 可统计）；
        # 若走白名单放行：后端直接读到我们传的 X-User-Id（UV 仍可统计）。
        await http.call("POST", "/ops/pv/track", account=acct, auth=True, user_id=True,
                        body=body, expect_business_ok=False)
    except Exception as exc:  # 埋点失败绝不阻塞浏览/下单主流程
        log.debug("PV 埋点跳过(%s): %s", page_type, exc)


async def browse_product(http: HttpClient, acct: Account, product: Dict[str, Any],
                         stats: Stats, with_search: bool) -> None:
    # 浏览属于登录用户行为，统一带 token（公开接口带 token 也放行，
    # 同时保证 /search/food 这类需鉴权接口不报 401，token 过期时也能自动重登）
    await http.call("GET", "/product/list", account=acct, auth=True)
    await track_pv(http, acct, "HOME")
    fid = product["fid"]
    await http.call("GET", f"/product/detail/{fid}", account=acct, auth=True)
    # 商品详情浏览埋点：带 fid 才会累加到「每道菜浏览量」与「独立访客(按商品)」
    await track_pv(http, acct, "PRODUCT_DETAIL", fid=fid)
    await http.call("GET", f"/product/like/{fid}/count", account=acct, auth=True)
    if with_search:
        await http.call("GET", "/search/food", account=acct, auth=True,
                        params={"keyword": random.choice(cfg.SEARCH_KEYWORDS)})
        await track_pv(http, acct, "SEARCH")
    stats.add_pv(acct.device)


async def cart_add(http: HttpClient, acct: Account, product: Dict[str, Any], num: int) -> None:
    body = {
        "fid": product["fid"],
        "fname": product.get("fname"),
        "realprice": product.get("realprice"),
        "fphoto": first_photo(product.get("fphoto")),
        "num": num,
    }
    await http.call("POST", "/cart/add", account=acct, auth=True, user_id=True, body=body)


async def cart_update(http: HttpClient, acct: Account, fid: str, num: int) -> None:
    await http.call("POST", "/cart/update", account=acct, auth=True, user_id=True,
                    params={"fid": fid, "num": num})


async def cart_list(http: HttpClient, acct: Account) -> List[Dict[str, Any]]:
    payload = await http.call("GET", "/cart/list", account=acct, auth=True, user_id=True)
    return payload.get("data") or []


async def order_create(http: HttpClient, acct: Account, amount: Decimal) -> Dict[str, Any]:
    body = {
        "address": random.choice(cfg.ADDRESS_POOL),
        "tel": random_tel(),
        "deliveryType": weighted_choice(cfg.DELIVERY_OPTIONS, cfg.DELIVERY_WEIGHTS),
        "payment": weighted_choice(cfg.PAYMENT_OPTIONS, cfg.PAYMENT_WEIGHTS),
        "ps": random.choice(cfg.PS_POOL),
        "freeOrderNo": None,
        "originalAmount": amount_to_json_number(amount),
        "expectAmount": amount_to_json_number(amount),
    }
    # 非幂等：超时不重试，避免"第一次已建单 + 重发再建"造成重复订单与 -2003/-2004
    payload = await http.call("POST", "/order/create", account=acct, auth=True,
                              user_id=True, body=body, retryable=False)
    return payload.get("data") or {}


async def order_confirm(http: HttpClient, acct: Account, roid: str) -> None:
    # 非幂等：超时不重试（重复支付由后端状态机拦，但没必要冒险）
    await http.call("POST", f"/order/confirm/{roid}", account=acct, auth=True,
                    retryable=False)


async def review_submit(http: HttpClient, acct: Account, fid: str, star: int,
                        text: str, order_id: Optional[str] = None) -> None:
    """提交一条商品评价：POST /product/review（multipart 表单）。

    后端 contract（res-product ProductReviewController）：
      - 参数 fid / starRating(1-5) / reviewText(<=500) / orderId(可选) / images(可选)
      - 需要登录态，但网关**不会**注入 X-User-Id → 脚本必须自带（user_id=True）
      - 仅"已支付"订单（status∈{1,2}）对应的商品可评，一人一菜只能评一次
    """
    form = aiohttp.FormData()
    form.add_field("fid", fid)
    form.add_field("starRating", str(star))
    if text:
        form.add_field("reviewText", text)
    if order_id:
        form.add_field("orderId", order_id)
    await http.call("POST", "/product/review", account=acct, auth=True, user_id=True,
                    form=form)


async def like_submit(http: HttpClient, acct: Account, fid: str, like_type: int) -> None:
    """点赞(1)/点踩(-1)：POST /product/like（query 参数）。

    后端 contract（res-product ProductLikeController）：
      - 参数 fid + likeType(1=赞 / -1=踩)；需登录态且网关不注入 X-User-Id → 自带
      - 仅"已支付"用户可操作（NOT_PURCHASED=-3004）
      - ⚠️ 开关式语义：同一 (userId,fid) 用相同 likeType 再调一次会**取消**并扣减计数！
        因此调用方必须保证同一 (账号, 菜品) 只调用一次（见 Account.liked_fids）。
    """
    await http.call("POST", "/product/like", account=acct, auth=True, user_id=True,
                    params={"fid": fid, "likeType": like_type})


# ============================================================
# Worker 运行上下文
# ============================================================
@dataclass
class Ctx:
    http: HttpClient
    stats: Stats
    products: List[Dict[str, Any]]
    stop: asyncio.Event
    start_time: float
    target_orders: int
    work_sem: asyncio.Semaphore
    pbar: Optional[Any] = None

    async def sleep(self, seconds: float) -> None:
        """可被停止信号打断的 sleep，保证 Ctrl+C / 到达目标后能立刻退出。"""
        if seconds <= 0:
            return
        try:
            await asyncio.wait_for(self.stop.wait(), timeout=seconds)
        except asyncio.TimeoutError:
            pass

    async def request_gap(self) -> None:
        await asyncio.sleep(random.uniform(cfg.REQUEST_GAP_MIN, cfg.REQUEST_GAP_MAX))

    def on_order_done(self, device: str, paid: bool) -> None:
        completed = self.stats.add_order(device, paid)
        if self.target_orders and completed >= self.target_orders:
            self.stop.set()
        if self.pbar is None:
            return  # 梯度测试模式：不刷进度条、不写进度文件、不打点
        self.pbar.update(1)
        if completed % cfg.SAVE_PROGRESS_EVERY == 0:
            self.checkpoint(completed)

    def checkpoint(self, completed: int) -> None:
        save_progress(self.stats)
        elapsed = time.monotonic() - self.start_time
        st = self.stats
        log.info("进度 %d 笔 | PV=%d 未支付=%d 已支付=%d 客服=%d 错误=%d 耗时=%.0fs",
                 completed, st.browse_pv, st.orders_unpaid, st.orders_paid,
                 st.cs_connects, st.errors, elapsed)
        say(f"   进度 {completed:,} 笔 | 浏览PV: {st.browse_pv:,} | "
            f"下单(未支付): {st.orders_unpaid:,} | 下单(已支付): {st.orders_paid:,} | "
            f"客服连接: {st.cs_connects:,} | 评价: {st.reviews:,} | 点赞: {st.likes:,} | "
            f"错误: {st.errors}")


# ============================================================
# 四类 Worker
# ============================================================
async def browse_worker(ctx: Ctx, acct: Account) -> None:
    while not ctx.stop.is_set():
        async with ctx.work_sem:
            if ctx.stop.is_set():
                break
            try:
                if not ctx.products:
                    raise BusinessSkip("商品列表为空")
                product = random.choice(ctx.products)
                await browse_product(ctx.http, acct, product, ctx.stats,
                                     with_search=random.random() < cfg.SEARCH_PROBABILITY)
                await ctx.request_gap()
            except BusinessSkip:
                pass
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                _count_error(ctx, exc, "浏览", acct)
        await ctx.sleep(random.uniform(cfg.BROWSE_ROUND_GAP_MIN, cfg.BROWSE_ROUND_GAP_MAX))


def _count_error(ctx: Ctx, exc: Exception, tag: str, acct: Account) -> None:
    """HttpClient 已经对请求类错误计过数，这里只补记其余异常，避免重复统计。"""
    if not isinstance(exc, (ApiError, RetryableError, Unauthorized)):
        ctx.stats.errors += 1
    error_log.warning("[%s] %s -> %s", tag, acct.username, exc)


def _review_texts_for_star(star: int) -> List[str]:
    """按星级挑对应语气的文案池：1~2 星差评 / 3 星中性 / 4~5 星好评。"""
    if star <= 2:
        return cfg.REVIEW_TEXTS_NEGATIVE
    if star == 3:
        return cfg.REVIEW_TEXTS_NEUTRAL
    return cfg.REVIEW_TEXTS_POSITIVE


async def _maybe_review(ctx: Ctx, acct: Account, fid: str, roid: Optional[str]) -> None:
    """按概率对刚支付成功的菜品提交一条评价（打分 + 评论）。

    后端只允许"已支付过该商品"的用户评价，所以这里紧跟在支付成功之后提交；
    一人一菜只能评一次，重复会被后端以 -3103 拒绝（脚本按跳过处理，不计错误）。
    文案按星级分池，避免"2 星却配好评文案"的穿帮。
    """
    if not cfg.REVIEW_ENABLE or random.random() >= cfg.REVIEW_PROBABILITY:
        return
    if fid in acct.reviewed_fids:
        return
    star = weighted_choice([1, 2, 3, 4, 5], cfg.REVIEW_STAR_WEIGHTS)
    text = random.choice(_review_texts_for_star(star))
    try:
        await review_submit(ctx.http, acct, fid, star, text, roid)
        acct.reviewed_fids.add(fid)
        ctx.stats.reviews += 1
        log.info("提交评价成功: user=%s fid=%s star=%s", acct.username, fid, star)
    except BusinessSkip as exc:
        acct.reviewed_fids.add(fid)   # 未购买/已评过：本轮不再重试
        log.info("[评价] 跳过: user=%s %s", acct.username, exc)
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        _count_error(ctx, exc, "评价", acct)


async def _maybe_like(ctx: Ctx, acct: Account, fid: str) -> None:
    """按概率对刚支付成功的菜品点赞/点踩。

    ⚠️ 后端是开关式：同一 (账号, 菜品) 用相同 likeType 再调一次会取消点赞，
    所以必须用 acct.liked_fids 去重，保证每个 (账号, 菜品) 只提交一次。
    """
    if not cfg.LIKE_ENABLE or random.random() >= cfg.LIKE_PROBABILITY:
        return
    if fid in acct.liked_fids:
        return
    like_type = weighted_choice([1, -1], cfg.LIKE_WEIGHTS)
    try:
        await like_submit(ctx.http, acct, fid, like_type)
        acct.liked_fids.add(fid)
        ctx.stats.likes += 1
        log.info("点赞成功: user=%s fid=%s likeType=%s", acct.username, fid, like_type)
    except BusinessSkip as exc:
        acct.liked_fids.add(fid)   # 未购买等：本轮不再重试
        log.info("[点赞] 跳过: user=%s %s", acct.username, exc)
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        _count_error(ctx, exc, "点赞", acct)


async def _do_order_cycle(ctx: Ctx, acct: Account, pay: bool) -> None:
    """一次完整的「浏览 -> 加购 -> (改数量) -> 取购物车 -> 下单 -> (支付) -> 评价/点赞」流程。"""
    if not ctx.products:
        raise BusinessSkip("商品列表为空")
    product = random.choice(ctx.products)

    await ctx.http.call("GET", f"/product/detail/{product['fid']}", account=acct)
    ctx.stats.add_pv(acct.device)
    await ctx.request_gap()

    await cart_add(ctx.http, acct, product, random.randint(*cfg.CART_ADD_NUM_RANGE))
    await ctx.request_gap()

    if random.random() < cfg.CART_UPDATE_PROBABILITY:
        await cart_update(ctx.http, acct, product["fid"],
                          random.randint(*cfg.CART_UPDATE_NUM_RANGE))
        await ctx.request_gap()

    items = await cart_list(ctx.http, acct)
    if not items:
        raise BusinessSkip("购物车为空，跳过本轮")
    # 真实前端「购物车页」埋点（CART），后台各页面访问量会多出「购物车」一项
    await track_pv(ctx.http, acct, "CART")

    amount = calc_amount(items)
    order = await order_create(ctx.http, acct, amount)
    roid = order.get("roid")
    if not roid:
        raise BusinessSkip(f"下单响应缺少 roid: {str(order)[:160]}")
    # 真实前端「下单结算页」埋点（ORDER），对应后台「下单结算」一项
    await track_pv(ctx.http, acct, "ORDER")

    if pay:
        await order_confirm(ctx.http, acct, roid)
        # 只有走完支付才计入"已支付"口径，一笔订单只统计一次
        ctx.on_order_done(acct.device, paid=True)
        log.info("下单并支付成功: user=%s roid=%s 金额=%s 明细=%d",
                 acct.username, roid, amount, len(items))
        # 支付成功后按概率对该菜品提交评价（打分 + 评论）与点赞/点踩
        await _maybe_review(ctx, acct, product["fid"], roid)
        await _maybe_like(ctx, acct, product["fid"])
    else:
        ctx.on_order_done(acct.device, paid=False)
        log.info("下单成功(未支付): user=%s roid=%s 金额=%s 明细=%d",
                 acct.username, roid, amount, len(items))


async def order_worker(ctx: Ctx, acct: Account, pay: bool) -> None:
    while not ctx.stop.is_set():
        async with ctx.work_sem:
            if ctx.stop.is_set():
                break
            try:
                await _do_order_cycle(ctx, acct, pay)
                await ctx.request_gap()
            except BusinessSkip as exc:
                log.info("[下单] 跳过: user=%s %s", acct.username, exc)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                _count_error(ctx, exc, f"下单 pay={pay}", acct)
        await ctx.sleep(random.uniform(cfg.ORDER_ROUND_GAP_MIN, cfg.ORDER_ROUND_GAP_MAX))


def _ws_connect(uri: str, ua: str):
    """兼容 websockets 10~15 的请求头参数改名（extra_headers -> additional_headers）。"""
    common = {
        "open_timeout": cfg.WS_OPEN_TIMEOUT,
        "close_timeout": 5,
        "ping_interval": 20,
        "ping_timeout": 20,
        "max_size": None,
    }
    try:
        return websockets.connect(uri, additional_headers={"User-Agent": ua}, **common)
    except TypeError:
        return websockets.connect(uri, extra_headers={"User-Agent": ua}, **common)


async def cs_worker(ctx: Ctx, acct: Account) -> None:
    """只建立 WebSocket 连接、收下 AI 欢迎语就断开，不发送任何消息（不消耗大模型 token）。"""
    if websockets is None:
        error_log.warning("未安装 websockets 库，客服模拟 Worker 已跳过")
        return

    while not ctx.stop.is_set():
        uri = (
            f"ws://{cfg.CS_WEBSOCKET_HOST}:{cfg.CS_WEBSOCKET_PORT}{cfg.CS_WEBSOCKET_PATH}?"
            + urlencode({"userId": acct.user_id, "role": "user", "userName": acct.username})
        )
        try:
            async with await _ws_connect(uri, acct.ua) as _ws:
                ctx.stats.cs_connects += 1
                log.info("客服连接建立: user=%s", acct.username)
                # 真实前端打开「客服页(Chat)」会发一条 pageType=OTHER 的 PV 埋点
                # （见前端 router/index.ts 的 routeToPageType: Chat -> OTHER）。
                # 这里补上，后台「其他」页访问量才会有数据。
                await track_pv(ctx.http, acct, "OTHER")
                await ctx.sleep(random.uniform(cfg.CS_CONNECT_HOLD_MIN, cfg.CS_CONNECT_HOLD_MAX))
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            ctx.stats.errors += 1
            error_log.warning("[客服] %s -> %s", acct.username, exc)
        await ctx.sleep(random.uniform(cfg.CS_ROUND_GAP_MIN, cfg.CS_ROUND_GAP_MAX))


# ============================================================
# 进度汇报
# ============================================================
async def reporter(ctx: Ctx) -> None:
    while not ctx.stop.is_set():
        try:
            await asyncio.wait_for(ctx.stop.wait(), timeout=2.0)
        except asyncio.TimeoutError:
            pass
        if ctx.stop.is_set():
            break
        elapsed = time.monotonic() - ctx.start_time
        ctx.stats.refresh_qps(elapsed)
        if ctx.pbar is not None:
            limit = cfg.TOTAL_RUNTIME_MINUTES * 60
            timepart = f"{int(elapsed // 60):02d}:{int(elapsed % 60):02d}"
            if limit:
                timepart += f"/{int(limit // 60):02d}:{int(limit % 60):02d}"
            st = ctx.stats
            ctx.pbar.set_postfix_str(
                f"PV={st.browse_pv:,} 未付={st.orders_unpaid:,} "
                f"已付={st.orders_paid:,} 客服={st.cs_connects:,} "
                f"QPS={st.qps:.1f} 错误={st.errors} 用时={timepart}"
            )


# ============================================================
# 展示
# ============================================================
def print_banner(accounts_total: int) -> None:
    n_browse = round(accounts_total * cfg.BEHAVIOR_BROWSE_RATIO)
    n_unpaid = round(accounts_total * cfg.BEHAVIOR_ORDER_UNPAID_RATIO)
    n_paid = round(accounts_total * cfg.BEHAVIOR_ORDER_PAID_RATIO)
    n_cs = max(0, accounts_total - n_browse - n_unpaid - n_paid)
    say("")
    say("=" * 78)
    say("自动化下单脚本启动")
    say(f"  账号总数: {accounts_total}")
    say(f"  浏览型: {n_browse} | 下单不支付: {n_unpaid} | 下单并支付: {n_paid} | 客服模拟: {n_cs}")
    say(f"  网关地址: {cfg.GATEWAY_URL}")
    say(f"  客服WebSocket: ws://{cfg.CS_WEBSOCKET_HOST}:{cfg.CS_WEBSOCKET_PORT}{cfg.CS_WEBSOCKET_PATH}")
    say(f"  停止条件: 下单 {cfg.TARGET_TOTAL_ORDERS or '不限'} 笔 或 运行 "
        f"{cfg.TOTAL_RUNTIME_MINUTES or '不限'} 分钟（任一满足）")
    say(f"  最大并发: {cfg.CONCURRENT_WORKERS} | 单请求超时: {cfg.REQUEST_TIMEOUT}s | "
        f"重试: {cfg.MAX_RETRIES} | QPS上限: {cfg.MAX_QPS or '不限'}")
    say("=" * 78)


def print_final(ctx: Ctx, group_counts: Dict[str, int], device_counts: Dict[str, int]) -> None:
    st = ctx.stats
    elapsed = time.monotonic() - ctx.start_time
    say("")
    say("=" * 78)
    say(f"执行完毕 (运行 {int(elapsed // 60)} 分 {int(elapsed % 60)} 秒)")
    say(f"  浏览PV:        {st.browse_pv:,}")
    say(f"  下单(未支付):  {st.orders_unpaid:,}")
    say(f"  下单(已支付):  {st.orders_paid:,}")
    say(f"  下单合计:      {st.total_orders:,}")
    say(f"  客服连接:      {st.cs_connects:,}")
    say(f"  评价(打分):    {st.reviews:,}")
    say(f"  点赞/点踩:     {st.likes:,}")
    total_pv = sum(st.device_pv.values()) or 1
    parts = [f"{d} {st.device_pv.get(d, 0):,} ({st.device_pv.get(d, 0) * 100.0 / total_pv:.0f}%)"
             for d in DEVICE_ORDER]
    say("  设备分布(PV):  " + " | ".join(parts))
    say("  设备分布(单):  " + " | ".join(f"{d} {st.device_orders.get(d, 0):,}" for d in DEVICE_ORDER))
    qps = st.requests / elapsed if elapsed else 0.0
    say(f"  总请求数:      {st.requests:,} | 平均QPS: {qps:.1f}")
    say("  账号分群:      " + " | ".join(
        f"{BEHAVIOR_LABEL[k]} {v}" for k, v in group_counts.items()))
    say("  设备分配:      " + " | ".join(f"{k} {v}" for k, v in device_counts.items()))
    say(f"  错误:          {st.errors}")
    say(f"  日志:          {LOG_PATH}")
    say(f"  错误日志:      {ERROR_LOG_PATH}")
    say("=" * 78)


# ============================================================
# 主流程
# ============================================================
def build_accounts(count: int) -> List[Account]:
    return [Account(username=username_of(i), password=cfg.ACCOUNT_PASSWORD) for i in range(count)]


async def run(args: argparse.Namespace) -> None:
    apply_overrides(args)
    setup_logging()

    stop = asyncio.Event()
    stats = Stats()

    # ---- 断点续传 ----
    if not args.reset_progress:
        data = load_progress()
        if data and int(data.get("completed", 0)) > 0:
            stats.load_progress(data)
            # QPS / 重试次数是"本次运行"的指标，续传时归零，否则平均值会被历史数据稀释
            stats.requests = 0
            stats.retries = 0
            say(f"[断点] 检测到历史进度：已完成 {stats.total_orders} 笔，"
                f"失败 {stats.errors} 次，最后更新 {data.get('lastUpdate')}")
            say("       统计口径将在该基础上累加（--reset-progress 可忽略断点）")

    print_banner(cfg.ACCOUNT_COUNT)

    # ---- 断点已是「完成态」：直接退出，避免对着已灌过数据的库空跑 ----
    # 场景：上一轮已跑满 --target-orders，progress.json 里 completed >= 目标。
    # 此时再跑只会重复下单，被后端下单锁/购物车状态拒绝（-2003/-2004），没有意义。
    target_now = cfg.TARGET_TOTAL_ORDERS
    if (not args.reset_progress) and target_now and stats.total_orders >= target_now:
        say(f"[断点] 上次运行已完成 {stats.total_orders:,} 笔，已达本次目标 {target_now:,} 笔，无需继续。")
        say("       重新灌数据请加 --reset-progress；想在此基础上叠加请提高 --target-orders。")
        say("       （重复下单会被后端拒绝：-2003 重复提交 / -2004 购物车为空）")
        return

    connector = aiohttp.TCPConnector(
        limit=cfg.CONCURRENT_WORKERS + 5,
        limit_per_host=cfg.CONCURRENT_WORKERS + 5,
        ttl_dns_cache=300,
        force_close=False,
    )
    timeout = aiohttp.ClientTimeout(total=cfg.REQUEST_TIMEOUT, connect=cfg.CONNECT_TIMEOUT)

    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        http = HttpClient(session, stats)

        # 设备与 UA 必须在注册/登录前分配好，这样所有请求都带真实 UA
        accounts = build_accounts(cfg.ACCOUNT_COUNT)
        device_counts = assign_devices(accounts)

        # ---- 阶段一 / 二：注册 + 登录 ----
        accounts = await prepare_accounts(http, accounts, stats)

        # ---- 阶段三：设备类型（已在上方分配，这里只展示） ----
        say("[3/5] 分配设备类型: " + " | ".join(f"{k} {v}个" for k, v in device_counts.items()))

        # ---- 阶段四：行为分群 ----
        groups = assign_behaviors(accounts)
        group_counts = {k: len(v) for k, v in groups.items()}
        say("[4/5] 行为分群: " + " | ".join(
            f"{BEHAVIOR_LABEL[k]} {v}个" for k, v in group_counts.items()))

        # ---- 阶段五：预加载商品 ----
        say("[5/5] 预加载商品列表 ...")
        probe_ua = accounts[0].ua if accounts else ""
        products = await load_products(http, probe_ua)
        say(f"      获取到 {len(products)} 个在售商品")
        if not products:
            raise RuntimeError("商品列表为空，请先给 resfood 表灌数据（或检查 res-product 服务）")

        # ---- 并发执行 ----
        target = cfg.TARGET_TOTAL_ORDERS
        pbar_total = target if target else None
        pbar = tqdm(total=pbar_total, initial=min(stats.total_orders, target) if target else 0,
                    desc="下单进度", unit="笔", dynamic_ncols=True, file=sys.stdout,
                    mininterval=0.5)

        ctx = Ctx(
            http=http,
            stats=stats,
            products=products,
            stop=stop,
            start_time=time.monotonic(),
            target_orders=target,
            work_sem=asyncio.Semaphore(cfg.CONCURRENT_WORKERS),
            pbar=pbar,
        )

        say("")
        say(f"开始并发执行（最大 {cfg.CONCURRENT_WORKERS} 并发，"
            f"运行 {cfg.TOTAL_RUNTIME_MINUTES or '不限'} 分钟，"
            f"目标 {target or '不限'} 笔下单）...")
        say("")

        tasks: List[asyncio.Task] = []
        for acct in groups[B_BROWSE]:
            tasks.append(asyncio.create_task(browse_worker(ctx, acct), name=f"browse-{acct.username}"))
        for acct in groups[B_ORDER_UNPAID]:
            tasks.append(asyncio.create_task(order_worker(ctx, acct, pay=False),
                                             name=f"unpaid-{acct.username}"))
        for acct in groups[B_ORDER_PAID]:
            tasks.append(asyncio.create_task(order_worker(ctx, acct, pay=True),
                                             name=f"paid-{acct.username}"))
        for acct in groups[B_CS]:
            tasks.append(asyncio.create_task(cs_worker(ctx, acct), name=f"cs-{acct.username}"))
        tasks.append(asyncio.create_task(reporter(ctx), name="reporter"))

        async def timeout_watchdog() -> None:
            if cfg.TOTAL_RUNTIME_MINUTES and cfg.TOTAL_RUNTIME_MINUTES > 0:
                await ctx.sleep(cfg.TOTAL_RUNTIME_MINUTES * 60)
                stop.set()

        watchdog = asyncio.create_task(timeout_watchdog(), name="watchdog")

        try:
            await stop.wait()
        except (KeyboardInterrupt, asyncio.CancelledError):
            say("\n[中断] 收到停止信号，正在优雅退出 ...")
            stop.set()

        log.info("触发停止，开始回收 Worker")
        for t in tasks + [watchdog]:
            if not t.done():
                t.cancel()
        await asyncio.gather(*tasks, watchdog, return_exceptions=True)

        pbar.close()
        save_progress(stats)
        print_final(ctx, group_counts, device_counts)


# ============================================================
# 并发梯度测试模式（对应文档 8.7 节）
# ============================================================
async def run_ramp(args: argparse.Namespace) -> None:
    apply_overrides(args)
    setup_logging()
    say("")
    say("=" * 78)
    say("并发梯度测试模式（自动注册/登录账号，然后逐级加压找瓶颈）")
    say("=" * 78)

    stop = asyncio.Event()
    stats = Stats()
    connector = aiohttp.TCPConnector(limit=200, limit_per_host=200, ttl_dns_cache=300)
    timeout = aiohttp.ClientTimeout(total=cfg.REQUEST_TIMEOUT, connect=cfg.CONNECT_TIMEOUT)

    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        http = HttpClient(session, stats)
        accounts = build_accounts(cfg.ACCOUNT_COUNT)
        assign_devices(accounts)
        accounts = await prepare_accounts(http, accounts, stats)
        for a in accounts:
            a.behavior = B_ORDER_PAID

        probe_ua = accounts[0].ua if accounts else ""
        products = await load_products(http, probe_ua)
        say(f"      获取到 {len(products)} 个在售商品")
        if not products:
            raise RuntimeError("商品列表为空，无法压测")

        ctx = Ctx(http=http, stats=stats, products=products, stop=stop,
                  start_time=time.monotonic(), target_orders=0,
                  work_sem=asyncio.Semaphore(200), pbar=None)

        say("")
        header = (f"{'并发':>4} | {'轮次':>5} | {'成功':>5} | {'失败':>5} | "
                  f"{'成功率':>7} | {'平均耗时':>9} | {'P95耗时':>9} | {'QPS':>7}")
        say(header)
        say("-" * len(header))

        for conc in cfg.RAMP_LADDER:
            conc_eff = min(conc, len(accounts))
            workers = accounts[:conc_eff]
            rounds = cfg.RAMP_ROUNDS_PER_WORKER
            latencies: List[float] = []
            ok = 0
            fail = 0
            stats.requests = 0
            t0 = time.monotonic()

            async def one(acct: Account) -> None:
                nonlocal ok, fail
                for _ in range(rounds):
                    if stop.is_set():
                        return
                    s = time.monotonic()
                    try:
                        await _do_order_cycle(ctx, acct, pay=True)
                        latencies.append(time.monotonic() - s)
                        ok += 1
                    except Exception as exc:
                        fail += 1
                        error_log.warning("[RAMP] %s -> %s", acct.username, exc)

            await asyncio.gather(*(one(a) for a in workers))

            elapsed = max(time.monotonic() - t0, 1e-6)
            total = ok + fail
            rate = ok / total if total else 0.0
            avg = sum(latencies) / len(latencies) if latencies else 0.0
            p95 = sorted(latencies)[max(0, int(len(latencies) * 0.95) - 1)] if latencies else 0.0
            qps = stats.requests / elapsed

            note = "" if conc_eff == conc else f"  (账号不足，实际 {conc_eff})"
            say(f"{conc:>4} | {rounds * conc_eff:>5} | {ok:>5} | {fail:>5} | "
                f"{rate * 100:>6.1f}% | {avg * 1000:>7.0f}ms | {p95 * 1000:>7.0f}ms | "
                f"{qps:>7.1f}{note}")

            if total and rate < cfg.RAMP_ABORT_SUCCESS_RATE:
                say(f"[!] 成功率 {rate * 100:.1f}% 低于阈值 "
                    f"{cfg.RAMP_ABORT_SUCCESS_RATE * 100:.0f}%，停止继续加压")
                break

    say("")
    say("梯度测试结束。建议结合 res-order / res-cart 日志与 MySQL、Redis 连接池监控定位瓶颈。")


# ============================================================
# 参数覆盖
# ============================================================
def apply_overrides(args: argparse.Namespace) -> None:
    if args.accounts is not None:
        cfg.ACCOUNT_COUNT = args.accounts
    if args.workers is not None:
        cfg.CONCURRENT_WORKERS = args.workers
    if args.runtime is not None:
        cfg.TOTAL_RUNTIME_MINUTES = args.runtime
    if args.target_orders is not None:
        cfg.TARGET_TOTAL_ORDERS = args.target_orders
    if args.qps is not None:
        cfg.MAX_QPS = args.qps
    if args.gateway:
        cfg.GATEWAY_URL = args.gateway
    if args.ws_host:
        cfg.CS_WEBSOCKET_HOST = args.ws_host
    if args.ws_port is not None:
        cfg.CS_WEBSOCKET_PORT = args.ws_port


# ============================================================
# 离线自检
# ============================================================
def selftest() -> int:
    failures = 0

    def check(name: str, condition: bool, extra: str = "") -> None:
        nonlocal failures
        mark = "[OK]  " if condition else "[FAIL]"
        if not condition:
            failures += 1
        print(f"{mark} {name} {extra}")

    # ---- 金额精度 ----
    items = [
        {"realprice": 28.8, "num": 5},
        {"realprice": 0.1, "num": 3},
        {"realprice": 12.35, "num": 2},
    ]
    amt = calc_amount(items)
    # 28.8*5 = 144.0 ; 0.1*3 = 0.3 ; 12.35*2 = 24.7  =>  169.0
    check("金额计算精度", amt == Decimal("169.0"), f"-> {amt} (期望 169.0)")
    check("金额可 JSON 序列化", amount_to_json_number(amt) == 169.0,
          f"-> {amount_to_json_number(amt)}")
    check("金额与后端 BigDecimal 口径一致（3×0.1=0.3）",
          calc_amount([{"realprice": 0.1, "num": 1}] * 3) == Decimal("0.3"),
          f"-> {calc_amount([{'realprice': 0.1, 'num': 1}] * 3)}")

    # ---- 账号命名 ----
    check("账号命名规则", username_of(0) == "testbuyer000" and username_of(99) == "testbuyer099",
          f"-> {username_of(0)} ... {username_of(99)}")

    # ---- fphoto ----
    check("fphoto 取首图", first_photo("http://a/1.jpg,http://a/2.jpg,") == "http://a/1.jpg",
          f"-> {first_photo('http://a/1.jpg,http://a/2.jpg,')}")

    # ---- 设备分群 ----
    random.seed(42)
    accts = build_accounts(1000)
    dc = assign_devices(accts)
    tot = sum(dc.values())
    check("设备占比 DESKTOP≈45%", abs(dc["DESKTOP"] / tot - 0.45) < 0.03,
          f"-> {dc['DESKTOP'] / tot:.3f}")
    check("设备占比 MOBILE≈40%", abs(dc["MOBILE"] / tot - 0.40) < 0.03,
          f"-> {dc['MOBILE'] / tot:.3f}")
    check("设备占比 TABLET≈15%", abs(dc["TABLET"] / tot - 0.15) < 0.03,
          f"-> {dc['TABLET'] / tot:.3f}")
    check("UA 与设备类型匹配", all(a.ua in cfg.UA_POOL[a.device] for a in accts))
    check("平板 UA 不含普通手机关键字（否则会被后端判成 MOBILE）",
          all(("iPad" in ua) or ("Tablet" in ua) for ua in cfg.UA_POOL["TABLET"]))
    check("手机 UA 含 Mobile 关键字（否则会被后端判成 TABLET）",
          all("Mobile" in ua for ua in cfg.UA_POOL["MOBILE"]))
    check("桌面 UA 不含 Mobile/Android 关键字",
          all(("Mobile" not in ua) and ("Android" not in ua) for ua in cfg.UA_POOL["DESKTOP"]))

    # ---- 行为分群 ----
    groups = assign_behaviors(accts)
    gc = {k: len(v) for k, v in groups.items()}
    check("行为分群总数守恒", sum(gc.values()) == 1000, f"-> {gc}")
    check("行为占比 浏览≈60%", abs(gc[B_BROWSE] / 1000 - 0.60) < 0.03,
          f"-> {gc[B_BROWSE] / 1000:.3f}")
    check("行为占比 下单未支付≈25%", abs(gc[B_ORDER_UNPAID] / 1000 - 0.25) < 0.03,
          f"-> {gc[B_ORDER_UNPAID] / 1000:.3f}")
    check("行为占比 下单已支付≈10%", abs(gc[B_ORDER_PAID] / 1000 - 0.10) < 0.03,
          f"-> {gc[B_ORDER_PAID] / 1000:.3f}")
    check("行为占比 客服≈5%", abs(gc[B_CS] / 1000 - 0.05) < 0.03,
          f"-> {gc[B_CS] / 1000:.3f}")
    browse_devices = {a.device for a in groups[B_BROWSE]}
    check("设备与行为独立交叉（浏览组内设备不单一）", len(browse_devices) >= 2,
          f"-> {sorted(browse_devices)}")

    # ---- 随机数据 ----
    tels = [random_tel() for _ in range(200)]
    check("手机号为 11 位数字且以 1 开头",
          all(len(t) == 11 and t.isdigit() and t[0] == "1" for t in tels),
          f"-> 样例 {tels[0]}")
    check("地址池 >= 20 条", len(cfg.ADDRESS_POOL) >= 20, f"-> {len(cfg.ADDRESS_POOL)} 条")
    check("搜索词池非空", len(cfg.SEARCH_KEYWORDS) > 0, f"-> {len(cfg.SEARCH_KEYWORDS)} 个")

    # ---- 占比合法性 ----
    bsum = (cfg.BEHAVIOR_BROWSE_RATIO + cfg.BEHAVIOR_ORDER_UNPAID_RATIO
            + cfg.BEHAVIOR_ORDER_PAID_RATIO + cfg.BEHAVIOR_CUSTOMER_SERVICE_RATIO)
    dsum = cfg.DEVICE_DESKTOP_RATIO + cfg.DEVICE_MOBILE_RATIO + cfg.DEVICE_TABLET_RATIO
    check("行为占比之和为 1", abs(bsum - 1.0) < 1e-9, f"-> {bsum}")
    check("设备占比之和为 1", abs(dsum - 1.0) < 1e-9, f"-> {dsum}")

    # ---- 依赖 ----
    check("websockets 依赖", websockets is not None,
          "-> 已安装" if websockets is not None else "-> 缺失，客服模拟阶段会被跳过")

    print("")
    print(f"自检结束：{'全部通过' if failures == 0 else str(failures) + ' 项失败'}")
    return 1 if failures else 0


# ============================================================
# 入口
# ============================================================
def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="外卖系统自动化下单压测脚本",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    p.add_argument("--accounts", type=int, help="测试账号总数（默认取 config.ACCOUNT_COUNT）")
    p.add_argument("--workers", type=int, help="最大并发 Worker 数（默认取 config.CONCURRENT_WORKERS）")
    p.add_argument("--runtime", type=int, help="总运行时长（分钟，0=不限）")
    p.add_argument("--target-orders", type=int, dest="target_orders",
                   help="下单总数达到多少就停（0=不限）")
    p.add_argument("--qps", type=int, help="全局 QPS 上限（0=不限速）")
    p.add_argument("--gateway", type=str, help="网关地址，如 http://localhost:20001")
    p.add_argument("--ws-host", type=str, dest="ws_host", help="客服 WebSocket 主机")
    p.add_argument("--ws-port", type=int, dest="ws_port", help="客服 WebSocket 端口")
    p.add_argument("--ramp", action="store_true", help="并发梯度测试模式（逐级加压）")
    p.add_argument("--reset-progress", action="store_true", dest="reset_progress",
                   help="忽略 progress.json，从头开始")
    p.add_argument("--selftest", action="store_true", help="离线自检，不发起任何网络请求")
    return p.parse_args(argv)


def main() -> int:
    args = parse_args()
    if args.selftest:
        return selftest()
    try:
        if args.ramp:
            asyncio.run(run_ramp(args))
        else:
            asyncio.run(run(args))
        return 0
    except KeyboardInterrupt:
        say("\n已手动中断。")
        return 130
    except Exception as exc:
        say(f"\n[致命错误] {exc}")
        logging.getLogger("auto_order.error").exception("脚本异常退出")
        return 1


if __name__ == "__main__":
    sys.exit(main())
