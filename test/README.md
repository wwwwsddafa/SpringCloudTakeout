# 自动化下单压测脚本（auto_order）

按 `Pro31SpringCloud/docs/auto-order-script-spec.md` 实现。通过 HTTP API 走完整业务链路：
**注册 → 登录 → 浏览商品 → 加入购物车 → 改数量 → 下单 → 确认支付**，
并按行为分群模拟真实流量（浏览型 / 下单不支付 / 下单并支付 / 客服模拟）。

> 本目录只放脚本，**不改动项目里的任何 Java 代码**。

## 文件

| 文件 | 说明 |
|------|------|
| `config.py` | 全部可配置项（账号数、占比、UA 池、地址池、超时、停止条件……） |
| `auto_order.py` | 主脚本 |
| `requirements.txt` | Python 依赖 |
| `tools/mock_server.py` | 本地 Mock 后端，**仅用于验证脚本本身能跑通**，不参与真实压测 |
| `progress.json` | 断点续传进度（运行后自动生成） |
| `auto-order.log` | 汇总日志（运行后自动生成） |
| `auto-order-error.log` | 错误日志（运行后自动生成） |

## 安装与运行

```bash
cd auto_order
pip install -r requirements.txt

# 0) 先离线自检（不发任何请求）
python auto_order.py --selftest

# 1) 按 config.py 全量跑（100 账号 / 最大 50 并发 / 60 分钟 或 10000 笔下单）
python auto_order.py

# 2) 小规模试跑
python auto_order.py --accounts 20 --workers 20 --target-orders 200 --runtime 30

# 3) 断点续传（默认开启；--reset-progress 忽略历史）
python auto_order.py

# 4) 并发梯度压测（1 → 10 → 20 → 50 逐级加压，找系统瓶颈）
python auto_order.py --ramp
```

### 命令行参数

| 参数 | 说明 |
|------|------|
| `--accounts N` | 测试账号总数，默认 `config.ACCOUNT_COUNT` |
| `--workers N` | 最大并发 Worker 数 |
| `--runtime N` | 总运行时长（分钟，0=不限） |
| `--target-orders N` | 下单总数达到 N 笔就停（0=不限） |
| `--qps N` | 全局 QPS 上限（0=不限速） |
| `--gateway URL` | 网关地址 |
| `--ws-host` / `--ws-port` | 客服 WebSocket 主机/端口 |
| `--ramp` | 并发梯度测试模式 |
| `--reset-progress` | 忽略 `progress.json`，从头开始 |
| `--selftest` | 离线自检（金额精度、UA 分群、占比、命名规则等） |

## 运行前置条件

1. **验证码绕过已生效**：`res-userservice/.../CaptchaServiceImpl.java` 的 `verifyCaptcha`
   已对 `captchaKey` 以 `test-` 开头的情况直接放行（当前仓库里已存在这段逻辑，无需再改）。
2. 以下服务已启动：Nacos `8848`、Redis、MySQL、`res-gateway` `20001`、
   `res-userservice`、`res-product`、`res-cart`、`res-order`、`res-search`、
   `res-customer-service`（客服 WebSocket `10002`）。
3. `resfood` 表里已有在售商品（后端返回的 `realprice` 会被脚本原样用于下单金额计算）。

## 脚本做了什么

**阶段一：初始化（顺序执行）**
1. 构造 `testbuyer000 ~ testbuyer{N-1}`，逐个 `POST /user/register`
   （已存在则跳过，兼容后端把业务异常映射成非 200 的情况）。
2. 逐个 `POST /user/login` 拿 `token` + `userId`。
3. 按 45% / 40% / 15% 给每个账号分配 `DESKTOP` / `MOBILE` / `TABLET` 及固定 `User-Agent`
   （**先分配再注册登录**，保证所有请求都带真实 UA）。
4. 按 60% / 25% / 10% / 5% 切分行为分群（设备与行为独立交叉随机）。
5. `GET /product/list` 预加载商品列表，所有 Worker 共享。

