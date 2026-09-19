"""
RAG 检索逻辑：把用户问题向量化，在 ChromaDB 中取 top-k 相似文档。
"""
import config

from rag.vector_store import get_collection


def search_knowledge(query: str, top_k: int = None) -> list:
    """
    检索相关知识。
    :return: [{"content": str, "metadata": dict, "score": float}, ...]（按相似度降序）
    """
    top_k = top_k or config.RAG_TOP_K
    if not query or not query.strip():
        return []

    col = get_collection()
    try:
        res = col.query(query_texts=[query], n_results=top_k)
    except Exception:  # noqa: BLE001
        return []

    docs = (res.get("documents") or [[]])[0]
    metas = (res.get("metadatas") or [[]])[0]
    dists = (res.get("distances") or [[]])[0]

    results = []
    for d, m, dist in zip(docs, metas, dists):
        # ChromaDB 余弦距离 ∈ [0,2]，转换为相似度分数
        results.append({
            "content": d,
            "metadata": m or {},
            "score": round(1 - float(dist), 4),
        })
    return results
