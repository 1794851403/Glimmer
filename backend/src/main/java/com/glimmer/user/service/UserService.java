package com.glimmer.user.service;

import com.glimmer.user.dto.UpdateProfileDTO;
import com.glimmer.user.vo.SelfProfileVO;
import com.glimmer.user.vo.UserBriefVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户模块对外的服务：本人资料、作者简要信息与关注关系查询。
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
}
