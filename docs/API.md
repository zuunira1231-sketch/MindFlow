# MindFlow 前后端联调接口（2026-09-14）

本文按 `/Users/Admin/Downloads/MindFlow_backend` 当前代码整理；未修改后端代码。标注“实测”的响应来自本次用户 Postman 回传，标注“代码示例”的响应仅展示代码确定的字段结构。当前环境无法连接用户运行中的 `localhost:8080`，因此未实测的接口不能冒充实测。

## 共通约定

- 基础地址：本地为 `http://localhost:8080`，正式环境必须使用 HTTPS。除 PDF 外，请求和响应均为 JSON。业务接口现在需要已验证的单账号会话；该账号在后端映射到现有数据库用户 ID 1，不接受前端传入的用户 ID。
- 时间字段为不带时区的本地日期时间字符串，例如 `2026-09-15T01:21:02.235269`；前端不要直接把 `expiresAt` 当 UTC 解析。
- 错误统一结构：`{"timestamp":"2026-09-14T00:53:07.458949","status":400,"error":"Bad Request","message":"Draft 的对话来源与原始内容不一致，请重新生成 Draft","path":"/analysis/draft/plans"}`。`message` 是中文展示文案，目前没有稳定的机器错误码。
- `planRequestId`：前端每次**新建方案**产生的新 UUID；网络重试同一请求时复用原 UUID。`Draft.request_id` 是第一次 AI 返回的编号，不是同一字段。
- 最终方案有效期 24 小时。`GET` 的 `status` 可能是 `READY`、`EXPIRED`、`STALE`、`APPLIED`；只有 `READY` 可首次确认。`APPLIED` 的重复确认幂等返回已存结果。

## 单账号登录与前端请求（2026-09-14）

本次只改后端，尚未部署。所有业务路由（包括对话导入、Draft、方案生成/确认、Domain、Knowledge、Relation/领域认知及 Evolution 查询）均要求登录；未登录不得读写。仅 `/auth/csrf`、`/auth/login`、`/auth/me` 可匿名调用。业务用户 ID 由服务端根据通过验证的 `ROLE_DEMO` 会话确定为 1，前端不要传 `userId`。

### 统一调用顺序

1. 页面启动或准备登录时 `GET /auth/csrf`，保存响应里的 `token`；该 GET 会建立匿名会话。所有请求都带浏览器 Cookie：`fetch` 用 `credentials: "include"`，Axios 用 `withCredentials: true`。
2. `POST /auth/login` 发送 JSON 账号密码，并加请求头 `X-CSRF-TOKEN: <token>`。登录成功后浏览器保留 `JSESSIONID`；不要把用户名/密码保存到本地存储。
3. 刷新页面后先 `GET /auth/me` 判断会话是否仍有效，再 `GET /auth/csrf` 获取当前会话可用的令牌。所有 `POST/PUT/DELETE`，**包括 PDF `multipart/form-data` 上传、确认沉淀和退出**，都带 `X-CSRF-TOKEN`；GET 不需要该头。
4. `POST /auth/logout` 成功后清除前端内存令牌；再次登录须重新调用 `GET /auth/csrf`。会话失效后业务请求返回 401，前端返回登录页。

### `GET /auth/csrf`

无需登录、无 Body。成功 200（针对性 HTTP 测试实际验证；令牌每个会话不同）：

```json
{"token":"<本次会话产生的令牌>","headerName":"X-CSRF-TOKEN"}
```

令牌保存在服务端会话中，**不是**需由 JavaScript 读取的 CSRF Cookie。调用时必须带上同一浏览器会话 Cookie；不要跨会话复用令牌。

### `POST /auth/login`

`Content-Type: application/json`，请求头 `X-CSRF-TOKEN` 必填：

```json
{"username":"<已配置的演示账号名>","password":"<用户输入的密码>"}
```

成功 200，响应结构（针对性 HTTP 测试验证；账号名仅为示意，不是真实部署凭据）：

```json
{"authenticated":true,"userId":1,"username":"<演示账号名>"}
```

账号或密码错误：401，统一错误体示例：

