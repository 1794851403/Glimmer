# 微光 Glimmer

> 面向年轻人的「图文种草 + 实时互动 + 限时抢购」兴趣内容社区，AI 贯穿内容生产与治理。

一个从零开始、严格按软件工程流程推进的个人求职项目：**需求 → 架构 → 设计 → 开发 → 测试 → 部署**，先做单体、后演进为微服务。

## 产品定位

- **一句话**：面向 18–30 岁年轻人的「图文种草 + 实时互动 + 限时好物抢购」兴趣内容社区。
- **核心价值**：记录发现 · 热闹互动 · 边逛边抢 · AI 降门槛。
- **差异化**：社区与秒杀打通成闭环，AI 贯穿内容生产与治理。

## 技术栈（规划）

- **后端**：Java 17 / Spring Boot 3 / MyBatis-Plus / MySQL 8 / Redis / Redisson / RabbitMQ / WebSocket
- **搜索**：Elasticsearch 8 + IK 中文分词；Kibana（开发辅助）
- **AI**：LLM API（帖子自动打标签、评论情感分析、智能摘要）
- **测试**：Apifox（接口功能 / 契约）+ Apache JMeter（压测）
- **部署**：Docker / docker-compose
- **演进**：Spring Cloud Alibaba（Nacos / Gateway / OpenFeign / Sentinel / Seata）+ SkyWalking

## 项目结构

```
glimmer/
├── docs/            # 需求、架构、数据库、接口等文档（文档驱动）
│   ├── README.md
│   ├── 01-产品需求文档.md
│   ├── 02-架构设计文档.md
│   ├── 03-数据库设计分析.md    (决策已确认)
│   ├── 03-数据库设计文档.md    (已定稿)
│   ├── 03-数据库表结构ER图.png / .svg
│   ├── 04-接口设计文档.md      (v1.10 已定稿)
│   ├── 06-部署文档.md          (VM 组件 / 版本 / 卷 / 网桥)
│   └── apifox/                 # OpenAPI 3.0 契约与 Apifox 导入说明
├── backend/         # 后端代码（单体）
├── frontend/        # 前端（可选）
├── deploy/          # Docker Compose / VM 部署资产
└── README.md
```

## 文档

- [文档索引](docs/README.md)
- [产品需求文档 PRD](docs/01-产品需求文档.md)
- [用户故事地图](docs/01-用户故事地图.png)（[HTML 源](docs/01-用户故事地图.html)）
- [架构设计文档](docs/02-架构设计文档.md)
- [数据库设计文档](docs/03-数据库设计文档.md)（[ER 图](docs/03-数据库表结构ER图.png)）
- [接口设计文档](docs/04-接口设计文档.md)
- [虚拟机 Docker 部署文档](docs/06-部署文档.md)
- [OpenAPI 3.0 / Apifox 导入文件](docs/apifox/glimmer-openapi.json)（[导入说明](docs/apifox/README.md)）

## 开发路线

| 阶段 | 产出 | 状态 |
|---|---|---|
| 1 需求分析 | 产品需求文档 PRD | ✅ 已评审 |
| 2 架构设计 | 技术方案/架构设计文档 | ✅ v1.2（P0 已收口；P1 随 M1/M2） |
| 3 详细设计 | 数据库 + 接口设计文档 | ✅ 已定稿（数据库 v1.4、接口 v1.10） |
| 4 开发 | 后端代码（单体） | 🚧 骨架 + 登录链路已端到端跑通；`/users/me`、图片上传已实现，待 VM 联调 |
| 5 测试 | 接口测试 + 压测报告 | 待开始 |
| 6 部署 | Docker 部署（CentOS 7 `192.168.157.129`） | 🚧 MySQL + Redis core/cache + RabbitMQ（延迟插件）已启动；ES / Kibana 待搜索模块 |
| 7 演进 | 微服务化 | 待开始 |

## 运行

（开发阶段补充）
