# Graph Report - poori-sabzi  (2026-09-29)

## Corpus Check
- Corpus is ~2,895 words - fits in a single context window. You may not need a graph.

## Summary
- 53 nodes · 68 edges · 8 communities (7 shown, 1 thin omitted)
- Extraction: 63% EXTRACTED · 37% INFERRED · 0% AMBIGUOUS · INFERRED: 25 edges (avg confidence: 0.93)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Core Architecture and Platform
- Department Tracking and Reporting
- Submissions and File Storage
- Identity and Authentication
- Deadlines and Notifications
- Faculty Directory and Skills
- Academics and Course Allocations
- Shared Infrastructure

## God Nodes (most connected - your core abstractions)
1. `Modular Monolith Architecture` - 6 edges
2. `Spring Boot Backend Application` - 6 edges
3. `FileStorage Interface` - 5 edges
4. `Academics Module` - 5 edges
5. `Submissions Module` - 5 edges
6. `Faculty Dashboard Documentation` - 4 edges
7. `Department Coordinator Role` - 4 edges
8. `React TypeScript SPA` - 4 edges
9. `Backend-for-Frontend (BFF) Auth Pattern` - 4 edges
10. `Deadlines Module` - 4 edges

## Surprising Connections (you probably didn't know these)
- `Academics Module` --implements--> `Teaching Allocation Feature`  [INFERRED]
  Faculty Dashboard_ PRD, MVP, Architecture.html → Faculty Dashboard_ PRD, MVP, Architecture.html  _Bridges community 1 → community 6_
- `Deadlines Module` --implements--> `Deadlines Management Feature`  [INFERRED]
  Faculty Dashboard_ PRD, MVP, Architecture.html → Faculty Dashboard_ PRD, MVP, Architecture.html  _Bridges community 1 → community 4_
- `Submissions Module` --implements--> `Submissions Management Feature`  [INFERRED]
  Faculty Dashboard_ PRD, MVP, Architecture.html → Faculty Dashboard_ PRD, MVP, Architecture.html  _Bridges community 5 → community 2_
- `Audit Module` --shares_data_with--> `audit_log Entity`  [INFERRED]
  Faculty Dashboard_ PRD, MVP, Architecture.html → Faculty Dashboard_ PRD, MVP, Architecture.html  _Bridges community 2 → community 3_
- `Faculty Dashboard Documentation` --cites--> `dc_sops_tracker.xlsx SOP Tracker`  [EXTRACTED]
  Faculty Dashboard_ PRD, MVP, Architecture.html → Faculty Dashboard_ PRD, MVP, Architecture.html  _Bridges community 0 → community 1_

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Authentication and Identity Subsystem** — faculty_dashboard__prd__mvp__architecture_microsoft_entra_id, faculty_dashboard__prd__mvp__architecture_bff_auth_pattern, faculty_dashboard__prd__mvp__architecture_identity_module, faculty_dashboard__prd__mvp__architecture_app_user_entity, faculty_dashboard__prd__mvp__architecture_signin_workflow [EXTRACTED 1.00]
- **Submission Lifecycle Subsystem** — faculty_dashboard__prd__mvp__architecture_submissions_management, faculty_dashboard__prd__mvp__architecture_submissions_module, faculty_dashboard__prd__mvp__architecture_file_storage_abstraction, faculty_dashboard__prd__mvp__architecture_submission_entity, faculty_dashboard__prd__mvp__architecture_submission_file_entity, faculty_dashboard__prd__mvp__architecture_submission_upload_workflow [EXTRACTED 1.00]
- **Modular Monolith Technical Architecture** — faculty_dashboard__prd__mvp__architecture_react_frontend, faculty_dashboard__prd__mvp__architecture_reverse_proxy, faculty_dashboard__prd__mvp__architecture_spring_boot_backend, faculty_dashboard__prd__mvp__architecture_postgresql_database, faculty_dashboard__prd__mvp__architecture_modular_monolith [EXTRACTED 1.00]

## Communities (8 total, 1 thin omitted)

### Community 0 - "Core Architecture and Platform"
Cohesion: 0.25
Nodes (11): ArchUnit Architecture Boundary Verification, dc_dashboard.jsx Prototype, Faculty Dashboard Documentation, IDOR Prevention via Context Scoping, Modular Monolith Architecture, MVP Scope Definition, PostgreSQL Database, React TypeScript SPA (+3 more)

### Community 1 - "Department Tracking and Reporting"
Cohesion: 0.20
Nodes (10): Department Coordinator Role, Dashboard Metrics Feature, dc_sops_tracker.xlsx SOP Tracker, Deadlines Management Feature, Dean Role, Progress Tracking Feature, Reporting Module, Reports Generation Feature (+2 more)

### Community 2 - "Submissions and File Storage"
Cohesion: 0.28
Nodes (9): Audit Logging Feature, Audit Module, FileStorage Interface, LocalDiskStorage Component, S3Storage Component, submission Entity, submission_file Entity, Submission Upload Data Flow (+1 more)

### Community 3 - "Identity and Authentication"
Cohesion: 0.40
Nodes (6): app_user Entity, audit_log Entity, Backend-for-Frontend (BFF) Auth Pattern, Identity Module, Microsoft Entra ID (OIDC), OIDC Sign-In Data Flow

### Community 4 - "Deadlines and Notifications"
Cohesion: 0.40
Nodes (6): deadline_assignment Entity, Deadline Creation Data Flow, deadline Entity, Deadlines Module, Notifications Module, Reminders and Notifications Feature

### Community 5 - "Faculty Directory and Skills"
Cohesion: 0.33
Nodes (6): Directory Module, Faculty Directory Feature, faculty Entity, Faculty Role, skill Entity, Submissions Management Feature

### Community 6 - "Academics and Course Allocations"
Cohesion: 0.83
Nodes (4): Academics Module, course Entity, course_offering Entity, teaching_assignment Entity

## Knowledge Gaps
- **10 isolated node(s):** `Dean Role`, `Reminders and Notifications Feature`, `Audit Logging Feature`, `MVP Scope Definition`, `LocalDiskStorage Component` (+5 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 10 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **1 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `faculty Entity` connect `Faculty Directory and Skills` to `Identity and Authentication`, `Deadlines and Notifications`, `Academics and Course Allocations`?**
  _High betweenness centrality (0.268) - this node is a cross-community bridge._
- **Why does `Spring Boot Backend Application` connect `Core Architecture and Platform` to `Submissions and File Storage`, `Identity and Authentication`?**
  _High betweenness centrality (0.245) - this node is a cross-community bridge._
- **Why does `deadline_assignment Entity` connect `Deadlines and Notifications` to `Submissions and File Storage`, `Faculty Directory and Skills`?**
  _High betweenness centrality (0.230) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `Academics Module` (e.g. with `course Entity` and `course_offering Entity`) actually correct?**
  _`Academics Module` has 4 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `Submissions Module` (e.g. with `submission Entity` and `submission_file Entity`) actually correct?**
  _`Submissions Module` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Dean Role`, `Reminders and Notifications Feature`, `Audit Logging Feature` to the rest of the system?**
  _10 weakly-connected nodes found - possible documentation gaps or missing edges._