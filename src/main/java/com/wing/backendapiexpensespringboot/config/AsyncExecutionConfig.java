package com.wing.backendapiexpensespringboot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncExecutionConfig {

    public static final String USER_BOOTSTRAP_EXECUTOR = "userBootstrapExecutor";

    private final AppConfig appConfig;

    @Bean(name = USER_BOOTSTRAP_EXECUTOR)
    public Executor userBootstrapExecutor() {
        AppConfig.Async async = appConfig.getBootstrap().getAsync();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix("user-bootstrap-");
        executor.initialize();
        return executor;
    }
}
