package com.glimmer.content.service;

import com.glimmer.content.dto.CreatePostDTO;
import com.glimmer.content.vo.PostCreateVO;

/**
 * 帖子内容模块对外的服务：发布帖子。
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
}
