package com.stubu.specdriven;

import com.stubu.specdriven.testsupport.TestOidcProvider;
import java.util.Map;
import org.springframework.boot.SpringApplication;

/**
 * Local development launcher: starts the application with the {@code dev} profile (H2 in-memory,
 * sample employees) against a mock OIDC identity provider on port 9000, so the whole login flow
 * works without a real Microsoft or Google account.
 *
 * <pre>./mvnw spring-boot:test-run</pre>
 *
 * Sign in with one of the emails in {@code db/dev/R__dev_seed_data.sql}, e.g. alice.employee@example.com.
 */
public final class DevApplication {

    private static final int IDP_PORT = 9000;

    private DevApplication() {
    }

    public static void main(String[] args) {
        TestOidcProvider idp = TestOidcProvider.start(IDP_PORT, true);
        Map<String, String> properties = idp.applicationProperties("mock");
        properties.put("stubu.iam.providers.mock.display-name", "Test identity provider");
        properties.forEach(System::setProperty);

        SpringApplication application = new SpringApplication(Application.class);
        application.setAdditionalProfiles("dev");
        application.run(args);
    }
}
