# -*- coding: utf-8 -*-
"""全量同步 resfood 数据（来自 resfood_data.sql）到 Elasticsearch。
只使用 Python 标准库，通过 ES REST API 写入。"""
import json
import re
import urllib.request

SQL_PATH = r"C:\Users\wc\Desktop\SpringCloud_takeoutPython\resfood_spider\resfood_data.sql"
ES_URL = "http://localhost:9200"
INDEX = "resfood"

MAPPING = {
    "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "analysis": {
            "analyzer": {
                "ik_max_word_analyzer": {"type": "custom", "tokenizer": "ik_max_word"},
                "ik_smart_analyzer": {"type": "custom", "tokenizer": "ik_smart"},
            }
        },
    },
    "mappings": {
        "properties": {
            "fid":      {"type": "keyword"},
            "fname":    {"type": "text", "analyzer": "ik_max_word_analyzer", "search_analyzer": "ik_smart_analyzer"},
            "normprice": {"type": "double"},
            "realprice": {"type": "double"},
            "detail":   {"type": "text", "analyzer": "ik_max_word_analyzer", "search_analyzer": "ik_smart_analyzer"},
            "fphoto":   {"type": "keyword", "index": False},
            "category": {"type": "text", "analyzer": "ik_max_word_analyzer", "search_analyzer": "ik_smart_analyzer",
                         "fields": {"keyword": {"type": "keyword"}}},
            "status":   {"type": "integer"},
            "likeCount":   {"type": "integer"},
            "dislikeCount": {"type": "integer"},
            "fnameVector": {"type": "dense_vector", "dims": 1024, "index": True, "similarity": "cosine"},
        }
    },
}


def split_values(inner):
    """把括号内内容按深度 0 的逗号切分（字符串内部逗号不算）。"""
    values, buf, in_str = [], [], False
    i, n = 0, len(inner)
    while i < n:
        ch = inner[i]
        if in_str:
            buf.append(ch)
            if ch == "'":
                if i + 1 < n and inner[i + 1] == "'":
                    buf.append("'")
                    i += 2
                    continue
                in_str = False
            i += 1
        else:
            if ch == "'":
                in_str = True
                buf.append(ch)
                i += 1
            elif ch == ",":
                values.append("".join(buf).strip())
                buf = []
                i += 1
            else:
                buf.append(ch)
                i += 1
    values.append("".join(buf).strip())
    return values


def parse_sql_values(text):
    """解析 INSERT INTO ... VALUES 后的每一行 (值, 值, ...) 元组。"""
    start = text.index("VALUES") + len("VALUES")
    body = text[start:]
    rows = []
    i, n = 0, len(body)
    while i < n:
        # 找下一个 '('
        while i < n and body[i] != "(":
            i += 1
        if i >= n:
            break
        # 提取配对括号的内容（记录字符串状态，避免字符串里的括号/逗号干扰）
        depth, in_str, content = 0, False, []
        j = i
        while j < n:
            ch = body[j]
            if in_str:
                content.append(ch)
                if ch == "'":
                    if j + 1 < n and body[j + 1] == "'":
                        content.append("'")
                        j += 2
                        continue
                    in_str = False
                j += 1
                continue
            if ch == "'":
                in_str = True
                content.append(ch)
                j += 1
            elif ch == "(":
                depth += 1
                if depth > 1:
                    content.append(ch)
                j += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    break
                content.append(ch)
                j += 1
            else:
                content.append(ch)
                j += 1
        rows.append(split_values("".join(content)))
        i = j + 1
    return rows


def to_py(v):
    v = v.strip()
    if v.startswith("'") and v.endswith("'"):
        return v[1:-1].replace("''", "'")
    if v in ("NULL", "null", ""):
        return None
    if re.fullmatch(r"-?\d+", v):
        return int(v)
    if re.fullmatch(r"-?\d+\.\d+", v):
        return float(v)
    return v


def http(method, path, payload=None, content_type="application/json"):
    url = ES_URL + path
    data, headers = None, {}
    if payload is not None:
        data = payload if isinstance(payload, bytes) else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = content_type
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        return e.code, json.loads(body) if body.startswith("{") else {"error": body}


def main():
    with open(SQL_PATH, encoding="utf-8") as f:
        sql = f.read()
    rows = parse_sql_values(sql)
    print(f"parsed rows: {len(rows)}")

    # 自检第一行
    first = rows[0]
    print("sample row:", first)
    assert len(first) == 8, f"expected 8 columns, got {len(first)}"

    # 1) 删除旧索引（全量重建）
    code, resp = http("DELETE", f"/{INDEX}")
    print(f"DELETE /{INDEX} -> {code}")

    # 2) 创建索引（含 IK 分词映射）
    code, resp = http("PUT", f"/{INDEX}", MAPPING)
    reason = resp.get("error", {}).get("reason", "ok") if isinstance(resp, dict) else resp
    print(f"PUT /{INDEX} -> {code} {reason}")

    # 3) bulk 写入
    ndjson = []
    for row in rows:
        rec = dict(zip(["fid", "fname", "normprice", "realprice", "detail", "fphoto", "category", "status"],
                       [to_py(v) for v in row]))
        ndjson.append(json.dumps({"index": {"_index": INDEX, "_id": rec["fid"]}}, ensure_ascii=False))
        ndjson.append(json.dumps(rec, ensure_ascii=False))
    body = ("\n".join(ndjson) + "\n").encode("utf-8")
    code, resp = http("POST", "/_bulk", body, "application/x-ndjson")
    if code not in (200, 201):
        print(f"bulk failed: {code} {resp}")
        return
    items = resp.get("items", [])
    errs = [it for it in items if it.get("index", {}).get("status", 200) >= 400]
    ok = [it for it in items if it.get("index", {}).get("status", 200) < 400]
    print(f"bulk done: ok {len(ok)} / failed {len(errs)}")
    for e in errs[:5]:
        print("  failed:", e)

    # 4) 强制刷新并校验文档数
    http("POST", f"/{INDEX}/_refresh")
    code, resp = http("GET", f"/{INDEX}/_count")
    print(f"ES docs: {resp.get('count')}")


if __name__ == "__main__":
    main()
