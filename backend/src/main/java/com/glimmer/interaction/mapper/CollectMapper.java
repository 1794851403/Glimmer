package com.glimmer.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.interaction.entity.CollectDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * t_collect 的 Mapper；收藏关系只由 interaction 模块写入。
 */
@Mapper
public interface CollectMapper extends BaseMapper<CollectDO> {

    /**
     * 插入收藏关系；唯一键冲突时忽略并返回 0。
     *
     * @param userId 收藏用户 ID
     * @param postId 帖子 ID
     * @return 1=新增成功，0=已存在
     */
    @Insert("INSERT IGNORE INTO t_collect (user_id, post_id) VALUES (#{userId}, #{postId})")
    int insertIgnore(@Param("userId") Long userId, @Param("postId") Long postId);
}
