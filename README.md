# 智易校园（Zhiyi Campus）

智易校园是一个面向高校场景的前后端分离交易平台。项目以校内闲置流转为核心，支持出售、求购、以物换物和校园跑腿，并将多学校数据隔离、JWT 鉴权、本地内容治理、站内会话、钱包订单、信誉成长与后台治理整合为完整的交易闭环。

## 目录

- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [性能与数据模型](#性能与数据模型)
- [快速开始](#快速开始)
- [容器化](#容器化)
- [常用命令](#常用命令)
- [测试体系](#测试体系)
- [项目目录结构](#项目目录结构)
- [API 与文档入口](#api-与文档入口)
- [开源许可](#开源许可)
- [致谢](#致谢)

## 核心功能

- **校园身份与账号安全**：按学校和学号注册登录，支持学校邮箱规则、密保找回、密码修改、账号注销、BCrypt 密码加密、登录失败限流与 JWT 会话失效控制。
- **多类型校园集市**：出售（`SELL`）、求购（`BUY`）、换物（`SWAP`）和跑腿（`ERRAND`），提供分类、关键词、价格、标签、排序与分页筛选。
- **内容发现**：本地生成的商品标签、近期爆款榜、标签趋势、换物匹配、跑腿专区与活动专题。
- **本地内容治理**：本地规则检测违规关键词，命中后隐藏商品并交由管理员复核；支持用户举报、卖家整改和限时申诉。
- **收藏与站内沟通**：商品收藏、买卖双方会话、未读消息统计和管理员客服会话。
- **交易闭环**：平台钱包、充值流水、创建/取消订单、确认收货与交易评价；资金操作必须携带 `X-Idempotency-Key` 幂等键，同键重复请求恰好执行一次并重放同一结果。
- **信誉与成长体系**：经验值、等级、六维信誉雷达、校园关系标签和违规处罚记录。
- **运营管理后台**：数据看板、交易热力图、商品流转谱系、独立用户封禁、内容审核与申诉复核、学校/分类/活动管理和客服收件箱。

## 技术栈

| 层级       | 技术                                                                                                                                                                                                         |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 前端       | TypeScript 5.9（strict）、Vue 3.5.41、Vue Router 5.2.0、Pinia 4.0.2、Element Plus 2.14.4、Axios 1.19.0、Vite 8.2.1、`@vitejs/plugin-vue` 6.0.8、Auto Import / Components、openapi-typescript（契约类型生成） |
| 后端       | Java 25、Spring Boot 4.1.1、Spring MVC、MyBatis-Plus 3.5.17（Boot 4 Starter）、Maven 3.9.16                                                                                                                  |
| 基础库     | Lombok 1.18.46、JJWT 0.13.0、Jackson 3                                                                                                                                                                       |
| 数据与安全 | MySQL 9.7 LTS、Connector/J 9.7.0、JWT（httpOnly Cookie 下发 + Bearer 双通道）、BCrypt、来源白名单 CORS                                                                 |
| 文件存储   | 本地文件系统，通过 `/uploads/**` 提供访问                                                                                                                                                                    |
| 接口风格   | RESTful JSON，统一返回 `{ code, message, data }`                                                                                                                                                             |

## 性能与数据模型

- **索引化大厅推荐**：商品发布时生成不可变 `feed_key`，默认推荐按复合索引稳定分页，翻页使用带 TTL 的 HMAC-SHA256 签名游标，过期或签名不匹配从首屏重启。
- **批量读模型与聚合统计**：列表先分页再按 ID 集合批量读取关联数据；交易日趋势、成交额和地点热力由聚合 SQL 直接返回小结果集，不引入本地缓存；商品详情浏览量只做内存累加，后台任务批量落库到独立统计表 `item_view_stat`，崩溃最多损失一个刷新窗口的增量。
- **强类型领域契约**：后端状态使用带 `@EnumValue` 的领域枚举，前端状态码枚举集中在 `src/constants/domain.ts`；API 契约类型以 OpenAPI 规格为唯一真相源，治理与生成流程见[接口约定](#接口约定)。

修改查询条件、排序字段或学校隔离规则时，应同步用 `EXPLAIN ANALYZE` 复核初始化脚本中的复合索引命中情况，而不是盲目新增单列索引。

## 快速开始

> 宿主机不想安装 JDK / Maven / Node / MySQL？可直接使用[容器化](#容器化)的开发机，或用 Dev Container 让 IDE 直连容器，这是更为推荐的方法。

### 1. 开发环境

- JDK 25
- Maven 3.9.16
- Node.js 24.20.0
- npm 11.19.0
- MySQL 9.7.2

以上为已验证的开发配置，并非最低兼容版本要求。

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

初始数据库包含一个默认管理员：后台账号 `admin`，初始密码 `123456`。

### 3. 配置并启动后端

后端默认使用虚拟线程处理请求，敏感配置经环境变量注入。`JWT_SECRET` 没有默认值，必须是至少 32 字节随机数据的 Base64 编码，获取方法：

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

```bash
openssl rand -base64 32
```
后端启动示例：

PowerShell 示例：

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

完整配置项、环境变量与默认值见 [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)；后端默认监听 `http://localhost:8080`，MySQL 不在 `localhost:3306` 时修改其中的数据源 URL。

### 4. 启动前端

新开一个终端，在项目根目录执行：

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:3000`。Vite 会将 `/api` 和 `/uploads` 请求代理到 `http://localhost:8080`。

## 容器化

推荐使用 Docker 开发和运行本项目，避免版本兼容问题。

```bash
# —— 首次使用 ——
cp .env.example .env          # JWT_SECRET 必填，Windows开发环境使用 Copy-Item 命令或手动复制
docker compose up -d          # 启动容器
docker compose exec dev bash  # 进入 Docker 容器终端

# —— 容器内，与普通机器完全一致 ——
mysql -uroot < /repo/zhiyi_campus_init.sql    # 初始化数据库，仅首次需要
cd frontend && npm ci && npm run dev          # 前端 http://localhost:3000
cd backend  && mvn spring-boot:run            # 后端 http://localhost:8080

# —— 停止 / 删除 / 重建 ——
docker compose stop             # 停止（容器保留，数据不动）
docker compose restart          # 重启整台"机器"
docker compose down             # 停止并删除容器（数据卷保留，下次 up -d 数据照旧）
docker compose down -v          # 连数据卷一起删（数据库、Maven 缓存、node_modules 全清，回到出厂）
docker compose up -d --build    # 改了 Dockerfile 后重建镜像并重建容器；改 .env 只需 up -d
```

VS Code / GitHub Codespaces 用户直接 "Dev Containers: Reopen in Container"：进入时 MySQL 已就绪，集成终端跑上面同样的命令即可；调试配置见 `.vscode/launch.json`。关闭 VS Code 窗口会停掉容器，数据都在卷里不丢。

数据与端口：

- 端口：`3000` Vite、`8080` 后端直连、`5005` JDWP；MySQL 3306 不发布到宿主，root 默认无密码，宿主机上导入脚本用 `docker compose exec -T dev mysql -uroot < 脚本.sql`，交互操作用 `docker compose exec dev mysql -uroot`。
- 数据库在 `zhiyi-campus_mysql-data`、Maven 缓存在 `zhiyi-campus_maven-repo` 命名卷；上传文件落在 `backend/uploads`（已 gitignore）。
- 想给数据库设密码：`ALTER USER 'root'@'localhost' IDENTIFIED BY '...'`，把同一值填进 `.env` 的 `MYSQL_PASSWORD` 后 `docker compose up -d` 重建容器；彻底重置 = 删 `zhiyi-campus_mysql-data` 卷后重新 `up -d` 并重新导脚本。

注意事项：

- 首次 `npm ci` 必须在容器内执行（`node_modules` 是 Linux 二进制、存放在命名卷）；改动 `package.json` 后在容器内重跑一次即可。
- 容器内不回写 `auto-imports.d.ts` / `components.d.ts`；新增 store/组件后在宿主机跑一次 `npm run build`（而非 `dev`）让完整声明入库。
- `backend/target` 在命名卷里（宿主不可见）；需要 JaCoCo 报告时用 `docker compose cp dev:/repo/backend/target/site/jacoco ./jacoco`。
- Windows 上若改 `.vue` 不触发 HMR，在 `frontend/vite.config.ts` 的 `server.watch` 加 `usePolling` 即可。
- E2E / 系统测试在宿主机或 CI 跑（见 [测试体系](#测试体系)）：后端发布在宿主 8080，`run-e2e.mjs` 的代理目标无需改动；跑之前先停掉容器内的 Vite，避免 3000 端口冲突。

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
npm run gen:icons     # 由品牌源图 assets/brand/logo-source.png 生成 public/ 下全部图标
```

前端生产构建产物位于 `frontend/dist`。

品牌图标以 `frontend/assets/brand/logo-source.png` 为唯一真相源，`npm run gen:icons` 生成 `frontend/public/` 下全部图标；源图是构建输入而非应用资产，刻意置于 `src/assets` 之外，请勿搬动。

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
│   │   ├── composables/               # 分页列表、聊天事件流与请求竞态守卫
│   │   ├── constants/                 # API 领域状态码、统一展示映射与路由路径常量
│   │   ├── router/                    # 页面路由与访问守卫
│   │   ├── stores/                    # Pinia 状态管理
│   │   ├── types/                     # OpenAPI 生成契约类型与领域别名
│   │   ├── utils/                     # 请求、鉴权、信誉与交易工具
│   │   └── views/                     # 首页、商品、聊天、钱包、后台等页面
│   ├── assets/                        # 品牌源图等构建输入（非应用资产，勿搬入 src/assets）
│   ├── public/                        # 图标等原样复制的静态资产（由 gen:icons 生成）
│   ├── scripts/                       # API 契约快照更新与品牌图标生成脚本
│   ├── tests/                         # Vitest 组件/工具测试与 Playwright E2E
│   ├── package.json                   # npm 脚本与依赖
│   └── vite.config.ts                 # Vite 配置与开发代理
├── compose.yaml                       # 单文件编排
├── docker/                            # 开发容器入口脚本（MySQL 随容器开机自启）
├── .devcontainer/                     # VS Code / Codespaces 直连同一 compose
├── .dockerignore                      # 构建上下文排除（node_modules / target 等）
├── .env.example                       # 容器编排密钥模板（.env 不入库）
├── zhiyi_campus_init.sql              # MySQL 初始化脚本
├── .github/workflows/                 # CI 流水线：测试金字塔与开发容器冒烟
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

- 错误语义：HTTP 状态码粗分类，body 的 `code` 细分业务原因；业务失败返回真实 4xx/5xx，映射表见 `ResultCode`。凭证类失败（密码/密保错误）用 400 而非 401——HTTP 401 保留给会话失效，前端收到即清除登录态并跳转登录页。
- **认证错误唯一映射（P0-1）**：`JwtInterceptor` 对无效/过期或注销后的旧 Token 直写 `401 + UNAUTHORIZED(401)`，对改密/改角色后的旧 Token 直写 `401 + SESSION_INVALIDATED(1401)`，并清除 httpOnly 会话 Cookie；业务层 `USER_CANCELLED(1008)` 是 403，不触发前端登出。前端只以**真实 HTTP 401** 作为清理登录态的依据。
- **失败信封元数据（P1-3）**：失败响应携带必填 `meta.requestOutcome`（`REJECTED`=可清幂等键 / `PROCESSING`=处理中 / `UNKNOWN`=保留幂等键），前端以其为幂等键处理的唯一权威判据；残缺信封（缺 `code/message/data`、`meta` 缺失或非法、非 JSON、代理 HTML）按传输层错误保守处理（RETAIN）；允许退避的失败（如 429 交易繁忙）附标准 `Retry-After` 头。
- **`@BusinessErrors` 声明纪律**：每个 operation 须显式声明 `@BusinessErrors`（空注解=已审计且无特有业务错误，`BAD_REQUEST` 隐式允许），漏声明会被 strict 模式契约测试（`BusinessErrorContractVerifier`）拦截。
- **契约治理**：仓库根目录的 `openapi.json` 是从运行中后端导出的规范化快照，`frontend/src/types/api.gen.d.ts` 由快照生成、禁止手改，仅 `src/types/contracts.ts` 与 `src/types/models.ts` 可直接导入（前者约束 operation，后者为 schema 领域别名与少量客户端组装类型）；CI 的 `api-contract-drift` 作业比对实时规格、快照与前端类型，任一漂移即失败。

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
| `/api/admin`                   | 独立管理员认证、看板、用户列表与搜索、封禁与强制重置密码、内容/申诉治理、学校、分类、活动与客服管理 | [`后台控制器`](backend/src/main/java/com/zhiyi/module/admin/controller/)、[`BanController`](backend/src/main/java/com/zhiyi/module/user/controller/BanController.java)、[`AdminCategoryController`](backend/src/main/java/com/zhiyi/module/item/controller/AdminCategoryController.java)、[`EventTopicController`](backend/src/main/java/com/zhiyi/module/item/controller/EventTopicController.java) |

### Swagger / OpenAPI

后端启动后可通过以下地址查看或获取实时接口定义：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- OpenAPI YAML：`http://localhost:8080/v3/api-docs.yaml`

Swagger UI 默认将受保护接口标记为 JWT Bearer 鉴权。调用这类接口前，点击页面右上角的 **Authorize**，粘贴用户端或管理端登录接口返回的 JWT（无需手动添加 `Bearer ` 前缀）。公开接口可以直接调用。

后端 DTO/VO 变更后，启动一次后端并执行 `npm run gen:api:dev` 刷新根目录 `openapi.json` 快照与 `frontend/src/types/api.gen.d.ts`（也可分别手动更新，命令见[常用命令](#常用命令)）；生成类型的治理规则与 CI 门禁见上文[契约治理](#接口约定)。

也可从以下位置交叉核对接口实现：

- 后端控制器：[`backend/src/main/java/com/zhiyi/module`](backend/src/main/java/com/zhiyi/module/)
- 前端请求封装：[`frontend/src/api`](frontend/src/api/)
- 数据库结构：[`zhiyi_campus_init.sql`](zhiyi_campus_init.sql)
- 运行配置：[`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)

## 开源许可

本项目基于 [MIT License](LICENSE) 开源。你可以自由使用、复制、修改、合并、发布和分发本项目，但须保留原始版权声明及许可声明。

## 致谢

感谢 [zhiyi-school](https://github.com/kwang888210/zhiyi-school) 项目及其贡献者提供的开源实现与实践经验。