```json
{"timestamp":"2026-09-14T14:35:00","status":401,"error":"Unauthorized","message":"账号或密码错误","path":"/auth/login"}
```

缺少/无效 CSRF 令牌：403，`message:"请求缺少有效的 CSRF 令牌或无权访问"`。为避免泄露账号是否存在，错误用户名和错误密码使用同一文案。

### `GET /auth/me`

无需 Body。未登录或会话过期时也返回 **200**，不是 401：

```json
{"authenticated":false,"userId":null,"username":null}
```

已登录则返回与登录成功同形的 `{authenticated:true,userId:1,username:"..."}`。前端刷新后用该接口恢复登录 UI；不要仅凭本地状态判断已登录。

### `POST /auth/logout`

需已登录，带 `X-CSRF-TOKEN`，无 Body。成功 200：

```json
{"authenticated":false,"userId":null,"username":null}
```

后端会立即使该浏览器会话失效；再用旧 Cookie 访问业务接口应返回 401。未登录/会话过期调用退出也是 401。已登录但 CSRF 令牌缺失/错误为 403。

### 会话、跨域与演示账号初始化

- `JSESSIONID` 是 `HttpOnly` Cookie，只通过 Cookie 跟随请求；前端不读取或存入 localStorage。会话空闲超时配置为 8 小时，退出或服务重启后需要重新登录。首版单后端实例，不共享跨实例会话；不同电脑各自登录同一账号，读取的是同一 MySQL 用户 1 的演示数据。
- Cookie 默认 `Secure=true`、`SameSite=Lax`、仅 Cookie 会话跟踪。正式环境使用 HTTPS，建议反向代理让前后端通过同一 HTTPS 域名访问，避免跨站 Cookie 问题；不要把后端 HTTP 端口直接暴露给公网。可在代理层限制登录请求频率。
- 本地 HTTP 联调：后端环境变量设 `SESSION_COOKIE_SECURE=false`，前端固定使用 `http://localhost:5173`，后端 `http://localhost:8080`；默认 CORS 允许该来源和凭据。**不要**在正式环境把 `SESSION_COOKIE_SECURE` 设为 `false`。正式前端/后端若不同源，还需把实际前端 HTTPS Origin 配到 `CORS_ALLOWED_ORIGINS`，不能用 `*` 配合凭据。
- 启动后端前必须通过环境变量提供 `DEMO_USERNAME` 与 `DEMO_PASSWORD_HASH`（BCrypt 哈希），缺少时启动失败，不存在代码内置默认账号。不要在仓库、接口文档、聊天或前端构建配置中保存真实密码。Mac 本地可运行 `htpasswd -nBC 12 <自选用户名>`，按提示在终端交互输入密码；输出 `用户名:哈希` 后，仅把冒号后的哈希作为 `DEMO_PASSWORD_HASH` 放入本机/部署环境私密配置。该哈希对应的明文密码只由使用者记住；现有 MySQL 数据无需迁移。

## 对话导入

### `POST /conversation-imports/pdf`

`multipart/form-data`：必填文件字段 `file`，可选文本字段 `title`。仅支持从 ChatGPT 分享页打印出的**可选择文本 PDF**，文件内部限制 15,000,000 字节、1–400 页，须能提取分享链接页脚；Spring 上传配置为单文件 15MB、整请求 16MB。Postman 不要手填 multipart `Content-Type`，由客户端自动生成边界。

成功响应（实测）：

```json
{"conversationId":1,"status":"IMPORTED","messageCount":4}
```

### `POST /conversation-imports/text`

请求：`{"title":"可选标题","sourcePlatform":"可选来源","content":"完整聊天文本"}`，`content` 非空。响应与 PDF 导入同形：`conversationId: number`、`status: string`、`messageCount: number`（代码示例，未取得该接口的实时响应）。

### `POST /conversation-imports/messages`

请求：`{"title":"可选标题","sourcePlatform":"可选来源","messages":[{"role":"user","content":"问题"},{"role":"assistant","content":"回答"}]}`。`messages` 至少一条，条目 `role/content` 非空；业务层仅接受 `user` 和 `assistant`。响应与 PDF 导入同形（代码示例）。

