# -*- coding: utf-8 -*-
"""
Stage 2.5: 下载菜品图片到本地，绕过 meishitx 防盗链(空Referer→403, 带任意Referer→200)
- 并发下载到 images/<fid>.jpg
- 断点续跑(已存在且>1KB则跳过)
- 失败重试 + 多个 Referer 轮换
- 下载完成后把 fphoto 改为 /images/<fid>.jpg，并重新生成 resfood_data.sql
"""
import os
import json
import time
import shutil
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed

import spider  # 复用 spider.build_sql

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
IMG_DIR = os.path.join(BASE_DIR, "images")
JSON_PATH = os.path.join(BASE_DIR, "resfood_data.json")
SQL_PATH = os.path.join(BASE_DIR, "resfood_data.sql")

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
# 防盗链实测: 空Referer→403, 带任意Referer→200, 这里轮换几个即可
REFERERS = [
    "https://home.meishichina.com/",
    "https://www.baidu.com/",
    "https://www.meishichina.com/",
]
HEADERS = {
    "User-Agent": UA,
    "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9",
}

os.makedirs(IMG_DIR, exist_ok=True)


def local_path(fid):
    return f"/images/{fid}.jpg"


def download(rec):
    fid = rec["fid"]
    url = rec.get("fphoto", "")
    out = os.path.join(IMG_DIR, f"{fid}.jpg")
    # 断点续跑
    if os.path.exists(out) and os.path.getsize(out) > 1024:
        return fid, True, "skip"
    last_err = ""
    for ref in REFERERS:
        for attempt in range(3):
            try:
                h = dict(HEADERS)
                h["Referer"] = ref
                r = requests.get(url, headers=h, timeout=20)
                ct = r.headers.get("Content-Type", "")
                if r.status_code == 200 and ct.startswith("image"):
                    with open(out, "wb") as f:
                        f.write(r.content)
                    return fid, True, "ok"
                last_err = f"HTTP {r.status_code} ct={ct[:20]}"
            except Exception as e:
                last_err = str(e)[:60]
            time.sleep(0.4)
    return fid, False, last_err


def main():
    recs = json.load(open(JSON_PATH, encoding="utf-8"))
    print(f"读取 {len(recs)} 条记录")

    # 备份原始(远程URL版) JSON 与 SQL
    json_bak = JSON_PATH + ".remote_bak"
    if not os.path.exists(json_bak):
        shutil.copy(JSON_PATH, json_bak)
        print(f"已备份原始JSON(远程URL): {json_bak}")
    sql_bak = SQL_PATH + ".remote_bak"
    if os.path.exists(SQL_PATH) and not os.path.exists(sql_bak):
        shutil.copy(SQL_PATH, sql_bak)
        print(f"已备份原始SQL(远程URL): {sql_bak}")

    print(f"开始下载 {len(recs)} 张图片 -> {IMG_DIR}")
    success = fail = 0
    fails = []
    with ThreadPoolExecutor(max_workers=8) as ex:
        futs = [ex.submit(download, r) for r in recs]
        done = 0
        for fut in as_completed(futs):
            fid, ok, msg = fut.result()
            done += 1
            if ok:
                success += 1
            else:
                fail += 1
                fails.append((fid, msg))
            if done % 20 == 0 or done == len(recs):
                print(f"  进度 {done}/{len(recs)} 成功 {success} 失败 {fail}")

    # 改写 fphoto 为本地路径
    for r in recs:
        r["fphoto"] = local_path(r["fid"])
    json.dump(recs, open(JSON_PATH, "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)

    # 重新生成 SQL
    sql = spider.build_sql(recs)
    with open(SQL_PATH, "w", encoding="utf-8") as f:
        f.write(sql)

    print(f"\n下载完成: 成功 {success} 失败 {fail}")
    if fails:
        print(f"失败 {len(fails)} 条(前10):")
        for fid, msg in fails[:10]:
            print(f"   {fid} -> {msg}")
    print(f"SQL 已用本地路径重新生成: {SQL_PATH}")
    print(f"图片目录: {IMG_DIR}")


if __name__ == "__main__":
    main()
