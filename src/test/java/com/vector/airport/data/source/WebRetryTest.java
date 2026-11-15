package com.vector.airport.data.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.vector.airport.FixtureDataTest;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.retry.RetryTemplate;

/** In the app, Web's calls are retried, and Spring AI keeps its own RetryTemplate: Web's retry is not a bean. */
@FixtureDataTest
class WebRetryTest {

    @Autowired
    ApplicationContext context;

    @Test
    void webIsRetryable() {
        assertThat(AopUtils.isAopProxy(context.getBean(Web.class))).isTrue();
    }

    @Test
    void springAiKeepsItsOwnRetry() {
        assertThat(context.getBeanNamesForType(RetryTemplate.class)).containsExactly("retryTemplate");
    }
}