> 当前没有“直接传 ChatGPT 分享 URL 并抓取内容”的接口。分享页需要先打印为 PDF 上传；也可走文本或消息入口。

## Draft 与最终方案

### `POST /analysis/draft`

请求二选一：`{"conversationId":1}`，或兼容文本入口 `{"chatContent":"聊天原文"}`。不要同时依赖两者；有 `conversationId` 时优先使用它。返回完整 `DraftDTO`，供用户审核；此时不写 Knowledge/Relation/Evolution/Domain。

Draft 字段（JSON 名称大小写需照用）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `title`, `summary`, `sourceContent` | string | 标题、总结、分析用对话原文；提交方案时 `sourceContent` 不可擅改 |
| `conversation_id` | number 或 null | 来源对话 ID；有值时后端验证归属与 `sourceContent` 一致 |
| `request_id` | string | 后端产生的本份 Draft 唯一 ID，原样带到方案请求 |
| `knowledge_preview` | array | `{title,description,temp_id,domain_ids:number[],domain_temp_ids:string[]}` |
| `relation_preview` | array | `{description,knowledge_temp_ids:string[]}`，至少两个 Knowledge 引用 |
| `evolution_preview` | array | `{title,description,knowledge_temp_ids:string[]}`，至少一个引用 |
| `domain_preview` | array | `{id:number|null,name,description,temp_id:string|null}`；已有领域用 `id`，拟新建领域用 `temp_id` |

`knowledge_preview` 的 `temp_id` 供本 Draft 中 Relation/Evolution 引用；删除 Knowledge 时，前端也须处理引用它的 Relation/Evolution。`domain_ids` 是已有领域数据库 ID，`domain_temp_ids` 是 Draft 中拟新建领域临时 ID。列表可能为空；已有领域的 `temp_id` 实际曾返回空字符串。

成功响应（实测，字段形状与真实值；长文本缩写仅为文档展示，不可直接当请求体发送）：

```json
{
  "title":"天空与云的颜色成因",
  "summary":"本次聊天围绕光的散射现象展开……",
  "sourceContent":"[USER]\n为什么天空是蓝色的？\n\n[ASSISTANT]\n……",
  "conversation_id":2,
  "request_id":"e2c2fe63-00b3-4c8f-a6d8-66b1e6abf9a9",
  "knowledge_preview":[{"title":"天空呈蓝色的原因","description":"……","temp_id":"knowledge_temp_1","domain_ids":[3],"domain_temp_ids":[]}],
  "relation_preview":[{"description":"……","knowledge_temp_ids":["knowledge_temp_1","knowledge_temp_2"]}],
  "evolution_preview":[],
  "domain_preview":[{"id":3,"name":"自然与物理常识","description":"日常自然现象背后的物理、化学等基础科学原理","temp_id":""}]
}
```

如需可直接用于本地联调的**完整真实 Draft**，本次用户还实际收到过以下响应。注意 `sourceContent` 来自 PDF，部分兼容汉字与换行是原始响应的一部分，提交方案时必须原样保留：

