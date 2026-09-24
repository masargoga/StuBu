# Project Context

> High-level context for the project: the problem being solved, who it's for, what's in scope, and what constraints apply.

## 1. Vision

Enable enterprises to reliably track employee working time with a secure, auditable application that supports flexible work patterns (multiple daily periods, corrections, and monthly reviews). The system should provide managers with visibility into team timekeeping and give employees a simple, trustworthy way to record and manage their hours.

Success means employees can track their time with minimal friction, managers can efficiently review and approve timesheets, and administrators maintain visibility and control over organizational data with a complete audit trail.

## 2. Users

### Employee
- Authenticates via IAM (Entra ID or Google)
- Records check-in and check-out times throughout the day
- Tracks multiple working periods per day
- Views current day's timeline and total worked hours
- Corrects historical time records
- Reviews their monthly timesheet before submission
- Submits completed timesheets for manager approval
- Tracks approval status (submitted, approved, rejected)
- Corrects rejected timesheets and resubmits

### Manager
- Authenticates via IAM with manager role
- Views pending timesheet approval requests from direct reports
- Reviews complete timesheet and timeline for each employee
- Approves or rejects monthly timesheets with optional rejection reason
- Views previously approved timesheets
- Switches between viewing direct reports only or all department employees

### Administrator
- Authenticates via IAM with admin role
- Manages employee records (create, edit, deactivate)
- Configures public holidays
- Views audit logs for compliance and troubleshooting
- Accesses all employee and timesheet data

## 3. Constraints

- **Authentication:** External IAM via OIDC/OAuth 2.0 (Microsoft Entra ID or Google Login)
- **Database:** H2 for development/testing; PostgreSQL for production
- **Scalability:** Must run as multiple Kubernetes pods without in-memory state or local filesystem dependencies
- **Audit Trail:** Immutable audit log for all relevant data changes
- **Data Ownership:** IAM system provides authenticated email only; application database provides all other employee information
- **Technology Stack:** Java 25, Spring Boot, Vaadin, Spring Security, JPA/Hibernate
- **Access:** No integration with external systems; all functionality runs within the application itself

---

# Related Documents

- [Spec README](README.md) — process overview and workflow
- [Architecture](architecture.md) — technology stack and application structure
- [Design System](design-system.md) — theme, component usage, and visual standards
- [Use Case Template](use-cases/use-case-template.md) — template for feature specifications
- [Data Model](datamodel/datamodel.md) — entity definitions and relationships
