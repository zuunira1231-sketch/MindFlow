package com.cogniflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cogniflow.entity.IntegrationPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IntegrationPlanMapper extends BaseMapper<IntegrationPlan> {
    @Select("SELECT * FROM integration_plan WHERE id = #{id} AND user_id = #{userId} FOR UPDATE")
    IntegrationPlan selectOwnedForUpdate(@Param("id") Long id,
                                         @Param("userId") Long userId);
}
