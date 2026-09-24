# Implementation Roadmap

Phased approach for implementing the Enterprise Employee Time Tracking Application.

## Overview

The application is designed to be built incrementally in 5 phases, with each phase delivering business value while maintaining code quality and test coverage.

**Estimated Scope:** 10,000 to 15,000 lines of production code + tests

## Phase 1: Foundation (Authentication & Time Recording)

**Duration:** Weeks 1–3  
**Deliverables:** Secure login, employee dashboard, basic time recording  
**Business Value:** Employees can authenticate and record check-in/check-out times

### Use Cases Implemented
- UC-001: Authenticate with IAM
- UC-002: Record Check-In and Check-Out
- UC-003: View Daily Timesheet

### Technical Deliverables

#### Database Schema
- `Employee` table with email, role, manager, department, active status
- `Department` table
- `TimeEntry` table with employee_id, date, check_in_time, check_out_time, is_active
- `AuditLog` table for basic logging

#### Backend Services
- `AuthenticationService` — OIDC/OAuth 2.0 integration abstraction
- `EmployeeService` — Employee lookup and role resolution
- `TimeEntryService` — Check-in/check-out logic
- `AuditService` — Log significant operations

#### Frontend (Vaadin)
- Login page with IAM provider options
- Employee dashboard with:
  - Check-In button (large, accessible)
  - Check-Out button (large, accessible)
  - Current status display
  - Daily timeline component (reusable)
  - Total hours and break time display
  - Responsive design (mobile-friendly)

#### Persistence
- JPA/Hibernate entity mappings
- Spring Data repositories
- Flyway/Liquibase database migrations
- H2 development configuration

#### Security
- Spring Security configuration
- OIDC/OAuth 2.0 client setup
- Session management
- CORS configuration (if needed)

#### Testing
- Unit tests for time calculations
- Unit tests for authentication flow
- Integration tests for persistence
- UI tests for critical journey (login → check-in → check-out)
- Security tests for authorization

### Definition of Done
- [ ] OIDC authentication working with test IAM system
- [ ] Employee can authenticate and access dashboard
- [ ] Check-in creates TimeEntry with server timestamp
- [ ] Check-out sets check_out_time on active entry
- [ ] Daily timeline displays all entries and total hours
- [ ] Database migrations are versioned and repeatable
- [ ] All unit/integration/UI tests pass
- [ ] Application starts cleanly on H2 and PostgreSQL
- [ ] Docker image builds successfully
- [ ] Kubernetes deployment manifests drafted

---

## Phase 2: Monthly Workflow (Timesheet & Submission)

**Duration:** Weeks 4–6  
**Deliverables:** Monthly timesheet view, editing, submission workflow  
**Business Value:** Employees can review, correct, and submit timesheets for approval

### Use Cases Implemented
- UC-004: Correct Historical Time Entries
- UC-005: View Monthly Timesheet
- UC-006: Submit Timesheet for Approval

### Technical Deliverables

#### Database Schema (Extensions)
- `Timesheet` table with employee_id, year, month, status, submitted_at, submitted_by, version
- Add audit fields to `TimeEntry` (created_at, updated_at, created_by, updated_by)
- Extend `AuditLog` with reason and old/new JSON values

#### Backend Services
- `TimesheetService` — Monthly timesheet logic
  - Auto-create DRAFT timesheet on first access
  - Validate month completion before submission
  - Enforce DRAFT status for editing
- `TimeEntryEditService` — Correction logic
  - Validate check-out > check-in
  - Validate no future dates
  - Support deletion with audit trail
- `NotificationService` (abstraction) — Email notifications
  - Manager receives "Timesheet Submitted" notification
  - Employees receive approval/rejection notifications

#### Frontend (Vaadin)
- Monthly timesheet view
  - Calendar or table layout
  - Date navigation (previous/next month)
  - Visual distinction for weekends/holidays
  - Daily totals and monthly total
  - Timesheet status badge
- Edit time entry modal
  - Date (read-only)
  - Check-in time
  - Check-out time
  - Correction reason (optional)
- Delete confirmation dialog
- Submit confirmation dialog with consequences warning
- Responsive design

#### Domain Model
- Implement TimeSheet state machine (DRAFT only, validate month completion)
- Implement TimeEntry validation rules
- Immutable audit trail for all changes

#### Testing
- Unit tests for month completion validation
- Unit tests for state transitions
- Integration tests for timesheet creation/update
- UI tests for editing and submission flows
- Validation tests (check-out before check-in, future dates, etc.)

