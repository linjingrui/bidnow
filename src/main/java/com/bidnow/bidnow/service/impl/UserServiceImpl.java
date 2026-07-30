package com.bidnow.bidnow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidnow.bidnow.common.BizException;
import com.bidnow.bidnow.common.JwtUtil;
import com.bidnow.bidnow.dto.LoginRequest;
import com.bidnow.bidnow.dto.RegisterRequest;
import com.bidnow.bidnow.entity.User;
import com.bidnow.bidnow.mapper.UserMapper;
import com.bidnow.bidnow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Long register(RegisterRequest request) {
        // 1. 查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BizException("用户名已被注册");
        }

        // 2. BCrypt 加密密码。同一个密码每次加密结果不同（随机盐值），无法反推
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 3. 存入数据库
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(encodedPassword);
        user.setPhone(request.getPhone());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        return user.getId();
    }

    @Override
    public String login(LoginRequest request) {
        // 1. 查用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BizException("用户名或密码错误");
        }

        // 2. BCrypt 验密：把明文跟库里加密后的比对
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }

        // 3. 生成 JWT
        return jwtUtil.generate(user.getId(), user.getUsername());
    }
}
