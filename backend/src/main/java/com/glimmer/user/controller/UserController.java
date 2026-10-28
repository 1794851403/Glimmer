package com.glimmer.user.controller;

import com.glimmer.common.api.Result;
import com.glimmer.user.dto.SelfProfile;
import com.glimmer.user.dto.UpdateProfileRequest;
import com.glimmer.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人资料接口。必须登录，鉴权由 AuthInterceptor 统一完成。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 查看本人资料（需登录）。
     *
     * <p>返回完整资料，既用于个人主页展示，也用于编辑页预填。
     */
    @GetMapping("/me")
    public Result<SelfProfile> me() {
        return Result.ok(userService.getMyProfile());
    }

    /**
     * 修改本人资料（需登录）。
     *
     * <p>局部更新：未传字段保持不变，{@code bio} 传空字符串表示清空。
     * 成功后返回修改后的完整资料，前端可直接替换本地状态。
     */
    @PatchMapping("/me")
    public Result<SelfProfile> updateMe(@RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateMyProfile(request));
    }
}
