# MindFlow v1.0

React/Vite 前端与 Java 17 / Spring Boot 后端。线上核心流程尚未完成验收。

## 目录

- `frontend/`：前端源码。
- `backend/`：后端源码与数据库脚本。
- `docs/API.md`：前后端接口说明。

## 本地运行

前端：进入 `frontend`，执行 `npm install`，设置本机 `.env.local` 中的 `VITE_API_BASE_URL=http://localhost:8080`，再执行 `npm run dev`。构建命令为 `npm run build`。

后端：需要 Java 17、Maven 和 MySQL。进入 `backend`，通过本机私密环境配置提供 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`AI_API_KEY`、`DEMO_USERNAME`、`DEMO_PASSWORD_HASH`，然后执行 `mvn spring-boot:run`。打包命令为 `mvn package`。

`DEMO_PASSWORD_HASH` 必须是 BCrypt 哈希，不提交真实凭据。本地 HTTP 联调需要 `SESSION_COOKIE_SECURE=false`；正式环境必须 HTTPS 且保持 `true`。默认 AI 服务为 DeepSeek，可通过 `AI_BASE_URL`、`AI_MODEL` 覆盖。

## 数据库

`backend/database/000_empty_database_schema.sql` 仅用于经确认的新空库，包含完整表结构，不含用户或业务数据。业务需要用户 ID 1 的基础记录。

`002`–`005` 为已有数据库的历史增量脚本；完整结构导入后不要重复执行。任何现有数据库的变更都需先核对实际结构。

## 核心流程

登录 → 文本/PDF 导入 → Draft 审核 → 生成最终方案 → 确认沉淀。生成最终方案只保存方案快照，确认后才写入认知数据。
