# -*- coding: utf-8 -*-
"""为 resfood 索引批量生成 fnameVector 向量并写回 ES。

向量来源：本地 Ollama 的 bge-m3 模型（1024 维，中文语义效果好）。
索引要求：resfood.fnameVector 必须是 dense_vector(dims=1024, index=true, similarity=cosine)。

用法：
    python sync_vectors_ollama.py            # 全量生成（已生成过的会跳过）
    python sync_vectors_ollama.py --force    # 强制全部重新生成
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request

ES_URL = "http://localhost:9200"
INDEX = "resfood"
OLLAMA_URL = "http://localhost:11434/api/embed"
MODEL = "bge-m3"
DIMS = 1024

# 用哪些字段拼成待向量化的文本。
# 2026-09-05 起改为 fname + detail：语义检索才能回答"辣的/软糯的菜"这类 query。
# 注意执行顺序(否则会用旧 detail 向量化)：
#   1) 把最新的 resfood_data.sql 导入 MySQL
#   2) 跑 sync_resfood_to_es.py 把【新 detail】同步进 ES 的 resfood 文档
#   3) 再跑本脚本 --force 用 fname+detail 重算全部向量
# 若仍只想按菜名检索，改回 ["fname"] 即可。
TEXT_FIELDS = ["fname", "detail"]

EMBED_BATCH = 16   # 一次送给 Ollama 的文本条数
BULK_BATCH = 64    # 攒够多少条再打一次 ES _bulk


def http(method, path, payload=None, ctype="application/json", timeout=180):
    url = ES_URL + path
    data = None
    headers = {}
    if payload is not None:
        data = payload if isinstance(payload, bytes) else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = ctype
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        return e.code, json.loads(raw) if raw.startswith("{") else {"error": raw}


def fetch_docs(force):
    """用 scroll 拉出需要生成向量的文档。"""
    query = {"match_all": {}} if force else {"bool": {"must_not": {"exists": {"field": "fnameVector"}}}}
    code, resp = http("POST", f"/{INDEX}/_search?scroll=2m", {
        "size": 200,
        "query": query,
        "_source": list(set(TEXT_FIELDS + ["fid"])),
    })
    if code != 200:
        print(f"查询失败: {code} {resp}")
        sys.exit(1)

    docs, scroll_id = [], resp.get("_scroll_id")
    while True:
        hits = resp["hits"]["hits"]
        if not hits:
            break
        docs.extend(hits)
        code, resp = http("POST", "/_search/scroll", {"scroll": "2m", "scroll_id": scroll_id})
        if code != 200:
            break
        scroll_id = resp.get("_scroll_id")
    if scroll_id:
        http("DELETE", "/_search/scroll", {"scroll_id": scroll_id})
    return docs


def build_text(src):
    parts = [str(src[f]).strip() for f in TEXT_FIELDS if src.get(f)]
    return " ".join(parts) if parts else ""


def embed(texts):
    payload = json.dumps({"model": MODEL, "input": texts}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(OLLAMA_URL, data=payload,
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=600) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    vecs = data.get("embeddings")
    if not vecs or len(vecs) != len(texts):
        raise RuntimeError(f"向量数量不匹配: 期望 {len(texts)}，实际 {len(vecs or [])}")
    for v in vecs:
        if len(v) != DIMS:
            raise RuntimeError(f"维度不匹配: 期望 {DIMS}，实际 {len(v)}")
    return vecs


def flush_bulk(lines):
    if not lines:
        return 0, 0
    body = ("\n".join(lines) + "\n").encode("utf-8")
    code, resp = http("POST", "/_bulk", body, "application/x-ndjson")
    if code not in (200, 201):
        print(f"  bulk 失败: {code} {str(resp)[:300]}")
        return 0, len(lines) // 2
    items = resp.get("items", [])
    ok = sum(1 for it in items if it.get("update", {}).get("status", 500) < 400)
    return ok, len(items) - ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="忽略已有向量，全部重新生成")
    args = ap.parse_args()

    print(f"ES    : {ES_URL}/{INDEX}")
    print(f"模型  : {MODEL} ({DIMS} 维)  via {OLLAMA_URL}")
    print(f"语义源: {' + '.join(TEXT_FIELDS)}")
    print("-" * 50)

    docs = fetch_docs(args.force)
    total = len(docs)
    print(f"待生成向量: {total} 条")
    if total == 0:
        print("没有需要处理的文档，退出。")
        return

    lines, done, failed, t0 = [], 0, 0, time.time()
    for i in range(0, total, EMBED_BATCH):
        chunk = docs[i:i + EMBED_BATCH]
        texts = [build_text(d["_source"]) for d in chunk]
        texts = [t if t else " " for t in texts]
        vecs = embed(texts)

        for d, v in zip(chunk, vecs):
            lines.append(json.dumps({"update": {"_index": INDEX, "_id": d["_id"]}}, ensure_ascii=False))
            lines.append(json.dumps({"doc": {"fnameVector": v}}, ensure_ascii=False))

        if len(lines) // 2 >= BULK_BATCH:
            ok, bad = flush_bulk(lines)
            done += ok
            failed += bad
            lines = []

        elapsed = time.time() - t0
        processed = min(i + EMBED_BATCH, total)
        eta = elapsed / processed * (total - processed) if processed else 0
        print(f"  进度 {processed}/{total}  已写入 {done}  耗时 {elapsed:.0f}s  预计剩余 {eta:.0f}s", flush=True)

    if lines:
        ok, bad = flush_bulk(lines)
        done += ok
        failed += bad

    http("POST", f"/{INDEX}/_refresh")
    code, resp = http("POST", f"/{INDEX}/_count", {"query": {"exists": {"field": "fnameVector"}}})

    print("-" * 50)
    print(f"完成: 成功 {done} / 失败 {failed} / 总耗时 {time.time() - t0:.0f}s")
    print(f"索引中含 fnameVector 的文档数: {resp.get('count')}")


if __name__ == "__main__":
    main()
