#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
执行 SQL 脚本到本地 Apex MySQL 容器或数据库
"""
import sys
import subprocess
import os

def run_sql(sql_content: str):
    # 优先尝试 docker 容器 mysql8
    try:
        cmd = [
            "docker", "exec", "-i", "mysql8",
            "mysql", "-u", "root", "-proot", "--default-character-set=utf8mb4", "apex"
        ]
        proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        out, err = proc.communicate(input=sql_content)
        if proc.returncode == 0:
            print("✅ SQL 执行成功 (via Docker mysql8):")
            if out.strip():
                print(out.strip())
            return True
        else:
            print(f"⚠️ Docker mysql8 执行警告/错误: {err}")
    except Exception as e:
        print(f"Docker 执行异常: {e}")

    # 备选：尝试系统本地 mysql 客户端
    try:
        cmd = ["mysql", "-h", "127.0.0.1", "-u", "root", "-proot", "--default-character-set=utf8mb4", "apex"]
        proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        out, err = proc.communicate(input=sql_content)
        if proc.returncode == 0:
            print("✅ SQL 执行成功 (via Local MySQL):")
            if out.strip():
                print(out.strip())
            return True
        else:
            print(f"❌ Local MySQL 执行失败: {err}")
            return False
    except Exception as e:
        print(f"❌ 无法连接 MySQL: {e}")
        return False

if __name__ == "__main__":
    if len(sys.argv) > 1:
        arg = sys.argv[1]
        if os.path.exists(arg):
            with open(arg, "r", encoding="utf-8") as f:
                content = f.read()
        else:
            content = " ".join(sys.argv[1:])
    else:
        content = sys.stdin.read()
    
    success = run_sql(content)
    sys.exit(0 if success else 1)

