package com.glimmer.user.service.impl;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.user.dto.UpdateProfileDTO;
import com.glimmer.user.vo.SelfProfileVO;
import com.glimmer.user.entity.UserDO;
import com.glimmer.user.entity.UserFollowDO;
import com.glimmer.user.mapper.UserFollowMapper;
import com.glimmer.user.mapper.UserMapper;
import com.glimmer.user.vo.UserBriefVO;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserMapper userMapper;
    private UserFollowMapper userFollowMapper;
    private UserServiceImpl userService;

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