```json
{
  "title":"天空与云的颜色成因",
  "summary":"本次聊天围绕光的散射现象展开：阳光穿过大气时，波长较短的蓝光更容易被空气分子散射，因此晴天天空呈蓝色；云中的小水滴和冰晶会把阳光中各种颜色一起散射并混合，因此云呈白色，云层过厚时阳光不易穿透，底部显得灰暗。",
  "sourceContent":"[USER]\n请⽤简洁、有趣的⽅式分享⼀个不涉及隐私的知识点：为什么天空通\n常看起来是蓝⾊的？控制在120字以内。\n\n[ASSISTANT]\n阳光⾥藏着各种颜⾊。穿过⼤⽓时，波⻓较短的蓝光更容易被空⽓分⼦散射，向四⾯⼋⽅跑进\n我们的眼睛，所以晴天的天空看起来是蓝⾊的。\n\n[USER]\n那为什么云看起来是⽩⾊的\n\n[ASSISTANT]\n云⾥有许多⼩⽔滴和冰晶。它们会把阳光中的各种颜⾊⼀起散射出来，混在⼀起进⼊我们的眼\n睛，就显得⽩啦。云太厚时，阳光不容易穿透，底部就会看起来灰灰的。",
  "conversation_id":1,
  "request_id":"6691fea5-2267-4343-8772-3c9b9c059526",
  "knowledge_preview":[
    {"title":"天空呈蓝色的原因","description":"阳光包含各种颜色，穿过大气时波长较短的蓝光更容易被空气分子散射，向四面八方进入眼睛，所以晴天的天空看起来是蓝色。","temp_id":"knowledge_temp_1","domain_ids":[3],"domain_temp_ids":[]},
    {"title":"云呈白色或灰色的原因","description":"云中的小水滴和冰晶会把阳光中各种颜色一起散射并混合，进入眼睛后显得白；云太厚时阳光不易穿透，底部就会看起来灰暗。","temp_id":"knowledge_temp_2","domain_ids":[3],"domain_temp_ids":[]}
  ],
  "relation_preview":[{"description":"天空呈蓝色与云呈白色或灰色都源于光的散射原理，区别在于散射主体不同：空气分子选择性散射短波蓝光，云中的水滴和冰晶则混合散射多种颜色的光。","knowledge_temp_ids":["knowledge_temp_1","knowledge_temp_2"]}],
  "evolution_preview":[{"title":"从天空颜色扩展到云的颜色","description":"用户在理解天空呈蓝色（空气分子散射蓝光）的基础上，进一步追问云为何呈白色，将同一散射原理扩展到水滴和冰晶的混合散射，形成对光的散射现象更完整的认知。","knowledge_temp_ids":["knowledge_temp_1","knowledge_temp_2"]}],
  "domain_preview":[{"id":3,"name":"自然与物理常识","description":"日常自然现象背后的物理、化学等基础科学原理","temp_id":""}]
}
```

### `POST /analysis/draft/plans`

请求：`{"planRequestId":"新 UUID","draft":<用户审核后的完整 DraftDTO>}`。即使用户没改 Draft，也必须走此接口；旧 `/analysis/draft/commit` 不可作为“没改”的捷径。生成方案时调用第二次 AI，但不写 Knowledge/Relation/Evolution/Domain/领域摘要；数据库会保存 `planned` Draft 与方案快照。

成功响应：`{planId:number,status:string,expiresAt:string,changes:{knowledge:[],relations:[],evolutions:[],domains:[],domainSummaries:[]}}`。`changes` 精确字段如下：

| 数组 | 每项外层字段 | `before`/`after` 字段 |
|---|---|---|
| `knowledge` | `operation:"CREATE"|"UPDATE"`, `ref:string`, `before:object|null`, `after:object` | `before:{id,title,description,domainIds:number[],domainNames:string[]}`；`after:{title,description,domainIds:number[],domainTempIds:string[],domainNames:string[]}` |
| `relations` | `operation:"CREATE"|"UPDATE"`, `ref:string|null`, `before:object|null`, `after:object` | `before:{id,description,knowledgeIds:number[],knowledgeTitles:string[]}`；`after:{description,knowledge:string[],knowledgeIds:number[],knowledgeTempIds:string[]}` |
| `evolutions` | `operation:"CREATE"`, `ref:null`, `before:null`, `after:object` | `after:{eventType:"NEW"|"EXPANDED"|"REVISED"|"CORRECTED",title,content,knowledge:string[],knowledgeIds:number[],knowledgeTempIds:string[]}` |
| `domains` | `operation:"CREATE"|"USE"`, `ref:string`, `before:null`, `after:object` | `CREATE` 的 `after:{name,description}`、`ref` 为新领域 temp_id；`USE` 的 `after:{id,name,description}`、`ref:"existing:<id>"`。`USE` 不表示修改已有 Domain |
| `domainSummaries` | 无 `operation/ref/before/after` 外层包装 | `{domainRef:"existing:<id>"|"new:<temp_id>",domainName,before:string|null,after:string}` |

