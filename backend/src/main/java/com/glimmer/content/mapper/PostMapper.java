package com.glimmer.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.content.entity.PostDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * t_post 的 Mapper；写入口归 content 模块所有。
 */
@Mapper
public interface PostMapper extends BaseMapper<PostDO> {
}
