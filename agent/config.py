"""
全局配置（从 .env 读取，缺省给出可离线运行的默认值）。

LLM 支持两种后端（均兼容 OpenAI API 格式）：
  1. OpenAI 兼容端点（DeepSeek / OpenAI / 通义千问 等）—— 需要 API Key
  2. Ollama 本地模型（如 qwen2.5:3b）—— 无需联网、无需 Key，开箱即用

未配置 API Key 且检测到本地 Ollama 时，会自动回退到 Ollama，保证项目可立即跑通。
"""
import os
import httpx

from dotenv import load_dotenv

load_dotenv()

# ---------- LLM（OpenAI 兼容） ----------
# 可直接用 DeepSeek；也可切换到 OpenAI / 通义等任意 OpenAI 兼容服务。
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", os.getenv("DEEPSEEK_API_KEY", ""))
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")).rstrip("/")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", os.getenv("DEEPSEEK_MODEL", "deepseek-chat"))

# ---------- LLM（Ollama 本地） ----------
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434/v1").rstrip("/")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5:3b")

# ---------- 选择使用哪种 LLM 后端 ----------
# 取值： "openai" | "ollama" | "auto"(默认)
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "auto").lower()
LLM_TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.3"))

# ---------- Embedding（知识库向量化） ----------
# "local" ：ChromaDB 内置本地模型（all-MiniLM-L6-v2，首次运行需联网下载）
# "ollama"：本地 Ollama embedding（如 bge-m3，完全离线，推荐）
# "openai"：OpenAI 兼容 Embedding（需 KEY）
EMBEDDING_PROVIDER = os.getenv("EMBEDDING_PROVIDER", "local").lower()
EMBEDDING_OLLAMA_MODEL = os.getenv("EMBEDDING_OLLAMA_MODEL", "bge-m3")
EMBEDDING_API_KEY = os.getenv("EMBEDDING_API_KEY", OPENAI_API_KEY)
EMBEDDING_BASE_URL = os.getenv("EMBEDDING_BASE_URL", OPENAI_BASE_URL).rstrip("/")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")

# ---------- Java 微服务（网关地址） ----------
JAVA_GATEWAY_URL = os.getenv("JAVA_GATEWAY_URL", os.getenv("JAVA_SERVICE_URL", "http://localhost:10002")).rstrip("/")
# 离线演示模式：true 时工具返回模拟数据，无需真实 Java 后端
MOCK_JAVA = os.getenv("MOCK_JAVA", "false").lower() in ("1", "true", "yes", "on")
JAVA_HTTP_TIMEOUT = float(os.getenv("JAVA_HTTP_TIMEOUT", "10"))

# ---------- AI 服务自身 ----------
AI_SERVICE_HOST = os.getenv("AI_SERVICE_HOST", "0.0.0.0")
AI_SERVICE_PORT = int(os.getenv("AI_SERVICE_PORT", "8000"))

# ---------- 检索参数 ----------
RAG_TOP_K = int(os.getenv("RAG_TOP_K", "3"))
CHROMA_PATH = os.getenv("CHROMA_PATH", "./chroma_db")
CHROMA_COLLECTION = os.getenv("CHROMA_COLLECTION", "faq_knowledge")


def ollama_reachable(base_url: str = OLLAMA_BASE_URL, timeout: float = 1.5) -> bool:
    """快速探测本地 Ollama 是否可用。"""
    try:
        with httpx.Client(timeout=timeout) as c:
            c.get(base_url.replace("/v1", "") + "/api/tags")
        return True
    except Exception:
        return False


def resolve_llm_provider() -> str:
    """
    决定实际使用的 LLM 后端：
      - 显式配置为 ollama  → ollama
      - 显式配置为 openai  → 有 Key 用 openai，无 Key 且 Ollama 可用则回退 ollama
      - auto（默认）       → 有 Key 用 openai，否则尝试 ollama
    """
    if LLM_PROVIDER == "ollama":
        return "ollama"
    if LLM_PROVIDER == "openai":
        if OPENAI_API_KEY:
            return "openai"
        if ollama_reachable():
            return "ollama"
        return "openai"  # 仍返回 openai，运行时给出明确报错
    # auto
    if OPENAI_API_KEY:
        return "openai"
    if ollama_reachable():
        return "ollama"
    return "openai"