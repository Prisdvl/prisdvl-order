package com.prisdvl.order.service;

import com.prisdvl.order.dto.UserLoginDTO;
import com.prisdvl.order.entity.User;

public interface UserService {

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    User wxLogin(UserLoginDTO userLoginDTO);
}
