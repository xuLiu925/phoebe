package com.rebuild.phoebe.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rebuild.phoebe.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
