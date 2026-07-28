package com.bidnow.bidnow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidnow.bidnow.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
