package com.glimmer.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.user.dto.UpdateProfileDTO;
import com.glimmer.user.vo.FollowResultVO;
import com.glimmer.user.vo.FollowUserVO;
import com.glimmer.user.vo.SelfProfileVO;
import com.glimmer.user.vo.UserProfileVO;
import com.glimmer.user.entity.UserDO;
import com.glimmer.user.entity.UserFollowDO;
import com.glimmer.user.mapper.UserFollowMapper;
import com.glimmer.user.mapper.UserMapper;
import com.glimmer.user.vo.UserBriefVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserMapper userMapper;
    private UserFollowMapper userFollowMapper;
    private UserServiceImpl userService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测没有 MyBatis 启动流程，先注册实体元数据，Lambda 条件才能生成 SQL。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UserDO.class);
        TableInfoHelper.initTableInfo(assistant, UserFollowDO.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userFollowMapper = mock(UserFollowMapper.class);
        userService = new UserServiceImpl(userMapper, userFollowMapper);
        UserContext.set(new LoginUser(1L, "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldReturnCurrentProfile() {
        when(userMapper.selectById(1L)).thenReturn(user("小光", "", ""));

        SelfProfileVO profile = userService.getMyProfile();

        assertThat(profile.id()).isEqualTo("1");
        assertThat(profile.phone()).isEqualTo("138****8000");
        assertThat(profile.nickname()).isEqualTo("小光");
        assertThat(profile.role()).isEqualTo("USER");
        assertThat(profile.followingCount()).isEqualTo(2);
        assertThat(profile.followerCount()).isEqualTo(3);
    }

    @Test
    void shouldUpdateOnlyProvidedFields() {
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user("小光", "", "记录生活"));

        userService.updateMyProfile(new UpdateProfileDTO("  小光  ", null, null));

        UserDO patch = capturedPatch();
        assertThat(patch.getId()).isEqualTo(1L);
        assertThat(patch.getNickname()).isEqualTo("小光");
        assertThat(patch.getAvatarUrl()).isNull();
        assertThat(patch.getBio()).isNull();
    }

    @Test
    void shouldClearBioWithEmptyString() {
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user("小光", "", ""));

        userService.updateMyProfile(new UpdateProfileDTO(null, null, ""));

        UserDO patch = capturedPatch();
        assertThat(patch.getBio()).isEmpty();
        assertThat(patch.getNickname()).isNull();
        assertThat(patch.getAvatarUrl()).isNull();
    }

    @Test
    void shouldAcceptHttpAvatar() {
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user("小光", "https://cdn.example.com/a.png", ""));

        userService.updateMyProfile(
                new UpdateProfileDTO(null, "https://cdn.example.com/a.png", null));

        assertThat(capturedPatch().getAvatarUrl()).isEqualTo("https://cdn.example.com/a.png");
    }

    @Test
    void shouldRejectEmptyRequest() {
        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateProfileDTO(null, null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldRejectBlankNickname() {
        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateProfileDTO("   ", null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldCountNicknameByCodePoint() {
        String nickname = "😀".repeat(17);
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user(nickname, "", ""));

        userService.updateMyProfile(new UpdateProfileDTO(nickname, null, null));

        assertThat(capturedPatch().getNickname()).isEqualTo(nickname);
    }

    @Test
    void shouldRejectTooLongNickname() {
        String nickname = "a".repeat(33);

        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateProfileDTO(nickname, null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldRejectTooLongBio() {
        String bio = "a".repeat(201);

        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateProfileDTO(null, null, bio)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldCountBioByCodePoint() {
        String bio = "😀".repeat(150);
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user("小光", "", bio));

        userService.updateMyProfile(new UpdateProfileDTO(null, null, bio));

        assertThat(capturedPatch().getBio()).isEqualTo(bio);
    }

    @Test
    void shouldRejectInvalidAvatarUrl() {
        assertThatThrownBy(() -> userService.updateMyProfile(
                new UpdateProfileDTO(null, "ftp://cdn.example.com/a.png", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldRejectMissingUser() {
        when(userMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(userService::getMyProfile)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void shouldReturnPublicProfileForAnonymous() {
        UserContext.clear();
        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));

        UserProfileVO profile = userService.getPublicProfile("2");

        assertThat(profile.id()).isEqualTo("2");
        assertThat(profile.nickname()).isEqualTo("阿野");
        assertThat(profile.avatarUrl()).isEmpty();
        assertThat(profile.bio()).isEmpty();
        assertThat(profile.followingCount()).isEqualTo(2);
        assertThat(profile.followerCount()).isEqualTo(3);
        assertThat(profile.isFollowing()).isFalse();
        assertThat(profile.isMe()).isFalse();
        assertThat(profile.createdAt()).isPositive();
        verifyNoInteractions(userFollowMapper);
    }

    @Test
    void shouldReturnPublicProfileWithViewerFollowState() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));
        when(userFollowMapper.selectCount(any())).thenReturn(1L);

        UserProfileVO profile = userService.getPublicProfile("2");

        assertThat(profile.isFollowing()).isTrue();
        assertThat(profile.isMe()).isFalse();
    }

    @Test
    void shouldMarkOwnPublicProfileWithoutFollowQuery() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "小光"));

        UserProfileVO profile = userService.getPublicProfile("1");

        assertThat(profile.isMe()).isTrue();
        assertThat(profile.isFollowing()).isFalse();
        verifyNoInteractions(userFollowMapper);
    }

    @Test
    void shouldRejectPublicProfileForMissingOrBannedUser() {
        when(userMapper.selectById(2L)).thenReturn(null);
        UserDO banned = user(3L, "阿野");
        banned.setStatus(1);
        when(userMapper.selectById(3L)).thenReturn(banned);

        assertThatThrownBy(() -> userService.getPublicProfile("2"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> userService.getPublicProfile("3"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> userService.getPublicProfile("abc"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void shouldResolvePublicUserId() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));

        assertThat(userService.resolvePublicUserId("2")).isEqualTo(2L);
    }

    @Test
    void shouldReturnBriefOrNull() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));
        when(userMapper.selectById(99L)).thenReturn(null);

        UserBriefVO brief = userService.getBrief(2L);

        assertThat(brief.id()).isEqualTo("2");
        assertThat(brief.nickname()).isEqualTo("阿野");
        assertThat(userService.getBrief(99L)).isNull();
        assertThat(userService.getBrief(null)).isNull();
    }

    @Test
    void shouldBatchLoadDistinctBriefs() {
        when(userMapper.selectBatchIds(List.of(2L, 3L))).thenReturn(List.of(
                user(2L, "阿野"), user(3L, "小满")));

        Map<Long, UserBriefVO> briefs = userService.getBriefs(List.of(2L, 3L, 2L));

        assertThat(briefs).containsOnlyKeys(2L, 3L);
        assertThat(briefs.get(2L).nickname()).isEqualTo("阿野");
        assertThat(briefs.get(3L).nickname()).isEqualTo("小满");
        verify(userMapper).selectBatchIds(List.of(2L, 3L));
    }

    @Test
    void shouldReturnEmptyBriefMapForEmptyIds() {
        assertThat(userService.getBriefs(List.of())).isEmpty();
        assertThat(userService.getBriefs(null)).isEmpty();
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldDetectFollowing() {
        when(userFollowMapper.selectCount(any())).thenReturn(1L);

        assertThat(userService.isFollowing(1L, 2L)).isTrue();
    }

    @Test
    void shouldNotTreatSelfAsFollowing() {
        assertThat(userService.isFollowing(1L, 1L)).isFalse();
        assertThat(userService.isFollowing(null, 2L)).isFalse();
        verifyNoInteractions(userFollowMapper);
    }

    @Test
    void shouldListFollowingIdsWithoutSelfOrDuplicates() {
        when(userFollowMapper.selectList(any())).thenReturn(Arrays.asList(
                follow(1L, 2L),
                follow(1L, 2L),
                follow(1L, 3L),
                follow(1L, 1L),
                follow(1L, null)));

        assertThat(userService.listFollowingIds(1L)).containsExactly(2L, 3L);
    }

    @Test
    void shouldFollowUserAndIncreaseCounts() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));
        when(userFollowMapper.selectCount(any())).thenReturn(0L);

        FollowResultVO result = userService.follow("2");

        assertThat(result.userId()).isEqualTo("2");
        assertThat(result.following()).isTrue();
        assertThat(result.followerCount()).isEqualTo(4);

        ArgumentCaptor<UserFollowDO> captor = ArgumentCaptor.forClass(UserFollowDO.class);
        verify(userFollowMapper).insert(captor.capture());
        assertThat(captor.getValue().getFollowerId()).isEqualTo(1L);
        assertThat(captor.getValue().getFolloweeId()).isEqualTo(2L);
        verify(userMapper, times(2)).update(isNull(), any());
    }

    @Test
    void shouldTreatRepeatFollowAsIdempotent() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));
        when(userFollowMapper.selectCount(any())).thenReturn(1L);

        FollowResultVO result = userService.follow("2");

        assertThat(result.following()).isTrue();
        assertThat(result.followerCount()).isEqualTo(3);
        verify(userFollowMapper, never()).insert(any(UserFollowDO.class));
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldRejectSelfFollow() {
        assertThatThrownBy(() -> userService.follow("1"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(userMapper, userFollowMapper);
    }

    @Test
    void shouldRejectFollowMissingOrBannedUser() {
        when(userMapper.selectById(2L)).thenReturn(null);
        UserDO banned = user(3L, "阿野");
        banned.setStatus(1);
        when(userMapper.selectById(3L)).thenReturn(banned);

        assertThatThrownBy(() -> userService.follow("2"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> userService.follow("3"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void shouldUnfollowUserAndDecreaseCounts() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));
        when(userFollowMapper.delete(any())).thenReturn(1);

        FollowResultVO result = userService.unfollow("2");

        assertThat(result.following()).isFalse();
        assertThat(result.followerCount()).isEqualTo(2);
        verify(userMapper, times(2)).update(isNull(), any());
    }

    @Test
    void shouldTreatRepeatUnfollowAsIdempotent() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));
        when(userFollowMapper.delete(any())).thenReturn(0);

        FollowResultVO result = userService.unfollow("2");

        assertThat(result.following()).isFalse();
        assertThat(result.followerCount()).isEqualTo(3);
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldPageFollowersWithViewerFollowingFlag() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        UserFollowDO newest = follow(3L, 2L);
        newest.setId(10L);
        newest.setCreatedAt(now);
        UserFollowDO older = follow(4L, 2L);
        older.setId(9L);
        older.setCreatedAt(now.minusMinutes(1));
        UserFollowDO viewerFollows = follow(1L, 3L);

        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));
        when(userFollowMapper.selectList(any()))
                .thenReturn(List.of(newest, older))
                .thenReturn(List.of(viewerFollows));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(3L, "小满"), user(4L, "阿野")));

        CursorPage<FollowUserVO> page = userService.listFollowers("2", null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo("3");
        assertThat(page.items().get(0).isFollowing()).isTrue();
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    void shouldReturnEmptyFollowersWhenNone() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "阿野"));
        when(userFollowMapper.selectList(any())).thenReturn(List.of());

        CursorPage<FollowUserVO> page = userService.listFollowers("2", null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldRejectFollowListForMissingUser() {
        when(userMapper.selectById(2L)).thenReturn(null);

        assertThatThrownBy(() -> userService.listFollowers("2", null, 20))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private UserDO capturedPatch() {
        ArgumentCaptor<UserDO> captor = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).updateById(captor.capture());
        return captor.getValue();
    }

    private UserDO user(long id, String nickname) {
        UserDO user = user(nickname, "", "");
        user.setId(id);
        return user;
    }

    private UserFollowDO follow(long followerId, Long followeeId) {
        UserFollowDO follow = new UserFollowDO();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        return follow;
    }

    private UserDO user(String nickname, String avatarUrl, String bio) {
        UserDO user = new UserDO();
        user.setId(1L);
        user.setPhone("13800138000");
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setBio(bio);
        user.setRole(0);
        user.setStatus(0);
        user.setFollowingCount(2);
        user.setFollowerCount(3);
        user.setCreatedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        return user;
    }
}
