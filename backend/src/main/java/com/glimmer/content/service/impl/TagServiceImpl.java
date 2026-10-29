package com.glimmer.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glimmer.content.entity.TagDO;
import com.glimmer.content.mapper.TagMapper;
import com.glimmer.content.service.TagService;
import com.glimmer.content.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 标签查询实现。
 *
 * <p>标签字典数据量小，MVP 直接查 MySQL，不加缓存。
 */
@Service
public class TagServiceImpl implements TagService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final TagMapper tagMapper;

    public TagServiceImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    /**
     * 关键字去首尾空白后按名称模糊匹配，按 ID 升序返回；`limit` 默认 20、越界夹到 1~50，
     * 直接以 {@code LIMIT n} 拼入查询，避免额外 COUNT。
     *
     * @param keyword 标签名关键字，可为空
     * @param limit   返回数量，可为空
     * @return 标签列表
     */
    @Override
    public List<TagVO> list(String keyword, Integer limit) {
        LambdaQueryWrapper<TagDO> query = new LambdaQueryWrapper<>();
        String normalizedKeyword = keyword == null ? null : keyword.strip();
        if (StringUtils.hasText(normalizedKeyword)) {
            query.like(TagDO::getName, normalizedKeyword);
        }
        query.orderByAsc(TagDO::getId)
                .last("LIMIT " + normalizeLimit(limit));

        return tagMapper.selectList(query).stream()
                .map(TagVO::from)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
