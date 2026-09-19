"""
LLM 与 Embedding 工厂。
根据 config 解析出的后端，返回 LangChain 兼容的模型实例。
"""
from langchain_openai import ChatOpenAI

import config


def get_chat_model(streaming: bool = False) -> ChatOpenAI:
    """
    返回对话模型（兼容 OpenAI API 格式）。
    :param streaming: 是否开启流式（供 SSE 打字机使用）
    """
    provider = config.resolve_llm_provider()

    if provider == "ollama":
        return ChatOpenAI(
            model=config.OLLAMA_MODEL,
            base_url=config.OLLAMA_BASE_URL,
            api_key="ollama",  # Ollama 不校验 key，但字段必填
            temperature=config.LLM_TEMPERATURE,
            streaming=streaming,
        )

    # openai 兼容（DeepSeek / OpenAI / 通义 ...）
    if not config.OPENAI_API_KEY:
        raise RuntimeError(
            "未检测到 LLM API Key。请在 .env 中配置 OPENAI_API_KEY（或 DEEPSEEK_API_KEY），"
            "或将 LLM_PROVIDER 设为 ollama 并使用本地模型。"
        )
    return ChatOpenAI(
        model=config.OPENAI_MODEL,
        base_url=config.OPENAI_BASE_URL,
        api_key=config.OPENAI_API_KEY,
        temperature=config.LLM_TEMPERATURE,
        streaming=streaming,
    )


class OllamaEmbedding:
    """
    通过本地 Ollama 的 /api/embed 生成向量（如 bge-m3），完全离线、无需下载模型。
    base_url 兼容 http://localhost:11434 与 http://localhost:11434/v1 两种写法。
    """

    def __init__(self, base_url: str, model: str):
        self._base = base_url.replace("/v1", "").rstrip("/")
        self._model = model

    def name(self) -> str:
        return f"ollama-{self._model}"

    def embed_documents(self, texts):
        import httpx

        with httpx.Client(timeout=120) as c:
            r = c.post(
                self._base + "/api/embed",
                json={"model": self._model, "input": list(texts)},
            )
            r.raise_for_status()
            return r.json()["embeddings"]

    def embed_query(self, input):
        # ChromaDB 期望返回 List[List[float]]（与 __call__ 一致）
        if isinstance(input, str):
            input = [input]
        return self.embed_documents(input)

    def __call__(self, input):
        # ChromaDB 1.x 调用协议：传入 list[str]
        if isinstance(input, str):
            input = [input]
        return self.embed_documents(input)


class OpenAICompatibleEmbedding:
    """自定义 Embedding 函数，支持任意 OpenAI 兼容端点（含自定义 base_url）。"""

    def __init__(self, api_key: str, base_url: str, model: str):
        from openai import OpenAI

        self._client = OpenAI(api_key=api_key or "empty", base_url=base_url)
        self._model = model

    def name(self) -> str:
        return f"openai-{self._model}"

    def embed_documents(self, texts):
        resp = self._client.embeddings.create(model=self._model, input=texts)
        return [d.embedding for d in resp.data]

    def embed_query(self, input):
        # ChromaDB 期望返回 List[List[float]]（与 __call__ 一致）
        if isinstance(input, str):
            input = [input]
        return self.embed_documents(input)

    def __call__(self, input):
        # ChromaDB 1.x 调用协议：传入 list[str]
        if isinstance(input, str):
            input = [input]
        return self.embed_documents(input)


def get_embedding_function():
    """
    返回 ChromaDB 可用的 embedding 函数。
    - local  ：ChromaDB 内置本地模型（all-MiniLM-L6-v2，首次运行需联网下载）
    - ollama ：本地 Ollama embedding（如 bge-m3，完全离线，推荐）
    - openai ：OpenAI 兼容 Embedding
    """
    if config.EMBEDDING_PROVIDER == "openai":
        return OpenAICompatibleEmbedding(
            api_key=config.EMBEDDING_API_KEY,
            base_url=config.EMBEDDING_BASE_URL,
            model=config.EMBEDDING_MODEL,
        )
    if config.EMBEDDING_PROVIDER == "ollama":
        return OllamaEmbedding(config.OLLAMA_BASE_URL, config.EMBEDDING_OLLAMA_MODEL)

    # 本地离线 embedding（ChromaDB 默认）
    from chromadb.utils import embedding_functions

    return embedding_functions.DefaultEmbeddingFunction()
