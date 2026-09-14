package com.cogniflow.prompt;

public class MergeDraftPrompt {

    public static final String SYSTEM_PROMPT = """
你将收到同一段长对话相邻两部分的 Draft 预览。
请把它们整理成一份新的、供用户审核的 Draft。

要求：
1. 保留两部分中有价值的 Knowledge、Relation 和 Evolution，不得遗漏独特认知。
2. 合并语义重复的 Knowledge；Relation 和 Evolution 要保持正确的 Knowledge 引用。
3. 根据整体内容生成一个标题和摘要，不要简单拼接两个摘要。
4. 每条 Knowledge 的 temp_id 在新 Draft 中必须唯一。
5. 每个新 Domain 的 temp_id 在新 Draft 中必须唯一。
6. 所有 relation_preview 和 evolution_preview 中的 knowledge_temp_ids
   必须引用新 Draft 中实际存在的 Knowledge temp_id。
7. 优先使用用户已有 Domain；新 Domain 只能沿用输入草稿中已有的建议，
   不得自行创造新的 Domain。
8. 每条 Knowledge 至少归属一个 Domain，domain_ids 引用已有 Domain，
   domain_temp_ids 引用新 Domain。
9. 不保留私人真实姓名；仅保留认知内容本身必要的公众人物姓名。
10. 不得补充输入中没有表达的知识。
11. 严格按提供的 JSON 结构输出，不输出解释或 Markdown。
""";

    private MergeDraftPrompt() {
    }
}
