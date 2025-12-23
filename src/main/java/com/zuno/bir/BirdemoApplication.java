package com.zuno.bir;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 应用主启动类
 * <p>
 * {@link SpringBootApplication} 是一个复合注解，它包含了：
 * - {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration}: 启用Spring Boot的自动配置机制。
 * - {@link org.springframework.context.annotation.ComponentScan}: 在应用程序所在的包（"com.zuno.bir"）及其子包中扫描组件。
 * - {@link org.springframework.context.annotation.Configuration}: 允许在上下文中注册额外的bean或导入其他配置类。
 * <p>
 * {@link MapperScan} 注解用于指定Mybatis-Plus框架需要扫描的Mapper接口所在的包。
 * Spring容器会自动创建这些接口的代理实现，并将其注册为bean。
 */
@SpringBootApplication
@MapperScan("com.zuno.bir.mapper")
public class BirdemoApplication {

    /**
     * 应用程序的主入口点。
     *
     * @param args 命令行参数。
     */
    public static void main(String[] args) {
        // 启动Spring Boot应用程序
        SpringApplication.run(BirdemoApplication.class, args);
    }

}
