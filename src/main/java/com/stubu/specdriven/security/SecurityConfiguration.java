package com.stubu.specdriven.security;

import com.stubu.specdriven.employee.EmployeeRepository;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Authentication is delegated to OIDC identity providers; there is no username/password login.
 * Authorization is enforced server-side through the roles on the views ({@code @RolesAllowed}, ...).
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(IamProperties.class)
class SecurityConfiguration {

    static final String LOGIN_PATH = "/login";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, EmployeeOidcUserService oidcUserService,
            LoginSuccessHandler successHandler, LoginFailureHandler failureHandler, EmployeeRepository employees,
            @Value("${stubu.security.recheck-interval:PT5S}") Duration recheckInterval) throws Exception {
        http.addFilterBefore(new CurrentEmployeeFilter(employees, recheckInterval), AuthorizationFilter.class);
        // The probes of Docker and Kubernetes cannot sign in; they only see "UP" or "DOWN".
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health/**").permitAll());
        http.oauth2Login(oauth2 -> oauth2
                .loginPage(LOGIN_PATH)
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                .successHandler(successHandler)
                .failureHandler(failureHandler));
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView(LoginView.class, LOGIN_PATH));
        return http.build();
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(IamProperties properties) {
        return new IamClientRegistrations(properties);
    }
}
