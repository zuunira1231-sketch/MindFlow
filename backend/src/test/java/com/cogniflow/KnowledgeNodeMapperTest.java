package com.cogniflow;

import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.mapper.KnowledgeNodeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class KnowledgeNodeMapperTest {
    @Autowired
    private KnowledgeNodeMapper knowledgeNodeMapper;

    @Test
    void testInsert() {
        KnowledgeNode knowledgeNode = new KnowledgeNode();

        knowledgeNode.setUserId(1L);
        knowledgeNode.setTitle("SpringBoot");
        knowledgeNode.setDescription("SpringBoot 是用于简化 Spring 应用开发的框架。");

        int result = knowledgeNodeMapper.insert(knowledgeNode);

        System.out.println("插入结果：" + result);
        System.out.println("生成的ID：" + knowledgeNode.getId());
    }
}
