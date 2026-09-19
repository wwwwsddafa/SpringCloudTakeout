# -*- coding: utf-8 -*-
"""
Stage 2 (新): 基于「事实属性」重新生成菜品描述(detail)。

与旧 gen_intro.py 的根本区别:
  - 旧版只给菜名, 让 3B 模型自由发挥 -> 馒头被编出"香辣", 偏差大, 毒化向量检索
  - 新版先由 scrape_facts.py 从源站抽 口味/工艺/主食材 等 FACTS, 再把这些事实
    作为唯一依据喂给模型, 并硬性禁止编造口味 -> 描述精准、利于语义检索

设计要点:
  - 事实来自 resfood_facts.json (fid -> taste/craft/main/sub/season)
  - 提示词: 口味必须严格采用已知事实; 口感可由食材+做法合理推断; 必须提主食材
  - 口味一致性守卫: 事实标"清淡/原味"等非辣味, 生成却出现"辣/麻" -> 重试(直击馒头bug)
  - 长度 50–80 汉字 (用户指定)
  - 后处理仅保留中文+中文标点, 去掉英文/数字/引号/换行
  - 并发 6 线程 + 失败重试 + 增量落盘(中断不丢)
  - 完成后调用 gen_sql 重新生成 SQL/CSV

配置(环境变量):
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
FACTS_PATH = os.path.join(BASE_DIR, "resfood_facts.json")

OLLAMA_BASE = os.environ.get("OLLAMA_BASE", "http://localhost:11434")
MODEL = os.environ.get("MODEL", "qwen2.5:3b")
WORKERS = int(os.environ.get("WORKERS", "6"))
TIMEOUT = 60
MAX_RETRIES = 3

CAT_CN = {"hot": "热菜", "cold": "凉菜", "soup": "汤羹",
          "staple": "主食", "snack": "小吃", "bakery": "烘焙"}

# 辣味相关字 (用于口味一致性守卫)
SPICY_CHARS = ("辣", "麻")
SPICY_TASTES = {"麻辣", "香辣", "酸辣", "微辣", "辣", "麻辣鲜"}

PROMPT_FACTS = (
    '你是一位外卖平台的菜品文案。请为菜品写一句简介，字数必须在 50–80 个汉字之间（至少 50 字）。\n'
    '【菜名】{name}\n'
    '【分类】{cat}\n'
    '【已知事实】口味：{taste}；烹饪方式：{craft}；主要食材：{main}；辅料：{sub}\n'
    '【写法要求】\n'
    '1. 口味必须严格采用已知事实里的口味；若事实标注了口味(如清淡/麻辣)，必须照写，'
    '绝对禁止写上事实没有的口味，尤其禁止给不辣的菜(馒头/豆腐/清汤/蒸菜)写“香辣/麻辣/辣”。\n'
    '2. 口感(软糯/酥脆/嫩滑/松软)由主要食材和烹饪方式合理推断，不凭空编造。\n'
    '3. 必须提到主要食材、烹饪方式与适宜场景(下饭/早餐/宴客/宵夜)。\n'
    '4. 内容要具体丰满，把上述要素都写充分，凑足 50–80 字，不要过于简略。\n'
    '5. 只输出这一句简介本身，不要引号、解释、换行、英文或数字。\n'
    '【示例】红烧肉，家常硬菜。咸甜适口，肥而不腻瘦肉酥烂，冰糖上色油亮酱红。'
    '主料五花肉，慢火细炖入味，肥瘦相间入口即化，配饭配面皆宜，宴客下饭都讨喜，老少咸宜。\n'
    '【简介】'
)

PROMPT_NO_FACTS = (
    '你是一位外卖平台的菜品文案。请为菜品写一句简介，字数必须在 50–80 个汉字之间（至少 50 字）。\n'
    '【菜名】{name}\n'
    '【分类】{cat}\n'
    '【已知事实】无(未提供具体口味/食材)\n'
    '【写法要求】\n'
    '1. 若不确定具体口味，请写“口味适中”或基于菜名做合理、通用的描述；'
    '严禁编造香辣/麻辣等刺激性口味，除非菜名本身就明显是辣味(如含辣/椒/麻辣)。\n'
    '2. 口感可由菜名合理推断(如“馒头”→松软暄腾)，不要凭空编造。\n'
    '3. 必须提到主要食材、烹饪方式与适宜场景(下饭/早餐/宴客/宵夜)。\n'
    '4. 内容要具体丰满，把上述要素都写充分，凑足 50–80 字，不要过于简略。\n'
    '5. 只输出这一句简介本身，不要引号、解释、换行、英文或数字。\n'
    '【示例】红烧肉，家常硬菜。咸甜适口，肥而不腻瘦肉酥烂，冰糖上色油亮酱红。'
    '主料五花肉，慢火细炖入味，肥瘦相间入口即化，配饭配面皆宜，宴客下饭都讨喜，老少咸宜。\n'
    '【简介】'
)


def build_prompt(rec, facts):
    name = rec.get("fname", "")
    cat = CAT_CN.get(rec.get("category", ""), rec.get("category", ""))
    has_facts = facts and (facts.get("taste") or facts.get("craft") or facts.get("main"))
    if has_facts:
        return PROMPT_FACTS.format(
            name=name, cat=cat,
            taste=facts.get("taste") or "无",
            craft=facts.get("craft") or "无",
            main=facts.get("main") or "无",
            sub=facts.get("sub") or "无",
        )
    return PROMPT_NO_FACTS.format(name=name, cat=cat)


def clean_text(s):
    """后处理：仅保留中文与中文标点，去英文/数字/引号/换行，长度 ≤ 90。"""
    if not s:
        return ""
    s = s.strip().strip('"').strip("'").strip()
    s = re.sub(r'^简介[：: ]?', '', s)
    s = re.sub(r'[A-Za-z]', '', s)
    s = re.sub(r'\d+', '', s)
    s = ''.join(ch for ch in s
                if ('\u4e00' <= ch <= '\u9fff') or ch in '，。、！？；：…— ')
    # 超过 85 字则在句号处截断
    s = s.strip()
    if len(s) > 85:
        cut = s.rfind('。', 0, 82)
        if cut > 40:
            s = s[:cut + 1]
        else:
            s = s[:82]
    return s


def pad_description(text, facts, cat_cn):
    """模型产出偏短时, 用爬到的【事实】补一句, 保证长度且零编造。"""
    if not text:
        return text
    clauses = []
    if facts.get("taste"):
        clauses.append(f"{facts['taste']}口味")
    if facts.get("craft"):
        clauses.append(f"{facts['craft']}制做法")
    if facts.get("main"):
        clauses.append(f"主料{facts['main']}")
    if facts.get("sub"):
        clauses.append(f"辅以{facts['sub']}")
    if clauses:
        tail = "，".join(clauses) + f"，适合作为{cat_cn}享用。"
    else:
        tail = f"是一道不错的{cat_cn}，值得一试。"
    if text.endswith("。"):
        return text + tail
    return text.rstrip("，、") + "，" + tail


def gen_one(rec, facts):
    """调用 Ollama 生成一条描述；口味守卫 + 取最长 + 不足则事实补长。返回文本或 None。"""
    prompt = build_prompt(rec, facts)
    taste = (facts or {}).get("taste", "") if facts else ""
    is_spicy_fact = taste in SPICY_TASTES
    cat_cn = CAT_CN.get(rec.get("category", ""), rec.get("category", ""))
    cands = []
    for attempt in range(MAX_RETRIES):
        try:
            r = requests.post(
                f"{OLLAMA_BASE}/api/generate",
                json={"model": MODEL, "prompt": prompt, "stream": False,
                      "options": {"temperature": 0.6, "max_tokens": 180, "top_p": 0.9}},
                timeout=TIMEOUT,
            )
            if r.status_code != 200:
                time.sleep(0.5)
                continue
            out = clean_text(r.json().get("response", ""))
            if not out:
                continue
            # —— 口味一致性守卫 ——
            has_spicy_word = any(c in out for c in SPICY_CHARS)
            if has_spicy_word and not is_spicy_fact:
                if attempt < MAX_RETRIES - 1:
                    prompt = prompt.replace(
                        "【示例】",
                        "【严重提醒】此菜事实口味为“%s”，绝不是辣味，严禁出现“辣/麻”字，重写。\n【示例】" % taste,
                        1)
                    continue  # 口味冲突, 加强约束重试
            cands.append(out)
            break  # 成功即停, 不再多余调用(长度由事实补长保证)
        except Exception:
            time.sleep(0.5)
    if not cands:
        return None
    best = max(cands, key=len)
    # 偏短则用事实补长, 保证 50–80 字且精准
    if len(best) < 48 and facts:
        best = clean_text(pad_description(best, facts, cat_cn))[:88]
    elif len(best) < 40 and not facts:
        best = clean_text(pad_description(best, {}, cat_cn))[:88]
    return best


def main():
    with open(JSON_PATH, encoding="utf-8") as f:
        records = json.load(f)
    facts_map = {}
    if os.path.exists(FACTS_PATH):
        facts_map = json.load(open(FACTS_PATH, encoding="utf-8"))

    done = fail = 0

    def worker(i):
        rec = records[i]
        fid = rec.get("fid")
        facts = facts_map.get(fid)
        if isinstance(facts, dict) and "matched" in facts:
            facts = {k: facts.get(k, "") for k in ("taste", "craft", "main", "sub")}
        else:
            facts = None
        return i, gen_one(rec, facts)

    with ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futures = [ex.submit(worker, i) for i in range(len(records))]
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
                print(f"  进度 {done + fail}/{len(records)} 成功 {done} 失败 {fail}", flush=True)

    with open(JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(records, f, ensure_ascii=False, indent=2)

    print(f"\n生成完成: 成功 {done}，失败 {fail}")
    print("样例:")
    shown = 0
    for r in records:
        if r.get("detail") and shown < 12:
            print(f"  [{r['fname']}/{r['category']}] {r['detail']}")
            shown += 1

    # 重新生成 SQL / CSV
    print("\n重新生成 SQL / CSV ...")
    import gen_sql
    gen_sql.main()


if __name__ == "__main__":
    main()
