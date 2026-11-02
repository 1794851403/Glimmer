package com.glimmer.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.interaction.entity.LikeDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * t_like 的 Mapper；点赞关系只由 interaction 模块写入。
 */
@Mapper
public interface LikeMapper extends BaseMapper<LikeDO> {

    /**
     * 插入点赞关系；唯一键冲突时忽略并返回 0。
     *
     * @param userId 点赞用户 ID
     * @param postId 帖子 ID
     * @return 1=新增成功，0=已存在
     */
    @Insert("INSERT IGNORE INTO t_like (user_id, post_id) VALUES (#{userId}, #{postId})")
    int insertIgnore(@Param("userId") Long userId, @Param("postId") Long postId);
}
