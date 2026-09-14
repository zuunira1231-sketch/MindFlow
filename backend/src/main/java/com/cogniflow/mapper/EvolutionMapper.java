package com.cogniflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cogniflow.entity.Evolution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EvolutionMapper
        extends BaseMapper<Evolution> {

    @Select("""
            <script>
            SELECT e.* FROM evolution e
            WHERE e.user_id = #{userId}
            <if test="domainId != null">
              AND EXISTS (
                SELECT 1 FROM evolution_knowledge ek
                JOIN knowledge_domain kd ON kd.knowledge_id = ek.knowledge_id
                JOIN knowledge_node kn ON kn.id = ek.knowledge_id AND kn.user_id = #{userId}
                WHERE ek.evolution_id = e.id AND kd.domain_id = #{domainId}
              )
            </if>
            <choose>
              <when test="ascending">
                ORDER BY e.created_at ASC, e.step_order ASC, e.id ASC
              </when>
              <otherwise>
                ORDER BY e.created_at DESC, e.step_order DESC, e.id DESC
              </otherwise>
            </choose>
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Evolution> selectHistoryPage(@Param("userId") Long userId,
                                      @Param("domainId") Long domainId,
                                      @Param("ascending") boolean ascending,
                                      @Param("limit") int limit,
                                      @Param("offset") long offset);
}