`knowledge` 的 `CREATE.ref` 是新 Knowledge 的原始 temp_id；`UPDATE.ref` 为 `existing:<id>`。`relations.CREATE.ref`、所有 `evolutions.ref` 是 `null`。`after.knowledge` 是供界面展示的标题列表；真正引用仍看 `knowledgeIds/knowledgeTempIds`。`domainIds/domainTempIds` 与 `knowledgeIds/knowledgeTempIds` 均为**最终引用**，不要把它们误当新增差异。新建项在方案阶段没有真实数据库 ID。

非空成功响应节选（实测 planId 1；仅截取代表项，不是可完整提交的响应）：

```json
{
  "planId":1,"status":"READY","expiresAt":"2026-09-15T00:57:17.818147",
  "changes":{
    "knowledge":[{"operation":"UPDATE","ref":"existing:9","before":{"id":9,"title":"天空呈蓝色的原因","description":"……因此晴天天空看起来是蓝色的。","domainIds":[3],"domainNames":["自然与物理常识"]},"after":{"title":"天空呈蓝色的原因","description":"……所以晴天的天空看起来是蓝色的。","domainIds":[3],"domainTempIds":[],"domainNames":["自然与物理常识"]}}],
    "relations":[],"evolutions":[],
    "domains":[{"operation":"USE","ref":"existing:3","before":null,"after":{"id":3,"name":"自然与物理常识","description":"日常自然现象背后的物理、化学等基础科学原理"}}],
    "domainSummaries":[{"domainRef":"existing:3","domainName":"自然与物理常识","before":"旧摘要……","after":"新摘要……"}]
  }
}
```

空变更成功响应（实测，完整）：

```json
{"planId":2,"status":"READY","expiresAt":"2026-09-15T01:21:02.235269","changes":{"knowledge":[],"relations":[],"evolutions":[],"domains":[],"domainSummaries":[]}}
```

### `GET /analysis/draft/plans/{planId}`

无 Body。成功响应与 `POST /analysis/draft/plans` 完全同形；`status` 会实时显示 `READY/EXPIRED/STALE/APPLIED`。`GET` 未在此任务实测；字段由同一 `IntegrationPlanResponse` 代码确定。方案不存在或非当前用户：当前实现为 400，而非 404。

### `POST /analysis/draft/plans/{planId}/confirm`

无 Body。只有用户审核并接受最终方案后调用；事务中一次性执行全部认知变更，重复点击同一 `planId` 幂等。响应为 `CommitDraftResponse`：

```json
{
  "draftId":11,
  "status":"completed",
  "cognitiveUpdate":{
    "knowledge_updates":[{"id":null,"temp_id":"knowledge_temp_1","title":"……","description":"……","domain_ids":[3],"domain_temp_ids":[]}],
    "relation_updates":[{"id":null,"description":"……","knowledge_ids":[],"knowledge_temp_ids":["knowledge_temp_1","knowledge_temp_2"]}],
    "evolution_updates":[{"title":"……","content":"……","event_type":"NEW","knowledge_ids":[],"knowledge_temp_ids":["knowledge_temp_1","knowledge_temp_2"]}]
  }
}
```

上例是**代码结构示例，不是本次新确认接口的实测响应**。其中更新数组中的 `id:null` 仍表示 AI 方案里“计划新建”，**不是入库后的真实 ID**；不要用此响应中的 `id` 更新前端实体缓存。用户这次实际确认的 planId 2 是空变更：按代码应返回 `cognitiveUpdate` 的三个数组均为空，Draft/方案状态会更新，但知识库内容不变；具体响应尚未贴到此任务。

### `POST /analysis/draft/commit`

旧接口已停用，正常 Draft 请求会返回 **410 Gone**，错误 `message` 为“旧接口已停用：请先生成最终方案，再由用户确认执行”。前端不要调用。

## Domain

领域首页和详情查询已于 2026-09-14 补充。首版无分页，详情返回完整列表；知识标签以 `knowledge_node.updated_at` 倒序（为空时用 `created_at` 兜底）、相同时间按 Knowledge ID 倒序，取前 4 条。它代表“最近修改的知识”，不是用户最近点击/浏览的时间。

