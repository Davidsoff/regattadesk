# Context Map

This file documents the bounded domains that already exist in RegattaDesk's code, infrastructure, and product design documents.

## Domains

### Platform-Delivery

**Owns**: the canonical runtime stack, build and dependency baselines, and delivery mechanics for backend, frontend, PostgreSQL, Traefik, Authelia, and MinIO.

**Domain docs**: [`platform-delivery`](domains/platform-delivery/overview.md)

### Identity-Access

**Owns**: edge authentication via Traefik and Authelia, forwarded identity trust boundaries, backend role enforcement, and anonymous public-session JWT bootstrap.

**Domain docs**: [`identity-access`](domains/identity-access/overview.md)

### Core-Regatta

**Owns**: the event-sourced domain core for regattas, athletes, crews, entries, regatta setup, event history, and projection foundations.

**Domain docs**: [`core-regatta`](domains/core-regatta/overview.md)

### Rules-Draw

**Owns**: rulesets, blocks, bib pools, draw generation, draw publication, and the scheduling constraints that feed public and operator workflows.

**Domain docs**: [`rules-draw`](domains/rules-draw/overview.md)

### Public-Delivery

**Owns**: versioned public APIs, cache policy, SSE delivery, public UI surfaces, formatting/i18n display behavior, and printable export presentation.

**Domain docs**: [`public-delivery`](domains/public-delivery/overview.md)

### Operator-Capture

**Owns**: operator tokens, capture sessions, line-scan manifests and tiles, marker workflows, offline synchronization, and station handoff flows.

**Domain docs**: [`operator-capture`](domains/operator-capture/overview.md)

### Adjudication

**Owns**: investigations, penalties, DSQ and exclusion decisions, approval gates, and result-state transitions that affect publication.

**Domain docs**: [`adjudication`](domains/adjudication/overview.md)

### Finance

**Owns**: payment status tracking, club and entry finance summaries, invoice generation, and financial bulk operations.

**Domain docs**: [`finance`](domains/finance/overview.md)

### Operability

**Owns**: health, observability, performance gates, security hardening, testing strategy, quality gates, and operational runbooks.

**Domain docs**: [`operability`](domains/operability/overview.md)

## Relationships

### Platform-Delivery → Identity-Access (platform dependency)

Provides the Traefik, Authelia, PostgreSQL, and environment wiring that the authentication boundary relies on.

**Contract**: [interfaces](domains/platform-delivery/interfaces.md)

### Platform-Delivery → Public-Delivery (platform dependency)

Provides the frontend, backend, reverse proxy, and CDN-friendly deployment shape needed for versioned public delivery.

**Contract**: [interfaces](domains/platform-delivery/interfaces.md)

### Platform-Delivery → Operator-Capture (platform dependency)

Provides MinIO, backend runtime, and compose overlays required for line-scan ingest and operator workflows.

**Contract**: [interfaces](domains/platform-delivery/interfaces.md)

### Identity-Access → Core-Regatta (upstream security contract)

Supplies forwarded staff identity and role information that protects regatta setup and staff CRUD endpoints.

**Contract**: [interfaces](domains/identity-access/interfaces.md)

### Identity-Access → Public-Delivery (upstream security contract)

Supplies anonymous public-session bootstrap and JWT cookie handling used before version discovery and public content fetches.

**Contract**: [interfaces](domains/identity-access/interfaces.md)

### Identity-Access → Operator-Capture (upstream security contract)

Supplies role and token trust boundaries for operator APIs, station handoff flows, and staff-proxied retrieval.

**Contract**: [interfaces](domains/identity-access/interfaces.md)

### Identity-Access → Adjudication (upstream security contract)

Supplies role-based staff access so only authorized jury and admin actors can mutate investigation and approval state.

**Contract**: [interfaces](domains/identity-access/interfaces.md)

### Identity-Access → Finance (upstream security contract)

Supplies `financial_manager` and admin access boundaries for payment and invoice operations.

