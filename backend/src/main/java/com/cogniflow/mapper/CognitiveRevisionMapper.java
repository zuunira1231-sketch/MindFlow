package com.cogniflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CognitiveRevisionMapper {
    @Insert("INSERT IGNORE INTO cognitive_revision(user_id, revision) VALUES(#{userId}, 0)")
    void ensure(@Param("userId") Long userId);

    @Select("SELECT revision FROM cognitive_revision WHERE user_id = #{userId}")
    Long current(@Param("userId") Long userId);

    @Select("SELECT revision FROM cognitive_revision WHERE user_id = #{userId} FOR UPDATE")
    Long lock(@Param("userId") Long userId);

    @Update("UPDATE cognitive_revision SET revision = revision + 1 WHERE user_id = #{userId}")
    void bump(@Param("userId") Long userId);
}
