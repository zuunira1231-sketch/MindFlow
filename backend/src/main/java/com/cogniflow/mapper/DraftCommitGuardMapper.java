package com.cogniflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DraftCommitGuardMapper {
    @Select("SELECT applied_plan_id FROM draft_commit_guard WHERE user_id=#{userId} "
            + "AND draft_request_id=#{requestId}")
    Long findAppliedPlanId(@Param("userId") Long userId,
                           @Param("requestId") String requestId);

    @Insert("INSERT INTO draft_commit_guard(user_id,draft_request_id,applied_plan_id) "
            + "VALUES(#{userId},#{requestId},#{planId})")
    void insert(@Param("userId") Long userId,
                @Param("requestId") String requestId,
                @Param("planId") Long planId);
}
