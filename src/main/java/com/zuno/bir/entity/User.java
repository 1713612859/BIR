package com.zuno.bir.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户实体类
 * <p>
 * 代表系统中的一个用户，如收银员、服务员或经理。
 * 使用 Lombok 的 @Data 注解自动生成 getter, setter, toString, equals 和 hashCode 方法。
 * 使用 Mybatis-Plus 的 @TableName 注解将该实体映射到数据库的 "user" 表。
 */
@Data
@TableName("user")
public class User {
    /**
     * 用户ID，主键
     */
    @TableId
    private Long userId;
    /**
     * 用户登录名
     */
    private String username;
    /**
     * 用户的真实姓名或全名
     */
    private String fullName;
    /**
     * 用户角色
     * 例如：CASHIER (收银员), WAITER (服务员), MANAGER (经理)
     */
    private String role;
    /**
     * 用户密码
     * 在实际应用中，应存储加密后的密码哈希值。
     */
    private String password;
    /**
     * 用户的联系电话
     */
    private String phone;
}
