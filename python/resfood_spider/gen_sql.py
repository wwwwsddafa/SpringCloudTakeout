# -*- coding: utf-8 -*-
"""
Stage 3: 由 resfood_data.json 生成
  - resfood_data.sql  (INSERT INTO resfood ...)
  - resfood_data.csv  (fid,fname,normprice,realprice,detail,fphoto,category,status)
                      utf-8-sig 编码，Excel / Navicat / DBeaver 直接打开中文不乱码
                      create_time / update_time 有 DB 默认值，不导出，导入时自动填充
"""
import os
import json
import csv
import spider  # 复用 spider.build_sql 与 spider.CAT_CN

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
JSON_PATH = os.path.join(BASE_DIR, "resfood_data.json")
SQL_PATH = os.path.join(BASE_DIR, "resfood_data.sql")
CSV_PATH = os.path.join(BASE_DIR, "resfood_data.csv")

CSV_COLUMNS = ["fid", "fname", "normprice", "realprice", "detail", "fphoto", "category", "status"]


def main():
    with open(JSON_PATH, encoding="utf-8") as f:
        records = json.load(f)

    empty = [r for r in records if not r.get("detail")]
    print(f"读取 {len(records)} 条 | detail 为空 {len(empty)} 条")
    if empty:
        print("警告: 以下菜品缺少简介，将写入空 detail:")
        for r in empty[:15]:
            print("   ", r.get("fname"))

    # 1) SQL
    sql = spider.build_sql(records)
    with open(SQL_PATH, "w", encoding="utf-8") as f:
        f.write(sql)
    print(f"SQL 已生成: {SQL_PATH} ({len(records)} 条)")

    # 2) CSV (utf-8-sig + 表头)
    with open(CSV_PATH, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(CSV_COLUMNS)
        for r in records:
            category = spider.CAT_CN.get(r["category"], r["category"])
            writer.writerow([
                r["fid"],
                r["fname"],
                r["normprice"],
                r["realprice"],
                r["detail"],
                r["fphoto"],
                category,
                r["status"],
            ])
    print(f"CSV 已生成: {CSV_PATH} ({len(records)} 条)")


if __name__ == "__main__":
    main()
