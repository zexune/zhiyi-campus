# OpenAPI 契约基线（bootstrap 标记）

> 仓库契约治理文档，与 CI 工作流及 breaking 审批记录同置于 `.github/`。

- **基线状态**：`bootstrap`（首次引入 `openapi.json` 快照，无历史基线可比）
- **引入时间**：2026-08-26
- **历史基线**：无 —— 基线提交树中不存在 `openapi.json`，CI 的 oasdiff
  breaking 检查在本仓库首次引入快照时输出 `baseline unavailable` 并跳过
  （bootstrap 语义），不做静默通过。

## 本快照引入时随附批准的契约变化

本文件即「显式批准记录」：以下 wire 变化与快照首次落盘同批发生，
经任务清单评审批准，不构成对既有消费者的静默破坏（首次建立契约快照）：

1. **统一信封类型分离（P1-1）**：`Result*` Schema 更名为 `ApiSuccess*`；
   失败信封新增统一 `ApiFailure` Schema。失败 JSON 形状固定为
   `{code,message,data,meta}`，四个字段均必填；`meta.requestOutcome` 也必填，
   取值为 `REJECTED/PROCESSING/UNKNOWN`。没有详情时 `data` 仍为显式 `null`，
   保持旧消费者对 `code/message/data` 三字段的读取兼容。
2. **认证错误语义（P0-1）**：`USER_CANCELLED(1008)` 映射 403（业务拒绝）；
   新增 `SESSION_INVALIDATED(1401)` 映射 401；401 响应清除会话 Cookie。
3. **可空性修正（P0-2/P0-3）**：`ChatItemSummaryVO` 新增必填 `type`
   （SELL/BUY/SWAP/ERRAND）；`ItemCardVO.price`、`ChatItemSummaryVO.price`
   声明 required + nullable（SWAP 恒为 null）；无封面图统一为显式 null
   （不再是空字符串）；`OrderVO.reviewed` 恒显式赋值。
4. **弱类型消除（P0-4）**：`/api/admin/violation-logs` 返回命名
   `ViolationLogRowResponse`（替代 `Map<String,Object>`）；
   `/api/admin/unban-user` 请求体改为 `UnbanUserDTO`（替代 `Map<String,Long>`）；
   `/api/auth/security-question` 返回 `SecurityQuestionVO`。
5. **分页契约（P1-6）**：公开边界统一 `PageResponse<T>`
   （current/size/pages/records/total，与旧 IPage 序列化字段一致），
   MyBatis-Plus `IPage` 不再泄漏到 OpenAPI。
6. **语义拆分（P2）**：feed/榜单族返回 `ItemSummaryResponse`（省略
   详情/所有者字段），详情/我的发布族返回 `ItemDetailResponse`；
   单订单操作返回 `OrderDetailResponse`（无 `reviewed`）；
   `ItemCardVO` 保留为兼容适配层（收藏/发布/编辑/重新上架族）。
7. **operation 级错误契约（P1-4/P4）**：所有 operation 显式声明
   `@BusinessErrors`（审计为空也用空注解），公共失败 400/401/403/405/406/413/415/500 与
   业务码在同一 HTTP 状态下合并（404 由业务码显式声明，不再是公共响应），
   引用 `ApiFailure`（全字段 required、无 body 级 retryAfterSeconds），
   含可退避码（3004/3006/1005）的状态声明可选 `Retry-After` 响应头与
   `x-retry-after-business-codes`，`x-business-codes` 扩展机器可读。

## 后续基线

自下一次提交起，PR 将以 merge-base 提取历史快照运行固定版本 oasdiff
（v1.29.1）的 breaking 检查；破坏性变更必须在
`.github/openapi-breaking-approvals.txt` 登记规格内容指纹对
（`<base-spec-sha256> <new-spec-sha256> <PR/ADR 说明>`）方可通过——
内容指纹而非基线提交 SHA，避免同一基线上的不同 PR 互相放行。

## Breaking migration：全局 HTTP 状态码语义迁移

