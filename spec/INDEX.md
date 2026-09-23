# Specification Index

Complete specification set for the Enterprise Employee Time Tracking Application.

## Quick Navigation

### Start Here
1. **[Project Context](project-context.md)** — Vision, users, and constraints
2. **[Specifications Summary](SPECIFICATIONS_SUMMARY.md)** — Overview and key decisions
3. **[Data Model](datamodel/datamodel.md)** — Entities and relationships

### Deep Dives by Role

#### For Employees
- [UC-001: Authenticate with IAM](use-cases/use-case-001-authenticate-with-iam.md)
- [UC-002: Record Check-In / Check-Out](use-cases/use-case-002-record-check-in-check-out.md)
- [UC-003: View Daily Timesheet](use-cases/use-case-003-view-daily-timesheet.md)
- [UC-004: Correct Historical Time Entries](use-cases/use-case-004-correct-historical-time-entries.md)
- [UC-005: View Monthly Timesheet](use-cases/use-case-005-view-monthly-timesheet.md)
- [UC-006: Submit Timesheet for Approval](use-cases/use-case-006-submit-timesheet.md)
- [UC-008: Correct and Resubmit Rejected Timesheet](use-cases/use-case-008-resubmit-rejected-timesheet.md)

#### For Managers
- [UC-007: Review and Approve/Reject Timesheet](use-cases/use-case-007-review-approve-reject-timesheet.md)
- [UC-009: View Employee Timesheet and Timeline](use-cases/use-case-009-manager-view-employee-timesheet.md)
- [UC-010: Switch Between Direct Reports and Department](use-cases/use-case-010-manager-switch-employee-scope.md)

#### For Administrators
- [UC-011: Manage Employees](use-cases/use-case-011-admin-manage-employees.md)
- [UC-012: Configure Public Holidays](use-cases/use-case-012-admin-configure-public-holidays.md)
- [UC-013: View Audit Logs](use-cases/use-case-013-admin-view-audit-logs.md)
- [UC-014: View All Employee and Timesheet Data](use-cases/use-case-014-admin-view-all-employee-data.md)

## Use Case Status

All 14 use cases are specified and ready for implementation:

| UC# | Feature | Role(s) | Status |
|-----|---------|---------|--------|
| 001 | Authenticate with IAM | All | ✓ Specified |
| 002 | Record Check-In / Check-Out | Employee | ✓ Specified |
| 003 | View Daily Timesheet | Employee | ✓ Specified |
| 004 | Correct Historical Time Entries | Employee | ✓ Specified |
| 005 | View Monthly Timesheet | Employee | ✓ Specified |
| 006 | Submit Timesheet for Approval | Employee | ✓ Specified |
| 007 | Review and Approve/Reject Timesheet | Manager | ✓ Specified |
| 008 | Correct and Resubmit Rejected Timesheet | Employee | ✓ Specified |
| 009 | View Employee Timesheet and Timeline | Manager | ✓ Specified |
| 010 | Switch Employee Scope | Manager | ✓ Specified |
| 011 | Manage Employees | Admin | ✓ Specified |
| 012 | Configure Public Holidays | Admin | ✓ Specified |
| 013 | View Audit Logs | Admin | ✓ Specified |
| 014 | View All Employee and Timesheet Data | Admin | ✓ Specified |

## Document Structure

```
spec/
  ├── PROJECT-CONTEXT.md           ← Vision, users, constraints
  ├── ARCHITECTURE.md              ← Tech stack, structure
  ├── SPECIFICATIONS_SUMMARY.md    ← Overview (this collection)
  ├── INDEX.md                     ← This file
  │
  ├── datamodel/
  │   └── datamodel.md             ← Entities and relationships
  │
  └── use-cases/
      ├── use-case-001-*.md        ← Authentication
      ├── use-case-002-*.md        ← Check-in / Check-out
      ├── use-case-003-*.md        ← Daily view
      ├── use-case-004-*.md        ← Edit historical entries
      ├── use-case-005-*.md        ← Monthly view
      ├── use-case-006-*.md        ← Submit timesheet
      ├── use-case-007-*.md        ← Manager approval
      ├── use-case-008-*.md        ← Resubmit rejected
      ├── use-case-009-*.md        ← Manager view
      ├── use-case-010-*.md        ← Manager scope
      ├── use-case-011-*.md        ← Admin manage employees
      ├── use-case-012-*.md        ← Admin holidays
      ├── use-case-013-*.md        ← Admin audit logs
      ├── use-case-014-*.md        ← Admin directory
      └── use-case-template.md     ← Template for new use cases
```

## Reading Guide for Different Roles

### Business Analysts / Product Owners
1. [Project Context](project-context.md)
2. [Specifications Summary](SPECIFICATIONS_SUMMARY.md)
3. Individual use cases by role

