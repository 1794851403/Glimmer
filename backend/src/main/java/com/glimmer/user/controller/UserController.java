package com.glimmer.user.controller;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.api.Result;
import com.glimmer.user.dto.UpdateProfileDTO;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.FollowResultVO;
import com.glimmer.user.vo.FollowUserVO;
import com.glimmer.user.vo.SelfProfileVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public Result<SelfProfileVO> me() {
        return Result.ok(userService.getMyProfile());
    }

    /**
     * 修改本人资料（需登录）。
     *
     * <p>局部更新：未传字段保持不变，{@code bio} 传空字符串表示清空。
     * 成功后返回修改后的完整资料，前端可直接替换本地状态。
     */
    @PatchMapping("/me")
    public Result<SelfProfileVO> updateMe(@RequestBody UpdateProfileDTO request) {
        return Result.ok(userService.updateMyProfile(request));
    }

    /**
     * 关注用户（需登录）。
     *
     * <p>不能关注自己；重复关注幂等，返回当前状态与目标用户粉丝数。
     *
     * @param userId 目标用户 ID
     */
    @PostMapping("/{userId}/follow")
    public Result<FollowResultVO> follow(@PathVariable String userId) {
        return Result.ok(userService.follow(userId));
    }

    /**
     * 取关用户（需登录）。
     *
     * <p>不能取关自己；重复取关幂等，返回当前状态与目标用户粉丝数。
     *
     * @param userId 目标用户 ID
     */
    @DeleteMapping("/{userId}/follow")
    public Result<FollowResultVO> unfollow(@PathVariable String userId) {
        return Result.ok(userService.unfollow(userId));
    }

    /**
     * 粉丝列表（可选登录）。
     *
     * <p>按关注关系创建时间倒序；登录时每条记录带 isFollowing；
     * 坏游标按首页处理，size 默认 20、越界夹到 1~50。
     *
     * @param userId 目标用户 ID
     * @param cursor 上一页的 nextCursor，可为空
     * @param size   每页数量，可为空
     */
    @GetMapping("/{userId}/followers")
    public Result<CursorPage<FollowUserVO>> followers(@PathVariable String userId,
                                                      @RequestParam(required = false) String cursor,
                                                      @RequestParam(required = false) Integer size) {
        return Result.ok(userService.listFollowers(userId, cursor, size));
    }

    /**
     * 关注列表（可选登录）。
     *
     * <p>按关注关系创建时间倒序；登录时每条记录带 isFollowing；
     * 坏游标按首页处理，size 默认 20、越界夹到 1~50。
     *
     * @param userId 目标用户 ID
     * @param cursor 上一页的 nextCursor，可为空
     * @param size   每页数量，可为空
     */
    @GetMapping("/{userId}/following")
    public Result<CursorPage<FollowUserVO>> following(@PathVariable String userId,
                                                      @RequestParam(required = false) String cursor,
                                                      @RequestParam(required = false) Integer size) {
        return Result.ok(userService.listFollowing(userId, cursor, size));
    }
}
