# 外卖 AI 客服服务 — 接口文档

> **版本**：1.0.0  
> **基础地址**：`http://localhost:8000`  
> **维护者**：AI 客服团队  

---

## 目录

- [1. 概述](#1-概述)
- [2. 对话接口](#2-对话接口)
  - [2.1 非流式对话 `POST /api/chat`](#21-非流式对话-post-apichat)
  - [2.2 SSE 流式对话 `POST /api/chat/stream`](#22-sse-流式对话-post-apichatstream)
- [3. 健康检查 `GET /health`](#3-健康检查-get-health)
- [4. 数据模型详解](#4-数据模型详解)
  - [4.1 请求体 ChatRequest](#41-请求体-chatrequest)
  - [4.2 响应体 ChatResponse](#42-响应体-chatresponse)
  - [4.3 意图类型 intent](#43-意图类型-intent)
  - [4.4 动作类型 action](#44-动作类型-action)
  - [4.5 工具调用 tool_calls](#45-工具调用-tool_calls)
- [5. 内部处理流程](#5-内部处理流程)
- [6. Java 后端对接指南](#6-java-后端对接指南)
- [7. 前端对接指南](#7-前端对接指南)
- [8. 配置说明](#8-配置说明)
- [9. 错误处理](#9-错误处理)

---

## 1. 概述

本服务是「外卖 AI 客服系统」的核心模块，基于 **FastAPI + LangGraph + ChromaDB** 构建。  
它作为独立进程运行，对外提供 HTTP REST API。

### 系统架构

```
┌──────────┐     ┌─────────────────────┐     ┌──────────────────────┐
│  前端/App  │ ──→ │  Java 微服务网关      │ ──→ │  Spring Cloud 微服务   │
│          │     │  :8080              │     │  (订单/商品/用户/物流)  │
└──────────┘     │                     │     └──────────────────────┘
                 │  res-customer-      │               ↑
                 │  service :10002     │               │ HTTP
                 │        │            │               │
                 │        │ 转发消息    │     ┌─────────┴────────────┐
                 │        ↓            │     │  AI 客服服务 (本项目)  │
                 │  POST /api/chat ────→     │  FastAPI :8000        │
                 │  ←── 返回 JSON 回复  │     │  LangGraph 编排       │
                 └─────────────────────┘     └──────────────────────┘
```

### 核心能力

| 能力 | 说明 |
|------|------|
| 意图识别 | 自动分类用户消息（订单查询 / 退单 / 商品查询 / 账户查询 / 知识问答 / 闲聊 / 转人工） |
| 工具调用 | 调用 Java 微服务查询真实数据（订单 / 物流 / 商品 / 用户） |
| 知识问答 | 从 ChromaDB 知识库检索 FAQ（退换货政策、配送说明等） |
| 多轮对话 | 维护会话历史，支持上下文连续对话 |
| 转人工 | 退单/敏感操作自动标记转人工，前端据此展示转接 UI |
| 流式输出 | Server-Sent Events（SSE）打字机效果 |

---

## 2. 对话接口

### 2.1 非流式对话 `POST /api/chat`

**推荐场景**：前端轮询或一次性请求，等待完整回复后展示。

#### 请求

```
POST /api/chat
Content-Type: application/json
```

**请求体**（JSON）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | string | ✅ | 会话 ID，用于标识一次连续对话 |
| `user_id` | string | ✅ | 用户 ID，唯一标识用户 |
| `user_name` | string | ✅ | 用户昵称，用于生成个性化回复 |
| `message` | string | ✅ | 用户当前输入的消息文本 |
| `history` | array | ❌ | 历史对话记录，格式见下方 |

**history 数组元素格式**：

```json
{ "role": "user", "content": "你好" }
{ "role": "assistant", "content": "您好，有什么可以帮您的？" }
```

**完整请求示例**：

```json
{
  "session_id": "SES_20260906_001",
  "user_id": "user_13800001111",
  "user_name": "张三",
  "message": "我的订单 2096213091871096832 到哪了？",
  "history": [
    { "role": "user", "content": "你好" },
    { "role": "assistant", "content": "您好，我是XX外卖的AI客服助手，有什么可以帮您的？" }
  ]
}
```

#### 响应

**HTTP 200** — 成功：

```json
{
  "session_id": "SES_20260906_001",
  "reply": "您的订单 2096213091871096832 正在配送中，骑手李师傅已取餐，预计 11:15 左右送达，当前距您约 1.2 公里。",
  "action": {
    "type": "none",
    "reason": ""
  },
  "tool_calls": [
    {
      "tool": "query_order",
      "result": {
        "roid": "2096213091871096832",
        "status": "DELIVERING",
        "statusText": "配送中",
        "amount": 38.5,
        "shopName": "老王家黄焖鸡米饭",
        "items": [
          { "name": "黄焖鸡米饭", "price": 22.0, "qty": 1 },
          { "name": "可乐", "price": 6.5, "qty": 2 }
        ],
        "address": "XX市XX区XX路123号",
        "createTime": "2026-09-05 10:42:11"
      }
    }
  ]
}
```

**HTTP 200** — 转人工场景：

```json
{
  "session_id": "SES_20260906_001",
  "reply": "已为您提交退单申请，人工客服将尽快核实处理。如需加快进度，也可在订单详情页查看进度。",
  "action": {
    "type": "transfer_to_human",
    "reason": "涉及订单变更/资金，需人工客服审批"
  },
  "tool_calls": [
    {
      "tool": "refund_order",
      "result": { "status": "pending_approval", "message": "退单申请已提交，人工客服将尽快处理" }
    }
  ]
}
```

**HTTP 200** — 异常降级（兜底）：

```json
{
  "session_id": "SES_20260906_001",
  "reply": "服务暂时异常：connection timeout",
  "action": { "type": "none", "reason": "" },
  "tool_calls": []
}
```

---

### 2.2 SSE 流式对话 `POST /api/chat/stream`

**推荐场景**：前端需要打字机效果，逐字展示 AI 回复。

#### 请求

```
POST /api/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

请求体格式与 `/api/chat` 完全相同，参考 [2.1 请求体](#请求)。

#### 响应（SSE 事件流）

响应头：

```
Content-Type: text/event-stream
Cache-Control: no-cache
X-Accel-Buffering: no
```

**事件类型一：`token`** — 逐 token 推送回复内容

```
event: token
data: {"content": "您"}

event: token
data: {"content": "的"}

event: token
data: {"content": "订单"}

...
```

**事件类型二：`done`** — 流结束，携带工具调用结果和转人工标记

```
event: done
data: {"session_id":"SES_20260906_001","tool_calls":[...],"action":{"type":"none","reason":""}}
```

**事件类型三：`error`** — 发生异常

```
event: error
data: {"message": "服务暂时异常：connection timeout"}
```

**完整 SSE 流示例**：

```
event: token
data: {"content": "您"}

event: token
data: {"content": "的"}

event: token
data: {"content": "订单"}

event: token
data: {"content": " "}

event: token
data: {"content": "2096213091871096832"}

event: token
data: {"content": " 正在"}

event: token
data: {"content": "配送"}

event: token
data: {"content": "中"}

event: token
data: {"content": "，"}

event: token
data: {"content": "预计"}

event: token
data: {"content": "11"}

event: token
data: {"content": ":"}

event: token
data: {"content": "15"}

event: token
data: {"content": " 送达"}

event: done
data: {"session_id":"SES_20260906_001","tool_calls":[{"tool":"query_order","result":{...}}],"action":{"type":"none","reason":""}}
```

---

## 3. 健康检查 `GET /health`

```
GET /health
```

**响应示例**：

```json
{
  "status": "ok",
  "llm_provider": "ollama",
  "mock_java": true,
  "embedding_provider": "ollama"
}
```

| 字段 | 说明 |
|------|------|
| `status` | 固定 `"ok"` 表示服务正常 |
| `llm_provider` | 当前使用的 LLM 后端：`"openai"` / `"ollama"` |
| `mock_java` | 是否开启 Mock 模式（`true` 时工具返回模拟数据） |
| `embedding_provider` | 当前使用的 Embedding 后端：`"local"` / `"ollama"` / `"openai"` |

---

## 4. 数据模型详解

### 4.1 请求体 ChatRequest

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `session_id` | string | ✅ | — | 会话唯一标识，建议格式：`SES_{timestamp}_{random}` |
| `user_id` | string | ✅ | — | 用户唯一标识，与 Java 端用户系统一致 |
| `user_name` | string | ✅ | — | 用户昵称，AI 会用于生成个性化回复 |
| `message` | string | ✅ | — | 用户原始输入文本，建议不超过 2000 字符 |
| `history` | array | ❌ | `[]` | 历史对话，取最近 6 轮即可，仅作上下文参考 |

### 4.2 响应体 ChatResponse

| 字段 | 类型 | 说明 |
|------|------|------|
| `session_id` | string | 原样返回请求中的会话 ID |
| `reply` | string | AI 生成的回复文本（纯文本，前端自行处理换行） |
| `action` | object | 动作标记，见 [4.4 动作类型](#44-动作类型-action) |
| `tool_calls` | array | 本次对话调用的工具及结果，见 [4.5 工具调用](#45-工具调用-tool_calls) |

### 4.3 意图类型 intent

AI 内部会将用户消息分类为以下意图（**前端无需关心，但理解其含义有助于调试**）：

| 意图 | 含义 | 触发节点 |
|------|------|----------|
| `order_query` | 查询订单状态、物流、配送进度 | tool → 调 Java 订单接口 |
| `refund` | 要求退款、退货、退单、取消 | 直接标记转人工 |
| `product_query` | 查询商品信息、搜索商品 | tool → 调 Java 商品接口 |
| `account_query` | 查询账户信息（会员、余额等） | tool → 调 Java 用户接口 |
| `faq` | 通用问题（退换货政策、配送规则等） | rag → 查知识库 |
| `chitchat` | 问候、闲聊、无明确诉求 | reply → 直接回复 |
| `transfer_human` | 明确要求转人工客服 | reply → 返回转人工标记 |

### 4.4 动作类型 action

**前端根据 `action.type` 决定后续行为**：

| type | 含义 | 前端处理 |
|------|------|----------|
| `"none"` | 正常回复，无需特殊处理 | 展示 `reply` 文本即可 |
| `"transfer_to_human"` | 需要转人工客服 | 展示 `reply` 后，提示用户「正在转接人工」，同时前端/Java 端发起转人工流程 |

**转人工触发条件**：

| 场景 | reason 示例 |
|------|-------------|
| 用户明确要求转人工 | `"用户明确要求转接人工客服"` |
| 退单/取消订单 | `"涉及订单变更/资金，需人工客服审批"` |
| 后端 Java 服务全部调用失败 | `"后端服务调用失败"` |

### 4.5 工具调用 tool_calls

`tool_calls` 数组记录了本次对话中 AI 调用了哪些 Java 微服务接口，方便前端做调试或二次展示。

**数组元素格式**：

```json
{
  "tool": "query_order",
  "result": { ... }
}
```

**可用工具列表**：

| 工具名 | 用途 | 调用的 Java 接口 |
|--------|------|-----------------|
| `query_order` | 查询订单详情 | `GET /order/detail/{order_id}` |
| `query_order_logistics` | 查询物流/配送状态 | `GET /order/logistics/{order_id}` |
| `list_user_orders` | 查询用户订单列表 | `GET /order/user/{user_id}?page=1&size=20` |
| `query_product` | 查询商品详情 | `GET /product/{product_id}` |
| `search_product` | 搜索商品 | `GET /search/search?keyword=xxx&page=1&size=10` |
| `query_user_info` | 查询用户账户信息 | `GET /user/{user_id}` |
| `refund_order` | 提交退单申请 | 不实际调用 Java，直接标记转人工 |
| `cancel_order` | 提交取消订单申请 | 不实际调用 Java，直接标记转人工 |

**注意**：`refund_order` 和 `cancel_order` 按风控要求不会直连 Java 资金接口，AI 仅返回「已提交申请」并标记转人工。

---

## 5. 内部处理流程

AI 服务内部使用 LangGraph 状态图编排，处理链路如下：

```
用户消息进入
     │
     ▼
┌─────────────┐
│  intent_node │  意图识别（LLM 分类）
│  意图识别     │  输出：intent + confidence
└──────┬──────┘
       │
       ├── order_query / product_query / account_query ──→ tool_node
       │                                                   │
       │   ┌───────────────────────────────────────────────┘
       │   │  调用 Java 微服务获取数据
       │   │  输出：tool_results
       │   │
       ├── faq ──────────────────────────────────────────→ rag_node
       │                                                   │
       │   ┌───────────────────────────────────────────────┘
       │   │  从 ChromaDB 知识库检索 FAQ
       │   │  输出：retrieved_docs
       │   │
       ├── chitchat / transfer_human ─────────────────────→ reply_node
       │                                                   │
       └───────────────────────────────────────────────────┤
                                                           │
                                                           ▼
                                                     ┌──────────┐
                                                     │reply_node│
                                                     │ 生成回复  │
                                                     └────┬─────┘
                                                          │
                                                          ▼
                                                     ┌──────────┐
                                                     │router_node│
                                                     │ 转人工判断 │
                                                     └────┬─────┘
                                                          │
                                          ┌───────────────┴───────────────┐
                                          ▼                               ▼
                                       END                            END
                                    (正常返回)                   (need_human=true)
```

---

## 6. Java 后端对接指南

### 6.1 Java 作为调用方（转发用户消息）

Java 端 `res-customer-service`（端口 10002）需要：

1. 接收前端发来的用户消息
2. 判断是否为 AI 可处理的问题（可由 Java 判断或全部转发给 AI 判断）
3. 构造 `ChatRequest` JSON，调用 `POST http://localhost:8000/api/chat`
4. 收到 `ChatResponse` 后：
   - 提取 `reply` 推送给前端
   - 检查 `action.type`，若为 `"transfer_to_human"`，发起转人工流程

**伪代码示例**：

```java
// 构造请求
String json = """
{
    "session_id": "%s",
    "user_id": "%s",
    "user_name": "%s",
    "message": "%s",
    "history": %s
}
""".formatted(sessionId, userId, userName, message, historyJson);

// 调用 AI 服务
HttpResponse response = httpClient.post("http://localhost:8000/api/chat", json);

// 解析响应
ChatResponse result = objectMapper.readValue(response.body(), ChatResponse.class);

// 推送给前端
pushToFrontend(result.getReply());

// 判断是否转人工
if ("transfer_to_human".equals(result.getAction().get("type"))) {
    transferToHumanAgent(sessionId, result.getAction().get("reason"));
}
```

### 6.2 Java 作为被调用方（AI 工具调用 Java 微服务）

AI 服务通过 HTTP 调用 Java 网关（默认 `http://localhost:8080`）获取数据。

**Java 端需要提供的接口**：

| 接口路径 | 方法 | 参数 | 返回 |
|----------|------|------|------|
| `/order/detail/{order_id}` | GET | 路径参数 | 订单详情 JSON |
| `/order/logistics/{order_id}` | GET | 路径参数 | 物流信息 JSON |
| `/order/user/{user_id}` | GET | `?page=1&size=20` | 用户订单列表 JSON |
| `/product/{product_id}` | GET | 路径参数 | 商品详情 JSON |
| `/search/search` | GET | `?keyword=xxx&page=1&size=10` | 搜索结果 JSON |
| `/user/{user_id}` | GET | 路径参数 | 用户信息 JSON |

**注意**：AI 服务内置了容错机制，Java 接口不可用时不会崩溃，而是返回错误信息并标记转人工。

### 6.3 Mock 模式联调

`.env` 中 `MOCK_JAVA=true` 时，AI 服务不调用真实 Java 接口，直接返回模拟数据。  
**前后端联调时可以保持 Mock 模式**，先对接对话流程，等 Java 接口就绪后再切换 `MOCK_JAVA=false`。

---

## 7. 前端对接指南

### 7.1 非流式模式（推荐起步）

**适用场景**：快速集成、轮询场景。

**流程**：

```
用户输入 → 前端发送 POST /api/chat → 等待完整响应 → 展示 reply

如果 action.type === "transfer_to_human"：
  → 展示 "正在为您转接人工客服..."
  → 前端通知 Java 端发起转人工
```

**JavaScript 示例**：

```javascript
async function sendMessage(message, history = []) {
  const response = await fetch('http://localhost:8000/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      session_id: currentSessionId,
      user_id: currentUserId,
      user_name: currentUserName,
      message: message,
      history: history  // 最近几轮对话
    })
  });

  const data = await response.json();

  // 展示 AI 回复
  displayMessage(data.reply, 'assistant');

  // 检查是否需要转人工
  if (data.action.type === 'transfer_to_human') {
    showTransferToHuman(data.reply);
  }

  return data;
}
```

### 7.2 SSE 流式模式（推荐正式使用）

**适用场景**：需要打字机效果，提升用户体验。

**流程**：

```
用户输入 → 前端发送 POST /api/chat/stream → 持续接收 token 事件 → 逐字拼接展示

收到 done 事件 → 检查 action.type → 判断是否转人工
```

**JavaScript 示例**：

```javascript
async function sendMessageStream(message, history = []) {
  const response = await fetch('http://localhost:8000/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      session_id: currentSessionId,
      user_id: currentUserId,
      user_name: currentUserName,
      message: message,
      history: history
    })
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let replyText = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';  // 保留未完成的行

    for (const line of lines) {
      if (line.startsWith('event: token')) {
        // 下一行是 data
        continue;
      }
      if (line.startsWith('data: ')) {
        const data = JSON.parse(line.slice(6));
        if (data.content) {
          replyText += data.content;
          updateMessage(replyText);  // 逐字更新 UI
        }
      }
      if (line.startsWith('event: done')) {
        // done 事件的 data 在下一行
        continue;
      }
      if (line.startsWith('event: error')) {
        continue;
      }
    }
  }

  // 最终处理 done 事件（可能在上面的循环中已处理，这里做兜底）
  // 实际项目中建议用 EventSource 或更完善的 SSE 解析库
}
```

### 7.3 历史对话管理

**前端需要维护 `history` 数组**，每次请求时带上最近几轮对话（建议 6 轮即可）：

```javascript
let history = [];

function onUserSend(message) {
  // 发送时带上历史
  sendMessage(message, history);

  // 用户消息入历史
  history.push({ role: 'user', content: message });
}

function onAiReply(reply) {
  // AI 回复入历史
  history.push({ role: 'assistant', content: reply });

  // 保留最近 12 条（6 轮对话）
  if (history.length > 12) {
    history = history.slice(-12);
  }
}
```

---

## 8. 配置说明

AI 服务通过 `.env` 文件配置，修改后需重启服务。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `LLM_PROVIDER` | `auto` | LLM 后端：`auto` / `openai` / `ollama` |
| `OPENAI_API_KEY` | — | OpenAI 兼容 API Key（DeepSeek/OpenAI/通义） |
| `OLLAMA_BASE_URL` | `http://localhost:11434/v1` | Ollama 本地地址 |
| `OLLAMA_MODEL` | `qwen2.5:3b` | Ollama 模型名称 |
| `EMBEDDING_PROVIDER` | `local` | Embedding 后端：`local` / `ollama` / `openai` |
| `MOCK_JAVA` | `false` | 是否 Mock Java 后端（`true` 时工具返回模拟数据） |
| `JAVA_GATEWAY_URL` | `http://localhost:8080` | Java 微服务网关地址 |
| `AI_SERVICE_HOST` | `0.0.0.0` | AI 服务监听地址 |
| `AI_SERVICE_PORT` | `8000` | AI 服务监听端口 |
| `RAG_TOP_K` | `3` | 知识库检索返回条数 |
| `JAVA_HTTP_TIMEOUT` | `10` | 调用 Java 接口超时时间（秒） |

---

## 9. 错误处理

### 9.1 AI 服务异常

当 AI 服务内部发生异常时，**不会返回 HTTP 5xx**，而是返回 HTTP 200，`reply` 中包含错误描述：

```json
{
  "session_id": "xxx",
  "reply": "服务暂时异常：connection timeout",
  "action": { "type": "none", "reason": "" },
  "tool_calls": []
}
```

### 9.2 Java 后端不可用

当 Java 后端接口全部调用失败时，AI 会自动标记转人工：

```json
{
  "session_id": "xxx",
  "reply": "抱歉，查询服务暂时不可用，已为您转接人工客服，请稍候。",
  "action": {
    "type": "transfer_to_human",
    "reason": "后端服务调用失败"
  },
  "tool_calls": [
    { "tool": "query_order", "result": { "_error": true, "message": "无法连接后端服务：..." } }
  ]
}
```

### 9.3 SSE 流中断

流式模式下如果发生异常，会发送 `error` 事件：

```
event: error
data: {"message": "服务暂时异常：..."}
```

前端收到 `error` 事件后，应展示错误提示，并允许用户重试。

---

> **文档更新日期**：2026-09-06  
> **对应版本**：v1.0.0