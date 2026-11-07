# Glimmer 基础设施部署（CentOS 7 VM）

目标机：`192.168.157.129`（CentOS 7）。开发期后端跑在 Windows 宿主机，通过 VM IP 访问 MySQL / Redis / RabbitMQ / ES。

> 完整的组件清单、版本矩阵、数据卷与网桥说明见 [docs/06-部署文档.md](../docs/06-部署文档.md)；本文件保留 VM 安装、防火墙与排错步骤。

> **CentOS 7 已于 2024-06 EOL**。本项目继续使用它是为了复用现有 VM，但只作为内网开发/演示环境：
> - 不暴露到公网；
> - Docker 版本固定在 24.0.x（或 20.10.x），不要盲目升级到最新版；
> - 长期方案是迁移到 Rocky Linux 9 / AlmaLinux 9 / Ubuntu 22.04。

## 1. 端口规划

| 服务 | VM 端口 | 用途 | 开发期访问方 |
|---|---:|---|---|
| MySQL 8 | 3306 | 业务库 `glimmer` | Windows 后端 |
| redis-core | 6379 | 锁 / Token / 库存 / 资格 / idem / 布隆 | Windows 后端 + Redisson |
| redis-cache | 6380 | 详情 / Feed / 点赞 / AI / 限流 / 弹幕历史 | Windows 后端 |
| RabbitMQ AMQP | 5672 | 异步消息 | Windows 后端 |
| RabbitMQ 管理台 | 15672 | 查看队列 / 插件 | 浏览器 |
| Elasticsearch | 9200 | 搜索 | Windows 后端 |
| Kibana（tools） | 5601 | ES 调试，可选 | 浏览器 |
| backend（后续） | 8080 | Spring Boot | 前端 / Nginx |

Redis 两个实例在容器内都监听 6379，宿主机分别映射到 **6379 / 6380**。

## 2. VM 预检

```bash
cat /etc/redhat-release
uname -r
free -h
df -h
nproc
docker --version
docker compose version
systemctl status firewalld --no-pager
getenforce
timedatectl
```

最低建议：**4 核 / 8 GB / 40 GB 磁盘**。如果只有 4 GB，先不要启动 ES/Kibana，M1 阶段只用 MySQL + 两个 Redis + RabbitMQ。

## 3. 安装 Docker（CentOS 7）

CentOS 7 的默认 yum 源已下线；如果 `yum` 404，先把源切到 vault：

```bash
sudo sed -i 's/mirrorlist/#mirrorlist/g; s|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*.repo
sudo yum clean all && sudo yum makecache
```

安装 Docker CE 24.0.x 与 Compose v2：

```bash
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install -y docker-ce-24.0.9 docker-ce-cli-24.0.9 containerd.io docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
# 重新登录后生效
docker compose version
```

如果 `docker-compose-plugin` 装不上，可下载 Compose v2 静态二进制：

```bash
sudo curl -L https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

> 不要用 `docker-compose` v1：本文件使用了 Compose Spec 的 `name`、`depends_on.condition` 等字段。

## 4. 内核参数与 SELinux

Redis / ES / RabbitMQ 在同一台 VM 上，需要先调内核参数：

```bash
sudo tee /etc/sysctl.d/99-glimmer.conf >/dev/null <<'EOF'
vm.max_map_count=262144
vm.overcommit_memory=1
net.ipv4.ip_forward=1
net.core.somaxconn=1024
fs.file-max=100000
EOF
sudo sysctl --system
```

关闭透明大页（THP），否则 Redis 延迟会抖动：

```bash
echo never | sudo tee /sys/kernel/mm/transparent_hugepage/enabled
```

写入 `/etc/rc.local`（并确保可执行）以便重启后生效。

开发环境可将 SELinux 设为 permissive；**生产不要关闭**：

```bash
sudo setenforce 0
# 永久：/etc/selinux/config 中 SELINUX=permissive
```

## 5. 防火墙

只放行开发机所在网段。以下用 firewalld rich rule，避免把端口开到 `0.0.0.0`：

```bash
for p in 3306 6379 6380 5672 15672 9200 5601 8080; do
  sudo firewall-cmd --permanent --add-rich-rule="rule family=ipv4 source address=192.168.157.0/24 port port=${p} protocol=tcp accept"
