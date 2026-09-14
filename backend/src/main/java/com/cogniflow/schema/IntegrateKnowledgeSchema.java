package com.cogniflow.schema;

public class IntegrateKnowledgeSchema {

    public static final String JSON_SCHEMA = """
{
  "knowledge_updates": [
    {
      "id": null,
      "temp_id": "",
      "title": "",
      "description": "",
      "domain_ids": [],
      "domain_temp_ids": []
    }
  ],
  "relation_updates": [
    {
      "id": null,
      "description": "",
      "knowledge_ids": [],
      "knowledge_temp_ids": []
    }
  ],
  "evolution_updates": [
    {
      "title": "",
      "content": "",
      "event_type": "",
      "knowledge_ids": [],
      "knowledge_temp_ids": []
    }
  ]
}
""";

    private IntegrateKnowledgeSchema() {
    }
}