本批次随 bootstrap 快照一并引入的破坏性迁移（合并前必须逐条确认）：

- **旧行为**：业务失败以 `HTTP 200 + body.code` 送达，HTTP 层几乎不承载
  错误语义（网关、监控与重试策略无法按状态码工作）。
- **新行为**：业务失败返回**真实 4xx/5xx** + 完整 `ApiFailure` 信封
  （`{code, message, data, meta}`，全字段 required；`meta.requestOutcome`
  为幂等处置权威）；成功仍为 `HTTP 200 + code 200`。
- **发布顺序（不可颠倒）**：先发布兼容新旧信封的前端
  （`frontend/src/utils/request.ts` 同时识别新失败信封与旧
  `200+非200码` 信封），验证稳定后再发布新后端。
- **兼容窗口与回滚**：前端保留旧信封 fallback——只有 `code/message/data`
  齐备且 `meta` 自有属性完全不存在时才按业务码白名单推断幂等处置；
  `meta` 存在但残缺（null/缺字段/非法枚举）一律按结果不明（RETAIN）
  处理，不信任 body 的业务码与 message。回滚方案：新后端异常时回退旧
  后端即可，前端双信封兼容无需回滚；下线旧信封 fallback 前必须确认
  消费者登记表中所有消费者均已验证新信封。

### API 消费者登记（合并前必须完整回答：仓库外是否还有消费者？）

| 消费者 | 形态 | 负责人 | 版本 | 新信封验证状态 |
| --- | --- | --- | --- | --- |
| 当前 Vue Web（`frontend/`） | Web（本仓库） | 前端负责人 | v2.0.0 | ✅ 已验证（`tests/request.test.ts` 信封矩阵 + 双套 E2E） |
| 仓库外消费者 | 小程序/移动端/脚本/第三方 | API 负责人待确认 | 待登记 | ⛔ 未确认，不得发布后端迁移 |

仓库静态检查只能确认当前 Vue Web，不能证明仓库外没有消费者。API 负责人
必须在发布前确认并更新上表；未完成确认时不得上线全局 HTTP 状态迁移。
发现任何小程序、移动端、脚本或第三方消费者时，必须先登记并完成新信封
验证。

## `@BusinessErrors` 声明纪律

- 每个 Controller operation 都必须**显式出现** `@BusinessErrors`
  （`ControllerBusinessErrorsCompletenessTest` 构建期强制）；公共 OpenAPI
  响应（400/401/403/405/406/413/415/500）不能豁免 operation 级声明。
- **空注解表示"已审计且无特有业务错误"**，不表示遗漏：空注解与缺注解
  在架构测试下可区分。
- 只有 `BAD_REQUEST` 隐式允许（通用参数/内容校验码）；
  `FORBIDDEN`、`SERVER_ERROR`、`USER_NOT_FOUND`、`CONFLICT` 等业务层
  显式错误必须逐 operation 声明——包括跨校商品详情/溯源/收藏/举报、
  非本人商品操作、跨校聊天、客服账号缺失、以及经 `SchoolScopeGuard`、
  `requireOwn*`、`findAdmin()` 等辅助方法的分支。
- 运行时由 `BusinessErrorContractVerifier` 校验：strict 模式（契约测试）
  下 operation 抛出未声明业务码直接失败；生产环境记录契约违约日志。

## 三层契约兜底

| 环节 | 方式 | 验证内容 |
| --- | --- | --- |
| 开发更新 | 后端运行时执行 `npm run gen:api:dev` | 实时规格 → 快照 → 类型 |
| 本地最终验收 | 临时获取 `/v3/api-docs` 并执行比较脚本 | 实时规格 ↔ 快照 |
| CI | `api-contract-drift` | 干净环境重验两条边与 breaking change |

`api-contract-drift` 同时强制安装校验固定版本 oasdiff（v1.29.1，下载
进入 Runner 临时目录 + sha256 校验 + `--version` 冒烟，任一失败即任务
失败）；bootstrap 基线缺失只允许跳过历史快照比较，不允许跳过工具安装。
