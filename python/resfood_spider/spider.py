# -*- coding: utf-8 -*-
"""
美食天下 (meishichina.com) 菜谱爬虫
爬取 500+ 道菜品信息，生成 resfood 表的 INSERT SQL

目标表结构: resfood (fid, fname, normprice, realprice, detail, fphoto, category, status)
"""

import re
import json
import uuid
import random
import time
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin

# ============ 配置 ============
BASE_URL = "https://home.meishichina.com"
OUTPUT_DIR = "C:/Users/wc/Desktop/SpringCloud_takeoutPython/resfood_spider"

# 分类与价格区间映射（realprice 范围，normprice = realprice * 1.1~1.3）
CATEGORIES = {
    "recai":   {"label": "热菜", "en": "hot",    "price_range": (15, 48)},
    "liangcai": {"label": "凉菜", "en": "cold",   "price_range": (8, 26)},
    "tang":    {"label": "汤羹", "en": "soup",   "price_range": (12, 38)},
    "zhushi":  {"label": "主食", "en": "staple",  "price_range": (5, 22)},
    "xiaochi": {"label": "小吃", "en": "snack",   "price_range": (6, 30)},
    "hongbei": {"label": "烘焙", "en": "bakery",  "price_range": (12, 45)},
}

# 每分类抓取的最大页数
MAX_PAGES_PER_CAT = 12
# 目标总量
TARGET_COUNT = 500
# 并发数
WORKERS = 4
# 请求间隔 (秒)
MIN_DELAY = 0.3
MAX_DELAY = 0.8

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Referer": "https://home.meishichina.com/",
}

SESSION = requests.Session()
SESSION.headers.update(HEADERS)


def safe_request(url, max_retries=2):
    """带重试的安全请求"""
    for attempt in range(max_retries + 1):
        try:
            r = SESSION.get(url, timeout=15)
            r.encoding = "utf-8"
            if r.status_code == 200 and len(r.text) > 1000:
                return r.text
            elif r.status_code == 404:
                return None
        except Exception as e:
            if attempt < max_retries:
                time.sleep(1)
    return None


def get_list_page_url(cat, page):
    """获取分类列表页 URL"""
    if page == 1:
        return f"{BASE_URL}/recipe/{cat}/"
    return f"{BASE_URL}/recipe/{cat}/page/{page}/"


def parse_list_page(html):
    """从列表页提取所有菜品详情链接"""
    soup = BeautifulSoup(html, "lxml")
    links = set()
    for a in soup.find_all("a", href=True):
        h = a["href"]
        if "recipe-" in h and h.endswith(".html"):
            if h.startswith("http"):
                links.add(h)
            else:
                links.add(urljoin(BASE_URL, h))
    return links


def extract_food_name(title_text):
    """从title提取菜名
    例如: '清炒苦瓜的做法_清炒苦瓜怎么做_美食家_XXX的菜谱_美食天下'
    """
    # 方法1: 取第一个"的做法"前
    for sep in ["的做法", "怎么做", "怎么做好吃"]:
        idx = title_text.find(sep)
        if idx > 0:
            return title_text[:idx].strip()
    # 方法2: 取第一个下划线前
    idx = title_text.find("_")
    if idx > 1:
        return title_text[:idx].strip()
    return title_text[:30]


def parse_detail_page(html, url):
    """解析菜品详情页，返回结构化数据"""
    soup = BeautifulSoup(html, "lxml")
    data = {}

    # 1. 菜名
    title_tag = soup.find("title")
    title = title_tag.get_text(strip=True) if title_tag else ""
    fname = extract_food_name(title)
    if not fname:
        h1 = soup.find("h1")
        fname = h1.get_text(strip=True) if h1 else "未知菜品"
    data["fname"] = fname[:50]

    # 2. 图片
    img_url = ""
    for img in soup.find_all("img"):
        src = img.get("src", "")
        alt = img.get("alt", "")
        # 美食天下的图片URL含 meishitx.com 或 static. 且 src 较长
        if src.startswith("http") and ("meishitx" in src or "atta/recipe" in src) and len(src) > 40:
            # 去掉 ?x-oss-process 后缀
            img_url = src.split("?")[0]
            break
        # 如果上一个没匹配到，fallback: 找最大的图片
        if src.startswith("http") and "recipe" in src.lower() and len(src) > 30:
            img_url = src.split("?")[0]
    data["fphoto"] = img_url[:1000] if img_url else ""

    # 3. detail 不在此处抓取
    #    （菜品简介由本地千问 2.5 3B 小模型在 Stage2 生成，做法/食材一律不要）
    data["detail"] = ""

    data["url"] = url
    return data