### Architects / Tech Leads
1. [Project Context](project-context.md)
2. [Specifications Summary](SPECIFICATIONS_SUMMARY.md)
3. [Architecture](architecture.md)
4. [Data Model](datamodel/datamodel.md)
5. All use cases to understand complete workflow

### Developers
1. [Project Context](project-context.md)
2. [Specifications Summary](SPECIFICATIONS_SUMMARY.md)
3. [Architecture](architecture.md)
4. [Data Model](datamodel/datamodel.md)
5. Use cases in implementation order (typically UC-001 → UC-002 → ... → UC-014)

### QA / Testers
1. Individual use case documents
2. Business rules and alternative flows in each use case
3. Tests section of each use case

## Key Specifications

### Authentication & Authorization
- **Provider:** OIDC/OAuth 2.0 compatible (Microsoft Entra ID, Google)
- **Roles:** EMPLOYEE, MANAGER, ADMIN
- **Enforcement:** Server-side authorization (not UI-based)
- **Employee Matching:** Email from IAM matched to application database

### Data Model
Core entities:
- **Employee** — Users with roles and organizational context
- **Department** — Organizational grouping
- **TimeEntry** — Individual work periods (check-in → check-out)
- **Timesheet** — Monthly container (status: DRAFT → SUBMITTED → APPROVED/REJECTED)
- **PublicHoliday** — Calendar configuration
- **AuditLog** — Immutable audit trail

### Workflow States
```
DRAFT (editable by employee)
  ↓ submit
SUBMITTED (read-only for employee, awaiting manager approval)
  ├→ approve → APPROVED (immutable)
  └→ reject → REJECTED (returns to DRAFT for employee correction)
```

### Access Control Matrix

| Action | Employee | Manager | Admin |
|--------|----------|---------|-------|
| Check-in / Check-out | Own only | No | No |
| Edit own time entries | DRAFT only | No | No |
| View own timesheet | Yes | N/A | Yes |
| View employee timesheet | No | Authorized employees | Yes |
| Submit timesheet | DRAFT only | N/A | No |
| Approve timesheet | No | Yes | No |
| Reject timesheet | No | Yes | No |
| Manage employees | No | No | Yes |
| Configure holidays | No | No | Yes |
| View audit logs | No | No | Yes |

## Assumptions & Known Decisions

**See [Specifications Summary - Known Assumptions](SPECIFICATIONS_SUMMARY.md#known-assumptions--decisions) for complete list**

Key assumptions:
1. Calendar month is the timesheet period (not "last 30 days")
2. Break time calculated from gaps between periods
3. Public holidays are informational only
4. Email is immutable after employee creation
5. No automatic legal rule enforcement
6. Monolithic architecture (Spring Boot + Vaadin)
7. PostgreSQL for production (H2 for development)
8. Horizontal scaling for Kubernetes

## Open Questions

**See [Specifications Summary - Open Questions](SPECIFICATIONS_SUMMARY.md#open-questions-for-stakeholders) for complete list**

Key questions for stakeholders:
- Email notification provider and configuration
- Timezone strategy (browser-based or per-user override)
- Data retention duration
- Manager approval delegation / substitutes
- HR manual timesheet locking capability
- Break policy configuration
- Performance SLAs
- Future data export/API requirements
- German labor law compliance validation

## Using This Specification

### For Implementation
1. Use individual use case files as the source of truth
2. Cross-reference with Data Model for entity definitions
3. Follow business rules and state transitions exactly
4. Implement all alternative flows
5. Create tests based on flows and business rules

### For Change Requests
1. Identify which use case(s) are affected
2. Review current specification
3. Note what changes are proposed
4. Update specification first, then implementation
5. Update affected tests
6. Document changes and impact

### For New Features
1. Use the use-case template as a starting point
2. Follow the same structure as existing use cases
3. Cross-reference with Data Model for new entities
4. Define state transitions if applicable
5. Add to this index
6. Update project context if scope changes

## Quality Checklist for Specifications

Each use case should include:
- [ ] Clear goal statement
- [ ] Actor roles
- [ ] Preconditions
- [ ] Trigger event
- [ ] Main flow (numbered steps)
- [ ] Alternative flows (validation, error cases)
- [ ] Postconditions (success and failure)
- [ ] Business rules table
- [ ] Test coverage checklist
- [ ] UI surface description
- [ ] Access control

## Related Documents

- [Project Context](project-context.md) — Vision and constraints
- [Architecture](architecture.md) — Technology and structure
- [Design System](design-system.md) — Theme and components
- [Master Prompt](spec.md) — Original requirements (reference)

---

**Specification Collection Version:** 1.0  
**Last Updated:** 2024-01-15  
**Status:** Ready for Development  
**Next Step:** Begin Phase 1 Implementation (Foundation)
