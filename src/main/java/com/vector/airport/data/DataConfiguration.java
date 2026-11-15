package com.vector.airport.data;

import com.vector.airport.data.source.Web;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/** The data feature's Spring setup: binds {@code vector.data.*} and turns on the {@code @Retryable} of {@link Web}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataProperties.class)
@EnableResilientMethods
class DataConfiguration {}
