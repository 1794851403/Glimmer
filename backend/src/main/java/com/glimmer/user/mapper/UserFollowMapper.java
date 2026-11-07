package com.glimmer.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.user.entity.UserFollowDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * t_user_follow 的 Mapper。
 */
@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollowDO> {

    /**
     * 按 follower_id 升序批量查询某个作者的粉丝，供 Feed 写扩散分页使用。
     *
     * <p>同一作者下 follower_id 唯一，因此用 follower_id 作为游标不会重复；
     * 粉丝量较大时只做有界查询，避免一次把全部粉丝读进内存。
     *
     * @param followeeId     被关注者 ID
     * @param afterFollowerId 上一批最后一个 follower_id，首批传 0
     * @param limit           批大小
     * @return 粉丝用户 ID 列表
     */
    @Select("""
            SELECT follower_id
            FROM t_user_follow
            WHERE followee_id = #{followeeId}
              AND follower_id > #{afterFollowerId}
            ORDER BY follower_id ASC
            LIMIT #{limit}
            """)
    List<Long> listFollowerIds(@Param("followeeId") Long followeeId,
                               @Param("afterFollowerId") Long afterFollowerId,
                               @Param("limit") int limit);
}
