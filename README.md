# AgentCopilot

后端编排服务，支持任务规划、飞书文档/PPT 生成与预览。

## 预览接口

### 文档预览
`POST /api/v1/previews/document`

请求示例：
```json
{
  "userInput": "请生成一份智能客服方案",
  "docType": "需求文档"
}
```

### PPT 预览
`POST /api/v1/previews/presentation`

请求示例：
```json
{
  "userInput": "请生成一份年度经营汇报",
  "topic": "年度经营汇报"
}
```

### 返回原则
- 预览接口优先返回结构化 JSON，方便前端做块级/页级编辑。
- 同时保留 `rawMarkdown`，兼容后续导出飞书文档/PPT。


