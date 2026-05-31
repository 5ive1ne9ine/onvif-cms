package com.acme.cms.user.service;

import com.acme.cms.user.entity.SystemUser;
import com.acme.cms.user.mapper.SystemUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SystemUserMapper userMapper;

    public SystemUser findByUsername(String username) {
        return userMapper.selectOne(new QueryWrapper<SystemUser>().eq("username", username));
    }

    public SystemUser findById(Long id) {
        return userMapper.selectById(id);
    }
}
