package com.stubu.specdriven.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeAccess;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A signed-in session follows the employee record: when the employee is deactivated, removed or gets another
 * role, the session ends instead of carrying on with the rights from the time of login (spec.md §22, UC-011).
 */
class CurrentEmployeeFilterTest {

    private Optional<EmployeeAccess> stored;
    private final AtomicLong nanos = new AtomicLong();
    private int reads;
    private CurrentEmployeeFilter filter;
    private MockHttpServletRequest request;
    private MockHttpSession session;

    @BeforeEach
    void signIn() {
        Employee employee = new Employee("alice.employee@example.com", "Alice", "Employee", Role.EMPLOYEE, 1L);
        ReflectionTestUtils.setField(employee, "id", 7L);
        stored = Optional.of(new EmployeeAccess(true, Role.EMPLOYEE));
        EmployeeRepository repository = (EmployeeRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { EmployeeRepository.class }, (proxy, method, args) -> {
                    if (method.getName().equals("findAccessById")) {
                        reads++;
                        return stored;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        filter = new CurrentEmployeeFilter(repository, Duration.ofSeconds(5), nanos::get);
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "alice"));
        EmployeePrincipal principal = new EmployeePrincipal(employee, token);
        session = new MockHttpSession();
        request = new MockHttpServletRequest();
        request.setSession(session);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Sends a request through the filter; whether the user is still signed in afterwards. */
    private boolean stillSignedIn() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest(), "The request always continues");
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    @Test
    void anActiveEmployeeWithTheSameRoleStaysSignedIn() throws Exception {
        assertTrue(stillSignedIn());
        assertTrue(!session.isInvalid());
    }

    @Test
    void aDeactivatedEmployeeIsSignedOutAndTheSessionEnds() throws Exception {
        stored = Optional.of(new EmployeeAccess(false, Role.EMPLOYEE));

        assertTrue(!stillSignedIn(), "The user context is removed");
        assertTrue(session.isInvalid(), "The session is ended");
    }

    @Test
    void aRemovedEmployeeIsSignedOut() throws Exception {
        stored = Optional.empty();

        assertTrue(!stillSignedIn());
    }

    @Test
    void aChangedRoleEndsTheSessionSoTheNewRoleIsUsedAtTheNextLogin() throws Exception {
        stored = Optional.of(new EmployeeAccess(true, Role.MANAGER));

        assertTrue(!stillSignedIn());
    }

    @Test
    void theRecordIsReadOnlyOncePerIntervalAndTheChangeIsSeenAfterwards() throws Exception {
        assertTrue(stillSignedIn());
        assertTrue(stillSignedIn());
        assertEquals(1, reads, "Requests within the interval do not read the database again");

        stored = Optional.of(new EmployeeAccess(false, Role.EMPLOYEE));
        assertTrue(stillSignedIn(), "Not noticed yet within the interval");

        nanos.addAndGet(Duration.ofSeconds(6).toNanos());
        assertTrue(!stillSignedIn(), "Noticed once the interval is over");
        assertEquals(2, reads);
    }

    @Test
    void aFreshLoginWithTheNewRoleIsNotSentAwayBecauseOfAnOldAnswer() throws Exception {
        // The record says MANAGER, the session was built at login as EMPLOYEE: signed out, and that is remembered.
        stored = Optional.of(new EmployeeAccess(true, Role.MANAGER));
        assertTrue(!stillSignedIn());

        // The employee signs in again a moment later (within the interval) and now has the role of the record.
        Employee employee = new Employee("alice.employee@example.com", "Alice", "Employee", Role.MANAGER, 1L);
        ReflectionTestUtils.setField(employee, "id", 7L);
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "alice"));
        EmployeePrincipal manager = new EmployeePrincipal(employee, token);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(manager, "n/a", manager.getAuthorities()));

        assertTrue(stillSignedIn(), "The new session is valid");
    }

    @Test
    void anonymousRequestsAreLeftAlone() throws Exception {
        SecurityContextHolder.clearContext();

        assertTrue(!stillSignedIn());
        assertEquals(0, reads);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
