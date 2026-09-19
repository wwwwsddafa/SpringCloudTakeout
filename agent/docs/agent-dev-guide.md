# AI Agent 开发工程师 — 接口文档

> **版本**：1.0.0  
> **面向**：Python AI Agent 开发工程师  
> **Java 后端地址**：`http://localhost:10002`  
> **日期**：2026-09-06  

---

## 目录

- [1. 概述](#1-概述)
- [2. 你需要的接口（AI 服务对外暴露）](#2-你需要的接口ai-服务对外暴露)
  - [2.1 对话接口 `POST /api/chat`](#21-对话接口-post-apichat)
  - [2.2 健康检查 `GET /health`](#22-健康检查-get-health)
- [3. 你可以调用的接口（Java 后端提供给 AI）](#3-你可以调用的接口java-后端提供给-ai)
  - [3.1 转人工 `POST /customer-service/agent/ai-transfer`](#31-转人工-post-customer-serviceagentai-transfer)
  - [3.2 执行命令 `POST /customer-service/agent/command/execute`](#32-执行命令-post-customer-serviceagentcommandexecute)
  - [3.3 查询命令列表 `GET /customer-service/agent/commands`](#33-查询命令列表-get-customer-serviceagentcommands)
- [4. 完整调用流程](#4-完整调用流程)
- [5. 转人工机制](#5-转人工机制)
- [6. 配置参数](#6-配置参数)

---

## 1. 概述

你是 Python AI Agent 的开发工程师，负责构建基于 **LangGraph + FastAPI** 的 AI 客服核心。

### 你的职责

| 职责 | 说明 |
|------|------|
| 实现 `/api/chat` | 接收用户消息，返回 AI 回复 |
| 意图识别 | 判断用户意图（查订单 / 退单 / 查商品 / 闲聊 / 转人工） |
| 工具调用 | 调用 Java 后端接口获取真实数据 |
| 知识问答 | 从 ChromaDB 检索 FAQ 知识库 |
| 转人工决策 | 判断何时需要转人工，调用 Java 转人工接口 |

### 系统架构

```
┌──────────────┐         ┌──────────────────────┐         ┌─────────────────┐
│   前端 App    │ ──WS──→ │  res-customer-service │ ──HTTP─→│ 你的 AI 服务     │
│              │ ←──WS── │  (Java, :10002)      │ ←──JSON─│  FastAPI :8000  │
└──────────────┘         │                      │         │                 │
                         │  ┌────────────────┐  │         │  /api/chat      │
                         │  │ AgentController │  │←────────│                 │
                         │  │ ai-transfer     │  │ 调用    └─────────────────┘
                         │  │ command/execute │  │
                         │  └────────────────┘  │
                         └──────────────────────┘
```

---

## 2. 你需要的接口（AI 服务对外暴露）

这是你的 Python 服务需要实现的接口，Java 端会调用你。

### 2.1 对话接口 `POST /api/chat`

Java 后端收到用户消息后，转发到你的服务。

#### 请求

```
POST /api/chat
Content-Type: application/json
```

```json
{
  "session_id": "SESc0dd8cd5e0e948d2",
  "user_id": "3500f5b9d1544e82ab554a2d931e6fdb",
  "user_name": "testuser",
  "message": "帮我查一下订单 2096483255623614464",
  "history": [
    { "role": "user", "content": "你好" },
    { "role": "assistant", "content": "您好，有什么可以帮您的？" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | string | ✅ | 会话 ID，格式 `SES{16位hex}` |
| `user_id` | string | ✅ | 用户 ID |
| `user_name` | string | ✅ | 用户昵称 |
| `message` | string | ✅ | 用户输入文本 |
| `history` | array | ❌ | 最近 12 条历史对话，每条 `{role, content}` |

#### 响应

```json
{
  "session_id": "SESc0dd8cd5e0e948d2",
  "reply": "您的订单 2096483255623614464 当前状态是「已支付」，预计 30 分钟内送达。",
  "action": {
    "type": "none",
    "reason": ""
  },
  "tool_calls": [
    {
      "tool": "query_order",
      "result": {
        "orderId": "2096483255623614464",
        "status": "PAID",
        "statusText": "已支付",
        "amount": 38.5
      }
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `session_id` | string | 透传回会话 ID |
| `reply` | string | AI 回复文本 |
| `action.type` | string | `"none"` 正常 / `"transfer_to_human"` 转人工 |
| `action.reason` | string | 转人工原因 |
| `tool_calls` | array | 本次调用的工具及结果 |

#### action.type 说明

| type | 含义 | Java 端行为 |
|------|------|------------|
| `"none"` | 正常回复 | 直接推送给前端 |
| `"transfer_to_human"` | 需要转人工 | Java 自动调用 `ai-transfer` 接口，触发转人工流程 |

> **注意**：当你在 `action.type` 返回 `"transfer_to_human"` 时，Java 端会自动处理转人工（释放 AI 客服、分配人工客服、WebSocket 通知前端）。你不需要再手动调用 `ai-transfer` 接口。

---

### 2.2 健康检查 `GET /health`

```
GET /health
```

```json
{
  "status": "ok",
  "llm_provider": "deepseek",
  "mock_java": false,
  "embedding_provider": "openai"
}
```

---

## 3. 你可以调用的接口（Java 后端提供给 AI）

这些是 Java 后端暴露的 REST 接口，你的 AI 服务可以通过 HTTP 调用它们来获取数据或触发操作。

### 3.1 转人工 `POST /customer-service/agent/ai-transfer`

**使用场景**：你的 AI 在 `reply` 中告知用户「正在转接人工」，同时主动调用此接口完成转人工。

> ⚠️ **通常不需要手动调用**：如果你在 `/api/chat` 响应中返回 `action.type = "transfer_to_human"`，Java 端会自动处理转人工。  
> 此接口适用于以下场景：你的 AI 判断需要转人工，但希望先回复用户一段话，再异步触发转人工。

#### 请求

```
POST /customer-service/agent/ai-transfer
Content-Type: application/json
```

```json
{
  "sessionId": "SESc0dd8cd5e0e948d2",
  "userId": "3500f5b9d1544e82ab554a2d931e6fdb",
  "reason": "用户要求退单，需人工处理"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sessionId` | string | ✅ | 会话 ID |
| `userId` | string | ✅ | 用户 ID |
| `reason` | string | ❌ | 转接原因，默认 "AI 无法处理该问题" |

#### 响应

```json
{
  "code": 200,
  "message": "已转接人工客服",
  "data": {
    "sessionId": "SESc0dd8cd5e0e948d2",
    "assignedAgentId": "1",
    "status": "assigned",
    "reason": "用户要求退单，需人工处理"
  }
}
```

| 返回字段 | 说明 |
|----------|------|
| `status` | `"assigned"` — 已分配人工客服；`"queued"` — 无空闲客服，进入排队 |
| `assignedAgentId` | 分配的人工客服 ID，`null` 表示排队中 |

#### 调用示例（Python）

```python
import requests

def transfer_to_human(session_id: str, user_id: str, reason: str = "AI 无法处理"):
    resp = requests.post(
        "http://localhost:10002/customer-service/agent/ai-transfer",
        json={
            "sessionId": session_id,
            "userId": user_id,
            "reason": reason
        },
        timeout=5
    )
    return resp.json()
```

---

### 3.2 执行命令 `POST /customer-service/agent/command/execute`

**使用场景**：你的 AI 需要查询订单、修改地址、退单等操作时，调用此接口执行对应命令。

#### 请求

```
POST /customer-service/agent/command/execute
Content-Type: application/json
X-User-Id: ai_agent
```

```json
{
  "commandName": "queryOrder",
  "targetUserId": "3500f5b9d1544e82ab554a2d931e6fdb",
  "sessionId": "SESc0dd8cd5e0e948d2",
  "params": {
    "orderId": "2096483255623614464"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `commandName` | string | ✅ | 命令名，见下方命令列表 |
| `targetUserId` | string | ✅ | 目标用户 ID |
| `sessionId` | string | ✅ | 会话 ID |
| `params` | object | ❌ | 命令参数，不同命令参数不同 |

#### 响应

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "data": {
      "orderId": "2096483255623614464",
      "status": "PAID",
      "statusText": "已支付",
      "amount": 38.5,
      "shopName": "老王家黄焖鸡米饭",
      "items": [
        { "name": "黄焖鸡米饭", "price": 22.0, "qty": 1 }
      ],
      "address": "XX市XX区XX路123号",
      "createTime": "2026-09-05 10:42:11"
    },
    "displayType": "CARD"
  }
}
```

需要审批时：

```json
{
  "code": 200,
  "message": "需要审批",
  "data": {
    "needApproval": true,
    "approvalId": "APR_20260906_001",
    "message": "此操作需要审批，已生成审批单"
  }
}
```

#### 可用命令

| commandName | 说明 | 参数 |
|-------------|------|------|
| `queryOrder` | 查询订单详情 | `orderId` (string) — 订单号 |
| `listUserOrders` | 查询用户订单列表 | 无（自动取当前用户） |
| `modifyAddress` | 修改订单收货地址 | `orderId` (string), `newAddress` (string) |
| `refundOrder` | 退单 | `orderId` (string) — 需要审批 |
| `cancelOrder` | 取消订单 | `orderId` (string) — 需要审批 |
| `queryProduct` | 查询商品详情 | `productId` (string) |
| `searchProduct` | 搜索商品 | `keyword` (string) |
| `queryUserInfo` | 查询用户信息 | 无（自动取当前用户） |
| `queryOrderLogistics` | 查询订单物流 | `orderId` (string) |

#### 调用示例（Python）

```python
import requests

def query_order(order_id: str, user_id: str, session_id: str):
    resp = requests.post(
        "http://localhost:10002/customer-service/agent/command/execute",
        headers={"X-User-Id": "ai_agent", "Content-Type": "application/json"},
        json={
            "commandName": "queryOrder",
            "targetUserId": user_id,
            "sessionId": session_id,
            "params": {"orderId": order_id}
        },
        timeout=10
    )
    return resp.json()


def modify_address(order_id: str, new_address: str, user_id: str, session_id: str):
    resp = requests.post(
        "http://localhost:10002/customer-service/agent/command/execute",
        headers={"X-User-Id": "ai_agent", "Content-Type": "application/json"},
        json={
            "commandName": "modifyAddress",
            "targetUserId": user_id,
            "sessionId": session_id,
            "params": {"orderId": order_id, "newAddress": new_address}
        },
        timeout=10
    )
    return resp.json()
```

---

### 3.3 查询命令列表 `GET /customer-service/agent/commands`

**使用场景**：启动时获取所有可用命令的元数据，用于构建 function call schema。

```
GET /customer-service/agent/commands
```

```json
{
  "code": 200,
  "data": [
    {
      "name": "queryOrder",
      "displayName": "查询订单",
      "description": "根据订单号查询订单详情",
      "params": [
        { "name": "orderId", "type": "STRING", "required": true, "description": "订单号" }
      ]
    },
    {
      "name": "modifyAddress",
      "displayName": "修改地址",
      "description": "修改订单收货地址",
      "params": [
        { "name": "orderId", "type": "STRING", "required": true, "description": "订单号" },
        { "name": "newAddress", "type": "STRING", "required": true, "description": "新地址" }
      ]
    }
  ]
}
```

---

## 4. 完整调用流程

### 4.1 正常对话流程

```
用户发送消息
  │
  ▼
Java 后端 (ChatWebSocketHandler)
  │ 构造 ChatRequest { session_id, user_id, message, history }
  │
  ▼
你的 AI 服务 POST /api/chat
  │
  ├─ 意图识别 → order_query / product_query / chitchat / ...
  │
  ├─ 需要查数据 → 调用 Java command/execute → 获取真实数据
  │
  └─ 生成回复 → 返回 ChatResponse { reply, action, tool_calls }
  │
  ▼
Java 后端
  │ 检查 action.type
  │
  ├─ "none" → 推送 reply 给前端
  │
  └─ "transfer_to_human" → 自动调用 ai-transfer → WebSocket 通知前端
```

### 4.2 你的 AI 主动转人工流程

```
你的 AI 判断需要转人工
  │
  ├─ 方案 A（推荐）：
  │   在 /api/chat 响应中返回 action.type = "transfer_to_human"
  │   Java 端自动处理转人工
  │
  └─ 方案 B（异步）：
      先回复用户一段话，再调用 POST /customer-service/agent/ai-transfer
      Java 端处理转人工
```

---

## 5. 转人工机制

### 5.1 触发方式

| 方式 | 说明 | 推荐 |
|------|------|------|
| 响应标记 | 在 `/api/chat` 返回 `action.type = "transfer_to_human"` | ✅ 推荐 |
| 主动调用 | 调用 `POST /customer-service/agent/ai-transfer` | 异步场景 |

### 5.2 转人工后发生什么

1. Java 端调用 `sessionManager.transferSession(sessionId, null)` — 释放 AI 客服
2. 会话状态变为 `WAITING`，进入排队
3. `autoAssignToAgent()` 尝试分配空闲人工客服
4. 通过 WebSocket 向用户推送 `type: "agent_transfer"` 消息
5. 前端收到后切换界面为「人工客服」模式

### 5.3 转人工判断建议

| 场景 | 建议 |
|------|------|
| 用户明确说「转人工」 | 立刻转 |
| 退单 / 取消订单 | 转人工 |
| 连续 3 轮无法理解用户意图 | 转人工 |
| Java 后端接口全部调用失败 | 转人工 |
| 正常查询、闲聊 | 不转 |

---

## 6. 配置参数

### 6.1 Java 后端地址

你的 AI 服务需要知道 Java 后端的地址：

```
JAVA_SERVICE_URL=http://localhost:10002
```

### 6.2 你的服务端口

Java 端通过以下配置找到你的 AI 服务：

```yaml
# res-customer-service application.yml
ai:
  service:
    url: http://localhost:8000
```

### 6.3 超时建议

| 接口 | 建议超时 |
|------|---------|
| `/api/chat`（Java → AI） | 30s |
| `/customer-service/agent/command/execute`（AI → Java） | 10s |
| `/customer-service/agent/ai-transfer`（AI → Java） | 5s |

---

## 附录 A：Java 端已实现的 AI 对接逻辑

以下逻辑已经在 Java 端实现，你无需关心：

- 用户连接时自动分配 AI 客服（`AiAgent`）
- 用户消息自动路由到 AI 先处理（`ChatWebSocketHandler.handleTextMessage`）
- AI 响应中 `action.type = "transfer_to_human"` 自动触发转人工（`AiAgent.generateAiReply`）
- 转人工后自动排队分配人工客服（`QueueService.autoAssignToAgent`）
- WebSocket 推送 `agent_transfer` 消息给前端
- AI 回复自动存入数据库（`chatMessageMapper.insert`）
- 历史消息过滤系统消息（`ChatMessageMapper.findRecentByUserId`）

## 附录 B：完整示例 — LangGraph 集成

```python
from langgraph.graph import StateGraph, END
from typing import TypedDict, Literal
import requests

JAVA_BASE = "http://localhost:10002"

class AgentState(TypedDict):
    session_id: str
    user_id: str
    user_name: str
    message: str
    history: list
    reply: str
    action: dict
    tool_calls: list

def call_java_command(command_name: str, user_id: str, session_id: str, params: dict):
    """调用 Java 后端命令"""
    resp = requests.post(
        f"{JAVA_BASE}/customer-service/agent/command/execute",
        headers={"X-User-Id": "ai_agent", "Content-Type": "application/json"},
        json={
            "commandName": command_name,
            "targetUserId": user_id,
            "sessionId": session_id,
            "params": params
        },
        timeout=10
    )
    return resp.json()

def transfer_to_human(session_id: str, user_id: str, reason: str):
    """调用 Java 后端转人工"""
    resp = requests.post(
        f"{JAVA_BASE}/customer-service/agent/ai-transfer",
        json={
            "sessionId": session_id,
            "userId": user_id,
            "reason": reason
        },
        timeout=5
    )
    return resp.json()

# 在 LangGraph 的 tool_node 中：
# 1. 调用 call_java_command() 获取数据
# 2. 将结果放入 tool_calls
# 3. 在 reply_node 中根据结果生成回复
# 4. 需要转人工时，在 action 中标记 type = "transfer_to_human"
```