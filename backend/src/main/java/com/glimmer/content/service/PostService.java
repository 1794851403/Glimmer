package com.glimmer.content.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.api.PageResult;
import com.glimmer.content.dto.CreatePostDTO;
import com.glimmer.content.vo.AdminPostSummaryVO;
import com.glimmer.content.vo.MyPostSummaryVO;
import com.glimmer.content.vo.PostAuditVO;
import com.glimmer.content.vo.PostCreateVO;
import com.glimmer.content.vo.PostDetailVO;
import com.glimmer.content.vo.PostSummaryVO;

import java.util.Collection;
import java.util.Map;

/**
 * 帖子内容模块对外的服务：发布、编辑、删除、重提、详情、列表与管理端审核。
 */
public interface PostService {

    /**
     * 发布一篇帖子。
     *
     * <p>登录用户可用；普通用户创建后为待审核状态，管理员跳过审核。
     * 帖子、图片、标签在同一事务内写入。
     *
     * @param request 标题、正文、图片与标签
     * @return 新帖子的 ID、状态与发布时间
     */
    PostCreateVO create(CreatePostDTO request);

    /**
     * 查看帖子详情。
     *
     * <p>游客和其他用户只能看公开可见帖子；作者本人可看自己的未删除帖子；
     * 管理员可看任意未删除帖子。审核原因与 AI 打标状态只对作者 / 管理员返回。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 帖子详情
     */
    PostDetailVO getDetail(String postId);

    /**
     * 解析并校验公开可见帖子，返回帖子 ID。
     *
     * <p>供 interaction 模块点赞、收藏、评论前复用；帖子不存在、ID 非法、已删除、
     * 状态不是 0/1 时统一返回 40402。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 帖子 ID
     */
    Long resolvePublicPostId(String postId);

    /**
     * 点赞关系变化后更新帖子点赞计数。
     *
     * <p>由 interaction 在写 {@code t_like} 成功后调用；{@code delta} 为 +1 / -1 / 0。
     * 0 表示关系没有变化（重复点赞 / 取消），只返回当前计数，不更新计数列。
     * 更新与查询在同一事务内完成，帖子不可见时返回 40402。
     *
     * @param postId 帖子 ID
     * @param delta  计数变化量
     * @return 更新后的点赞数
     */
    int onLikeChanged(Long postId, int delta);

    /**
     * 收藏关系变化后更新帖子收藏计数。
     *
     * <p>规则与 {@link #onLikeChanged(Long, int)} 一致，只是更新 {@code collect_count}。
     *
     * @param postId 帖子 ID
     * @param delta  计数变化量
     * @return 更新后的收藏数
     */
    int onCollectChanged(Long postId, int delta);

    /**
     * 评论关系变化后更新帖子评论计数。
     *
     * <p>由 interaction 在插入或软删除 {@code t_comment} 成功后调用；{@code delta} 为 +1 / -1 / 0。
     * 0 表示没有实际变化（重复删除），只返回当前计数，不更新计数列。
     * 新增评论要求帖子公开可见；删除评论只要求帖子行存在，避免帖子下架后无法清理评论。
     *
     * @param postId 帖子 ID
     * @param delta  计数变化量
     * @return 更新后的评论数
     */
    int onCommentChanged(Long postId, int delta);

    /**
     * 判断用户是否为帖子作者。
     *
     * <p>供 interaction 删除评论时校验“帖子作者”权限；帖子行存在且作者匹配即返回 true，
     * 不限制帖子当前状态，便于作者清理已下架 / 已删除帖子下的评论。
     *
     * @param postId 帖子 ID
     * @param userId 用户 ID
     * @return 是帖子作者返回 {@code true}
     */
    boolean isPostAuthor(Long postId, Long userId);

    /**
     * 按帖子 ID 批量查询公开可见帖子摘要。
     *
     * <p>供 interaction 的“我的点赞 / 我的收藏”按关系记录顺序回填帖子数据；
     * 只返回 {@code status IN (0,1) AND is_deleted=0} 的帖子，返回结果以帖子 ID 为键。
     * 当前登录用户存在时同时补 {@code liked} / {@code collected}。
     *
     * @param postIds 帖子 ID 集合
     * @return 以帖子 ID 为键的摘要；没有可见帖子时返回空 Map
     */
    Map<Long, PostSummaryVO> listSummariesByIds(Collection<Long> postIds);

    /**
     * 按作者集合分页查询公开可见帖子，按发布时间倒序。
     *
     * <p>供关注流调用；游标对客户端不透明，非法游标按首页处理。
     *
     * @param authorIds 作者 ID 集合，为空时返回空页
     * @param cursor    上一页返回的 nextCursor，可为空
     * @param size      每页数量，默认 20，越界自动夹到 1~50
     * @return 帖子摘要游标页
     */
    CursorPage<PostSummaryVO> listByAuthors(Collection<Long> authorIds, String cursor, Integer size);

