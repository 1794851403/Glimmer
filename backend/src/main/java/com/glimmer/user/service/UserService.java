package com.glimmer.user.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.user.dto.UpdateProfileDTO;
import com.glimmer.user.vo.FollowResultVO;
import com.glimmer.user.vo.FollowUserVO;
import com.glimmer.user.vo.SelfProfileVO;
import com.glimmer.user.vo.UserBriefVO;
import com.glimmer.user.vo.UserProfileVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户模块对外的服务：本人资料、公开主页、作者简要信息与关注关系查询。
 *
 * <p>Service 接口是模块对外的事实 Facade，跨模块调用只依赖接口；
 * 具体实现见 {@link com.glimmer.user.service.impl.UserServiceImpl}。
 */
public interface UserService {

    /**
     * 查看当前登录用户的完整资料。
     *
     * <p>返回全部可编辑字段，前端既用于个人主页展示，也用于编辑页预填。
     *
     * @return 当前用户的本人资料
     */
    SelfProfileVO getMyProfile();

    /**
     * 修改当前登录用户的资料。
     *
     * <p>局部更新：未提供的字段保持不变；{@code bio} 传空字符串表示清空。
     * 成功后重新查询并返回完整资料，调用方无需再发 GET。
     *
     * @param request 待修改字段，至少一个
     * @return 修改后的完整本人资料
     */
    SelfProfileVO updateMyProfile(UpdateProfileDTO request);

    /**
     * 查询用户公开主页。
     *
     * <p>只返回公开资料；用户不存在、ID 非法或已封禁统一返回 40401。
     * 可选登录：有登录态时补 {@code isFollowing} 与 {@code isMe}。
     *
     * @param rawUserId 用户 ID，允许数字字符串
     * @return 用户公开主页
     */
    UserProfileVO getPublicProfile(String rawUserId);

    /**
     * 解析并校验公开可见用户，返回用户 ID。
     *
     * <p>供 content 模块查询用户公开帖子时复用，避免跨模块直接访问用户表。
     *
     * @param rawUserId 用户 ID，允许数字字符串
     * @return 用户 ID
     */
    Long resolvePublicUserId(String rawUserId);

    /**
     * 查询单个用户简要信息，供帖子详情等跨模块组装使用。
     *
     * @param userId 用户 ID
     * @return 用户简要信息；用户不存在时返回 {@code null}
     */
    UserBriefVO getBrief(Long userId);

    /**
     * 批量查询用户简要信息，避免列表接口 N+1 查询。
     *
     * @param userIds 用户 ID 集合，允许为空或重复
     * @return 以用户 ID 为键的简要信息；不存在的用户不出现在结果中
     */
    Map<Long, UserBriefVO> getBriefs(Collection<Long> userIds);

    /**
     * 判断 follower 是否关注了 followee。
     *
     * @param followerId 关注者
     * @param followeeId 被关注者
     * @return 已关注返回 {@code true}；自己关注自己或参数为空返回 {@code false}
     */
    boolean isFollowing(Long followerId, Long followeeId);

    /**
     * 查询某个用户关注的所有用户 ID。
     *
     * @param followerId 关注者
     * @return 被关注用户 ID 列表；未关注任何人时返回空列表
     */
    List<Long> listFollowingIds(Long followerId);

    /**
     * 按 follower_id 升序批量查询某个作者的粉丝，供 Feed 写扩散使用。
     *
     * <p>内部方法，不对外暴露 HTTP 接口。首批 {@code afterFollowerId} 传 0，
     * 后续传上一批返回的最后一个 ID。
     *
     * @param followeeId      被关注者 ID
     * @param afterFollowerId 上一批最后一个粉丝 ID，可为空
     * @param limit           批大小
     * @return 粉丝 ID 列表；没有更多时返回空列表
     */
    List<Long> listFollowerIds(Long followeeId, Long afterFollowerId, int limit);

    /**
     * 关注用户。
     *
     * <p>需登录；不能关注自己；重复关注幂等。关注成功后同步维护双方计数。
     *
     * @param userId 目标用户 ID，允许数字字符串
     * @return 目标用户 ID、当前关注状态与目标用户粉丝数
     */
    FollowResultVO follow(String userId);

    /**
     * 取关用户。
     *
     * <p>需登录；不能取关自己；重复取关幂等。
     *
     * @param userId 目标用户 ID，允许数字字符串
     * @return 目标用户 ID、当前关注状态与目标用户粉丝数
     */
    FollowResultVO unfollow(String userId);

    /**
     * 分页查询粉丝列表，按关注关系创建时间倒序。
     *
     * <p>可选登录；登录时每条记录带 {@code isFollowing}。坏游标按首页处理。
     *
     * @param userId 目标用户 ID，允许数字字符串
     * @param cursor 上一页返回的 nextCursor，可为空
     * @param size   每页数量，默认 20，越界自动夹到 1~50
     * @return 用户简要信息游标页
     */
    CursorPage<FollowUserVO> listFollowers(String userId, String cursor, Integer size);

    /**
     * 分页查询关注列表，按关注关系创建时间倒序。
     *
     * <p>可选登录；登录时每条记录带 {@code isFollowing}。坏游标按首页处理。
     *
     * @param userId 目标用户 ID，允许数字字符串
     * @param cursor 上一页返回的 nextCursor，可为空
     * @param size   每页数量，默认 20，越界自动夹到 1~50
     * @return 用户简要信息游标页
     */
    CursorPage<FollowUserVO> listFollowing(String userId, String cursor, Integer size);
}
