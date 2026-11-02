package com.glimmer.interaction.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.glimmer.interaction.entity.CollectDO;
import com.glimmer.interaction.entity.LikeDO;
import com.glimmer.interaction.mapper.CollectMapper;
import com.glimmer.interaction.mapper.LikeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractionStatusProviderImplTest {

    private LikeMapper likeMapper;
    private CollectMapper collectMapper;
    private InteractionStatusProviderImpl provider;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LikeDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CollectDO.class);
    }

    @BeforeEach
    void setUp() {
        likeMapper = mock(LikeMapper.class);
        collectMapper = mock(CollectMapper.class);
        provider = new InteractionStatusProviderImpl(likeMapper, collectMapper);
    }

    @Test
    void shouldReturnLikedPostIds() {
        LikeDO row = new LikeDO();
        row.setPostId(1001L);
        when(likeMapper.selectList(any())).thenReturn(List.of(row));

        Set<Long> liked = provider.likedPostIds(1L, List.of(1001L, 1002L));

        assertThat(liked).containsExactly(1001L);
    }

    @Test
    void shouldReturnCollectedPostIds() {
        CollectDO row = new CollectDO();
        row.setPostId(1002L);
        when(collectMapper.selectList(any())).thenReturn(List.of(row));

        Set<Long> collected = provider.collectedPostIds(1L, List.of(1001L, 1002L));

        assertThat(collected).containsExactly(1002L);
    }

    @Test
    void shouldReturnEmptyForMissingUserOrPosts() {
        assertThat(provider.likedPostIds(null, List.of(1001L))).isEmpty();
        assertThat(provider.likedPostIds(1L, List.of())).isEmpty();
        assertThat(provider.collectedPostIds(null, List.of(1001L))).isEmpty();
        assertThat(provider.collectedPostIds(1L, null)).isEmpty();
        verify(likeMapper, never()).selectList(any());
        verify(collectMapper, never()).selectList(any());
    }
}