### `GET /domains`

**直接返回数组**，不是 `{data:[...]}`。按 `id` 升序；已验证的单账号会话对应数据库用户 1。每项是数据库实体的 JSON：

```json
[
  {
    "id":3,
    "userId":1,
    "name":"自然与物理常识",
    "description":"日常自然现象背后的物理、化学等基础科学原理",
    "cognitiveSummary":"该领域围绕自然与物理常识……",
    "createdAt":"2026-09-13T19:00:00",
    "updatedAt":"2026-09-14T01:30:00",
    "recentKnowledge":[{"id":9,"title":"天空呈蓝色的原因"}]
  }
]
```

这是**代码确定的结构 + 示例值**，不是实时抓取的完整数据。无领域时返回 `[]`；领域无知识时 `recentKnowledge:[]`。`recentKnowledge` 最多 4 项，其 `id` 是可用于跳转知识详情的真实数据库 ID。`cognitiveSummary` 为普通字符串或 `null`，**不是对象/数组**；新建但尚未形成摘要的领域通常为 `null`。

### `GET /domains/{domainId}/cognition`

按已验证会话对应的用户 ID 1 查询该领域；领域不存在或属于其他用户时返回 **404**，错误体沿用共通 `ApiErrorResponse`，`message` 为 `领域不存在`。无知识的领域返回 `knowledge:[]`、`relations:[]`。首版无分页。

- `domain`：原 Domain 对象，含 `id,userId,name,description,cognitiveSummary,createdAt,updatedAt`，不带 `recentKnowledge`。
- `knowledge`：该领域全部 Knowledge，按更新时间倒序。每项 `{id,title,description,domainIds:number[],updatedAt:string|null}`。`domainIds` 包含此知识所属的当前用户全部领域 ID，升序排列。
- `relations`：凡是**至少关联一条本领域 Knowledge** 的当前用户 Relation 都返回，按 Relation ID 升序。每项 `{id,description,knowledge:[{id,title,inCurrentDomain:boolean}]}`；内部 `knowledge` 按 ID 升序，包含该 Relation 关联的当前用户全部 Knowledge，包括领域外的知识。跨用户关联脏数据不会暴露。

成功结构示例（测试数据生成，非用户运行库的实时响应）：

```json
{
  "domain":{"id":1,"userId":1,"name":"Java 后端开发","description":"服务端架构","cognitiveSummary":"该领域的整体认知……","createdAt":"2026-09-13T15:04:15","updatedAt":"2026-09-14T03:00:00"},
  "knowledge":[{"id":5,"title":"构造器注入的优势","description":"依赖关系更明确，也更利于测试。","domainIds":[1,2],"updatedAt":"2026-09-13T15:04:15"}],
  "relations":[{"id":8,"description":"构造器注入提升可测试性。","knowledge":[{"id":5,"title":"构造器注入的优势","inCurrentDomain":true},{"id":9,"title":"可测试性","inCurrentDomain":false}]}]
}
```

空领域结构示例（测试断言确认空数组）：

```json
{"domain":{"id":3,"userId":1,"name":"空领域","description":null,"cognitiveSummary":null,"createdAt":null,"updatedAt":null},"knowledge":[],"relations":[]}
```

自动化测试已验证：`GET /domains` 只给用户 1 的领域和最多 4 条近期知识；详情包含跨领域 Relation 及 `inCurrentDomain`；不会返回其他用户的领域、知识或 Relation；空列表为 `[]`；不存在/非当前用户领域为 404。此测试使用隔离的内存数据库，不是用户真实库。

### `POST /domains`

请求 `{"name":"Java 服务端开发","description":"可选说明"}`；成功直接返回上述单个 Domain 对象（代码结构示例）。同名已有领域时后端返回已有对象，不再插入。`name` 必填。

### `PUT /domains/{domainId}`

请求同创建；成功直接返回修改后的单个 Domain 对象（代码结构示例）。同名冲突、无权操作或不存在时当前实现为 400。

### `DELETE /domains/{domainId}`

