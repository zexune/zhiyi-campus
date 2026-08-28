# 智易校园（Zhiyi Campus）

智易校园是一个面向高校场景的前后端分离交易平台。项目以校内闲置流转为核心，支持出售、求购、以物换物和校园跑腿，并将多学校数据隔离、JWT 鉴权、本地内容治理、站内会话、钱包订单、信誉成长与后台治理整合为完整的交易闭环。

## 目录

- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [性能与数据模型](#性能与数据模型)
- [快速开始](#快速开始)
- [常用命令](#常用命令)
- [测试体系](#测试体系)
- [项目目录结构](#项目目录结构)
- [API 与文档入口](#api-与文档入口)
- [开发注意事项](#开发注意事项)
- [开源许可](#开源许可)
- [致谢](#致谢)

## 核心功能

- **校园身份与账号安全**：按学校和学号注册登录，支持学校邮箱规则、密保找回、密码修改、账号注销、BCrypt 密码加密、登录失败限流与 JWT 会话失效控制。
- **多类型校园集市**：支持出售（`SELL`）、求购（`BUY`）、换物（`SWAP`）和跑腿（`ERRAND`），提供分类、关键词、价格、标签、排序与分页筛选。
- **内容发现**：提供本地生成的普通商品标签、近期爆款榜、标签趋势、换物匹配、跑腿专区与活动专题。
- **本地内容治理**：使用版本化、确定性的本地规则检测违规关键词，不检查价格；命中风险后隐藏商品并交由管理员复核，同时支持用户举报、卖家整改和每次违规一次的限时申诉。
- **收藏与站内沟通**：支持商品收藏、买卖双方会话、未读消息统计和管理员客服会话。
- **交易闭环**：支持平台钱包、充值流水、创建/取消订单、确认收货以及交易评价；下单以商品状态 `ON_SALE → RESERVED` 条件迁移保证同商品恰一成功；资金操作必须携带 `X-Idempotency-Key` 幂等键，同键重复请求恰好执行一次并重放同一结果；订单与商品状态独立建模。
- **信誉与成长体系**：包含经验值、等级、六维信誉雷达、校园关系标签和违规处罚记录。
- **运营管理后台**：提供数据看板、交易热力图、商品流转谱系、独立用户封禁、内容审核与申诉复核、学校/分类/活动管理和客服收件箱。

## 技术栈

| 层级       | 技术                                                                                                                                                                                                         |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 前端       | TypeScript 5.9（strict）、Vue 3.5.41、Vue Router 5.2.0、Pinia 4.0.2、Element Plus 2.14.4、Axios 1.19.0、Vite 8.2.1、`@vitejs/plugin-vue` 6.0.8、Auto Import / Components、openapi-typescript（契约类型生成） |
| 后端       | Java 25、Spring Boot 4.1.1、Spring MVC、MyBatis-Plus 3.5.17（Boot 4 Starter）、Maven 3.9.16                                                                                                                  |
| 基础库     | Lombok 1.18.46、JJWT 0.13.0、Jackson 3                                                                                                                                                                       |
| 数据与安全 | MySQL 9.7 LTS、Connector/J 9.7.0、JWT（HS256 + issuer/audience/tokenVersion，httpOnly Cookie 下发 + Bearer 双通道）、BCrypt、来源白名单 CORS                                                                 |
| 文件存储   | 本地文件系统，通过 `/uploads/**` 提供访问                                                                                                                                                                    |
| 接口风格   | RESTful JSON，统一返回 `{ code, message, data }`                                                                                                                                                             |

## 性能与数据模型

- **索引化大厅推荐**：商品发布时生成不可变 `feed_key`，默认推荐按 `(school_id, status, moderation_status, is_deleted, feed_key, id)` 复合索引稳定分页，翻页使用带 TTL 的 HMAC-SHA256 签名游标（绑定筛选条件、用户资料版本与快照上界，过期或签名不匹配从首屏重启）；同楼、同校区与全校商品使用分层计数和有界切片查询。
- **批量组装列表读模型**：商品卡片、买入/卖出订单和违规申诉先分页，再按 ID 集合批量读取关联商品、用户、评价、举报与标签，以固定数量的数据库往返完成页面组装。
- **规范化标签**：`tag` 保存标准标签，`item_tag` 保存多对多关系；筛选使用等值索引与 `EXISTS`。校级标签聚合为主库直读，查询命中 `item` 的 `(school_id, status, moderation_status, is_deleted)` 前缀索引与 `item_tag`/`tag` 等值连接，不引入本地缓存。
- **数据库聚合统计**：交易日趋势、成交额和地点热力由聚合 SQL 直接返回小结果集，并以半开时间区间匹配索引。
- **浏览量写缓冲**：商品详情读取路径只做内存原子累加，零数据库写；后台任务周期性把增量批量持久化到独立统计表 `item_view_stat`，以 `view_flush` 凭据行保证批次幂等。缓冲有界，溢出或崩溃时接受损失一个刷新窗口的增量，商品业务行全程无浏览写锁。
- **强类型领域契约**：后端状态使用带 `@EnumValue` 的领域枚举，前端全量 TypeScript（strict）；API 契约类型以 OpenAPI 规格为唯一真相源——后端启动导出 `/v3/api-docs` 快照至根目录 `openapi.json`，`npm run gen:api` 经 openapi-typescript 生成 `src/types/api.gen.d.ts`；`src/types/contracts.ts` 与 `src/types/models.ts` 是生成类型的两个受控消费入口，前者约束 operation，后者为 schema 起领域别名；状态码枚举（`as const` + 派生联合类型）集中在 `src/constants/domain.ts`；JSON 数组由 Jackson 3/MyBatis TypeHandler 统一映射为 `List<String>`。
- **响应 VO 必须声明 nullability**：所有对外返回的 VO/实体字段一律用 `@Schema(requiredMode = REQUIRED)` 标注恒有字段，可空字段追加 `nullable = true`（序列化为显式 null 而非省略），仅"随视图条件填充"的字段不标注；漏标只会让生成类型退化为可选（安全方向），但会削弱前端契约精度。Springdoc 输出的 `$ref + nullable` 会在快照规范化阶段改写为 OpenAPI 3.1 的 `anyOf`（引用类型 + `null`），生成类型可直接保留 `| null`，无需前端 Omit+覆写。

初始化脚本中的复合索引与上述查询形状是一体设计。修改查询条件、排序字段或学校隔离规则时，应同步用 `EXPLAIN ANALYZE` 复核索引命中情况，而不是盲目新增单列索引。

## 快速开始

### 1. 开发环境

- JDK 25
- Maven 3.9.16
- Node.js 24.17.0
- npm 11.13.0
- MySQL 9.7.2

以上版本为本项目开发、构建与测试时所采用的开发配置，仅代表已经验证的环境组合，并非最低兼容版本要求。其他版本可能可以正常运行，但尚未经过完整验证。

### 2. 初始化数据库

在项目根目录连接 MySQL：

```bash
mysql -u root -p --default-character-set=utf8mb4
```

进入 MySQL 客户端后执行初始化脚本；请将路径替换为本机项目的绝对路径：

```sql
SOURCE C:/path/to/zhiyi-campus/zhiyi_campus_init.sql;
```

也可以使用 MySQL Workbench、DataGrip 等数据库客户端直接运行 [`zhiyi_campus_init.sql`](zhiyi_campus_init.sql)。脚本会删除并重建 `zhiyi_campus` 数据库，然后创建业务表和种子数据，仅适用于可重置的开发环境。

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

| 环境变量                                | 说明                                                                       | 默认值                                        |
| --------------------------------------- | -------------------------------------------------------------------------- | --------------------------------------------- |
| `MYSQL_USERNAME`                        | MySQL 用户名                                                               | `root`                                        |
| `MYSQL_PASSWORD`                        | MySQL 密码                                                                 | 无                                            |
| `JWT_SECRET`                            | 必填；Base64 编码、解码后至少 32 字节的 JWT 签名密钥                       | 无                                            |
| `JWT_EXPIRATION`                        | Token 有效期，Spring Duration 格式                                         | `24h`                                         |
| `CORS_ALLOWED_ORIGINS`                  | 允许访问 API 的前端来源，多个值用逗号分隔                                  | `http://localhost:3000,http://127.0.0.1:3000` |
| `MODERATION_RULE_VERSION`               | 本地违规规则集版本，写入每条系统检测记录                                   | `2026.1`                                      |
| `CONTENT_WARNING_POINTS`                | 管理员确认内容违规时固定扣除的合规分                                       | `5`                                           |
| `APPEAL_WINDOW_DAYS`                    | 已确认违规允许申诉的天数                                                   | `7`                                           |
| `AUTH_COOKIE_NAME`                      | 登录会话 Cookie 名                                                         | `zhiyi_token`                                 |
| `AUTH_COOKIE_SECURE`                    | 生产 HTTPS 部署置 `true`，Cookie 仅经加密通道传输                          | `false`                                       |
| `LOGIN_FAIL_LIMIT`                      | 登录/密保失败限流在计数窗口内允许的失败次数                                | `5`                                           |
| `LOGIN_FAIL_WINDOW_SECONDS`             | 失败计数的固定窗口时长（秒）                                               | `900`                                         |
| `LOGIN_FAIL_LOCK_SECONDS`               | 达到阈值后的锁定时长（秒）                                                 | `300`                                         |
| `LOGIN_ATTEMPT_PURGE_RETENTION_SECONDS` | 已结束登录尝试记录的保留时长（秒，过期由后台任务清理）                     | `86400`                                       |
| `TRADE_ADMISSION_GLOBAL_SLOTS`          | 交易事务外全局并发准入上限，须小于连接池并预留非交易请求空间               | `30`                                          |
| `TRADE_ADMISSION_WAIT_MILLIS`           | 交易准入等待预算（毫秒），耗尽返回可重试的 `TRADE_BUSY`                    | `200`                                         |
| `VIEW_FLUSH_INTERVAL_MS`                | 浏览量缓冲批量刷新间隔（毫秒）                                             | `5000`                                        |
| `VIEW_BUFFER_MAX_KEYS`                  | 浏览量缓冲最大键数，达到后新键丢弃                                         | `50000`                                       |
| `OUTBOX_POLL_INTERVAL_MS`               | Outbox 通知事件的轮询间隔（毫秒）                                          | `1000`                                        |
| `OUTBOX_BATCH_SIZE`                     | 每轮最多消费的 Outbox 事件数                                               | `20`                                          |
| `OUTBOX_MAX_ATTEMPTS`                   | 单个 Outbox 事件的最大处理尝试次数                                         | `8`                                           |
| `SSE_TIMEOUT_MS`                        | 聊天事件 SSE 单连接生命周期上限（毫秒），到期断开由浏览器自动重连          | `1800000`                                     |
| `SSE_HEARTBEAT_INTERVAL_MS`             | 聊天事件 SSE 心跳间隔（毫秒），具名 ping 事件：服务端保活 + 客户端探活断流 | `20000`                                       |
| `FEED_CURSOR_TTL_SECONDS`               | 大厅 Feed 签名游标的有效期（秒）                                           | `900`                                         |
| `FEED_CURSOR_SECRET`                    | 游标 HMAC 签名密钥；未设置时复用 `JWT_SECRET`                              | 无                                            |

如果 MySQL 不在 `localhost:3306`，请修改 [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml) 中的数据源 URL。后端默认监听 `http://localhost:8080`。

### 4. 启动前端

新开一个终端，在项目根目录执行：

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:3000`。Vite 会将 `/api` 和 `/uploads` 请求代理到 `http://localhost:8080`。

数据库种子数据包含一个仅供本地开发使用的管理员：后台账号为 `admin`，初始密码为 `123456`。管理员必须从 `http://localhost:3000/admin/login` 独立登录，不选择学校，也不能进入交易大厅、发布、普通聊天、钱包或个人中心。首次登录后请在后台顶部修改密码，部署环境不得继续使用该凭据。

## 常用命令

### 后端

```bash
cd backend
mvn test
mvn clean package
mvn spotless:check   # 代码卫生门禁（CI 同款）
mvn spotless:apply   # 自动修复未用导入/行尾空白
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
npm run typecheck     # vue-tsc 类型检查（CI 同款）
npm run lint          # ESLint（CI 同款）
npm run lint:fix      # 自动修复可修复问题
npm run format:check  # Prettier 检查（CI 同款）
npm run format        # 按 Prettier 格式化
npm run gen:api       # 从根目录 openapi.json 快照重新生成契约类型（改后端 DTO 后执行）
npm run gen:api:dev   # 直接从本地运行中的后端 /v3/api-docs 生成并刷新快照来源
```

前端生产构建产物位于 `frontend/dist`。

## 测试体系

项目采用后端单元、HTTP 契约、真实 MySQL 持久化与事务、前端组件、浏览器烟测和完整系统 E2E 六层测试。快速回归命令如下：

```bash
cd backend
mvn verify

cd ../frontend
npm run test:all
```

需要 Docker 的真实 MySQL 集成测试使用 `mvn verify -Pintegration`；连接已启动的后端和专用测试数据库后，使用 `npm run test:system` 执行注册到评价及后台验收的完整业务旅程。覆盖范围、数据隔离、覆盖率门禁、CI 作业和新增用例规范见 [`TESTING.md`](TESTING.md)。

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
│   │   │   ├── item/                  # 商品、分类、活动与本地内容检测
│   │   │   ├── social/                # 收藏与聊天
│   │   │   ├── trade/                 # 钱包、订单与评价
│   │   │   └── admin/                 # 后台治理与数据看板
│   │   └── utils/                     # JWT 等工具类
│   ├── src/main/resources/
│   │   └── application.yml            # 服务、数据库、上传与内容治理配置
│   ├── src/test/java/                 # 后端单元、HTTP 契约与 MySQL 集成测试
│   └── uploads/                       # 本地上传文件（运行时目录）
├── frontend/                          # Vue 3 前端
│   ├── src/
│   │   ├── api/                       # 按业务模块封装的 API 请求
│   │   ├── assets/                    # 全局样式等静态资源
│   │   ├── components/                # 通用、布局、用户和交易组件
│   │   ├── constants/                 # API 领域状态码、统一展示映射与路由路径常量
│   │   ├── router/                    # 页面路由与访问守卫
│   │   ├── stores/                    # Pinia 状态管理
│   │   ├── utils/                     # 请求、鉴权、信誉与交易工具
│   │   └── views/                     # 首页、商品、聊天、钱包、后台等页面
│   ├── tests/                         # Vitest 组件/工具测试与 Playwright E2E
│   ├── package.json                   # npm 脚本与依赖
│   └── vite.config.ts                 # Vite 配置与开发代理
├── zhiyi_campus_init.sql              # MySQL 初始化脚本
├── .github/workflows/test.yml         # 六条 CI 测试与契约审计流水线
├── TESTING.md                         # 测试策略、命令与质量规范
├── LICENSE                            # MIT 开源许可证
└── README.md
```

`backend/target`、`frontend/dist` 和 `frontend/node_modules` 均为可重新生成的目录，不纳入源码版本控制。

## API 与文档入口

### 接口约定

- 后端 API 基础地址：`http://localhost:8080/api`
- 前端开发环境请求前缀：`/api`
- 除下列公开接口外，其他 `/api/**` 请求均需携带请求头 `Authorization: Bearer <JWT>`。
- 成功与业务失败均使用统一响应信封：

```json
{
  "code": 200,
  "message": "...",
  "data": {}
}
```

- 错误语义采用双层契约：**HTTP 状态码负责粗分类，body 的 `code` 负责细粒度业务原因**。成功为 `HTTP 200 + code 200`；业务失败返回真实 4xx/5xx（400 参数、403 权限、404 不存在、409 状态/并发冲突、429 限流与交易背压、500 系统），映射表见 `ResultCode`。凭证类失败（密码/密保错误）刻意用 400 而非 401——HTTP 401 保留给会话失效，前端收到即清除登录态并跳转登录页。
- **认证错误唯一映射（P0-1）**：业务层的 `USER_CANCELLED(1008)` 是 403（注销账户登录/资金操作被明确拒绝，不触发前端登出）；`JwtInterceptor` 发现 Token 无效/过期、账户注销后的旧 Token 时直写通用 `401 + UNAUTHORIZED(401)`，改密/改角色后的旧 Token 直写 `401 + SESSION_INVALIDATED(1401)`——拦截器不返回业务码 1008，且任何 401 都同时清除 httpOnly 会话 Cookie。前端只以**真实 HTTP 401** 作为清理登录态的依据。
- **失败信封元数据（P1-3）**：失败响应携带必填 `meta.requestOutcome`（`REJECTED`=明确拒绝可清幂等键 / `PROCESSING`=服务端处理中 / `UNKNOWN`=结果不明保留幂等键），前端在信封完整性校验通过后以它为唯一权威判据；残缺形态（缺 code/message/data、`meta` 不存在或为 null/缺字段/非法枚举、非 JSON、代理 HTML）不信任 body 的业务码与 message，按传输层错误保守处理（RETAIN）；允许退避的失败（如 429 交易繁忙）附标准 `Retry-After` 头。
- **`@BusinessErrors` 声明纪律**：每个 Controller operation 都必须显式声明 `@BusinessErrors`（空注解=已审计且无特有业务错误）；只有 `BAD_REQUEST` 隐式允许，`FORBIDDEN`/`SERVER_ERROR`/`USER_NOT_FOUND`/`CONFLICT` 等显式业务错误必须逐 operation 声明，否则 strict 模式契约测试（`BusinessErrorContractVerifier`）直接失败。
- **契约治理**：仓库根目录的 `openapi.json` 是从运行中后端导出的规范化快照（可空 `$ref` 统一为 anyOf、键序与 required/enum/x-business-codes 集合排序），`frontend/src/types/api.gen.d.ts` 由快照生成、禁止手改；CI 强制比对实时规格、快照与前端类型，任一漂移即失败。

公开接口包括：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/security-question`
- `GET /api/auth/security-questions`
- `POST /api/auth/reset-password`
- `POST /api/admin/auth/login`
- `POST /api/admin/auth/logout`
- `GET /api/school/list`
- `GET /api/category/list`
- `GET /api/user/{id}/card`
- `GET /api/user/{id}/reputation`

登录后访问受保护接口的示例：

```bash
curl http://localhost:8080/api/user/profile -H "Authorization: Bearer <JWT>"
```

### 接口分组

| 路径前缀                       | 主要能力                                                                                                                              | 后端入口                                                                                                                                                                                                                                                                                                                                                                                             |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/api/auth`                    | 注册、登录、密保与密码重置                                                                                                            | [`AuthController`](backend/src/main/java/com/zhiyi/module/user/controller/AuthController.java)                                                                                                                                                                                                                                                                                                       |
| `/api/school`、`/api/category` | 学校与商品分类字典                                                                                                                    | [`SchoolController`](backend/src/main/java/com/zhiyi/module/user/controller/SchoolController.java)、[`CategoryController`](backend/src/main/java/com/zhiyi/module/item/controller/CategoryController.java)                                                                                                                                                                                           |
| `/api/user`                    | 个人资料、经验、关系标签、信誉与账号安全                                                                                              | [`UserController`](backend/src/main/java/com/zhiyi/module/user/controller/UserController.java)                                                                                                                                                                                                                                                                                                       |
| `/api/item`                    | 商品发布、搜索、收藏、榜单、举报、申诉、换物与跑腿                                                                                    | [`ItemController`](backend/src/main/java/com/zhiyi/module/item/controller/ItemController.java)                                                                                                                                                                                                                                                                                                       |
| `/api/chat`                    | 会话、消息、客服与未读统计                                                                                                            | [`ChatController`](backend/src/main/java/com/zhiyi/module/social/controller/ChatController.java)                                                                                                                                                                                                                                                                                                     |
| `/api/wallet`                  | 余额、充值与资金流水                                                                                                                  | [`WalletController`](backend/src/main/java/com/zhiyi/module/trade/controller/WalletController.java)                                                                                                                                                                                                                                                                                                  |
| `/api/order`                   | 下单、确认、取消、买卖订单与评价                                                                                                      | [`OrderController`](backend/src/main/java/com/zhiyi/module/trade/controller/OrderController.java)                                                                                                                                                                                                                                                                                                    |
| `/api/admin`                   | 独立管理员认证、看板、用户列表（学校精确 + 学号/昵称/邮箱/手机号模糊）、封禁与强制重置密码、内容/申诉治理、学校、分类、活动与客服管理 | [`后台控制器`](backend/src/main/java/com/zhiyi/module/admin/controller/)、[`BanController`](backend/src/main/java/com/zhiyi/module/user/controller/BanController.java)、[`AdminCategoryController`](backend/src/main/java/com/zhiyi/module/item/controller/AdminCategoryController.java)、[`EventTopicController`](backend/src/main/java/com/zhiyi/module/item/controller/EventTopicController.java) |

### Swagger / OpenAPI

后端启动后可通过以下地址查看或获取实时接口定义：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- OpenAPI YAML：`http://localhost:8080/v3/api-docs.yaml`

Swagger UI 默认将受保护接口标记为 JWT Bearer 鉴权。调用这类接口前，点击页面右上角的 **Authorize**，粘贴用户端或管理端登录接口返回的 JWT（无需手动添加 `Bearer ` 前缀）。公开接口可以直接调用。

前端契约类型以该规格为唯一真相源：后端 DTO/VO 变更后，启动一次后端并执行 `npm run gen:api:dev` 刷新根目录 `openapi.json` 快照与 `frontend/src/types/api.gen.d.ts`（或分别手动更新）。`api.gen.d.ts` 为机器产物禁止手改；仅 `src/types/contracts.ts` 与 `src/types/models.ts` 可直接导入它，分别负责 operation 级请求/响应约束，以及 schema 领域别名与少量客户端组装类型。

快照一致性由 CI 强制审计（`api-contract-drift` 作业）：每次 push/PR 都会启动真实后端导出实时规格，与提交的 `openapi.json` 做语义级比对，再从快照重新生成类型与提交版比对——两处任一漂移即失败，并提示执行 `npm run gen:api:dev`。因此"改了后端忘记同步前端类型"无法合入。

也可从以下位置交叉核对接口实现：

- 后端控制器：[`backend/src/main/java/com/zhiyi/module`](backend/src/main/java/com/zhiyi/module/)
- 前端请求封装：[`frontend/src/api`](frontend/src/api/)
- 数据库结构：[`zhiyi_campus_init.sql`](zhiyi_campus_init.sql)
- 运行配置：[`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)

## 开发注意事项

- 上传文件默认保存在后端当前工作目录下的 `uploads` 文件夹；单文件上限为 5 MB，单次请求上限为 50 MB。
- 初始化脚本中的管理员密码和默认 MySQL 密码只适合本地开发，部署前必须替换；JWT 密钥没有默认值，启动时必须注入。
- 后端启动时执行完整性巡检：要求恰好一个 SYSTEM 技术主体和一个人工管理员，并校验封禁时间约束一致；任一检查失败即拒绝启动，不做静默修复。
- 登录凭证采用双通道：浏览器使用登录/注册时下发的 httpOnly 会话 Cookie（`SameSite=Lax`、`Path=/api`，前端 JavaScript 不持有 token）；`Authorization: Bearer` 仍保留给 Swagger 与编程客户端。登出接口（`/api/auth/logout`、`/api/admin/auth/logout`）清除 Cookie，幂等可匿名调用。生产 HTTPS 部署时设置 `AUTH_COOKIE_SECURE=true`。
- 管理员与普通用户使用不同登录入口和 API 空间：`ADMIN` Token 只能访问 `/api/admin/**`，`USER` Token 不能访问管理接口。
- 资金事务遵循统一锁序“协调行（幂等记录）→ 用户行（ID 升序）→ 商品行 → 订单行 → 流水/Outbox 插入”（允许跳过，禁止反向），用户行与下单商品行以 `NOWAIT` 加锁，锁繁忙映射为可重试的 `TRADE_BUSY`；`@RetryOnDeadlock` 只重试真正的死锁/锁等待超时（见 `RetryConfig`）。新增资金方法时应保持该锁序。
- 资金请求统一经生产入口 `TradingEntryService` 编排：先过事务外准入闸门（下单按商品单飞准入，所有资金操作受全局并发上限约束），等待预算耗尽即返回可重试的 `TRADE_BUSY`，此时不获取数据库连接也不创建幂等记录；死锁重试耗尽的锁冲突同样在该层统一转为 `TRADE_BUSY`。
- 数据库分别建模商品、内容审核、订单和资金流水，商品状态是可交易性的唯一权威来源，交易约束由对应业务表管理。
- 商品持久化状态只有 `ON_SALE`、`RESERVED`、`SOLD`、`OFF_SHELF`；内容审核为 `PASSED`、`PENDING`、`REJECTED`；订单为 `WAITING_MEET`、`COMPLETED`、`CANCELLED`。前端的“审核中”由审核状态派生。
- 聊天会话列表与未读数使用 SQL `GROUP BY` 聚合（每会话一行），消息历史按 `id` 倒序 keyset 分页（`beforeId` 向前翻页）；新增查询必须保持有界，不得全量加载消息明细。
- 站内系统通知走事务 Outbox：业务数据与通知事件在同一事务写入 `outbox_event`（提交前不可见，回滚随之消失），后台调度逐条消费且单事件失败不阻塞队列；新增通知按业务唯一性构造确定性 `event_id`，重复追加幂等跳过。
- 内容违规只执行可配置的固定合规扣分，商品下架统一由内容审核工作台裁决执行；账号封禁和解封只能在用户管理中独立执行。
- 后端使用 Java 25 虚拟线程处理请求；数据库吞吐受 HikariCP/MySQL 连接池上限约束。
- API 统一响应和鉴权快照使用不可变 Record；业务 JSON 栈使用 Jackson 3，Swagger/OpenAPI 的传递依赖由 springdoc 管理，业务代码不要引入 Jackson 2 类型。
- 前端页面统一使用 Vue 3 `<script setup>` / Composition API，Element Plus 组件与 API 按需导入；登录态与用户摘要由 `src/utils/auth.ts` 统一管理（响应式，唯一真相源），Pinia 不再持久化。
- 复杂页面采用“页面编排 + 子组件”边界：认证页三面板在 `views/login/panels/`、事件专题卡片在 `views/admin/topics/EventTopicCard.vue`，页面文件只负责布局与协调；跨面板共享的学校下拉走 `composables/useSchoolOptions.ts`。
- 前端有三类“唯一出处”约定：路由路径与命名路由集中在 `src/constants/routes.ts`（页面不得硬编码路径字符串）；时间/价格/占位图等展示格式化集中在 `src/utils/format.ts`；服务端分页列表（loading/error/empty + 分页状态机）统一用 `composables/usePagedList.ts`。
- 表单校验使用 Element Plus 声明式 `:rules`（提交统一走 `utils/formValidate.ts` 的 `validateForm`），不要在提交函数里手写 if 逐字段校验。
- 首页交易大厅的视图、`useMarketplaceHome` 状态副作用和 scoped 样式分文件维护。
- 代码卫生由工具链强制：后端 `mvn spotless:check`（未用导入/行尾空白/文件换行），前端 `npm run lint`（ESLint）与 `npm run format:check`（Prettier），CI 的 lint 作业拦截。仓库统一 LF 行尾（见 `.gitattributes` 与 `.editorconfig`）。
- 前端开发代理端口固定为 `3000`，后端端口固定为 `8080`；修改任一端口时需同步调整 [`frontend/vite.config.ts`](frontend/vite.config.ts)。
- 平台钱包仅处理项目内部余额与流水，不接入第三方支付渠道。

## 开源许可

本项目基于 [MIT License](LICENSE) 开源。你可以自由使用、复制、修改、合并、发布和分发本项目，但须保留原始版权声明及许可声明。

## 致谢

感谢 [zhiyi-school](https://github.com/kwang888210/zhiyi-school) 项目及其贡献者提供的开源实现与实践经验。