**阶段二：并发执行（每个账号一个 Worker，全局信号量限并发）**

| Worker | 行为 |
|--------|------|
| 浏览型 | `product/list` → `product/detail/{fid}` → `product/like/{fid}/count` →（50% 概率）`search/food`，停顿 2~8s |
| 下单不支付 | 详情 → `cart/add` →（50% 概率）`cart/update` → `cart/list` → `order/create`，**不支付** |
| 下单并支付 | 同上，最后追加 `order/confirm/{roid}` |
| 客服模拟 | 连 `ws://host:10002/ws/chat?userId=&role=user&userName=`，保持 3~5s 只收不发，断开后停 10~20s |

**金额精度**：购物车总额用 `Decimal` 计算 `SUM(realprice × num)`，与后端
`OrderServiceImpl` 的 `BigDecimal.compareTo` 校验口径完全一致，避免 `PRICE_CHANGED` / `AMOUNT_MISMATCH`。
下单后购物车由后端自动清空，脚本不再额外调用清空接口。

**健壮性**
- 网络错误/5xx/429：指数退避重试 3 次（1s → 2s → 4s）
- `401`：自动重新登录该账号后重试当前请求（每个请求最多重登一次）
- 业务错误（商品下架、购物车为空等）：记日志 + 跳过本轮，不影响其它 Worker
- 每 100 笔写一次 `progress.json` 并打一次汇总；启动时自动续传
- `Ctrl+C` 优雅退出：停掉所有 Worker、落盘进度、打印汇总

**停止条件**：`TARGET_TOTAL_ORDERS`（下单总数）或 `TOTAL_RUNTIME_MINUTES`（运行时长），**任一满足即停**。

## 关于文档里的一个口径不一致

规格书 1.2 节写"总计 10000 笔订单"，但 7.2.2 节的分群占比表推导出的是
`2500 未支付 + 1000 已支付 ≈ 3500 笔`。脚本把两者拆成了两个独立开关，
默认 `TARGET_TOTAL_ORDERS = 10000`、`TOTAL_RUNTIME_MINUTES = 60`，谁先到就听谁的。
想要严格复现"10000 笔"，直接保持默认即可；想要按占比自然产出，把
`TARGET_TOTAL_ORDERS` 设为 `0` 让它跑到时间上限。

## 用 Mock 后端自测（可选）

不启动整套微服务也能验证脚本：

```bash
# 终端 A
python tools/mock_server.py --http-port 28080 --ws-port 28081

# 终端 B
python auto_order.py --gateway http://127.0.0.1:28080 --ws-port 28081 \
    --accounts 20 --workers 20 --target-orders 60 --runtime 2 --reset-progress

# 查看 Mock 侧累计数据（含 amount_mismatch，非 0 说明金额口径不一致）
curl http://127.0.0.1:28080/__stats
```

Mock 严格复刻了后端的金额校验逻辑，所以 `amount_mismatch: 0` 可以证明
脚本算出来的 `originalAmount / expectAmount` 与后端一致。

## 评论 / 评分

支付成功后，脚本会按概率对该菜品提交一条真实评价（打分 + 评论文案），
用于填充运营后台的「评价数 / 评分 / 口碑」类数据。

后端契约（`res-product` `ProductReviewController`）：

- `POST /product/review`（**multipart 表单**）：`fid` / `starRating`(1-5) /
  `reviewText`(≤500) / `orderId`(可选) / `images`(可选)
- ⚠️ 该路径是网关白名单，**不会自动注入 `X-User-Id`**，脚本必须自己带上
  （脚本已用 `user_id=True` 处理；否则后端返回 `-1 系统内部错误`）
- 仅**已支付**订单（`checkPurchase` 要求 status ∈ {1,2}）对应商品可评价
- **一人一菜只能评一次**，重复提交返回 `-3103`（脚本按「跳过」处理，不计错误）

相关配置（`config.py`）：

