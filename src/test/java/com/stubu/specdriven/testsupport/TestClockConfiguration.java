package com.stubu.specdriven.testsupport;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the server clock with a {@link MutableClock} (UTC) that starts on 2026-09-23 08:03:14Z. Import it
 * with {@code @Import(TestClockConfiguration.class)} and autowire the {@code MutableClock}; it is the
 * primary {@code Clock}, so the application uses it too.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    public static final Instant START = Instant.parse("2026-09-23T08:03:14Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return MutableClock.utc(START);
    }
}