### Definition of Done
- [ ] Monthly timesheet view displays all entries for calendar month
- [ ] TimeEntry editing works with validation
- [ ] Deletion cascades properly and is audited
- [ ] Timesheet auto-created on first access to month
- [ ] Submit button only enabled after month end
- [ ] Submit changes status to SUBMITTED
- [ ] Manager receives notification (email abstraction in place)
- [ ] All alternative flows tested (validation failures, etc.)
- [ ] Optimistic locking prevents concurrent corruption
- [ ] Audit trail captures all changes with old/new values
- [ ] All tests pass

---

## Phase 3: Manager Approval Workflow

**Duration:** Weeks 7–9  
**Deliverables:** Manager approval interface, rejection workflow, resubmission  
**Business Value:** Managers can review and approve/reject timesheets; employees can correct and resubmit

### Use Cases Implemented
- UC-007: Review and Approve/Reject Timesheet
- UC-008: Correct and Resubmit Rejected Timesheet
- UC-009: Manager View Employee Timesheet and Timeline
- UC-010: Manager Switch Employee Scope

### Technical Deliverables

#### Database Schema (Extensions)
- Add `approved_at`, `approved_by`, `rejected_at`, `rejected_by`, `rejection_reason` to `Timesheet`
- Add index on (employee_id, manager_id) for authorization queries
- Add index on (status) for pending approval queries

#### Backend Services
- `ManagerAuthorizationService` — Manager scope and permission checking
  - Direct reports logic (managerId check)
  - Department employees logic (departmentId check)
- `TimesheetApprovalService` — Approval/rejection workflow
  - Validate manager authorization
  - Enforce SUBMITTED status for approval
  - Handle rejection reason capture
  - Return to DRAFT on rejection
  - Make immutable on approval
  - Record approval timestamp and approver
- Extend `NotificationService` — Approval/rejection notifications

#### Frontend (Vaadin)
- Manager dashboard
- Pending approvals queue
  - Table with employee, period, status, action links
  - Filters (employee, month, status)
  - Pagination
- Timesheet review modal
  - Read-only timesheet display (same as employee view)
  - Timeline visualization
  - Approve button
  - Reject button with reason form
  - Approval/rejection history
- Scope switcher (Direct Reports vs. Department)
  - Dropdown or toggle
  - Persists for session
  - Updates approval queue

#### Authorization Rules
- Manager can only access their employees
- Manager can only approve timesheets from authorized employees
- Approved timesheets become read-only to manager too
- Rejected timesheet returns to employee as DRAFT

#### Testing
- Authorization tests (manager scope enforcement)
- State transition tests (SUBMITTED → APPROVED, SUBMITTED → REJECTED → DRAFT → SUBMITTED)
- Rejection reason capture and display
- Notification trigger tests
- Scope switching tests
- UI tests for manager workflows

### Definition of Done
- [ ] Manager can view pending approval queue
- [ ] Manager can see only authorized employees
- [ ] Scope switcher works correctly (direct reports vs. department)
- [ ] Approval changes status to APPROVED with timestamp
- [ ] Rejection requires reason and returns status to DRAFT
- [ ] Employee sees rejection reason and can correct
- [ ] Resubmission sends notification to manager
- [ ] Approved timesheets are immutable
- [ ] Authorization enforced server-side
- [ ] All tests pass
- [ ] Audit trail captures approvals/rejections

---

## Phase 4: Administration

**Duration:** Weeks 10–12  
**Deliverables:** Employee management, public holidays, audit logs, data visibility  
**Business Value:** Administrators can manage organizational data and maintain compliance

### Use Cases Implemented
- UC-011: Manage Employees
- UC-012: Configure Public Holidays
- UC-013: View Audit Logs
- UC-014: View All Employee and Timesheet Data

### Technical Deliverables

#### Database Schema (Extensions)
- `PublicHoliday` table with date, name
- Ensure `AuditLog` has all necessary columns and indexes

#### Backend Services
- `EmployeeManagementService`
  - Create employee with validation
  - Update employee with change tracking
  - Deactivate employee (soft delete)
  - Fetch employee list with filters
  - Manager hierarchy validation
- `PublicHolidayService`
  - Add/edit/delete holidays
  - Unique constraint per date
  - Audit all changes
- `AuditLogService`
  - Query with filters (date range, user, entity type, action)
  - Pagination
  - Read-only (no modification)
- `DirectoryService` — Unified employee/timesheet viewing

#### Frontend (Vaadin)
- Employee management view
  - Employee table with sort/search
  - Filters (role, department, status)
  - Create employee form
  - Edit employee form
  - Deactivate button with confirmation
  - Validation error messages
- Public holidays view
  - Holiday table
  - Add holiday form (date picker, name)
  - Edit form
  - Delete with confirmation
