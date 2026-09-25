package com.stubu.specdriven.security;

import com.stubu.specdriven.employee.EmployeeAccess;
import com.stubu.specdriven.employee.EmployeeRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps a session in step with the employee record. The user context is built at login, so without this a
 * deactivated employee, or one whose role was changed, would carry on with the old rights until they sign out.
 * When the employee is no longer active, no longer exists or has a different role, the session is ended and the
 * request continues unauthenticated, which sends the browser to the login page.
 *
 * <p>The record is read at most once per {@code recheckInterval} and employee, so the many small requests of a
 * Vaadin page do not each hit the database.
 */
class CurrentEmployeeFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(CurrentEmployeeFilter.class);

    private record Checked(Optional<EmployeeAccess> access, long atNanos) {
    }

    private final EmployeeRepository employees;
    private final long recheckNanos;
    private final LongSupplier nanoTime;
    private final Map<Long, Checked> checked = new ConcurrentHashMap<>();

    CurrentEmployeeFilter(EmployeeRepository employees, Duration recheckInterval) {
        this(employees, recheckInterval, System::nanoTime);
    }

    CurrentEmployeeFilter(EmployeeRepository employees, Duration recheckInterval, LongSupplier nanoTime) {
        this.employees = employees;
        this.recheckNanos = recheckInterval.toNanos();
        this.nanoTime = nanoTime;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof EmployeePrincipal principal
                && !stillValid(principal)) {
            LOG.info("Ending the session of employee {}: the employee is inactive or their role changed",
                    principal.getEmployeeId());
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }

    private boolean stillValid(EmployeePrincipal principal) {
        Long id = principal.getEmployeeId();
        long now = nanoTime.getAsLong();
        Checked last = checked.get(id);
        if (last == null || now - last.atNanos() >= recheckNanos) {
            last = new Checked(employees.findAccessById(id), now);
            checked.put(id, last);
        }
        return last.access().map(access -> access.active() && access.role() == principal.getRole()).orElse(false);
    }
}
