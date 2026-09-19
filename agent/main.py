"""
AI 客服服务入口（FastAPI + LangGraph + ChromaDB）。

启动（在 .venv 下）：
    python main.py
或：
    uvicorn main:app --host 0.0.0.0 --port 8000

接口：
    POST /api/chat        非流式对话
    POST /api/chat/stream SSE 流式对话
    GET  /health          健康检查
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

import config
from api.chat import router as chat_router
from rag.init_data import init_knowledge_base


#yield 之前的代码在 FastAPI 服务启动时运行
#yield 之后的代码在 FastAPI 服务关闭时运行（当前为空）
#FastAPI 是异步框架，初始化操作可能涉及异步调用（如异步数据库连接、异步加载模型），所以必须使用 @asynccontextmanager。
@asynccontextmanager
async def lifespan(app: FastAPI):
    provider = config.resolve_llm_provider()
    if provider == "openai":
        model = config.OPENAI_MODEL
        print(f"[启动] LLM 后端：DeepSeek（{model}）")
    else:
        model = config.OLLAMA_MODEL
        print(f"[启动] LLM 后端：Ollama（{model}）")
    try:
        n = init_knowledge_base()
        print(f"[启动] 知识库就绪，共 {n} 条 FAQ。")
    except Exception as e:  # noqa: BLE001
        print(f"[启动] 知识库初始化失败：{e}")
    yield

# 创建 FastAPI 应用
app = FastAPI(title="外卖 AI 客服 (LangGraph)", version="1.0.0", lifespan=lifespan)
#添加 CORS 中间件, 允许所有来源访问
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"]
)
#注册路由  将 chat_router 中定义的所有接口挂载到 /api 前缀下
app.include_router(chat_router, prefix="/api")

#注册一个 GET 请求的路由，访问路径为 /health
@app.get("/health")
#这是一个 健康检查接口，用于快速验证服务是否正常运行，并返回当前系统的关键配置信息。
async def health():
    return {
        "status": "ok",
        "llm_provider": config.resolve_llm_provider(),
        "mock_java": config.MOCK_JAVA,
        "embedding_provider": config.EMBEDDING_PROVIDER,
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=config.AI_SERVICE_HOST, port=config.AI_SERVICE_PORT)