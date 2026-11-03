package com.glimmer.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.interaction.entity.CommentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * t_comment 的 Mapper；评论关系只由 interaction 模块写入。
 */
@Mapper
public interface CommentMapper extends BaseMapper<CommentDO> {

    /**
     * 批量统计当前页根评论下未删除的二级回复数。
     *
     * @param postId    帖子 ID
     * @param parentIds 当前页根评论 ID 集合，不能为空
     * @return 每个根评论一行；没有回复的根评论不出现在结果中
     */
    @Select("""
            <script>
            SELECT parent_id AS parentId, COUNT(*) AS replyCount
            FROM t_comment
            WHERE post_id = #{postId}
              AND is_deleted = 0
              AND parent_id IN
              <foreach collection="parentIds" item="parentId" open="(" separator="," close=")">
                #{parentId}
              </foreach>
            GROUP BY parent_id
            </script>
            """)
    List<CommentReplyCount> countVisibleReplies(@Param("postId") Long postId,
                                                @Param("parentIds") Collection<Long> parentIds);
}
