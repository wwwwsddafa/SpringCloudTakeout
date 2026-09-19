# -*- coding: utf-8 -*-
"""
把本地 images/ 下的菜品图上传到你本地的 MinIO，并把 fphoto 改成 MinIO 可访问 URL，
随后重新生成 resfood_data.sql / resfood_data.csv。

用法:
  # 1) 先空跑看会生成什么 URL (不需要凭据)
  python upload_to_minio.py --dry-run

  # 2) 真正上传 (凭据走环境变量，不写进文件)
  MINIO_ENDPOINT=localhost:9000 \
  MINIO_ACCESS_KEY=你的accessKey \
  MINIO_SECRET_KEY=你的secretKey \
  MINIO_BUCKET=res \
  MINIO_PREFIX=files \
  MINIO_PUBLIC_URL=http://localhost:9000 \
  python upload_to_minio.py

说明:
  - 最终 fphoto = {MINIO_PUBLIC_URL}/{bucket}/{prefix}/{fid}.jpg
    例: http://localhost:9000/res/files/c2dc1e30-....jpg  (与你现有数据格式一致)
  - MINIO_PUBLIC_URL 决定前端实际访问 MinIO 的主机, 部署到别的机器时改成局域网IP/域名
  - 上传失败的单条会跳过并报告, 不影响其余
"""
import os
import sys
import json
import argparse

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
JSON_PATH = os.path.join(BASE_DIR, "resfood_data.json")
IMG_DIR = os.path.join(BASE_DIR, "images")


def load_config():
    endpoint = os.environ.get("MINIO_ENDPOINT", "localhost:9000")
    access_key = os.environ.get("MINIO_ACCESS_KEY", "")
    secret_key = os.environ.get("MINIO_SECRET_KEY", "")
    bucket = os.environ.get("MINIO_BUCKET", "res")
    prefix = os.environ.get("MINIO_PREFIX", "files")
    public_url = os.environ.get("MINIO_PUBLIC_URL", f"http://{endpoint}")
    secure = os.environ.get("MINIO_SECURE", "0") in ("1", "true", "True")
    return dict(endpoint=endpoint, access_key=access_key, secret_key=secret_key,
                bucket=bucket, prefix=prefix, public_url=public_url.rstrip("/"), secure=secure)


def find_local_image(fid):
    for ext in ("jpg", "png"):
        p = os.path.join(IMG_DIR, f"{fid}.{ext}")
        if os.path.exists(p):
            return p, ext
    return None, None


def set_public_read(client, bucket):
    """给 bucket 设置匿名只读策略，使前端能用直接 URL 访问图片"""
    policy = {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Principal": {"AWS": ["*"]},
                "Action": ["s3:GetObject"],
                "Resource": [f"arn:aws:s3:::{bucket}/*"],
            }
        ],
    }
    import json as _json
    client.set_bucket_policy(bucket, _json.dumps(policy))
    print(f"已为 bucket '{bucket}' 设置匿名只读策略（前端可直接访问图片）")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只打印将生成的 URL, 不上传")
    args = ap.parse_args()

    cfg = load_config()
    records = json.load(open(JSON_PATH, encoding="utf-8"))

    if not args.dry_run and (not cfg["access_key"] or not cfg["secret_key"]):
        print("缺少 MINIO_ACCESS_KEY / MINIO_SECRET_KEY，无法上传。先用 --dry-run 预览，或设置环境变量。")
        sys.exit(1)

    client = None
    if not args.dry_run:
        try:
            from minio import Minio
        except ImportError:
            print("未安装 minio 客户端，请先: pip install minio")
            sys.exit(1)
        client = Minio(cfg["endpoint"], access_key=cfg["access_key"],
                       secret_key=cfg["secret_key"], secure=cfg["secure"])
        # 确保 bucket 存在，不存在则用当前凭据创建
        if not client.bucket_exists(cfg["bucket"]):
            print(f"bucket '{cfg['bucket']}' 不存在，正在创建...")
            client.make_bucket(cfg["bucket"])
        # 设置匿名只读策略，使前端能用直接 URL 访问图片
        set_public_read(client, cfg["bucket"])

    ok = fail = 0
    for r in records:
        fid = r["fid"]
        local_path, ext = find_local_image(fid)
        if not local_path:
            print(f"  [缺图] {r['fname']} ({fid}) 本地找不到图片，跳过")
            fail += 1
            continue
        object_name = f"{cfg['prefix']}/{fid}.{ext}"
        fphoto = f"{cfg['public_url']}/{cfg['bucket']}/{object_name}"

        if args.dry_run:
            print(f"  [dry-run] {r['fname']:20s} -> {fphoto}")
            r["fphoto"] = fphoto
            ok += 1
            continue

        last_err = None
        for attempt in range(3):
            try:
                client.fput_object(cfg["bucket"], object_name, local_path,
                                   content_type="image/jpeg" if ext == "jpg" else "image/png")
                r["fphoto"] = fphoto
                ok += 1
                break
            except Exception as e:
                last_err = e
                if attempt < 2:
                    print(f"  [重试 {attempt+1}] {r['fname']} ({fid}): {e}")
        else:
            print(f"  [失败] {r['fname']} ({fid}): {last_err}")
            fail += 1

    print(f"\n上传完成: 成功 {ok} | 失败 {fail}")

    if args.dry_run:
        print("(dry-run) 未写入任何文件。确认无误后去掉 --dry-run 并配置凭据再跑。")
        return

    # 持久化 fphoto 并重新生成 SQL / CSV
    json.dump(records, open(JSON_PATH, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print("已更新 resfood_data.json 的 fphoto")
    import gen_sql
    gen_sql.main()


if __name__ == "__main__":
    main()
