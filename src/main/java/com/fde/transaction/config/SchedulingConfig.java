package com.fde.transaction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enable Spring scheduling for the background job.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
