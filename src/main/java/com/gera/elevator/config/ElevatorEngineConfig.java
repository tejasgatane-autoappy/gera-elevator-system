package com.gera.elevator.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElevatorEngineConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}