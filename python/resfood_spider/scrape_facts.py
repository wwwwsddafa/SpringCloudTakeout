# -*- coding: utf-8 -*-
"""
为已有 498 道菜回源抽取「事实属性」(口味/工艺/主食材/辅料/调料)。

流程:
  1) 爬 6 个分类的列表页, 收集所有菜品详情 URL (与 spider.py 同源)
  2) 逐条抓详情页, 用验证过的解析提取 口味/工艺/主料/辅料/调料 + 菜名
  3) 按「规范化菜名」匹配回 resfood_data.json 的 498 道菜, 以 fid 为键存盘

输出:
  resfood_facts.json  -> { fid: {fname, taste, craft, main, sub, season, category, matched} }

说明:
  - 仅抽取事实, 不生成任何描述; 描述由 gen_detail.py 基于这些事实交给小模型写
  - 并发 6 线程 + safe_request 重试; 中断可重跑(已匹配的会跳过写盘前重建)
  - 不匹配的菜(菜名在列表页找不到)matched=False, 后续由模型仅按菜名推断
"""
import os
import re
import sys
import json
import time
import random
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import spider
from bs4 import BeautifulSoup

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_JSON = os.path.join(BASE_DIR, "resfood_data.json")
FACTS_JSON = os.path.join(BASE_DIR, "resfood_facts.json")

TOKEN = r"[\u4e00-\u9fa5A-Za-z0-9]+"
WORKERS = 6


def normalize(name):
    """规范化菜名用于匹配: 去空白、转小写(中文无影响)。"""
    return re.sub(r"\s+", "", name or "").lower()


def extract_facts(soup):
    """从详情页 HTML 抽取结构化事实属性 (已验证稳定)。"""
    txt = re.sub(r"\s+", " ", soup.get_text())
    facts = {"taste": "", "craft": "", "time": "", "diff": "",
             "main": "", "sub": "", "season": ""}
    m = re.search(rf"({TOKEN})\s*口味\s*({TOKEN})\s*工艺\s*({TOKEN})\s*耗时\s*({TOKEN})\s*难度", txt)
    taste_pos = -1
    if m:
        facts["taste"], facts["craft"], facts["time"], facts["diff"] = [g.strip() for g in m.groups()]
        taste_pos = m.start()  # 口味前味道词起点, 用于截断调料尾巴泄漏
    i_main = txt.find("主料")
    if i_main >= 0:
        i_sub = txt.find("辅料", i_main)
        i_season = txt.find("调料", i_sub if i_sub > i_main else i_main)
        bounds = [b for b in (i_sub, i_season, taste_pos) if b > i_main]
        end_main = min(bounds) if bounds else len(txt)
        facts["main"] = txt[i_main + 2:end_main].strip()
        if i_sub > i_main:
            b2 = [b for b in (i_season, taste_pos) if b > i_sub]
            facts["sub"] = txt[i_sub + 2:(min(b2) if b2 else len(txt))].strip()
        if i_season > i_main:
            facts["season"] = txt[i_season + 2:(taste_pos if taste_pos > i_season else len(txt))].strip()
    for k in ("main", "sub", "season"):
        facts[k] = re.sub(r"\s+", " ", facts.get(k, "")).strip()
    return facts


def fetch_one(url):
    """抓一个详情页, 返回 (规范菜名, 原始菜名, facts) 或 None。"""
    try:
        html = spider.safe_request(url)
        if not html:
            return None
        soup = BeautifulSoup(html, "lxml")
        title = soup.find("title")
        if not title:
            return None
        fname = spider.extract_food_name(title.get_text())
        if not fname or any(h in fname for h in ("大全", "分类", "菜谱", "合集", "全部")):
            return None
        facts = extract_facts(soup)
        return (normalize(fname), fname, facts)
    except Exception:
        return None


def collect_detail_urls():
    """爬列表页收集所有详情 URL (复用 spider 的列表解析)。"""
    print("=" * 60)
    print("阶段1: 收集详情链接 (6 分类)...")
    all_links = set()
    for cat_en, cat_cfg in spider.CATEGORIES.items():
        for page in range(1, spider.MAX_PAGES_PER_CAT + 1):
            url = spider.get_list_page_url(cat_en, page)
            html = spider.safe_request(url)
            if not html:
                break
            links = spider.parse_list_page(html)
            if not links:
                break
            all_links |= links
            time.sleep(random.uniform(0.2, 0.5))
        print(f"  {cat_cfg['label']}: 累计 {len(all_links)} 个链接")
    print(f"共收集 {len(all_links)} 个唯一详情链接")
    return list(all_links)


def main():
    urls = collect_detail_urls()

    print("\n" + "=" * 60)
    print(f"阶段2: 并发抽事实 ({len(urls)} 个详情页, {WORKERS} 线程)...")
    name_facts = {}   # 规范菜名 -> {fname, facts}
    done = 0
    ok = 0
    with ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futs = {ex.submit(fetch_one, u): u for u in urls}
        for fut in as_completed(futs):
            res = fut.result()
            done += 1
            if res:
                nname, fname, facts = res
                if nname and nname not in name_facts:
                    name_facts[nname] = {"fname": fname, "facts": facts}
                    ok += 1
            if done % 50 == 0:
                print(f"  进度 {done}/{len(urls)} | 成功解析 {ok}")
                sys.stdout.flush()
    print(f"详情解析完成: {done} 页 | 有效菜品 {ok} 个")
    print(f"去重后可得事实的菜名数: {len(name_facts)}")

    # 阶段3: 按菜名匹配回 498 道菜
    print("\n" + "=" * 60)
    print("阶段3: 匹配回 resfood_data.json ...")
    records = json.load(open(DATA_JSON, encoding="utf-8"))
    out = {}
    matched = 0
    unmatched = []
    for r in records:
        fid = r["fid"]
        nname = normalize(r["fname"])
        hit = name_facts.get(nname)
        if not hit:
            # 宽松匹配: 互相包含
            for k, v in name_facts.items():
                if nname and (nname in k or k in nname):
                    hit = v
                    break
        if hit:
            f = dict(hit["facts"])
            f["fname"] = r["fname"]
            f["category"] = r["category"]
            f["matched"] = True
            out[fid] = f
            matched += 1
        else:
            out[fid] = {"fname": r["fname"], "category": r["category"],
                        "taste": "", "craft": "", "main": "", "sub": "", "season": "",
                        "matched": False}
            unmatched.append(r["fname"])

    json.dump(out, open(FACTS_JSON, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"匹配成功: {matched}/{len(records)}")
    print(f"未匹配: {len(unmatched)} -> {unmatched[:20]}")
    print(f"已保存: {FACTS_JSON}")


if __name__ == "__main__":
    main()
