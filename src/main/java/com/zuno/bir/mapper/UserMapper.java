package com.zuno.bir.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zuno.bir.entity.User;

/**
 * 用户数据访问接口 (Mapper)
 * <p>
 * 继承自 Mybatis-Plus 的 {@link BaseMapper} 接口，
 * 这使得无需编写XML文件或SQL语句，即可拥有对 {@link User} 实体类的基本CRUD（创建、读取、更新、删除）操作能力。
 */
public interface UserMapper extends BaseMapper<User> {
}
