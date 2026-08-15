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
- **交易闭环**：支持平台钱包、充值流水、数据库原子商品预留、创建/取消订单、确认收货以及交易评价；订单与商品状态独立建模。
- **信誉与成长体系**：包含经验值、等级、六维信誉雷达、校园关系标签和违规处罚记录。
- **运营管理后台**：提供数据看板、交易热力图、商品强制下架、商品流转谱系、独立用户封禁、内容审核与申诉复核、学校/分类/活动管理和客服收件箱。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3.5.41、Vue Router 5.2.0、Pinia 4.0.2、Element Plus 2.14.4、Axios 1.19.0、Vite 8.2.1、`@vitejs/plugin-vue` 6.0.8、Auto Import / Components |
| 后端 | Java 25、Spring Boot 4.1.0、Spring MVC、MyBatis-Plus 3.5.17（Boot 4 Starter）、Maven 3.9.x（推荐 3.9.15） |
| 基础库 | Lombok 1.18.46、JJWT 0.13.0、Jackson 3 |
| 数据与安全 | MySQL 9.7 LTS、Connector/J 9.7.0、JWT（HS256 + issuer/audience/tokenVersion，httpOnly Cookie 下发 + Bearer 双通道）、BCrypt、Caffeine 本地缓存、来源白名单 CORS |
| 文件存储 | 本地文件系统，通过 `/uploads/**` 提供访问 |
| 接口风格 | RESTful JSON，统一返回 `{ code, message, data }` |

## 性能与数据模型

- **索引化大厅推荐**：商品发布时生成不可变 `feed_key`，默认推荐按 `(school_id, status, moderation_status, is_deleted, feed_key, id)` 复合索引稳定分页；同楼、同校区与全校商品使用分层计数和有界切片查询。
- **批量组装列表读模型**：商品卡片、买入/卖出订单和违规申诉先分页，再按 ID 集合批量读取关联商品、用户、评价、举报与标签，以固定数量的数据库往返完成页面组装。
- **规范化标签**：`tag` 保存标准标签，`item_tag` 保存多对多关系；筛选使用等值索引与 `EXISTS`。校级标签聚合使用 Caffeine 短缓存，并在商品内容或可见性变化的事务提交后精准失效。
- **数据库聚合统计**：交易日趋势、成交额和地点热力由聚合 SQL 直接返回小结果集，并以半开时间区间匹配索引。
- **强类型领域契约**：后端状态使用带 `@EnumValue` 的领域枚举，前端状态码集中在 `src/constants/domain.js`；JSON 数组由 Jackson 3/MyBatis TypeHandler 统一映射为 `List<String>`。

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

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MYSQL_USERNAME` | MySQL 用户名 | `root` |
| `MYSQL_PASSWORD` | MySQL 密码 | 无 |
| `JWT_SECRET` | 必填；Base64 编码、解码后至少 32 字节的 JWT 签名密钥 | 无 |
| `JWT_EXPIRATION` | Token 有效期，Spring Duration 格式 | `24h` |
| `CORS_ALLOWED_ORIGINS` | 允许访问 API 的前端来源，多个值用逗号分隔 | `http://localhost:3000,http://127.0.0.1:3000` |
| `MODERATION_RULE_VERSION` | 本地违规规则集版本，写入每条系统检测记录 | `2026.1` |
| `CONTENT_WARNING_POINTS` | 管理员确认内容违规时固定扣除的合规分 | `5` |
| `APPEAL_WINDOW_DAYS` | 已确认违规允许申诉的天数 | `7` |
| `TAG_CACHE_TTL` | 每个学校标签聚合缓存时长，Spring Duration 格式 | `60s` |

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
npm run lint          # ESLint（CI 同款）
npm run lint:fix      # 自动修复可修复问题
npm run format:check  # Prettier 检查（CI 同款）
npm run format        # 按 Prettier 格式化
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
│   │   ├── constants/                 # API 领域状态码与统一展示映射
│   │   ├── router/                    # 页面路由与访问守卫
│   │   ├── stores/                    # Pinia 状态管理
│   │   ├── utils/                     # 请求、鉴权、信誉与交易工具
│   │   └── views/                     # 首页、商品、聊天、钱包、后台等页面
│   ├── tests/                         # Vitest 组件/工具测试与 Playwright E2E
│   ├── package.json                   # npm 脚本与依赖
│   └── vite.config.js                 # Vite 配置与开发代理
├── zhiyi_campus_init.sql              # MySQL 初始化脚本
├── .github/workflows/test.yml         # 四层 CI 测试门禁
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
- `POST /api/admin/auth/login`
- `GET /api/school/list`
- `GET /api/category/list`
- `GET /api/user/{id}/card`
- `GET /api/user/{id}/reputation`

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
| `/api/item` | 商品发布、搜索、收藏、榜单、举报、申诉、换物与跑腿 | [`ItemController`](backend/src/main/java/com/zhiyi/module/item/controller/ItemController.java) |
| `/api/chat` | 会话、消息、客服与未读统计 | [`ChatController`](backend/src/main/java/com/zhiyi/module/social/controller/ChatController.java) |
| `/api/wallet` | 余额、充值与资金流水 | [`WalletController`](backend/src/main/java/com/zhiyi/module/trade/controller/WalletController.java) |
| `/api/order` | 下单、确认、取消、买卖订单与评价 | [`OrderController`](backend/src/main/java/com/zhiyi/module/trade/controller/OrderController.java) |
| `/api/admin` | 独立管理员认证、看板、用户封禁、内容/申诉治理、学校、分类、活动与客服管理 | [`后台控制器`](backend/src/main/java/com/zhiyi/module/admin/controller/)、[`BanController`](backend/src/main/java/com/zhiyi/module/user/controller/BanController.java)、[`AdminCategoryController`](backend/src/main/java/com/zhiyi/module/item/controller/AdminCategoryController.java)、[`EventTopicController`](backend/src/main/java/com/zhiyi/module/item/controller/EventTopicController.java) |

