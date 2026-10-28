package com.glimmer.user.service;

import com.glimmer.user.dto.SelfProfile;
import com.glimmer.user.dto.UpdateProfileRequest;

/**
 * 用户资料模块对外的服务：查看与修改本人资料。
 *
 * <p>Service 接口是模块对外的事实 Facade，跨模块调用只依赖接口；
 * 具体实现见 {@link com.glimmer.user.service.impl.UserServiceImpl}。
 */
public interface UserService {

    /**
     * 查看当前登录用户的完整资料。
     *
     * <p>返回全部可编辑字段，前端既用于个人主页展示，也用于编辑页预填。
     *
     * @return 当前用户的本人资料
     */
    SelfProfile getMyProfile();

    /**
     * 修改当前登录用户的资料。
     *
     * <p>局部更新：未提供的字段保持不变；{@code bio} 传空字符串表示清空。
     * 成功后重新查询并返回完整资料，调用方无需再发 GET。
     *
     * @param request 待修改字段，至少一个
     * @return 修改后的完整本人资料
     */
    SelfProfile updateMyProfile(UpdateProfileRequest request);
}
