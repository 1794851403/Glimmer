package com.glimmer.user.service.impl;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.user.dto.SelfProfile;
import com.glimmer.user.dto.UpdateProfileRequest;
import com.glimmer.user.entity.UserDO;
import com.glimmer.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserMapper userMapper;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userService = new UserServiceImpl(userMapper);
        UserContext.set(new LoginUser(1L, "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldReturnCurrentProfile() {
        when(userMapper.selectById(1L)).thenReturn(user("小光", "", ""));

        SelfProfile profile = userService.getMyProfile();

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

        userService.updateMyProfile(new UpdateProfileRequest("  小光  ", null, null));

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

        userService.updateMyProfile(new UpdateProfileRequest(null, null, ""));

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
                new UpdateProfileRequest(null, "https://cdn.example.com/a.png", null));

        assertThat(capturedPatch().getAvatarUrl()).isEqualTo("https://cdn.example.com/a.png");
    }

    @Test
    void shouldRejectEmptyRequest() {
        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateProfileRequest(null, null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldRejectBlankNickname() {
        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateProfileRequest("   ", null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldCountNicknameByCodePoint() {
        String nickname = "😀".repeat(17);
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user(nickname, "", ""));

        userService.updateMyProfile(new UpdateProfileRequest(nickname, null, null));

        assertThat(capturedPatch().getNickname()).isEqualTo(nickname);
    }

    @Test
    void shouldRejectTooLongNickname() {
        String nickname = "a".repeat(33);

        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateProfileRequest(nickname, null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldRejectTooLongBio() {
        String bio = "a".repeat(201);

        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateProfileRequest(null, null, bio)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldCountBioByCodePoint() {
        String bio = "😀".repeat(150);
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user("小光", "", bio));

        userService.updateMyProfile(new UpdateProfileRequest(null, null, bio));

        assertThat(capturedPatch().getBio()).isEqualTo(bio);
    }

    @Test
    void shouldRejectInvalidAvatarUrl() {
        assertThatThrownBy(() -> userService.updateMyProfile(
                new UpdateProfileRequest(null, "ftp://cdn.example.com/a.png", null)))
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

    private UserDO capturedPatch() {
        ArgumentCaptor<UserDO> captor = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).updateById(captor.capture());
        return captor.getValue();
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
