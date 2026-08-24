package com.zhiyi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启调度：Outbox 消费者、浏览量缓冲刷新、过期登录尝试清理等后台任务。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
