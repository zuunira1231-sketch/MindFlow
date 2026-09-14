package com.cogniflow.controller;

import com.cogniflow.config.CurrentUser;
import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.service.KnowledgeNodeService;
import com.cogniflow.mapper.CognitiveRevisionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeNodeController {

    @Autowired
    private KnowledgeNodeService knowledgeNodeService;

    @Autowired
    private CognitiveRevisionMapper cognitiveRevisionMapper;

    @PostMapping
    @Transactional
    public String create(@RequestBody KnowledgeNode knowledgeNode) {
        Long userId = CurrentUser.id();
        cognitiveRevisionMapper.ensure(userId);
        cognitiveRevisionMapper.lock(userId);
        knowledgeNode.setId(null);
        knowledgeNode.setUserId(userId);
        boolean result = knowledgeNodeService.save(knowledgeNode);
        if (result) cognitiveRevisionMapper.bump(userId);

        return result ? "创建成功" : "创建失败";
    }

    @GetMapping("/{id}")
    public KnowledgeNode getById(@PathVariable Long id) {
        KnowledgeNode node = knowledgeNodeService.getById(id);
        if (node == null || !CurrentUser.id().equals(node.getUserId())) {
            throw new IllegalArgumentException("Knowledge 不存在或无权访问");
        }
        return node;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public String delete(@PathVariable Long id) {
        Long userId = CurrentUser.id();
        cognitiveRevisionMapper.ensure(userId);
        cognitiveRevisionMapper.lock(userId);
        getById(id);
        boolean result = knowledgeNodeService.removeById(id);
        if (result) cognitiveRevisionMapper.bump(userId);

        return result ? "删除成功" : "删除失败";
    }

    @PutMapping("/{id}")
    @Transactional
    public String update(@PathVariable Long id,
                         @RequestBody KnowledgeNode knowledgeNode) {
        Long userId = CurrentUser.id();
        cognitiveRevisionMapper.ensure(userId);
        cognitiveRevisionMapper.lock(userId);
        getById(id);
        knowledgeNode.setId(id);
        knowledgeNode.setUserId(userId);

        boolean result = knowledgeNodeService.updateById(knowledgeNode);
        if (result) cognitiveRevisionMapper.bump(userId);

        return result ? "修改成功" : "修改失败";
    }
}