done
sudo firewall-cmd --reload
sudo firewall-cmd --list-rich-rules
```

如果 VM 使用 NAT，Windows 宿主机通常就在 `192.168.157.0/24` 内。不要把上述端口映射到公网。

## 6. 启动

```bash
cd deploy
cp .env.example .env
vi .env          # 修改所有 change-me 密码；BIND_IP 保持 192.168.157.129
docker compose up -d
docker compose ps
./smoke.sh
```

Kibana 可选：

```bash
docker compose --profile tools up -d kibana
```

常用排查：

```bash
docker compose logs -f mysql
docker compose logs -f redis-core
docker compose logs -f rabbitmq
docker compose logs -f elasticsearch
```

## 7. MySQL 远程访问

官方镜像会用 `MYSQL_USER` / `MYSQL_PASSWORD` 创建 `glimmer` 用户（host=`%`）。如果手工创建：

```sql
CREATE DATABASE IF NOT EXISTS glimmer
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'glimmer'@'192.168.157.%' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON glimmer.* TO 'glimmer'@'192.168.157.%';
FLUSH PRIVILEGES;
```

从 Windows 验证：

```powershell
Test-NetConnection 192.168.157.129 -Port 3306
Test-NetConnection 192.168.157.129 -Port 6379
Test-NetConnection 192.168.157.129 -Port 6380
Test-NetConnection 192.168.157.129 -Port 5672
Test-NetConnection 192.168.157.129 -Port 9200
```

## 7.1 RabbitMQ / Feed 队列检查

Feed 第一轮使用以下拓扑，后端启动后由 `RabbitAdmin` 自动声明：

| 名称 | 类型 | 说明 |
|---|---|---|
| `glimmer.feed.exchange` | topic exchange | 发帖事件入口，routing key `feed.post.published` |
| `glimmer.feed.fanout.queue` | durable queue | 写扩散消费者，期望 2 个消费者 |
| `glimmer.feed.dlx` | direct exchange | 死信交换机，routing key `feed.fanout.dead` |
| `glimmer.feed.fanout.dlq` | durable queue | 消费失败 / 消息转换失败的死信 |

在 VM 上检查：

```bash
cd deploy
docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers
docker compose exec rabbitmq rabbitmqctl list_exchanges name type
```

或打开管理台 `http://192.168.157.129:15672` 查看队列深度与消费者。`fanout.queue` 的 `messages`
持续增长且 `consumers=0` 时，先确认后端是否启动、RabbitMQ 连接是否报错；`dlq` 有消息时先看后端
`feed fanout failed` 日志，修复后确认无需保留现场再清空：

```bash
docker compose exec rabbitmq rabbitmqctl purge_queue glimmer.feed.fanout.dlq
```

## 8. 后端开发配置

后端**不复制一份密码配置**：`backend/src/main/resources/application.yml` 通过
`spring.config.import` 直接读取本目录的 `.env`（`../deploy/.env` 或 `deploy/.env`），
环境变量优先级更高。因此只需要维护 `.env` 一份密码。

```yaml
# application.yml（已提交）中的默认值，均可用环境变量覆盖
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:192.168.157.129}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:glimmer}?...
    username: ${MYSQL_USER:glimmer}
    password: ${MYSQL_PASSWORD:}
  rabbitmq:
    host: ${RABBITMQ_HOST:192.168.157.129}
    port: ${RABBITMQ_PORT:5672}

glimmer:
  jwt:
    secret: ${JWT_SECRET:}
  storage:
    oss:
      endpoint: ${OSS_ENDPOINT:}
      bucket: ${OSS_BUCKET:}
      access-key-id: ${OSS_ACCESS_KEY_ID:}
      access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
      public-base-url: ${OSS_PUBLIC_BASE_URL:}
  redis:
    core:
      host: ${REDIS_CORE_HOST:192.168.157.129}
      port: ${REDIS_CORE_PORT:6379}
      password: ${REDIS_CORE_PASSWORD:}
    cache:
      host: ${REDIS_CACHE_HOST:192.168.157.129}
      port: ${REDIS_CACHE_PORT:6380}
      password: ${REDIS_CACHE_PASSWORD:}
```

