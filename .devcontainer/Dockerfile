# syntax=docker/dockerfile:1
# 单容器开发环境：一台装齐 JDK 25 / Maven 3.9.16 / Node 24.20.0 / MySQL 9.7 的"开发机"。

FROM ubuntu:24.04

ARG MAVEN_VERSION=3.9.16
ARG NODE_VERSION=24.20.0

ENV DEBIAN_FRONTEND=noninteractive TZ=Asia/Shanghai

# ---- 基础工具（procps 供 pkill/pgrep，xz 解 Node 压缩包）----
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      ca-certificates curl wget gnupg xz-utils git procps tzdata lsb-release \
 && rm -rf /var/lib/apt/lists/*

# ---- JDK 25（Adoptium Temurin apt 源，与 maven 官方镜像同发行版）----
RUN install -d -m 0755 /etc/apt/keyrings \
 && curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
      | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg \
 && echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" \
      > /etc/apt/sources.list.d/adoptium.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends temurin-25-jdk \
 && rm -rf /var/lib/apt/lists/*

# ---- MySQL 9.7（Oracle 官方 apt 源，组件 mysql-9.7-lts）----
# mysql-apt-config 只用来提供签名 keyring（源文件由下面手写覆盖），必须
# 锁住它：升级/reconfigure 时它会按 debconf 默认（mysql-8.4-lts）改写
# 源文件，让 9.7 源静默漂移。静态 key 不可行，用 deb 内的 keyring 能通过验签。
# mysql-community-server 的 postinst 会尝试初始化 /var/lib/mysql（root
# 凭据不受我们控制），装完清空数据目录，把首次初始化交给 dev-entrypoint.sh
# （root 无密码空库，zhiyi_campus_init.sql 由你手动导入）。
RUN wget -q https://repo.mysql.com/mysql-apt-config_0.8.36-1_all.deb \
 && dpkg -i mysql-apt-config_0.8.36-1_all.deb \
 && apt-mark hold mysql-apt-config \
 && echo "deb [signed-by=/usr/share/keyrings/mysql-apt-config.gpg] https://repo.mysql.com/apt/ubuntu noble mysql-9.7-lts mysql-tools" \
      > /etc/apt/sources.list.d/mysql.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends mysql-community-server \
 && rm -f mysql-apt-config_0.8.36-1_all.deb \
 && rm -rf /var/lib/apt/lists/* \
 && rm -rf /var/lib/mysql \
 && mkdir -p /var/lib/mysql /run/mysqld \
 && chown mysql:mysql /var/lib/mysql /run/mysqld \
 && mysqld --version | grep -q ' 9\.7\.'

# ---- Maven（Apache 官方二进制包，版本钉死）----
RUN wget -q "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
 && tar -xzf "apache-maven-${MAVEN_VERSION}-bin.tar.gz" -C /opt \
 && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
 && rm "apache-maven-${MAVEN_VERSION}-bin.tar.gz"

# ---- Node（官方二进制包，版本钉死；x64 —— 与宿主开发机架构一致）----
RUN wget -q "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
 && tar -xJf "node-v${NODE_VERSION}-linux-x64.tar.xz" -C /usr/local --strip-components=1 \
 && rm "node-v${NODE_VERSION}-linux-x64.tar.xz"

COPY docker/dev-entrypoint.sh /usr/local/bin/dev-entrypoint.sh
RUN chmod +x /usr/local/bin/dev-entrypoint.sh

WORKDIR /repo
EXPOSE 3000 5005 8080

ENTRYPOINT ["/usr/local/bin/dev-entrypoint.sh"]
CMD ["sleep", "infinity"]