    /**
     * 查询指定用户的公开帖子，按发布时间倒序。
     *
     * <p>用户不存在、ID 非法或已封禁统一返回 40401；坏游标按首页处理。
     *
     * @param userId 作者用户 ID，允许数字字符串
     * @param cursor 上一页返回的 nextCursor，可为空
     * @param size   每页数量，默认 20，越界自动夹到 1~50
     * @return 帖子摘要游标页
     */
    CursorPage<PostSummaryVO> listByAuthor(String userId, String cursor, Integer size);

    /**
     * 查询全站最新公开帖子，按发布时间倒序。
     *
     * <p>可选登录；游标规则与其他列表一致。当前只支持 {@code latest}，
     * 其他排序值（含 {@code hot}）返回 40001。
     *
     * @param sort   排序方式，可为空，空值按 latest 处理
     * @param cursor 上一页返回的 nextCursor，可为空
     * @param size   每页数量，默认 20，越界自动夹到 1~50
     * @return 帖子摘要游标页
     */
    CursorPage<PostSummaryVO> listLatest(String sort, String cursor, Integer size);

    /**
     * 查询当前登录用户的帖子，按创建时间倒序。
     *
     * <p>包含待审核、已发布、审核拒绝与已下架状态；支持按状态筛选，不传表示全部。
     * 游标规则与其他列表一致。
     *
     * @param status 帖子状态 0~3，可为空
     * @param cursor 上一页返回的 nextCursor，可为空
     * @param size   每页数量，默认 20，越界自动夹到 1~50
     * @return 我的帖子摘要游标页
     */
    CursorPage<MyPostSummaryVO> listMyPosts(Integer status, String cursor, Integer size);

    /**
     * 管理端查询帖子列表，按页码分页。
     *
     * <p>{@code status} 取 {@code 0|1|2|3|ALL}，不传按 0（待审队列）处理；
     * 待审队列按创建时间升序，其他状态 / {@code ALL} 按创建时间倒序。
     * 非法状态返回 40001；非法作者 ID 按查无结果返回空页。
     * 管理端列表不查询当前管理员的点赞 / 收藏状态。
     *
     * @param status   帖子状态或 ALL，可为空
     * @param authorId 作者用户 ID，可为空
     * @param keyword  标题模糊关键词，可为空
     * @param page     页码，从 1 开始
     * @param size     每页数量，默认 20，越界夹到 1~100
     * @return 管理端帖子摘要页码分页
     */
    PageResult<AdminPostSummaryVO> listAdminPosts(String status, String authorId, String keyword,
                                                  Integer page, Integer size);

    /**
     * 管理端查看帖子详情。
     *
     * <p>任意未删除帖子均可查看，不受状态限制；不存在、ID 非法或已删除统一返回 40401。
     * 额外返回最近裁决信息与 {@code canAudit}；不查询关注与点赞 / 收藏状态。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 管理端帖子详情
     */
    PostDetailVO getAdminDetail(String postId);

    /**
     * 编辑帖子（仅作者本人）。
     *
     * <p>全量替换标题、正文、图片与标签；待审核 / 已发布 / 审核拒绝均可编辑，
     * 编辑后状态统一回到 0 待审核并清空审核信息。已下架帖子不可编辑。
     *
     * @param postId  帖子 ID，允许数字字符串
     * @param request 标题、正文、图片与标签
     * @return 编辑后的帖子详情
     */
    PostDetailVO update(String postId, CreatePostDTO request);

    /**
     * 删除帖子（仅作者本人）。
     *
     * <p>软删除，置 {@code is_deleted=1}；重复删除幂等返回。
     *
     * @param postId 帖子 ID，允许数字字符串
     */
    void delete(String postId);

    /**
     * 直接重新提交审核（仅作者本人，不修改内容）。
     *
     * <p>仅审核拒绝（status=2）可重提；成功后状态回到 0 待审核并清空审核信息。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 重提后的帖子状态
     */
    PostCreateVO resubmit(String postId);

    /**
     * 审核通过（仅管理员，不能审核自己的帖子）。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 审核结果
     */
    PostAuditVO approve(String postId);

    /**
     * 审核拒绝（仅管理员，不能审核自己的帖子）。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @param reason 拒绝原因，1~200 字符
     * @return 审核结果
     */
    PostAuditVO reject(String postId, String reason);

    /**
     * 下架帖子（仅管理员，不能操作自己的帖子）。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @param reason 下架原因，1~200 字符
     * @return 审核结果
     */
    PostAuditVO takedown(String postId, String reason);

    /**
     * 恢复已下架帖子（仅管理员，不能操作自己的帖子）。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 审核结果
     */
    PostAuditVO restore(String postId);
}