启动前确认 `.env` 中的 `JWT_SECRET` 至少 32 字节。在 IDE 中运行
`com.glimmer.GlimmerApplication`，工作目录设为 `backend/`；从仓库根目录执行
`mvn -f backend/pom.xml spring-boot:run` 也能读取 `deploy/.env`。若路径含中文导致
`ClassNotFoundException: com.glimmer.GlimmerApplication`，改用
`mvn -f backend/pom.xml -DskipTests package`，再从仓库根目录执行
`java -jar backend/target/glimmer-backend-0.1.0-SNAPSHOT.jar`。

图片存储开发和发布都只走 OSS，在 `.env` 填写 `OSS_ENDPOINT` / `OSS_BUCKET` /
`OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET`。OSS 桶保持私有，但需关闭 Bucket 的
「阻止公共访问」，后端才能在上传时通过对象 ACL 把单个对象设为 `public-read`；否则上传返回
`50301`。AK/SK 只放在 `.env`，不要提交。

如果后端也容器化，则 host 改为 `mysql` / `redis-core` / `redis-cache` / `rabbitmq` / `elasticsearch`，
端口改回容器端口（Redis 都是 6379）。

## 9. 数据持久化与备份

| 数据 | 卷 | 备份 |
|---|---|---|
| MySQL | `mysql-data` | `mysqldump` / VM 快照 |
| redis-core | `redis-core-data` | AOF 文件 + VM 快照 |
| RabbitMQ | `rabbitmq-data` | 主要为队列元数据；业务消息以 DB 为准 |
| Elasticsearch | `es-data` | 可从 MySQL 全量重建，非权威 |

最小备份：

```bash
docker compose exec -T mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction glimmer > glimmer-$(date +%F).sql
docker compose exec -T redis-core redis-cli BGREWRITEAOF
```

## 10. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| `docker compose` 报 `unknown field name` | 用了 v1 或旧版 Compose，升级到 v2 |
| ES 启动报 `max virtual memory areas vm.max_map_count [65530] is too low` | 执行第 4 节 sysctl |
| Redis 容器不断重启 | `.env` 中密码为空或含 `$`；改用简单强密码并检查 `docker compose logs` |
| MySQL 连不上 | 端口未放行 / 用户 host 不是 `%` / `BIND_IP` 不是 VM 当前 IP |
| SSH 能通，但 3306/6379/6380 从宿主机超时 | 先查 `sysctl net.ipv4.ip_forward`，为 0 时设为 1 并持久化到 `/etc/sysctl.d/`；若仍不通，继续查下一行 |
| VM 挂起后恢复，SSH 能通但所有 Docker 端口超时；`ip route get 172.28.0.x` 走 `ens33` 而不是 `br-*` | Docker 网桥在挂起 / 恢复后丢失 IPv4 地址。执行 `sudo systemctl restart docker`，确认 `ip -4 addr show br-*` 有 `172.28.0.1/16`、`ip route` 有 `172.28.0.0/16 dev br-*`；仍异常则重建 `glimmer-net` |
| RabbitMQ 延迟插件缺失 | 构建日志看 `ADD` 是否成功；离线时手动放 `.ez` 后改 `COPY` |
| Feed 关注流不更新，`glimmer.feed.fanout.queue` 有堆积 | 看管理台消费者数是否为 2、后端日志是否有 RabbitMQ 连接 / 反序列化错误；确认 `deploy/.env` 的 `RABBITMQ_DEFAULT_USER/PASS` 与后端读取值一致 |
| `glimmer.feed.fanout.dlq` 持续增长 | 常见原因是消息不是合法 JSON，或消费时 Redis 不可用；看 `feed fanout failed` 日志，恢复依赖后清空 DLQ。消费端 `requeue=false`，不会毒消息死循环 |
| IK 插件 404 | `IK_VERSION` 与 `ES_VERSION` 必须完全一致；确认 GitHub release 可用 |
| 磁盘满 | `docker system df` / `df -h`；ES 与镜像最占空间 |
