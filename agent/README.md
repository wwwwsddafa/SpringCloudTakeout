# 外卖平台 AI 客服系统（LangGraph + FastAPI + ChromaDB）

基于设计文档 `ai-customer-service-design.md` 实现的 Python AI 客服服务，作为独立进程运行，
通过 HTTP 与 Java 端（`res-customer-service`，端口 10002）通信：当用户消息路由到 `AiAgent` 时，
Java 转发到本服务的 `POST /api/chat`，本服务经 LangGraph 编排（意图识别 → 工具调用/RAG → 回复 → 转人工判断）
生成回复后返回，Java 再推送给前端。

## 能力
- **意图识别**：LLM 将用户消息分类为 订单查询 / 退单 / 商品查询 / 账户查询 / 知识问答 / 闲聊 / 转人工
- **工具调用**：LangGraph Tool 封装对 Java 微服务（订单/物流/商品/用户）的异步 REST 调用
- **知识问答（RAG）**：从 ChromaDB 检索 FAQ（数据源自 `knowledge-base.md`）
- **多轮对话**：维护会话历史上下文
- **转人工**：AI 无法处理（退单、低置信度、后端失败）时返回 `transfer_to_human` 标记
- **流式输出**：`/api/chat/stream` 提供 SSE 打字机效果

## 目录结构
```
.
├── main.py              # FastAPI 入口（含启动初始化知识库）
├── config.py            # 配置（LLM / Java 网关 / 端口 / 开关）
├── model.py             # LLM 与 Embedding 工厂
├── requirements.txt
├── .env.example         # 环境变量模板（复制为 .env 后填写）
├── knowledge-base.md    # 知识库源数据（RAG 数据源）
├── graph/
│   ├── state.py         # CustomerServiceState 状态定义
│   ├── nodes.py         # intent / tool / rag / reply / router 节点
│   └── builder.py       # LangGraph 状态图构建
├── tools/
│   ├── java_client.py   # Java 微服务统一调用 + 离线 MOCK + 容错
│   ├── order_tools.py   # 订单 / 物流 / 退单 工具
│   ├── product_tools.py # 商品查询 / 搜索 工具
│   └── user_tools.py    # 用户信息 工具
├── rag/
│   ├── vector_store.py  # ChromaDB 初始化
│   ├── retriever.py     # 检索逻辑
│   └── init_data.py     # 解析 knowledge-base.md 入库
└── api/
    ├── schemas.py       # 请求/响应模型
    └── chat.py          # /api/chat 与 /api/chat/stream
```

## 快速开始
```bash
# 1. 激活虚拟环境（本项目已内置 .venv）
.\.venv\Scripts\activate        # Windows
#   source .venv/bin/activate   # Linux/macOS

# 2. 安装依赖
pip install -r requirements.txt

# 3. 配置环境变量
cp .env.example .env
#   编辑 .env：填 OPENAI_API_KEY，或把 LLM_PROVIDER 设为 ollama

# 4. 启动（.env 中 MOCK_JAVA=true 可离线演示，无需 Java 后端）
python main.py
```

## 调用示例
```bash
# 非流式
curl -X POST http://localhost:8000/api/chat \
  -H "Content-Type: application/json" \
  -d '{"session_id":"SES_1","user_id":"user_001","user_name":"张三",
       "message":"我的订单 2096213091871096832 到哪了"}'

# 流式（SSE）
curl -N -X POST http://localhost:8000/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"session_id":"SES_1","user_id":"user_001","user_name":"张三",
       "message":"退换货政策是怎样的？"}'
```

## 说明
- **退单/取消订单**：按风控要求不直连资金接口，AI 仅提交申请并标记转人工。
- **知识库更新**：修改 `knowledge-base.md` 后重启服务即可全量刷新（ChromaDB 去重 upsert）。
- **本地离线运行**：`EMBEDDING_PROVIDER=local` + `LLM_PROVIDER=ollama` 可完全脱离外部 API 运行。
- **依赖版本**：原文档锁定 `langgraph==0.2.0` 与 `langchain==0.3.0` 因 `langchain-core` 版本冲突无法共存，
  本实现已改为兼容版本区间，接口与文档保持一致。
