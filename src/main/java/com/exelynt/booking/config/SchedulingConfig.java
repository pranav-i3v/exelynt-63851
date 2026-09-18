package com.exelynt.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the background jobs, currently just the expired refresh-token purge. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
