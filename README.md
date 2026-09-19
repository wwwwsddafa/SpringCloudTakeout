# 外卖点餐微服务系统（Spring Cloud Alibaba）

> 一个基于 Spring Cloud Alibaba 的微服务外卖点餐平台，包含 **12 个后端微服务**、Vue3 前端、Python 数据采集 / 同步脚本、AI 客服 Agent 与全链路压测脚本。
> 本仓库以前端 / 后端 / 脚本 / Agent 聚合（monorepo）方式组织，便于一站式展示与部署。

## 技术栈

**后端**
- 框架：Spring Boot 3.2 / Spring Cloud 2023.0.3 / Spring Cloud Alibaba 2023.0.3.2
- 语言与构建：Java 17、Maven
- 注册 / 配置中心：Nacos
- 服务网关：Spring Cloud Gateway（JWT 鉴权）
- 服务调用：OpenFeign
- 持久化：MySQL 8 + MyBatis-Plus + Druid
- 缓存 / 分布式锁：Redis
- 消息队列：RabbitMQ
- 搜索：Elasticsearch 8（Java API Client）
- 对象存储：MinIO
- 实时通信：WebSocket
- 工具：Hutool（Snowflake 发号）、Apache POI、OpenPDF

**前端**
- Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus + ECharts（用户端 / 管理端双构建）

**Python**
- `resfood_spider`：爬虫（美食天下）+ MinIO + Ollama 批量生成营销文案
- `es_sync`：菜品同步至 ES + Ollama 向量同步

**AI 客服 Agent**
- LangGraph + FastAPI + ChromaDB 的 RAG 客服，被 `res-customer-service` 通过 HTTP 调用

**压测**
- `auto_order`：Python 全链路压测（注册 → 下单 → 支付），金额 `Decimal` 对齐后端 `BigDecimal`

## 系统架构

```
                 ┌─────────────────────────┐
    浏览器 ─────▶ │   Spring Cloud Gateway   │  (JWT 校验 + Redis 黑名单 + 角色路由)
                 └───────────┬─────────────┘
                             │ OpenFeign
         ┌───────────┬───────┼───────────┬────────────┐
         ▼           ▼       ▼           ▼            ▼
    user-service  product   cart      order       search …  (12 个 res-* 微服务)
         │           │       │           │            │
         └─────┬─────┴───┬───┴─────┬──────┴────────────┘
               ▼         ▼         ▼
           MySQL 8    Redis    RabbitMQ ──▶ notification(邮件) / search(索引同步)
               │                          │
            Elasticsearch 8 ◀────────────┘
               │
         Ollama (embedding / 营销文案)
               │
  res-customer-service(WebSocket) ──HTTP──▶ AI Agent(LangGraph + RAG)
```

## 目录结构

```
takeout-monorepo/
├── backend/                 # Spring Cloud 后端（仅含 res-* 模块 + 根 pom.xml）
│   ├── pom.xml
│   ├── res-gateway/         # 网关 + 统一鉴权
│   ├── res-userservice/     # 用户 / 管理员
│   ├── res-product/         # 菜品
│   ├── res-cart/            # 购物车
│   ├── res-order/           # 订单
│   ├── res-search/          # 搜索（ES 混合检索）
│   ├── res-notification/    # 通知（RabbitMQ 邮件）
│   ├── res-customer-service/# 实时客服（WebSocket + AI）
│   ├── res-operations/      # 运营统计
│   ├── res-idGenerator/     # 分布式 ID（Snowflake）
│   ├── res-fileUpload/      # 文件（MinIO + MD5 去重）
│   └── res-common/          # 公共依赖 / ResultVo / Feign 声明
├── frontend/                # Vue3 前端（用户端 + 管理端）
├── python/
│   ├── resfood_spider/      # 菜品爬虫 + 营销文案生成
│   └── es_sync/             # ES 数据 / 向量同步
├── agent/                   # AI 客服 Agent（LangGraph + FastAPI + ChromaDB）
└── test/
    └── auto_order/          # 全链路压测脚本
```

## 后端模块说明

| 模块 | 职责 | 关键技术 |
|---|---|---|
| res-gateway | 网关与鉴权 | JWT 校验、Redis 黑名单、角色路由、注入 `X-User-Id` |
| res-userservice | 用户 / 管理员 | 注册登录、BCrypt、QQ OAuth、验证码、邮件 |
| res-product | 菜品 | 菜品 CRUD、评价、点赞；Feign 调 idGenerator / fileUpload |
| res-cart | 购物车 | Redis 存储 |
| res-order | 订单 | Redis 分布式锁、BigDecimal 金额校验、支付宝回调验签、状态机 |
| res-search | 搜索 | ES 8 混合检索（BM25 + 向量 + RRF）、MQ 异步建索引、降级 |
| res-notification | 通知 | RabbitMQ 消费邮件消息 |
| res-customer-service | 客服 | WebSocket、命令模式、AI Agent 编排、转人工 |
| res-operations | 运营 | HyperLogLog UV 去重、定时聚合 |
| res-idGenerator | ID 生成 | Hutool Snowflake |
| res-fileUpload | 文件 | MinIO + MD5 去重 + Redis 缓存 |
| res-common | 公共 | ResultVo / ResultCode、Feign 客户端、消息体 |

## 核心亮点

- **订单一致性**：Redis 分布式锁防重复提交、前后端 `BigDecimal` 金额双重校验、订单号幂等键抗支付宝异步回调、状态机管理订单流转。
- **菜品混合检索**：ES 8 将关键词 BM25 与 Ollama 语义向量召回融合，RRF 倒数排名融合重排；RabbitMQ 异步重建索引，向量生成失败时降级为关键词检索。
- **Java + AI 协同客服**：WebSocket 维持会话，命令模式解析意图，编排 LangGraph RAG Agent 自动应答，复杂问题转人工。

## 快速开始

### 前置依赖
- JDK 17、Maven 3.9+
- MySQL 8、Redis、RabbitMQ、Elasticsearch 8、MinIO、Nacos（注册 / 配置中心）
- Node.js 18+、Python 3.10+、Ollama（本地 embedding / 文案生成）

### 后端
```bash
cd backend
# 先启动 Nacos，并导入各模块所需配置
mvn -B clean package -DskipTests
# 依次启动 res-idGenerator / res-gateway / 各业务模块
```

### 前端
```bash
cd frontend
npm install
npm run build        # 用户端 → dist
npm run build:admin  # 管理端 → dist-admin
```

### Python 数据采集
```bash
cd python/resfood_spider
pip install -r requirements.txt
python spider.py     # 抓取菜品 → 落 MinIO → Ollama 生成营销文案 → 产出 SQL

cd ../es_sync
python sync.py       # 同步菜品到 ES 并生成向量
```

### AI 客服 Agent
```bash
cd agent
pip install -r requirements.txt
uvicorn main:app --port 8000   # res-customer-service 通过 /api/chat 调用
```

### 压测
```bash
cd test/auto_order
pip install -r requirements.txt
python run.py         # 注册 → 下单 → 支付 全链路
```

## 说明
- 本仓库后端仅包含 `res-*` 微服务模块（不含 `test-*` 示例模块与 `docs/`、`sql/` 等辅助目录）。
- 前端目录内的 `.md` 文档未纳入版本库（见 `.gitignore`）。