无 Body；成功为 **200 且空响应体**（控制器返回 `void`，未显式设 204）。已关联 Knowledge 的领域不能删除，返回 400。

## 错误与前端处理

| 场景 | 当前状态 | `message`/处理 |
|---|---:|---|
| 未登录访问任一业务接口，含 GET 或写入 | 401 | `请先登录`；先获取 CSRF 令牌并登录 |
| 已登录但写请求缺少/使用错误的 CSRF 令牌 | 403 | `请求缺少有效的 CSRF 令牌或无权访问`；调用 `GET /auth/csrf` 后重试 |
| 会话过期后访问业务接口 | 401 | `请先登录`；`GET /auth/me` 会返回 `authenticated:false` |
| Draft 对话来源与原文不一致 | 400 | 实测：`Draft 的对话来源与原始内容不一致，请重新生成 Draft`；保留 `/draft` 原 `sourceContent` |
| `@Valid` 字段缺失/空白 | 400 | 错误结构相同，`message` 是首个校验错误，如 `Draft 标题不能为空` |
| PDF 内容不合法、不是分享页、无法识别消息、超过解析器 15,000,000 字节、超 200 页 | 400 | 解析器抛出相应中文 `IllegalArgumentException`，同一错误结构 |
| HTTP 上传在进入 PDF 解析器前超过 Spring multipart 限制 | **未确认** | 代码未专门处理 `MaxUploadSizeExceededException`；可能被通用 500 处理，不能向前端承诺稳定的 413/400；前端先自行限制 15,000,000 字节 |
| 方案过期 | 409 | `方案已过期，请重新生成`；返回 Draft 修改页，生成新 `planRequestId` |
| 生成方案或确认期间认知库变化 | 409 | `生成方案期间认知库发生变化，请重新生成方案` / `生成摘要期间认知库发生变化，请重新生成方案` / `认知库已变化，方案过期，请重新生成并审核` |
| 同一 Draft 已由另一方案完成沉淀 | 409 | `这份 Draft 已由另一份方案完成沉淀`；勿重新执行旧方案 |
| 重复确认**同一个已成功 planId** | 200 | 幂等返回首次确认的 `CommitDraftResponse`，不是错误 |
| 旧 `/analysis/draft/commit` | 410 | 旧接口停用消息 |
| 方案 ID 不存在或不属于当前用户 | 400 | `方案不存在或不属于当前用户`；当前并非 404 |
| 未预料异常 | 500 | `服务器处理请求失败`，其他细节只记服务端日志 |

409 响应体代码示例：

```json
{"timestamp":"2026-09-14T01:30:00","status":409,"error":"Conflict","message":"方案已过期，请重新生成","path":"/analysis/draft/plans/2/confirm"}
```

## 跨域

`application.yml` 默认 `app.cors.allowed-origins` 为 `http://localhost:5173,http://localhost:3000`；`WebConfig` 对所有路径允许 `GET/POST/PUT/DELETE/OPTIONS`、任意请求头、`allowCredentials(true)`。针对性测试已通过 `localhost:5173` 的凭据预检。实际启动时可被 `CORS_ALLOWED_ORIGINS` 环境变量覆盖；`http://127.0.0.1:5173` 与 `http://localhost:5173` 不同，默认未列入。前端请求必须带 `credentials: "include"`，写请求还必须带 `X-CSRF-TOKEN`。本地 HTTP 后端需设置 `SESSION_COOKIE_SECURE=false`；正式环境保持默认 `true` 并使用 HTTPS。

## 前端实现注意

1. 最终审核页只依据 `changes` 展示**最终拟入库变化**，不能把第一次 Draft 当最终结果；第二次 AI 可能返回空变更，也可能新增 Draft 里没有的内容。
2. `changes` 中的 `before/after` 是审阅用完整内容，建议默认显示变更摘要，展开后显示全文；`domains.USE` 只是引用已有领域，非编辑。
3. 方案只读。用户不同意最终方案时返回 Draft 编辑并创建新方案；不要直接编辑 `changes` 后调用确认接口，确认接口只收 `planId`。
4. `GET /domains` 的领域摘要键是 `cognitiveSummary`（camelCase）；Draft 中的 `domain_preview`、`domain_ids` 等是 snake_case。`changes` 再次使用 camelCase，避免混用。
5. 当前只有一个经服务端会话验证的演示账号，映射到原有用户 1；没有注册、多账号管理或跨后端实例共享会话。

