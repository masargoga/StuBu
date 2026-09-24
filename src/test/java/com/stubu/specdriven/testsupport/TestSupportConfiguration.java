package com.stubu.specdriven.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Registers test-only web endpoints under {@code /test/**}. Import it with
 * {@code @Import(TestSupportConfiguration.class)}. The application denies every non-Vaadin request by
 * default, so these endpoints get their own filter chain: authenticated users only, everybody else is
 * sent to the login page just like for a protected view.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfiguration {

    @Bean
    WhoAmIController whoAmIController() {
        return new WhoAmIController();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain testEndpointsSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/test/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint("/login")))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(Customizer.withDefaults());
        return http.build();
    }
}