### Swagger / OpenAPI

后端启动后可通过以下地址查看或获取实时接口定义：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- OpenAPI YAML：`http://localhost:8080/v3/api-docs.yaml`

Swagger UI 默认将受保护接口标记为 JWT Bearer 鉴权。调用这类接口前，点击页面右上角的 **Authorize**，粘贴用户端或管理端登录接口返回的 JWT（无需手动添加 `Bearer ` 前缀）。公开接口可以直接调用。

也可从以下位置交叉核对接口实现：

- 后端控制器：[`backend/src/main/java/com/zhiyi/module`](backend/src/main/java/com/zhiyi/module/)
- 前端请求封装：[`frontend/src/api`](frontend/src/api/)
- 数据库结构：[`zhiyi_campus_init.sql`](zhiyi_campus_init.sql)
- 运行配置：[`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)

## 开发注意事项

- 上传文件默认保存在后端当前工作目录下的 `uploads` 文件夹；单文件上限为 5 MB，单次请求上限为 50 MB。
- 初始化脚本中的管理员密码和默认 MySQL 密码只适合本地开发，部署前必须替换；JWT 密钥没有默认值，启动时必须注入。
- 登录凭证采用双通道：浏览器使用登录/注册时下发的 httpOnly 会话 Cookie（`SameSite=Lax`、`Path=/api`，前端 JavaScript 不持有 token）；`Authorization: Bearer` 仍保留给 Swagger 与编程客户端。登出接口（`/api/auth/logout`、`/api/admin/auth/logout`）清除 Cookie，幂等可匿名调用。生产 HTTPS 部署时设置 `AUTH_COOKIE_SECURE=true`。
- 管理员与普通用户使用不同登录入口和 API 空间：`ADMIN` Token 只能访问 `/api/admin/**`，`USER` Token 不能访问管理接口。
- 资金事务（下单/确认/取消）遵循统一锁序“先 `sys_user` 行锁、后 `item_reservation` 行锁”，并对死锁/锁等待超时自动重试（见 `@RetryOnDeadlock` 与 `RetryConfig`）；新增资金方法时应保持该锁序并评估是否需要重试。
- 数据库分别建模商品、内容审核、订单和订单预留，状态与交易约束由对应业务表管理。
- 商品持久化状态只有 `ON_SALE`、`SOLD`、`OFF_SHELF`；内容审核为 `PASSED`、`PENDING`、`REJECTED`；订单为 `WAITING_MEET`、`COMPLETED`、`CANCELLED`。前端的“审核中”由审核状态派生。
- 聊天会话列表与未读数使用 SQL `GROUP BY` 聚合（每会话一行），消息历史按 `id` 倒序 keyset 分页（`beforeId` 向前翻页）；新增查询必须保持有界，不得全量加载消息明细。
- 内容违规只执行可配置的固定合规扣分。商品强制下架不自动扣经验，账号封禁和解封只能在用户管理中独立执行。
- 后端使用 Java 25 虚拟线程处理请求；数据库吞吐受 HikariCP/MySQL 连接池上限约束。
- API 统一响应和鉴权快照使用不可变 Record；业务 JSON 栈使用 Jackson 3，Swagger/OpenAPI 的传递依赖由 springdoc 管理，业务代码不要引入 Jackson 2 类型。
- 前端页面统一使用 Vue 3 `<script setup>` / Composition API，Element Plus 组件与 API 按需导入；登录态与用户摘要由 `src/utils/auth.js` 统一管理（响应式，唯一真相源），Pinia 不再持久化。
- 首页交易大厅的视图、`useMarketplaceHome` 状态副作用和 scoped 样式分文件维护；复杂页面采用“页面编排 + 组合函数/子组件”边界。
- 代码卫生由工具链强制：后端 `mvn spotless:check`（未用导入/行尾空白/文件换行），前端 `npm run lint`（ESLint）与 `npm run format:check`（Prettier），CI 的 lint 作业拦截。仓库统一 LF 行尾（见 `.gitattributes` 与 `.editorconfig`）。
- 前端开发代理端口固定为 `3000`，后端端口固定为 `8080`；修改任一端口时需同步调整 [`frontend/vite.config.js`](frontend/vite.config.js)。
- 平台钱包仅处理项目内部余额与流水，不接入第三方支付渠道。

## 开源许可

本项目基于 [MIT License](LICENSE) 开源。你可以自由使用、复制、修改、合并、发布和分发本项目，但须保留原始版权声明及许可声明。

## 致谢

感谢 [zhiyi-school](https://github.com/kwang888210/zhiyi-school) 项目及其贡献者提供的开源实现与实践经验。
