# -*- coding: utf-8 -*-
"""校验生成的 resfood_data.json / .sql 数据质量"""
import json
import sys
import os

def main():
    base = "C:/Users/wc/Desktop/SpringCloud_takeoutPython/resfood_spider"
    json_path = os.path.join(base, "resfood_data.json")
    if not os.path.exists(json_path):
        print("JSON 不存在，爬虫可能还没完成")
        return

    with open(json_path, encoding="utf-8") as f:
        records = json.load(f)

    print(f"总记录数: {len(records)}")
    if not records:
        print("无数据!")
        return

    # 1. 分类分布
    print("\n== 分类分布 ==")
    cat_count = {}
    for r in records:
        cat = r["category"]
        cat_count[cat] = cat_count.get(cat, 0) + 1
    for c, n in sorted(cat_count.items()):
        print(f"  {c}: {n}")

    # 2. 完整性检查
    print("\n== 字段完整性 ==")
    fields = ["fid", "fname", "normprice", "realprice", "detail", "fphoto", "category", "status"]
    for f in fields:
        missing = sum(1 for r in records if not r.get(f))
        print(f"  {f}: {len(records) - missing}/{len(records)} 有值")

    # 3. 重复检查
    fnames = [r["fname"] for r in records]
    dup = len(fnames) - len(set(fnames))
    print(f"\n重复菜名数: {dup}")

    # 4. 名称长度检查 (fname <= 50)
    over = [r["fname"] for r in records if len(r["fname"]) > 50]
    print(f"fname 超50字符: {len(over)}")
    if over:
        print("  示例:", over[:3])

    # 5. detail 长度检查 (<=5000)
    over_detail = [len(r["detail"]) for r in records if len(r["detail"]) > 5000]
    print(f"detail 超5000字符: {len(over_detail)}")

    # 6. 价格合理性
    print("\n== 价格区间 ==")
    prices = [r["realprice"] for r in records]
    print(f"  realprice: min={min(prices)}, max={max(prices)}")

    # 7. 展示几条样本
    print("\n== 样本 ==")
    for r in records[:3]:
        print(f"  [{r['category']}] {r['fname']} | 原价{r['normprice']} 现价{r['realprice']}")
        print(f"      图片: {r['fphoto'][:70]}")
        print(f"      detail: {r['detail'][:60]}...")

    print("\n== SQL 文件 ==")
    sql_path = os.path.join(base, "resfood_data.sql")
    if os.path.exists(sql_path):
        size = os.path.getsize(sql_path)
        with open(sql_path, encoding="utf-8") as f:
            sql = f.read()
        insert_count = sql.count("),") + sql.count(");")
        print(f"  文件大小: {size/1024:.1f} KB")
        print(f"  INSERT 语句存在: {'INSERT INTO resfood' in sql}")
    else:
        print("  SQL 文件尚未生成")

if __name__ == "__main__":
    main()