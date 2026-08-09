# 智易校园（Zhiyi Campus）

智易校园是一个面向高校场景的前后端分离交易平台。项目以校内闲置流转为核心，支持出售、求购、以物换物和校园跑腿，并将多学校数据隔离、JWT 鉴权、AI 辅助审核、站内会话、钱包订单、信誉成长与后台治理整合为完整的交易闭环。

## 目录

- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [常用命令](#常用命令)
- [项目目录结构](#项目目录结构)
- [API 与文档入口](#api-与文档入口)
- [开发注意事项](#开发注意事项)
- [开源许可](#开源许可)
- [致谢](#致谢)

## 核心功能

- **校园身份与账号安全**：按学校和学号注册登录，支持学校邮箱规则、密保找回、密码修改、账号注销、BCrypt 密码加密、登录失败限流与 JWT 会话失效控制。
- **多类型校园集市**：支持出售（`SELL`）、求购（`BUY`）、换物（`SWAP`）和跑腿（`ERRAND`），提供分类、关键词、价格、标签、排序与分页筛选。
- **智能发现**：提供 AI 动态标签、近期爆款榜、标签趋势、换物匹配、跑腿专区、活动专题与截止时间提醒。
- **AI 辅助审核**：商品发布前执行本地违规词和价格异常检查，并可接入远程 AI 完成内容审核与标签提取；远程服务不可用时自动转入人工复核流程。
- **收藏与站内沟通**：支持商品收藏、买卖双方会话、未读消息统计和管理员客服会话。
- **交易闭环**：支持平台钱包、充值流水、创建订单、取消订单、确认收货以及交易评价。
- **信誉与成长体系**：包含经验值、等级、六维信誉雷达、校园关系标签和违规处罚记录。
- **运营管理后台**：提供数据看板、交易热力图、商品强制下架、商品流转谱系、用户封禁、违规复核、学校/分类/活动管理和客服收件箱。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3.5.41、Vue Router 5.2.0、Pinia 4.0.2、Element Plus 2.14.4、Axios 1.19.0、Vite 8.2.1、`@vitejs/plugin-vue` 6.0.8、Auto Import / Components |
| 后端 | Java 25、Spring Boot 4.1.0、Spring MVC、MyBatis-Plus 3.5.17（Boot 4 Starter）、Maven 3.9.15 |
| 基础库 | Lombok 1.18.46、JJWT 0.13.0、Hutool 5.8.47、Jackson 3 |
| 数据与安全 | MySQL 8、JWT（HS256 + issuer/audience/tokenVersion）、BCrypt、Caffeine 本地缓存、来源白名单 CORS |
| 文件存储 | 本地文件系统，通过 `/uploads/**` 提供访问 |
| 接口风格 | RESTful JSON，统一返回 `{ code, message, data }` |

## 快速开始

### 1. 环境要求

- JDK 25（构建会拒绝其他主版本）
- Maven 3.9.15
- Node.js 22.12 或更高版本，以及 npm
- MySQL 8.0 或更高版本

AI 服务不是本地启动的必要条件。未配置 `AI_API_KEY` 时，本地审核仍会运行，需要远程 AI 的内容会降级为人工复核。

### 2. 初始化数据库

在项目根目录连接 MySQL：

```bash
mysql -u root -p --default-character-set=utf8mb4
```

进入 MySQL 客户端后执行初始化脚本；请将路径替换为本机项目的绝对路径：

```sql
SOURCE C:/path/to/zhiyi-campus/zhiyi_campus_init.sql;
```

也可以使用 MySQL Workbench、DataGrip 等数据库客户端直接运行 [`zhiyi_campus_init.sql`](zhiyi_campus_init.sql)。脚本会创建 `zhiyi_campus` 数据库、业务表以及学校、管理员和商品分类等种子数据，建议仅在新的开发数据库中执行一次。

### 3. 配置并启动后端

后端默认使用虚拟线程处理请求，并通过环境变量读取敏感配置。`JWT_SECRET` 没有开发默认值，必须是至少 32 字节随机数据的 Base64 编码。PowerShell 示例：

```powershell
cd backend
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "<你的 MySQL 密码>"
$jwtBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
mvn spring-boot:run
```

Bash / Zsh 示例：

```bash
cd backend
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="<你的 MySQL 密码>"
export JWT_SECRET="$(openssl rand -base64 32)"
mvn spring-boot:run
```

如需启用远程 AI 审核，再设置以下可选变量后重启后端：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MYSQL_USERNAME` | MySQL 用户名 | `root` |
| `MYSQL_PASSWORD` | MySQL 密码 | `password`（仅开发占位值） |
| `JWT_SECRET` | 必填；Base64 编码、解码后至少 32 字节的 JWT 签名密钥 | 无 |
| `JWT_EXPIRATION` | Token 有效期，Spring Duration 格式 | `24h` |
| `CORS_ALLOWED_ORIGINS` | 允许访问 API 的前端来源，多个值用逗号分隔 | `http://localhost:3000,http://127.0.0.1:3000` |
| `AI_API_URL` | AI 服务基础地址，后端会追加 `/chat/completions` | `https://api.deepseek.com` |
| `AI_API_KEY` | AI 服务密钥；留空时不调用远程审核 | 空 |
| `AI_MODEL` | 审核模型名称 | `deepseek-v4-pro` |

如果 MySQL 不在 `localhost:3306`，请修改 [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml) 中的数据源 URL。后端默认监听 `http://localhost:8080`。

### 4. 启动前端

新开一个终端，在项目根目录执行：

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:3000`。Vite 会将 `/api` 和 `/uploads` 请求代理到 `http://localhost:8080`。

数据库种子数据包含一个仅供本地开发使用的管理员：所属学校选择“上海大学”，学号为 `admin`，初始密码为 `123456`。首次登录后请立即修改密码，部署环境不得继续使用该凭据。

## 常用命令

### 后端

```bash
cd backend
mvn test
mvn clean package
```

构建产物位于 `backend/target/zhiyi-campus-1.0.0.jar`。在 `backend` 目录启动可确保默认上传目录仍为 `backend/uploads`：

```bash
java -jar target/zhiyi-campus-1.0.0.jar
```

### 前端

```bash
cd frontend
npm test
npm run build
npm run preview
```

前端生产构建产物位于 `frontend/dist`。

## 项目目录结构

```text
zhiyi-campus/
├── backend/                           # Spring Boot 后端
│   ├── pom.xml                        # Maven 依赖与构建配置
│   ├── src/main/java/com/zhiyi/
│   │   ├── common/                    # 统一响应、异常处理、通用校验
│   │   ├── config/                    # MVC、MyBatis-Plus 等配置
│   │   ├── interceptor/               # JWT 与角色权限拦截器
│   │   ├── module/
│   │   │   ├── user/                  # 用户、学校、认证、信誉与成长
│   │   │   ├── item/                  # 商品、分类、活动与 AI 审核
│   │   │   ├── social/                # 收藏与聊天
│   │   │   ├── trade/                 # 钱包、订单与评价
│   │   │   └── admin/                 # 后台治理与数据看板
│   │   └── utils/                     # JWT 等工具类
│   ├── src/main/resources/
│   │   └── application.yml            # 服务、数据库、上传与 AI 配置
│   ├── src/test/java/                  # 后端测试
│   └── uploads/                        # 本地上传文件（运行时目录）
├── frontend/                          # Vue 3 前端
│   ├── src/
│   │   ├── api/                       # 按业务模块封装的 API 请求
│   │   ├── assets/                    # 全局样式等静态资源
│   │   ├── components/                # 通用、布局、用户和交易组件
│   │   ├── router/                    # 页面路由与访问守卫
│   │   ├── stores/                    # Pinia 状态管理
│   │   ├── utils/                     # 请求、鉴权、信誉与交易工具
│   │   └── views/                     # 首页、商品、聊天、钱包、后台等页面
│   ├── tests/                         # Node.js 前端单元测试
│   ├── package.json                   # npm 脚本与依赖
│   └── vite.config.js                 # Vite 配置与开发代理
├── zhiyi_campus_init.sql              # MySQL 初始化脚本
├── LICENSE                            # MIT 开源许可证
└── README.md
```

`backend/target`、`frontend/dist` 和 `frontend/node_modules` 均为可重新生成的目录，已从源码版本控制中排除。

## API 与文档入口

### 接口约定

- 后端 API 基础地址：`http://localhost:8080/api`
- 前端开发环境请求前缀：`/api`
- 除下列公开接口外，其他 `/api/**` 请求均需携带请求头 `Authorization: Bearer <JWT>`。
- 成功与业务失败均使用统一响应结构：

```json
{
  "code": 200,
  "message": "...",
  "data": {}
}
```

公开接口包括：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/security-question`
- `GET /api/auth/security-questions`
- `POST /api/auth/reset-password`
- `GET /api/school/list`
- `GET /api/category/list`

登录后访问受保护接口的示例：

```bash
curl http://localhost:8080/api/user/profile -H "Authorization: Bearer <JWT>"
```

### 接口分组

| 路径前缀 | 主要能力 | 后端入口 |
| --- | --- | --- |
| `/api/auth` | 注册、登录、密保与密码重置 | [`AuthController`](backend/src/main/java/com/zhiyi/module/user/controller/AuthController.java) |
| `/api/school`、`/api/category` | 学校与商品分类字典 | [`SchoolController`](backend/src/main/java/com/zhiyi/module/user/controller/SchoolController.java)、[`CategoryController`](backend/src/main/java/com/zhiyi/module/item/controller/CategoryController.java) |
| `/api/user` | 个人资料、经验、关系标签、信誉与账号安全 | [`UserController`](backend/src/main/java/com/zhiyi/module/user/controller/UserController.java) |
| `/api/item` | 商品发布、搜索、收藏、榜单、换物与跑腿 | [`ItemController`](backend/src/main/java/com/zhiyi/module/item/controller/ItemController.java) |
| `/api/chat` | 会话、消息、客服与未读统计 | [`ChatController`](backend/src/main/java/com/zhiyi/module/social/controller/ChatController.java) |
| `/api/wallet` | 余额、充值与资金流水 | [`WalletController`](backend/src/main/java/com/zhiyi/module/trade/controller/WalletController.java) |
| `/api/order` | 下单、确认、取消、买卖订单与评价 | [`OrderController`](backend/src/main/java/com/zhiyi/module/trade/controller/OrderController.java) |
| `/api/admin` | 看板、用户、违规、内容、学校、分类、活动与客服管理 | [`后台控制器`](backend/src/main/java/com/zhiyi/module/admin/controller/)、[`BanController`](backend/src/main/java/com/zhiyi/module/user/controller/BanController.java)、[`AdminCategoryController`](backend/src/main/java/com/zhiyi/module/item/controller/AdminCategoryController.java)、[`EventTopicController`](backend/src/main/java/com/zhiyi/module/item/controller/EventTopicController.java) |

当前项目尚未集成 Swagger / OpenAPI 页面。开发时可从以下位置查看完整、实时的接口定义：

- 后端控制器：[`backend/src/main/java/com/zhiyi/module`](backend/src/main/java/com/zhiyi/module/)
- 前端请求封装：[`frontend/src/api`](frontend/src/api/)
- 数据库结构：[`zhiyi_campus_init.sql`](zhiyi_campus_init.sql)
- 运行配置：[`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)

## 开发注意事项

- 上传文件默认保存在后端当前工作目录下的 `uploads` 文件夹；单文件上限为 5 MB，单次请求上限为 50 MB。
- 初始化脚本中的管理员密码和默认 MySQL 密码只适合本地开发，部署前必须替换；JWT 密钥没有默认值，启动时必须注入。
- Java 25 虚拟线程已开启，Tomcat 平台线程池参数不再使用；数据库吞吐仍受 HikariCP/MySQL 连接池上限约束。
- API 统一响应和鉴权快照使用不可变 Record；后端 JSON 栈为 Jackson 3，不加载 Jackson 2 兼容层。
- 前端页面统一使用 Vue 3 `<script setup>` / Composition API，Element Plus 组件与 API 按需导入，Pinia 仅持久化用户 ID、昵称和角色摘要。
- 前端开发代理端口固定为 `3000`，后端端口固定为 `8080`；修改任一端口时需同步调整 [`frontend/vite.config.js`](frontend/vite.config.js)。
- 平台钱包是项目内部余额与流水机制，当前未接入第三方支付渠道。

## 开源许可

本项目基于 [MIT License](LICENSE) 开源。你可以自由使用、复制、修改、合并、发布和分发本项目，但须保留原始版权声明及许可声明。

## 致谢

本项目由 [zhiyi-school](https://github.com/kwang888210/zhiyi-school) 进一步开发而来，感谢原项目及其贡献者提供的基础实现与实践经验。我们在此基础上持续完善功能、架构与用户体验。