- Audit log view
  - Audit log table with filters
  - Date range picker
  - User filter
  - Entity type filter
  - Action filter
  - Detail modal for audit entry
  - Optional export button (CSV/PDF)
- Directory view
  - Employee list with advanced search
  - Department filter
  - Employee timesheet list
  - Timesheet drill-down
  - Historical timesheet viewing

#### Admin Authorization
- Only admins can access admin views
- Admin authorization centralized in `@RolesAllowed` or similar

#### Testing
- CRUD tests for employees and holidays
- Validation tests (email uniqueness, required fields)
- Authorization tests (admin-only access)
- Audit log query tests (filters, pagination)
- State consistency tests (soft delete, active status)

### Definition of Done
- [ ] Employee CRUD works with validation
- [ ] Email uniqueness enforced
- [ ] Deactivation prevents login
- [ ] Holiday management works
- [ ] Audit log is queryable and filterable
- [ ] Admin can see all employees and timesheets
- [ ] All changes audited
- [ ] Admin views require ADMIN role
- [ ] All tests pass

---

## Phase 5: Polish & Deployment

**Duration:** Weeks 13–15  
**Deliverables:** UI refinement, internationalization, performance, containerization  
**Business Value:** Production-ready application suitable for enterprise deployment

### Technical Deliverables

#### UI & UX Refinement
- Accessibility audit and fixes
  - WCAG compliance check
  - Screen reader testing
  - Keyboard navigation verification
  - High contrast mode support
- Mobile/tablet responsiveness testing and fixes
- Error message user-testing and refinement
- Timeline component usability improvements
- Button sizing and spacing review for accessibility

#### Internationalization (i18n)
- German localization
- English localization
- Date/time format per locale
- All UI strings externalized to message bundles
- Right-to-left text support (future-proofing)

#### Performance Optimization
- Database query optimization
  - Add missing indexes
  - N+1 query identification and fix
  - Pagination for large lists
  - Query result caching where appropriate
- Vaadin frontend optimization
  - Lazy loading where appropriate
  - Component rendering optimization
- Load testing (10,000 employees scenario)

#### Docker & Kubernetes
- Production Docker image
  - Multi-stage build
  - Minimal base image
  - Security scanning
  - Non-root user
- Kubernetes manifests
  - Deployment template
  - Service configuration
  - ConfigMap for application properties
  - Secrets for credentials
  - Health probe configuration
- Docker Compose for local development
- Environment variable documentation

#### Deployment Automation
- CI/CD pipeline (GitHub Actions, GitLab CI, or similar)
- Automated tests in pipeline
- Docker image building
- Container registry push
- Kubernetes deployment scripts

#### Security Hardening
- Dependency scanning for vulnerabilities
- Spring Security hardening
  - CSRF protection
  - XSS prevention
  - SQL injection prevention
- Secrets management
- Authentication token security
- Audit log protection (prevent tampering)
- Input validation review

#### Documentation
- Developer guide (architecture, domain model, setup)
- API documentation (if applicable)
- Database schema documentation
- Deployment guide (Docker, Kubernetes, PostgreSQL setup)
- Configuration guide
- Testing guide
- Security considerations document
- Troubleshooting guide
- German and English user guides

#### Comprehensive Testing
- Unit test coverage > 80%
- Integration test coverage for critical paths
- UI test coverage for all major user journeys
- Security test review
- Performance test results
- Accessibility compliance report
- Cross-browser testing (Chrome, Firefox, Safari, Edge)
- Mobile device testing

#### Monitoring & Observability
- Application logging (structured logs, JSON format)
- Error tracking
- Performance metrics (if applicable)
- Health check endpoints
- Readiness/liveness probe endpoints
- No sensitive data in logs

### Definition of Done
- [ ] UI accessible (WCAG AA compliance)
- [ ] Mobile responsive
- [ ] All text internationalized
- [ ] Performance acceptable (< 2s page load)
- [ ] Docker image builds and runs
- [ ] Kubernetes manifests provided
- [ ] All tests pass
- [ ] Security audit complete
- [ ] Documentation complete
- [ ] CI/CD pipeline working
- [ ] Ready for production deployment

---

## Cross-Phase Activities

### Throughout All Phases

#### Version Control
- Use Git with conventional commits
- Feature branches per use case or feature
- Pull request review process
- Merge after test passage

#### Code Quality
- SonarQube or similar static analysis
- Checkstyle/Spotbugs for Java
- Code review standards
- Test coverage targets
- Documentation standards

#### Database Migrations
- Every schema change in a versioned migration
- Migrations are additive, not destructive
- Support both H2 and PostgreSQL
- Test migrations from previous versions

