"""
知识库初始化：解析 knowledge-base.md，逐条 embed 入库 ChromaDB。

解析规则：
  ## 1. 退换货政策            → 顶层分类（category）
  ### 1.1 退单条件            → 条目（subcategory / 标题）
  **Q**：...                  → 问题
  **A**：...                  → 回答（持续到条目结束）
支持多行 Q/A。重新运行即全量刷新（先删后插）。
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import config
from rag.vector_store import get_collection, reset_collection

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MD_CANDIDATES = [
    os.path.join(ROOT, "knowledge-base.md"),
    os.path.join(ROOT, "docs", "knowledge-base.md"),
]


def _find_md() -> str:
    for p in MD_CANDIDATES:
        if os.path.exists(p):
            return p
    raise FileNotFoundError(
        "未找到 knowledge-base.md，请将其放置于项目根目录或 docs/ 目录下。"
    )


def load_entries() -> list:
    """读取并解析知识库 Markdown，返回条目列表。"""
    md_path = _find_md()
    with open(md_path, encoding="utf-8") as f:
        text = f.read()

    entries = []
    # 顶层分类：## 1. 退换货政策
    top_parts = re.split(r'^##\s+(\d+)\.\s+(.+)$', text, flags=re.M)
    # top_parts[0] 为前置内容；之后是 (编号, 标题, 正文) 三元组
    for i in range(1, len(top_parts), 3):
        cat_no = top_parts[i].strip()
        cat_title = top_parts[i + 1].strip()
        body = top_parts[i + 2]

        # 条目：### 1.1 退单条件
        sub_parts = re.split(r'^###\s+(\d+\.\d+)\s+(.+)$', body, flags=re.M)
        for j in range(1, len(sub_parts), 3):
            sub_no = sub_parts[j].strip()
            sub_title = sub_parts[j + 1].strip()
            entry_body = sub_parts[j + 2]

            qm = re.search(r'\*\*Q\*\*[：:]\s*(.*?)\s*(?=\*\*A\*\*|$)', entry_body, re.S)
            am = re.search(r'\*\*A\*\*[：:]\s*(.*?)(?=\Z)', entry_body, re.S)
            if not qm or not am:
                continue
            q = re.sub(r'\s+', ' ', qm.group(1)).strip()
            a = re.sub(r'\s+', ' ', am.group(1)).strip()
            if not q or not a:
                continue

            content = f"【{cat_title} · {sub_title}】\n问：{q}\n答：{a}"
            entries.append({
                "id": f"kb_{cat_no}_{sub_no.replace('.', '_')}",
                "content": content,
                "metadata": {
                    "category": cat_title,
                    "subcategory": sub_title,
                    "section": f"{cat_no}.{sub_no}",
                },
            })
    return entries


def init_knowledge_base() -> int:
    """
    全量初始化知识库（先删后插，保证刷新）。
    :return: 成功入库的条目数
    """
    # 全量重建：先删除旧集合（embedding 配置变化时向量不兼容），再重新创建
    reset_collection()
    col = get_collection()
    entries = load_entries()
    if not entries:
        return 0

    ids = [e["id"] for e in entries]
    docs = [e["content"] for e in entries]
    metas = [e["metadata"] for e in entries]

    # 删除旧数据（如果存在），实现幂等刷新
    try:
        col.delete(ids=ids)
    except Exception:  # noqa: BLE001
        pass

    col.upsert(ids=ids, documents=docs, metadatas=metas)
    return len(ids)


if __name__ == "__main__":
    n = init_knowledge_base()
    print(f"知识库初始化完成，共入库 {n} 条 FAQ。")