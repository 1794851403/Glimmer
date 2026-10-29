package com.glimmer.content.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.glimmer.content.entity.TagDO;
import com.glimmer.content.mapper.TagMapper;
import com.glimmer.content.vo.TagVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagServiceImplTest {

    private TagMapper tagMapper;
    private TagServiceImpl tagService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测没有 MyBatis 启动流程，先注册实体元数据，Lambda 条件才能生成 SQL。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TagDO.class);
    }

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        tagService = new TagServiceImpl(tagMapper);
    }

    @Test
    void shouldFilterByKeywordAndClampLimit() {
        when(tagMapper.selectList(any())).thenReturn(List.of(tag(3L, "露营")));

        List<TagVO> tags = tagService.list("  露营  ", 999);

        assertThat(tags).extracting(TagVO::id).containsExactly("3");
        assertThat(tags).extracting(TagVO::name).containsExactly("露营");

        LambdaQueryWrapper<TagDO> query = capturedQuery();
        assertThat(lastSql(query)).isEqualTo("LIMIT 50");
        assertThat(query.getSqlSegment()).contains("LIKE");
        assertThat(query.getParamNameValuePairs()).containsValue("%露营%");
    }

    @Test
    void shouldUseDefaultLimitAndIgnoreBlankKeyword() {
        when(tagMapper.selectList(any())).thenReturn(List.of());

        assertThat(tagService.list("   ", null)).isEmpty();

        LambdaQueryWrapper<TagDO> query = capturedQuery();
        assertThat(lastSql(query)).isEqualTo("LIMIT 20");
        assertThat(query.getParamNameValuePairs()).isEmpty();
    }

    @Test
    void shouldClampNonPositiveLimitToOne() {
        when(tagMapper.selectList(any())).thenReturn(List.of());

        tagService.list(null, 0);

        assertThat(lastSql(capturedQuery())).isEqualTo("LIMIT 1");
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<TagDO> capturedQuery() {
        ArgumentCaptor<LambdaQueryWrapper<TagDO>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(tagMapper).selectList(captor.capture());
        return captor.getValue();
    }

    /**
     * MP 3.5.7 没有公开 lastSql 的读取方法，测试里用反射读出来断言 limit 夹取。
     */
    private String lastSql(LambdaQueryWrapper<?> query) {
        try {
            Field field = AbstractWrapper.class.getDeclaredField("lastSql");
            field.setAccessible(true);
            return ((SharedString) field.get(query)).getStringValue().strip();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private TagDO tag(long id, String name) {
        TagDO tag = new TagDO();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }
}
