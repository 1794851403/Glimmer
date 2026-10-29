package com.glimmer.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.content.entity.PostTagDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * t_post_tag 的 Mapper。
 */
@Mapper
public interface PostTagMapper extends BaseMapper<PostTagDO> {
}
