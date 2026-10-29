package com.glimmer.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.content.entity.TagDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * t_tag 的 Mapper。
 */
@Mapper
public interface TagMapper extends BaseMapper<TagDO> {
}