#### Testing Strategy
- Write tests before/during implementation (TDD-style)
- Unit tests for business logic
- Integration tests for persistence
- UI tests for critical journeys
- Security tests for authorization
- Performance tests in Phase 5

#### Security Throughout
- OWASP top 10 awareness
- Server-side authorization on every endpoint
- Input validation
- Output encoding
- Secure configuration
- Secrets externalization

#### Documentation
- Update specs if changes required
- Document domain decisions
- Maintain architecture diagrams
- Keep README updated
- Document new services/APIs

---

## Dependencies & Prerequisites

### Before Phase 1 Starts
- [ ] Development environment setup (Java 25, Maven, IDE)
- [ ] PostgreSQL development instance
- [ ] IAM test environment (Entra ID or Google OAuth sandbox)
- [ ] Git repository established
- [ ] CI/CD pipeline template ready
- [ ] Coding standards documented
- [ ] Code review process established

### Tools & Services Required
- Database: PostgreSQL (and H2 for development)
- IAM: OIDC-compatible provider (Entra ID, Google, or mock)
- Email: SMTP server or mock email service (Phase 2+)
- Version control: Git
- Build: Maven
- Testing: JUnit 5, Vaadin test utilities
- Container: Docker
- Orchestration: Kubernetes (Phase 5)
- Optional: CI/CD (GitHub Actions, GitLab CI, Jenkins)

### Team Composition
- Backend developer (Spring Boot, JPA, database)
- Frontend developer (Vaadin, UI/UX)
- Database/DevOps engineer (PostgreSQL, Docker, Kubernetes)
- QA engineer (testing, automation)
- Tech lead (architecture, code review, decisions)

---

## Success Criteria

### Phase 1
✓ Employees can authenticate, check-in, and check-out  
✓ Time entries persist correctly  
✓ Daily timeline displays accurately

### Phase 2
✓ Employees can submit monthly timesheets  
✓ Managers receive notifications  
✓ Time entries can be corrected

### Phase 3
✓ Managers can approve/reject timesheets  
✓ Employees can resubmit rejected timesheets  
✓ Approved timesheets are immutable

### Phase 4
✓ Admins can manage employees and holidays  
✓ Audit logs are queryable and immutable  
✓ All organizational data is accessible

### Phase 5
✓ Application is production-ready  
✓ Accessible and internationalized  
✓ Deployed to Kubernetes cluster  
✓ Monitored and observable

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| OIDC integration complexity | Medium | High | Early spike, use libraries, abstract provider |
| Performance issues with 10K users | Low | High | Early load testing, index strategy, query optimization |
| State machine bugs | Medium | High | Explicit state tests, consider state library |
| Timezone handling issues | Medium | Medium | Early decision, test coverage, documentation |
| Audit trail data explosion | Low | High | Retention policy, archival strategy, pagination |
| Manager authorization bugs | Medium | High | Authorization matrix, explicit tests, code review |
| Concurrent edit data corruption | Low | High | Optimistic locking, transactional tests |
| Email delivery failures | Low | Medium | Abstraction, retry logic, fallback notifications |
| Database migration issues | Low | High | Testable migrations, rollback procedures |
| Kubernetes deployment issues | Medium | Medium | Early Kubernetes setup, DevOps expertise |

---

## Metrics & Tracking

### Code Metrics
- Lines of code (production vs. test)
- Code coverage percentage
- Cyclomatic complexity
- Technical debt (SonarQube)

### Process Metrics
- Phase completion percentage
- Test pass rate
- Defect count and severity
- Code review turnaround time
- Deployment frequency

### Business Metrics
- Use case completion
- User acceptance testing sign-off
- Security audit results
- Performance benchmarks
- Accessibility compliance score

---

## Rollback / Contingency

### If Phase Fails
1. Root cause analysis
2. Extend phase timeline
3. Prioritize critical functionality
4. De-scope non-critical features
5. Adjust Phase 5 timeline accordingly

### If Major Issue Discovered
1. Create incident response plan
2. Pause feature development if needed
3. Fix critical issues
4. Add tests to prevent recurrence
5. Resume development with updated plan

---

## Go-Live Readiness Checklist

Before production deployment:
- [ ] All 14 use cases implemented and tested
- [ ] Accessibility compliance verified
- [ ] Performance benchmarks met
- [ ] Security audit complete
- [ ] Database backup/restore procedures tested
- [ ] Kubernetes cluster ready
- [ ] Monitoring and alerting configured
- [ ] User documentation complete
- [ ] Admin procedures documented
- [ ] Disaster recovery plan in place
- [ ] Training completed
- [ ] Stakeholder sign-off obtained

---

**Roadmap Version:** 1.0  
**Last Updated:** 2024-01-15  
**Next Step:** Kick off Phase 1 (Foundation)
