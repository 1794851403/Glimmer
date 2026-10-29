package com.glimmer.content.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.content.dto.CreatePostDTO;
import com.glimmer.content.vo.PostCreateVO;
import com.glimmer.content.vo.PostDetailVO;
import com.glimmer.content.vo.PostSummaryVO;

import java.util.Collection;

/**
 * 帖子内容模块对外的服务：发布、详情与按作者列表查询。
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
}
