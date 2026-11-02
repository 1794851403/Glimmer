package com.glimmer.content.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.content.dto.CreatePostDTO;
import com.glimmer.content.vo.MyPostSummaryVO;
import com.glimmer.content.vo.PostAuditVO;
import com.glimmer.content.vo.PostCreateVO;
import com.glimmer.content.vo.PostDetailVO;
import com.glimmer.content.vo.PostSummaryVO;

import java.util.Collection;

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
