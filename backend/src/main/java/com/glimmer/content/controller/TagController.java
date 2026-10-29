package com.glimmer.content.controller;

import com.glimmer.common.api.Result;
import com.glimmer.content.service.TagService;
import com.glimmer.content.vo.TagVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口。标签列表匿名可访问，鉴权由 AuthInterceptor 白名单放行。
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 标签列表（匿名）。
     *
     * @param keyword 标签名关键字，可选
     * @param limit   返回数量，默认 20，越界自动夹到 1~50
     */
    @GetMapping
    public Result<List<TagVO>> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Integer limit) {
        return Result.ok(tagService.list(keyword, limit));
    }
}
