"""
ChromaDB 向量库封装。
- 使用持久化存储（config.CHROMA_PATH），重启后数据保留。
- embedding 由 model.get_embedding_function() 提供（本地离线 / OpenAI 兼容）。
"""
import chromadb

import config
from model import get_embedding_function

_client = None
_collection = None


def get_client() -> chromadb.PersistentClient:
    global _client
    if _client is None:
        _client = chromadb.PersistentClient(path=config.CHROMA_PATH)
    return _client


def get_collection():
    global _collection
    if _collection is None:
        _collection = get_client().get_or_create_collection(
            name=config.CHROMA_COLLECTION,
            embedding_function=get_embedding_function(),
            metadata={"hnsw:space": "cosine"},
        )
    return _collection


def reset_collection():
    """
    删除并清空现有集合（用于知识库全量重建）。
    注意：embedding 配置变更后必须重建，否则向量不兼容。
    """
    global _collection
    _collection = None
    try:
        get_client().delete_collection(config.CHROMA_COLLECTION)
    except Exception:  # noqa: BLE001 - 集合不存在时忽略
        pass
