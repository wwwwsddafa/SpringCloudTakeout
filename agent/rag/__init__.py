"""RAG 知识库包。"""
from rag.retriever import search_knowledge
from rag.init_data import init_knowledge_base, load_entries

__all__ = ["search_knowledge", "init_knowledge_base", "load_entries"]
