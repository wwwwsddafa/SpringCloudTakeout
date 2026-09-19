# -*- coding: utf-8 -*-
"""
Stage 2: 调用本地 Ollama 上的「千问 2.5 3B」模型，为每道菜生成外卖式简介(detail)。

设计要点：
  - 只依赖菜名(fname) + 分类(category)，prompt 要求纯中文外卖文案
  - 后处理强制过滤英文/拼音/数字，确保 detail 是干净的中文简介
  - 并发调用(默认 6 线程) + 失败重试(3 次)
  - 断点续跑：已生成 detail 的菜品自动跳过；每 20 条增量落盘，中断不丢数据

配置(环境变量覆盖)：
  OLLAMA_BASE  默认 http://localhost:11434
  MODEL         默认 qwen2.5:3b
  WORKERS       默认 6
"""
import os
import sys
import json
import time
import re
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
JSON_PATH = os.path.join(BASE_DIR, "resfood_data.json")

# ===================== 可配置项 =====================
OLLAMA_BASE = os.environ.get("OLLAMA_BASE", "http://localhost:11434")
MODEL = os.environ.get("MODEL", "qwen2.5:3b")
WORKERS = int(os.environ.get("WORKERS", "6"))
TIMEOUT = 60
MAX_RETRIES = 3
# ===================================================

CAT_CN = {
    "hot": "热菜", "cold": "凉菜", "soup": "汤羹",
    "staple": "主食", "snack": "小吃", "bakery": "烘焙",
}

PROMPT_TMPL = (
    '你是一位外卖平台的美食文案。请为菜品「{name}」（{cat}）'
    '写一句不超过30个汉字的诱人简介，让人看了想点单。'
    '要求：只用简体中文，严禁出现英文、拼音或数字；'
    '只输出这一句简介本身，不要引号、不要解释、不要换行。'
)


def build_prompt(name, cat):
    return PROMPT_TMPL.format(name=name, cat=CAT_CN.get(cat, cat))


def clean_text(s):
    """后处理：去英文/拼音/数字，仅保留中文与中文标点，截断到 40 字。"""
    if not s:
        return ""
    s = s.strip().strip('"').strip("'").strip()
    s = re.sub(r'^简介[：: ]?', '', s)          # 去掉可能的前缀
    s = re.sub(r'[A-Za-z]', '', s)               # 去掉英文/拼音
    s = re.sub(r'\d+', '', s)                    # 去掉数字
    # 仅保留中文与常见中文标点
    s = ''.join(ch for ch in s
                if ('\u4e00' <= ch <= '\u9fff') or ch in '，。、！？；：…— ')
    s = s.strip()
    return s[:40]


def gen_one(rec):
    name = rec.get("fname", "")
    cat = rec.get("category", "")
    prompt = build_prompt(name, cat)
    last_err = None
    for _ in range(MAX_RETRIES):
        try:
            r = requests.post(
                f"{OLLAMA_BASE}/api/generate",
                json={
                    "model": MODEL,
                    "prompt": prompt,
                    "stream": False,
                    "options": {"temperature": 0.85, "max_tokens": 60, "top_p": 0.9},
                },
                timeout=TIMEOUT,
            )
            if r.status_code == 200:
                cleaned = clean_text(r.json().get("response", ""))
                if cleaned:
                    return cleaned
            last_err = f"status {r.status_code}"
        except Exception as e:
            last_err = str(e)
        time.sleep(0.5)
    return None  # 彻底失败


def main():
    with open(JSON_PATH, encoding="utf-8") as f:
        records = json.load(f)

    todo_idx = [i for i, r in enumerate(records) if not r.get("detail")]
    print(f"总记录 {len(records)} | 需生成简介 {len(todo_idx)} | 已存在 {len(records) - len(todo_idx)}")

    if not todo_idx:
        print("无需生成，直接退出。")
        return

    done = fail = 0

    def worker(i):
        return i, gen_one(records[i])

    with ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futures = [ex.submit(worker, i) for i in todo_idx]
        for fut in as_completed(futures):
            i, result = fut.result()
            if result:
                records[i]["detail"] = result
                done += 1
            else:
                fail += 1
            if (done + fail) % 20 == 0:
                with open(JSON_PATH, "w", encoding="utf-8") as f:
                    json.dump(records, f, ensure_ascii=False, indent=2)
                print(f"  进度 {done + fail}/{len(todo_idx)} 成功 {done} 失败 {fail}")

    with open(JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(records, f, ensure_ascii=False, indent=2)

    print(f"\n生成完成: 成功 {done}，失败 {fail}")
    print("样例:")
    shown = 0
    for r in records:
        if r.get("detail") and shown < 10:
            print(f"  [{r['fname']}/{r['category']}] {r['detail']}")
            shown += 1


if __name__ == "__main__":
    main()
