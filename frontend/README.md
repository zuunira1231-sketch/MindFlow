# MindFlow 前端

## 启动

```bash
npm install
npm run dev
```

默认连接 `http://localhost:8080`。如需修改，在 `frontend/.env.local` 中设置：

```text
VITE_API_BASE_URL=http://localhost:8080
```

## 当前范围

- 领域首页读取 `GET /domains`，展示名称和 `cognitiveSummary`。
- 文本或 PDF 导入后，使用 `conversationId` 请求第一次 Draft。
- 审核完整 Draft，生成最终方案，确认后刷新领域。
- 最终审核地址包含 `planId`，刷新时调用查询接口恢复方案。

领域内知识标签及完整详情需要后端提供查询接口；当前不会显示模拟的用户知识。

草稿只保存在当前页面内存中，关闭审核层后可继续；刷新浏览器会丢失草稿。最终方案可通过地址中的 `planId` 恢复，但刷新后如果方案失效，需要重新导入对话。
