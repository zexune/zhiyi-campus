# 智易校园测试体系

本项目采用“快速单元测试为底座、契约与组件测试覆盖边界、真实 MySQL 集成测试校验事务、浏览器 E2E 兜底关键旅程”的分层策略。目标不是单纯追求用例数量，而是让每类风险都由最合适、最稳定的一层测试负责。

## 测试分层

| 层级 | 工具与入口 | 主要覆盖 | 是否依赖外部服务 |
| --- | --- | --- | --- |
| 后端单元测试 | JUnit 5、Mockito；`mvn test` | 用户、交易、内容治理、后台治理、查询组装、边界与异常 | 否 |
| HTTP 契约测试 | Spring MVC Test、MockMvc；`mvn test` | 参数校验、统一响应结构、金额/状态序列化、业务异常、用户端与管理端登录契约 | 否 |
| 持久化与事务集成测试 | Spring Boot Test、Testcontainers、MySQL 9.7 LTS；`mvn verify -Pintegration` | 真实建表 SQL、MyBatis 映射、唯一约束、钱包/订单/预留/流水原子性、事务回滚 | Docker |
| 前端工具与组件测试 | Vitest、Vue Test Utils、happy-dom；`npm test` | 领域映射、评价弹窗、钱包、买入订单、后台数据大盘及失败/空态 | 否 |
| 浏览器烟测 | Playwright Chromium；`npm run test:e2e` | 生产构建、路由守卫、钱包加载/充值/刷新、浏览器运行时错误 | 否，API 使用确定性 mock |
| 全系统 E2E | Playwright → Vue → Spring Boot → MySQL；`npm run test:system` | 注册、发布、充值、下单、确认收货、评价、后台看板与角色隔离 | MySQL 与后端 |

## 常用命令

### 快速回归

后端快速套件同时生成 JaCoCo 报告并执行覆盖率门禁：

```bash
cd backend
mvn verify
```

前端工具、组件和确定性浏览器测试：

```bash
cd frontend
npm ci
npm run test:all
```

开发时可使用 `npm run test:watch` 只重跑受影响的 Vitest 用例。

### 真实 MySQL 集成测试

本机安装并启动 Docker 后执行：

```bash
cd backend
mvn verify -Pintegration
```

该 profile 启动一次性 MySQL 9.7 LTS 容器，直接运行根目录的 `zhiyi_campus_init.sql`，并使用真实 SQL、索引、约束和事务语义。套件通过 `SELECT VERSION()` 校验数据库环境为 9.7.x。

### 完整系统 E2E

先使用 `zhiyi_campus_init.sql` 初始化专用测试数据库，并在 `http://127.0.0.1:8080` 启动后端；随后执行：

```bash
cd frontend
npm ci
npx playwright install chromium
npm run test:system
```

系统测试会注册带随机后缀的卖家和买家，并写入真实业务数据。不要对保存开发或生产数据的数据库运行该命令；本地建议使用可随时重建的专用测试库。CI 会自动创建隔离的 MySQL 服务并完成初始化。

## 覆盖率门禁

- 后端 `mvn verify` 要求全项目行覆盖率不低于 60%、分支覆盖率不低于 45%；HTML 报告位于 `backend/target/site/jacoco/index.html`。
- 前端 `npm run test:coverage` 要求全源码语句/函数/行覆盖率不低于 12%，分支覆盖率不低于 8%；评价弹窗、钱包、买入订单、后台大盘和交易工具函数另设 65%–100% 的定向门禁。HTML 报告位于 `frontend/coverage/index.html`。
- 覆盖率是回归下限，不是完成标准。涉及资金、权限、状态机或事务的改动，即使覆盖率未下降，也必须补充能验证业务结果和失败回滚的断言。

## 测试数据与隔离

- 单元测试只 mock 当前层之外的依赖，避免把数据库或 Spring 容器偷偷带入快速套件。
- 使用 Mapper mock 执行 MyBatis-Plus Lambda Wrapper 的单元测试，必须在 `@BeforeAll` 中通过 `MybatisMetadata.initialize(实体类, Mapper类)` 显式初始化所需元数据，不能依赖其他测试留下的全局缓存。
- MVC 契约测试只加载控制器切片，并显式 mock 服务、JWT 与角色拦截器，确保失败时能定位到 HTTP 边界。
- Testcontainers 集成测试使用一次性数据库；HTTP 交易旅程使用唯一测试数据并在用例结束时显式清理。
- 事务失败用例在钱包流水写入处制造数据库错误，并同时断言余额、订单和商品预留均未留下部分提交。
- 浏览器烟测使用精确 URL 谓词拦截 API，不能用过宽规则误拦截 Vite 源模块或静态资源。
- 全系统 E2E 使用唯一学号和邮箱，避免并行执行时互相覆盖；管理员只使用初始化脚本提供的本地测试账号。

## 用例质量约定

新增或修改测试时遵循以下规则：

1. 测试名描述可观察行为和条件，不复述实现方法名。
2. 每个用例保持清晰的准备、执行、断言结构；失败路径必须断言错误码、消息或持久化结果。
3. 金额使用精确十进制断言，状态使用领域枚举/状态码断言，不用模糊字符串包含代替。
4. HTTP 契约同时校验状态码和 `{ code, message, data }`，重要 DTO 字段校验 JSON 类型和值。
5. 组件测试通过用户可见文本、角色、按钮状态和 API 调用结果断言，避免绑定内部 ref 或 CSS 实现细节。
6. E2E 不使用固定休眠等待业务完成；依赖 URL、响应或可见状态。失败时保留截图、trace 和视频。
7. 修复缺陷时先添加能复现问题的最小测试，再修复实现，并保留该用例作为回归保护。

## CI 门禁

`.github/workflows/test.yml` 在 push 和 pull request 上执行五条独立流水线：

| 作业 | 阻断内容 |
| --- | --- |
| Lint（Spotless + vue-tsc + ESLint/Prettier） | 后端代码卫生检查、前端类型检查（vue-tsc strict）、lint 与格式检查 |
| Backend unit + MVC contract | 编译、快速测试套件、JaCoCo 门禁 |
| Backend real MySQL integration | Testcontainers 全链路与事务回滚 |
| Frontend component + mocked browser | Vitest 覆盖率门禁、生产构建、Playwright 烟测 |
| Full Vue + Spring + MySQL journey | 真实浏览器、真实后端和真实 MySQL 的关键交易闭环 |

失败诊断会以 GitHub Actions artifact 保存：后端 Surefire/Failsafe 报告、JaCoCo、前端覆盖率、Playwright report、trace、截图和视频。

## 新功能应落在哪一层

- 纯计算、规则、状态转换：优先单元测试。
- 新增或改变请求字段、响应字段、校验或错误码：补 MVC 契约测试。
- 新 SQL、约束、锁、事务、多表写入：补 Testcontainers 集成测试。
- 新组件交互、加载/空态/失败态：补 Vitest 组件测试。
- 用户可见关键旅程或跨前后端状态变化：补 Playwright E2E；资金、权限和治理主链路必须使用真实系统 E2E。
