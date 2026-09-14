package com.cogniflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.mapper.KnowledgeNodeMapper;
import com.cogniflow.service.KnowledgeNodeService;
import org.springframework.stereotype.Service;

//这个实现类通过 KnowledgeNodeMapper 操作 KnowledgeNode
@Service
public class KnowledgeNodeServiceImpl
        extends ServiceImpl<KnowledgeNodeMapper, KnowledgeNode>
        implements KnowledgeNodeService {

}
