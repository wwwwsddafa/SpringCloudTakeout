# -*- coding: utf-8 -*-
"""
一键自动化编排器。

用法:
  python run_all.py                 # 假定 resfood_data.json 已由爬虫产出，跑 小模型生成 → 出SQL
  python run_all.py --with-spider   # 先跑完整爬虫(约20-30分钟)，再生成简介与SQL

Pipeline:
  Stage1 spider.py      -> resfood_data.json  (菜名+图片+分类+自动价格, detail留空)
  Stage1b scrape_facts.py -> resfood_facts.json (回源抽 口味/工艺/主食材 事实)
  Stage2 gen_detail.py  -> resfood_data.json  (基于事实 + 本地千问2.5 3B 填 detail)
  Stage3 gen_sql.py     -> resfood_data.sql   (INSERT 语句)
"""
import os
import sys
import subprocess

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PY = sys.executable


def run(script):
    print(f"\n===== 运行 {script} =====")
    return subprocess.run([PY, os.path.join(BASE_DIR, script)], cwd=BASE_DIR).returncode


def main():
    if "--with-spider" in sys.argv:
        if run("spider.py") != 0:
            print("spider.py 失败，终止。"); sys.exit(1)
    if run("scrape_facts.py") != 0:
        print("scrape_facts.py 失败，终止。"); sys.exit(1)
    if run("gen_detail.py") != 0:
        print("gen_detail.py 失败，终止。"); sys.exit(1)
    if run("gen_sql.py") != 0:
        print("gen_sql.py 失败。"); sys.exit(1)
    print("\n全部完成 ✅  产物: resfood_data.sql")


if __name__ == "__main__":
    main()
