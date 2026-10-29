package com.glimmer.content.service;

import com.glimmer.content.vo.TagVO;

import java.util.List;

/**
 * 标签查询服务。
 */
public interface TagService {

    /**
     * 查询标签列表。
     *
     * <p>匿名可用；关键字为空时返回标签字典，limit 越界时夹到 1~50。
     *
     * @param keyword 标签名关键字，可为空
     * @param limit   返回数量，默认 20
     * @return 标签列表
     */
    List<TagVO> list(String keyword, Integer limit);
}
