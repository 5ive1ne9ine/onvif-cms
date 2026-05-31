package com.acme.cms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class TaskExecutorConfig {

    /**
     * 用于 ONVIF 事件订阅 / 录制编排 / 心跳的调度池
     */
    @Bean(name = "onvifScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler onvifScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(8);
        s.setThreadNamePrefix("onvif-sch-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(10);
        s.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return s;
    }
}