## 尚缺的真实样本

当前会话确有 PDF 导入、Draft、方案非空/空变更的用户实测响应；但未提供当前新版 `confirm`、`GET /domains`、`GET /domains/{domainId}/cognition`、`POST/PUT/DELETE /domains`、文本/消息导入与 GET 方案的完整实时响应。本任务当前也无法连接 `localhost:8080`。上述接口的字段和状态均已按代码列出，但需要用户或前端在实际联调时补抓响应，尤其是上传超出 Spring multipart 限制的实际状态码；文档没有把代码示例伪装成实测。

## v1.0 演化记录查询（2026-09-14）

### `GET /evolutions?domainId={可选}&page=1&size=20&order=desc`

只读接口，不调用 AI、不修改认知数据。需登录；已验证单账号会话对应用户 1。`domainId` 省略时查询该用户全部 Evolution；传入时，仅返回**当前**至少关联了该领域一条 Knowledge 的 Evolution。一条 Evolution 可同时出现在多个领域视图中，数据库仍只有一条记录。

| 参数 | 类型/默认值 | 规则 |
|---|---|---|
| `domainId` | 可选 number | 领域不存在或不属于当前用户：404 |
| `page` | integer，默认 1 | 从 1 开始；必须大于 0 |
| `size` | integer，默认 20 | 1–100 |
| `order` | `asc`/`desc`，默认 `desc` | `asc`：`createdAt, stepOrder, id` 全部升序；`desc`：全部降序。即使时间相同，顺序也稳定 |

响应为 `{items:EvolutionItem[],page:number,size:number,hasMore:boolean}`，无数据时 `items:[]`、`hasMore:false`。首版未返回 `total`。每个 `EvolutionItem` 有 `id,title,content,eventType,createdAt,stepOrder,knowledge,domains`：

- `eventType` 沿用 `NEW / EXPANDED / REVISED / CORRECTED`；`content` 为数据库中保存的原文，没有“变更前/变更后”字段。
- `knowledge` 为该 Evolution 关联的当前用户 Knowledge，结构 `{id,title}`，按 ID 升序；无关联时 `[]`。
- `domains` 为这些 Knowledge **当前**所属的当前用户领域去重集合，结构 `{id,name}`，按 ID 升序；无关联时 `[]`。知识日后改换领域，旧 Evolution 的领域展示归属也会随之变化；首版没有历史快照。
- `createdAt` 与其他接口一样是不带时区的本地日期时间字符串。

实际成功响应（针对性集成测试通过真实 Controller/Mapper、隔离 H2 数据库得到；**不是用户 MySQL 运行库的样本**）：

```json
{"items":[{"id":10,"title":"First","content":"Original text","eventType":"NEW","createdAt":"2026-09-14T11:30:00","stepOrder":1,"knowledge":[{"id":5,"title":"Sky"},{"id":6,"title":"Clouds"}],"domains":[{"id":1,"name":"Physics"},{"id":2,"name":"Software"}]}],"page":1,"size":20,"hasMore":false}
```

错误响应沿用文档开头的 `ApiErrorResponse`：

- `domainId` 不存在或属于其他用户：404，`message:"领域不存在"`，`path:"/evolutions"`。
- `page<1`、`size<1` 或 `size>100`：400，`message:"page 必须大于 0，size 必须在 1 到 100 之间"`。
- `order` 不是小写 `asc`/`desc`：400，`message:"order 只能是 asc 或 desc"`。
- 页码超过末页：200，`{"items":[],"page":原页码,"size":请求大小,"hasMore":false}`。

针对性测试覆盖全局和领域筛选、跨领域归属、当前用户隔离、空结果、翻页及同时间稳定排序；未进行本次任务以外的全项目检查。