def generate_price(cat_en):
    """按分类生成价格"""
    cfg = CATEGORIES.get(cat_en, {"price_range": (10, 30)})
    lo, hi = cfg["price_range"]
    realprice = round(random.uniform(lo, hi), 1)
    # 四舍五入到.5
    realprice = round(realprice * 2) / 2
    # normprice = realprice * 1.1~1.3
    normprice = round(realprice * random.uniform(1.1, 1.35), 1)
    return normprice, realprice


# 分类英文key -> 中文（与你现有数据库数据一致：热菜/凉菜/...）
CAT_CN = {
    "hot": "热菜", "cold": "凉菜", "soup": "汤羹",
    "staple": "主食", "snack": "小吃", "bakery": "烘焙",
}


def build_sql(records):
    """生成 INSERT SQL 语句"""
    lines = [
        "-- ============================================",
        "-- 菜品数据 (resfood) 自动生成",
        f"-- 生成时间: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- 总记录数: {len(records)}",
        "-- ============================================",
        "",
        "USE springcloud_res161;",
        "",
        "-- 清空旧数据（谨慎操作，生产环境请注释掉）",
        "-- SET FOREIGN_KEY_CHECKS = 0;",
        "-- TRUNCATE TABLE resfood;",
        "-- SET FOREIGN_KEY_CHECKS = 1;",
        "",
        "INSERT INTO resfood (fid, fname, normprice, realprice, detail, fphoto, category, status) VALUES",
    ]

    value_lines = []
    for idx, rec in enumerate(records):
        fid = rec["fid"]
        fname = rec["fname"].replace("'", "\\'")
        normprice = rec["normprice"]
        realprice = rec["realprice"]
        detail = rec["detail"].replace("'", "\\'")
        fphoto = rec["fphoto"].replace("'", "\\'")
        category = CAT_CN.get(rec["category"], rec["category"])
        status = rec["status"]

        val = f"('{fid}', '{fname}', {normprice}, {realprice}, '{detail}', '{fphoto}', '{category}', {status})"
        value_lines.append(val)

    lines.append(",\n".join(value_lines))
    lines.append(";")
    lines.append("")
    lines.append("-- 数据导入完成 --")
    return "\n".join(lines)