```python
REVIEW_ENABLE = True                     # 是否启用评价
REVIEW_PROBABILITY = 0.35                # 每次支付成功后评价的概率
REVIEW_STAR_WEIGHTS = [1, 2, 6, 26, 65]  # 1~5 星权重（偏好评，贴近真实外卖分布）
REVIEW_TEXTS_NEGATIVE = [ ... ]          # 1~2 星：差评/吐槽文案（12 条）
REVIEW_TEXTS_NEUTRAL  = [ ... ]          # 3 星：中性文案（10 条）
REVIEW_TEXTS_POSITIVE = [ ... ]          # 4~5 星：好评文案（18 条）
```

> 文案**按星级分池**：1~2 星走差评池、3 星走中性池、4~5 星走好评池，
> 避免出现"打 2 星却写『好评』"这种穿帮。

> 注意：评价会写入 `res_product_review` 表，商品详情页能直接看到；
> 但**运营后台的「评价/评分」排行不会自动更新**——`res-operations` 的
> `aggregateProductStats` 目前把 `reviewCount/avgStar/likeCount` 写死为 0，
> 需要后端补一段「聚合时读取评价/点赞统计」的逻辑才能在后台展示。

## 点赞 / 点踩

支付成功后同样按概率对该菜品点赞或点踩，用来填充后台「点赞数」列。

后端契约（`res-product` `ProductLikeController`）：

- `POST /product/like`（**query 参数**）：`fid` + `likeType`（`1`=赞 / `-1`=踩）
- 同样需要登录态 + 自带 `X-User-Id`；仅已支付用户可操作（`-3004`）
- ⚠️ **开关式语义**：同一 `(userId, fid)` 用**相同** `likeType` 再调一次会
  **取消**点赞并扣减计数。脚本用 `Account.liked_fids` 保证每个
  (账号, 菜品) 只提交一次（Mock 侧有 `like_toggle_off` 计数器，正常必须为 0）

```python
LIKE_ENABLE = True           # 是否启用点赞/点踩
LIKE_PROBABILITY = 0.30      # 每次支付成功后点赞/点踩的概率
LIKE_WEIGHTS = [0.85, 0.15]  # [赞(1), 踩(-1)] 的权重
```

> 点赞数会同步到 `resfood.like_count`（后端每 30 分钟落库），
> 所以后台「点赞数」列同样只差聚合侧去读它。

## 页面访问埋点（各页面访问量）

除浏览（`HOME` / `PRODUCT_DETAIL` / `SEARCH`）外，脚本还会发：

| 埋点 pageType | 触发时机 | 后台对应项 |
|---|---|---|
| `CART` | 取购物车成功后 | 购物车 |
| `ORDER` | 下单成功后 | 下单结算 |
| `OTHER` | 每次客服 WebSocket 连接 | 其他 |

> 后端 `StatsAggregationServiceImpl.PAGE_TYPES` 只聚合
> `HOME / PRODUCT_DETAIL / SEARCH / CART / ORDER / OTHER` 六种，
> `ORDER_DETAIL` / `USER_CENTER` / `PRODUCT_LIST` 发了也不会入库。

## 压测前建议

按文档 8.7 节：先 `--ramp` 找瓶颈，再决定并发。若 Feign 调用成为瓶颈，
可考虑：批量预取 ID、合并 `listItems` + `clearCart` 两次 Feign 调用、
对 `testbuyer` 前缀账号跳过支付成功邮件发送（改动均在 Java 侧，本脚本不涉及）。

## 断点续传的注意事项

`progress.json` 会记录累计口径（下单数 / PV / 评价等）。若上一轮已经跑满
`--target-orders`，再次运行会**被启动守卫拦下并直接退出**（避免对着已灌过数据的库
空跑、重复下单）。要重新灌数据请加 `--reset-progress`；想在原基础上继续请提高
`--target-orders`。`--reset-progress` 只重置脚本自身的计数，**不会清空后端数据**。
