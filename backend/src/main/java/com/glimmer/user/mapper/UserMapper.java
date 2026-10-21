package com.glimmer.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.user.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * t_user 的写入口归 user 模块所有，其他模块只能调用 user 的 Service。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