def main():
    # 阶段1: 收集所有详情链接
    print("=" * 60)
    print("阶段1: 收集菜品详情链接...")
    print("=" * 60)

    all_links = {}  # url -> cat_en
    for cat_en, cat_cfg in CATEGORIES.items():
        print(f"\n[分类] {cat_cfg['label']} ({cat_en})")
        for page in range(1, MAX_PAGES_PER_CAT + 1):
            url = get_list_page_url(cat_en, page)
            html = safe_request(url)
            if not html:
                print(f"  第{page}页: 获取失败, 跳过")
                break
            links = parse_list_page(html)
            if not links:
                print(f"  第{page}页: 无链接, 结束")
                break
            new_count = 0
            for l in links:
                if l not in all_links:
                    all_links[l] = cat_en
                    new_count += 1
            print(f"  第{page}页: 获取 {len(links)} 个链接 (新增 {new_count})")
            time.sleep(random.uniform(0.2, 0.5))

            # 如果该分类已经有足够链接，提前结束
            cat_count = sum(1 for v in all_links.values() if v == cat_en)
            if cat_count >= 120:
                print(f"  {cat_cfg['label']} 已收集 {cat_count} 个链接, 足够")
                break

    print(f"\n共收集 {len(all_links)} 个唯一链接")
    if len(all_links) < 500:
        print(f"警告: 链接数不足500 ({len(all_links)})，将全部抓取")
    else:
        print(f"目标: 500+, 将随机选取以确保每个分类覆盖")

    # 确保每个分类至少有一些，总量 >= 500
    selected = {}
    for cat_en, cat_cfg in CATEGORIES.items():
        cat_links = [url for url, c in all_links.items() if c == cat_en]
        random.shuffle(cat_links)
        # 每分类选至少 80 个
        take = min(len(cat_links), max(80, len(all_links) // len(CATEGORIES)))
        selected[cat_en] = cat_links[:take]

    final_links = []
    for cat_en, urls in selected.items():
        for url in urls:
            final_links.append((url, cat_en))

    random.shuffle(final_links)

    # 限总量到 TARGET_COUNT
    if len(final_links) > TARGET_COUNT:
        # 保持分类平衡 - 按比例截取
        kept = []
        counts = {}
        for url, cat_en in final_links:
            counts[cat_en] = counts.get(cat_en, 0) + 1
        # 确保每分类至少 30 个
        min_per_cat = 30
        max_from_cat = TARGET_COUNT - min_per_cat * (len(CATEGORIES) - 1)
        for cat_en in CATEGORIES:
            if counts.get(cat_en, 0) > max_from_cat:
                counts[cat_en] = max_from_cat
        # 按比例选
        per_cat = {}
        remaining = TARGET_COUNT
        for i, cat_en in enumerate(CATEGORIES):
            if i == len(CATEGORIES) - 1:
                per_cat[cat_en] = remaining
            else:
                raw = min(counts.get(cat_en, 0), remaining // (len(CATEGORIES) - i))
                per_cat[cat_en] = raw
                remaining -= raw
        taken = {c: 0 for c in CATEGORIES}
        for url, cat_en in final_links:
            if taken[cat_en] < per_cat[cat_en]:
                kept.append((url, cat_en))
                taken[cat_en] += 1
            if len(kept) >= TARGET_COUNT:
                break
        final_links = kept

    print(f"最终选取 {len(final_links)} 个菜品进行详情抓取")
    for cat_en in CATEGORIES:
        cnt = sum(1 for _, c in final_links if c == cat_en)
        print(f"  {CATEGORIES[cat_en]['label']}: {cnt}")

    # 阶段2: 抓取详情页
    print("\n" + "=" * 60)
    print("阶段2: 抓取菜品详情...")
    print("=" * 60)

    records = []
    success = 0
    fail = 0

    for idx, (url, cat_en) in enumerate(final_links):
        html = safe_request(url)
        if not html:
            fail += 1
            if (idx + 1) % 20 == 0:
                print(f"  进度: {idx+1}/{len(final_links)} | 成功: {success} | 失败: {fail}")
            continue

        try:
            data = parse_detail_page(html, url)
        except Exception as e:
            fail += 1
            if (idx + 1) % 20 == 0:
                print(f"  进度: {idx+1}/{len(final_links)} | 成功: {success} | 失败: {fail}")
            continue

        # 生成价格
        normprice, realprice = generate_price(cat_en)

        # 生成 fid
        fid = str(uuid.uuid4())

        record = {
            "fid": fid,
            "fname": data["fname"],
            "normprice": normprice,
            "realprice": realprice,
            "detail": data["detail"],
            "fphoto": data["fphoto"],
            "category": CATEGORIES[cat_en]["en"],
            "status": 1,
        }
        records.append(record)
        success += 1

        if (idx + 1) % 20 == 0:
            print(f"  进度: {idx+1}/{len(final_links)} | 成功: {success} | 失败: {fail}")

        # 延迟
        time.sleep(random.uniform(MIN_DELAY, MAX_DELAY))

    print(f"\n详情抓取完成: 成功 {success} 条, 失败 {fail} 条")

    # 阶段3: 生成 SQL
    print("\n" + "=" * 60)
    print("阶段3: 生成 SQL 文件...")
    print("=" * 60)

    sql = build_sql(records)
    sql_path = f"{OUTPUT_DIR}/resfood_data.sql"
    with open(sql_path, "w", encoding="utf-8") as f:
        f.write(sql)

    print(f"SQL 文件已生成: {sql_path}")

    # 也保存 JSON 原始数据
    json_path = f"{OUTPUT_DIR}/resfood_data.json"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(records, f, ensure_ascii=False, indent=2)

    print(f"JSON 数据已保存: {json_path}")

    # 统计
    print(f"\n{'='*60}")
    print(f"爬取完成! 共 {len(records)} 条菜品数据")
    print(f"{'='*60}")
    for cat_en in CATEGORIES:
        cnt = sum(1 for r in records if r["category"] == CATEGORIES[cat_en]["en"])
        print(f"  {CATEGORIES[cat_en]['label']} ({CATEGORIES[cat_en]['en']}): {cnt}")
    print(f"\nSQL 文件路径: {sql_path}")
    print(f"导入方式: mysql -u root -p springcloud_res161 < {sql_path}")
    print(f"或在 MySQL 客户端中执行: source {sql_path};")


if __name__ == "__main__":
    main()