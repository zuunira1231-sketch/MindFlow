package com.cogniflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cogniflow.entity.Domain;

import java.util.List;

public interface DomainService extends IService<Domain> {

    /**
     * 查询某个用户的全部领域。
     *
     * 第一次 AI 分析前会调用它，
     * 用户查看领域列表时也会调用它。
     */
    List<Domain> listByUserId(Long userId);

    /**
     * 创建领域。
     *
     * 用户主动创建和用户确认 AI 建议后，
     * 都可以复用这个方法。
     */
    Domain createDomain(
            Long userId,
            String name,
            String description
    );

    /**
     * 修改当前用户的领域名称和说明。
     */
    Domain updateDomain(
            Long domainId,
            Long userId,
            String name,
            String description
    );

    /**
     * 删除当前用户尚未关联 Knowledge 的空领域。
     */
    void deleteDomain(Long domainId, Long userId);
}
