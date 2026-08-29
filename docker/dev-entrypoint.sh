#!/bin/bash
# 单容器开发环境入口：这台"机器"开机只做一件事——把 MySQL 服务拉起来。
#   - 首次启动（数据卷为空）：初始化出 root 无密码的空库；想设密码自己
#     ALTER USER，并把同一值填进 .env 的 MYSQL_PASSWORD（后端连库与
#     本脚本的探活/关机会共用它），再 docker compose up -d 重建容器；
#   - 之后启动：数据卷非空，直接唤醒 mysqld（数据天然持久，不重置）。
# 应用进程（后端 / 前端）与普通机器一样由你在终端里手动启动，本脚本不参与。
# 容器收到 SIGTERM 时先优雅关库再退出，避免 InnoDB 崩溃恢复。
set -euo pipefail

DATADIR=/var/lib/mysql
# 当前数据库密码（默认空；仅当你自己给库设过密码时 .env 里才有 MYSQL_PASSWORD）
admin_args=()
if [ -n "${MYSQL_PASSWORD:-}" ]; then admin_args=(-p"$MYSQL_PASSWORD"); fi

# SIGTERM/SIGINT 处理器必须先于一切耗时操作注册：docker stop 转发来的 TERM
# 若落在数据目录初始化或 wait_ready 的等待窗口里，没有 trap 脚本就直接终止，
# mysqld 被收尾 SIGKILL 走崩溃恢复，违背文件头的承诺。cmd_pid 先占位（set -u），
# 此时主进程尚未启动，trap 里对它按空值跳过。
cmd_pid=""
trap 'mysqladmin -uroot "${admin_args[@]}" shutdown >/dev/null 2>&1 || true
      if [ -n "$cmd_pid" ]; then kill "$cmd_pid" >/dev/null 2>&1 || true; fi
      exit 143' TERM INT

wait_ready() {
  for _ in $(seq 1 60); do
    mysqladmin ping -uroot "${admin_args[@]}" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "✗ mysqld 未在 60s 内就绪，error.log 尾部：" >&2
  tail -15 /var/log/mysql/error.log >&2 2>/dev/null || true
  return 1
}

mkdir -p /run/mysqld && chown mysql:mysql /run/mysqld
# 统一属主，否则 mysqld 对 binlog.index 等文件 EACCES 秒退
chown -R mysql:mysql "$DATADIR"

if [ ! -d "$DATADIR/mysql" ]; then
  echo "→ 首次启动：初始化 MySQL 数据目录（root 无密码的空库，导入初始化脚本由你手动执行）"
  mysqld --initialize-insecure --user=mysql
fi

echo "→ 启动 mysqld（后台守护，数据卷：$DATADIR）"
mysqld --user=mysql &
wait_ready

# 主进程（默认 sleep infinity，保持容器存活）；退出时顺带关库
"$@" &
cmd_pid=$!
wait "$cmd_pid"
mysqladmin -uroot "${admin_args[@]}" shutdown 2>/dev/null || true