**Contract**: [interfaces](domains/identity-access/interfaces.md)

### Core-Regatta → Rules-Draw (customer-supplier)

Rules and draw workflows consume regatta events, crews, entries, and setup state from the event-sourced core.

**Contract**: [interfaces](domains/core-regatta/interfaces.md)

### Core-Regatta → Public-Delivery (customer-supplier)

Public schedule and results projections are derived from regatta, entry, and revision state maintained by the core domain.

**Contract**: [interfaces](domains/core-regatta/interfaces.md)

### Core-Regatta → Operator-Capture (customer-supplier)

Operator capture links markers to regatta entries and depends on entry identity and completion state from the core model.

**Contract**: [interfaces](domains/core-regatta/interfaces.md)

### Core-Regatta → Adjudication (customer-supplier)

Adjudication decisions operate on regatta entries and feed back status changes, penalties, and approval state into the core event stream.

**Contract**: [interfaces](domains/core-regatta/interfaces.md)

### Core-Regatta → Finance (customer-supplier)

Finance uses regatta entries, clubs, and billing scope from the core model as the subject of payment operations.

**Contract**: [interfaces](domains/core-regatta/interfaces.md)

### Rules-Draw → Public-Delivery (upstream publishing contract)

Draw publication and bib assignment produce the `draw_revision` and schedule state that public pages expose through immutable URLs.

**Contract**: [interfaces](domains/rules-draw/interfaces.md)

### Rules-Draw → Operator-Capture (upstream scheduling contract)

Blocks, bib pools, and published draw order drive operator station workflows and marker linking expectations.

**Contract**: [interfaces](domains/rules-draw/interfaces.md)

### Operator-Capture → Adjudication (evidence handoff)

Line-scan markers and linked capture evidence provide the timing facts used during investigations and approvals.

**Contract**: [interfaces](domains/operator-capture/interfaces.md)

### Adjudication → Public-Delivery (upstream publishing contract)

Penalty, DSQ, exclusion, and approval changes advance `results_revision` and change the public results surface.

**Contract**: [interfaces](domains/adjudication/interfaces.md)

### Finance → Core-Regatta (peer integration)

Payment status updates emit entry-level financial state changes while leaving regatta setup ownership in the core domain.

**Contract**: [interfaces](domains/finance/interfaces.md)

### Operability → Platform-Delivery (shared-kernel)

Observability overlays, security tests, and quality gates are enforced through the platform stack and its CI entrypoints.

**Contract**: [interfaces](domains/operability/interfaces.md)

### Operability → Identity-Access (cross-cutting policy)

Security validation scripts, edge hardening guidance, and JWT rotation runbooks constrain how identity and access are operated.

**Contract**: [interfaces](domains/operability/interfaces.md)

### Operability → Core-Regatta (cross-cutting policy)

Testing strategy, database migration guidance, and observability expectations apply to event-sourced core changes.

**Contract**: [interfaces](domains/operability/interfaces.md)

### Operability → Rules-Draw (cross-cutting policy)

Quality gates, test requirements, and performance validation apply to scheduling and draw changes.

**Contract**: [interfaces](domains/operability/interfaces.md)

### Operability → Public-Delivery (cross-cutting policy)

Accessibility gates, public performance expectations, and SSE observability shape the public delivery domain.

**Contract**: [interfaces](domains/operability/interfaces.md)

### Operability → Operator-Capture (cross-cutting policy)

Offline reliability checks, storage retention runbooks, and operator-critical accessibility expectations constrain operator capture workflows.

**Contract**: [interfaces](domains/operability/interfaces.md)

### Operability → Adjudication (cross-cutting policy)

Auditability, reproducibility, and regression coverage requirements govern adjudication changes.

**Contract**: [interfaces](domains/operability/interfaces.md)

### Operability → Finance (cross-cutting policy)

Security, audit logging, and test-gate requirements constrain finance changes.

**Contract**: [interfaces](domains/operability/interfaces.md)
