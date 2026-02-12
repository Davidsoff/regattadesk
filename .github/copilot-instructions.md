# RegattaDesk - Copilot Instructions

## Repository Overview

RegattaDesk is a web application for managing rowing head races (single-distance races). The repository is currently in the **design phase**, containing Product Design Documents (PDDs) and specifications rather than implementation code.

**Target Users:**
- **Staff (authenticated, desktop-first):** Regatta organizers, jury, info desk, finance
- **Operators (token-based, offline-capable PWA):** Start/finish line operators using line-scan camera UI
- **Public (anonymous, high-traffic):** Schedule viewing and live-updating results

**Core Principles:**
- Strong auditability via event sourcing
- Offline-capable operator PWA with conflict resolution
- Real-time updates via Server-Sent Events (SSE)
- Cacheable public pages with versioned URLs

## Tech Stack & Architecture

**Backend:** Quarkus (Java) + PostgreSQL
**Frontend:** Vue.js (3 separate surfaces: staff web, operator PWA, public web)
**Authentication:** Authelia SSO (Traefik ForwardAuth) for staff; QR token links for operators (no personal accounts)
**Data Architecture:** Event sourcing for auditability; projections for read models
**Real-time:** Server-Sent Events (SSE) for live updates to public results
**Deployment:** Containerized (Docker Compose with Traefik, Authelia, MinIO); CI/CD pipeline planned

## Project Structure

### Core Directories

- **`pdd/`** - Product Design Documents (source of truth for all decisions)
  - `design/` - **Primary design documents (authoritative source):**
    - `detailed-design.md` - **Complete product specification (primary reference)**
    - `style-guide.md` - UI/UX design system
    - `database-schema.md` - PostgreSQL schema
    - `openapi-v0.1.yaml` - REST API specification
  - `idea-honing.md` - Refined product definition
  - `summary.md` - Artifacts index and next steps
  - `rough-idea.md` - Initial concept (historical reference only, superseded by detailed-design.md)
  - `implementation/` - Implementation planning:
    - `plan.md` - Bounded context decomposition
    - `bc01-bc09` files - Feature lists per bounded context
    - `issues/` - GitHub issue backlogs in YAML format
  - `research/` - Technical research notes

- **`skills/`** - AI workflow automation skills
  - `pdd-todo-router/` - Routes PDD tasks to implementers
  - `pdd-todo-analyzer/` - Analyzes PDD todos
  - `pdd-todo-implementer/` - Implements PDD changes

- **`openspec/`** - OpenAPI specification tooling and config

- **`.codex/skills/`** - Codex AI skill definitions for OpenAPI workflows

- **`styleprompt.md`** - Comprehensive LLM prompt for design system guidance (reference this for UI/UX work)

- **`todo.md`** - Documentation review action items (currently empty)

## Domain Context

**Rowing Head Races:**
- Sequential class starts with interleaved finishes
- Random draw with stored seed (designed for future algorithms)
- Entry statuses: `active`, `withdrawn_before_draw`, `withdrawn_after_draw`, `dns` (Did Not Start), `dnf` (Did Not Finish), `excluded`, `dsq` (Disqualified)
- Draw publishing increments `draw_revision`
- Results publishing increments `results_revision`
- Public URLs are versioned: `/public/v{draw_revision}-{results_revision}/...`

**Key Workflows:**
- **Draw Management:** Import entries → Generate random draw → Assign bibs → Publish
- **Operator Capture:** Line-scan camera UI with marker creation/linking to bibs
- **Jury Process:** Investigations → Penalties/exclusions/DSQs → Approvals → Immutable results
- **Public Delivery:** SSE-based live updates; snapshot-on-connect with revision ticks

**Offline Behavior:**
- Operators can work without stable internet
- Conflict resolution: Last-Write-Wins (LWW) for unapproved entries
- Manual resolution required for edits to approved/immutable entries

## Development Status

**Current Phase:** Design and documentation
- ✅ **Detailed design complete** (`pdd/design/detailed-design.md` - **primary specification**)
- ✅ Database schema defined (`pdd/design/database-schema.md`)
- ✅ API specification complete (`pdd/design/openapi-v0.1.yaml`)
- ✅ Style guide complete (`pdd/design/style-guide.md`)
- ✅ Product definition complete (`pdd/idea-honing.md`)
- ✅ Implementation plan with bounded contexts (`pdd/implementation/`)
- ⏳ Implementation: Not yet started

**No Build/Test Commands Yet:** This repository contains design documents only. When implementation begins:
- Backend will use Quarkus build tools (Maven or Gradle)
- Frontend will use Vue CLI or Vite
- Tests will follow standard Java (JUnit) and JavaScript (Vitest/Jest) patterns

## Key Facts for Coding Agents

### When Working with This Repository:

1. **Primary Specification:** `pdd/design/detailed-design.md` is the authoritative product specification - always reference this first
2. **API Contract:** Use `pdd/design/openapi-v0.1.yaml` for API structure
3. **Database:** Use `pdd/design/database-schema.md` for data model
4. **Design System:** Reference `styleprompt.md` for comprehensive UI/UX guidance
5. **Implementation Tickets:** Check `pdd/implementation/issues/*.yaml` for planned work
6. **Note:** `pdd/rough-idea.md` is outdated - use `pdd/design/detailed-design.md` instead

### Style & Design Principles:

- **Approachable but dependable** brand vibe (calm, trustworthy)
- **Clarity under pressure** - staff and operators work in time-sensitive environments
- **Fast scanning of dense tabular data** - results tables are primary UI
- **Error prevention** - offline conflicts, approval gates, immutability after approval
- **Accessibility:** WCAG 2.2 AA minimum for public flows; aim for AAA where feasible
- **Internationalization:** Dutch (nl) and English (en) primary; 24h time; DD-MM-YYYY date format
- **Density options:** Comfortable default with compact/dense toggle
- **Sunlight readability:** Operator UI must work outdoors

### Bounded Contexts (v0.1 Implementation Scope):

1. **BC01 Platform and Delivery** - Infrastructure, deployment, health checks
2. **BC02 Identity and Access** - Authelia SSO integration, operator tokens
3. **BC03 Core Regatta Management** - Regatta, events, entries CRUD
4. **BC04 Rules, Scheduling, and Draw** - Draw generation, bib assignment
5. **BC05 Public Experience and Delivery** - Public site, SSE, caching
6. **BC06 Operator Capture and Line Scan** - Camera UI, marker linking, offline sync
7. **BC07 Results and Adjudication** - Investigations, approvals, results
8. **BC08 Finance and Payments** - Entry fees, payment tracking
9. **BC09 Operability, Hardening, and Quality** - Observability, monitoring, runbooks

### Browser Support Matrix:

- **Desktop:** Chrome/Firefox (current + current-1), Safari/Edge (current stable major)
- **Mobile:** iOS Safari (current iOS), Chrome for Android (current), Samsung Internet (current)
- Support matrix reviewed quarterly

## Important Notes for Implementation

- **Event Sourcing:** All state changes are events; projections derive read models
- **Approval Gates:** Approved data becomes immutable (critical for audit trail)
- **Versioned Public URLs:** Public results use `/public/v{draw_revision}-{results_revision}/` pattern
- **Operator Offline:** Use service workers; queue actions; sync when online
- **SSE Connection State:** Show minimal Live/Offline indicator based on SSE connection
- **Line-scan Camera UI:** Overview image + draggable detail window for marker precision
- **Token Security:** One active operator station per token; PIN flow for second device

## Demo Mode (Out of Scope for v0.1)

Demo mode is planned but not part of v0.1. Deferred to post-v0.1 implementation.

## When You Need More Information

- **Product requirements:** Check `pdd/design/detailed-design.md` (primary) and `pdd/idea-honing.md`
- **UI/UX decisions:** Check `styleprompt.md` and `pdd/design/style-guide.md`
- **API design:** Check `pdd/design/openapi-v0.1.yaml`
- **Database schema:** Check `pdd/design/database-schema.md`
- **Implementation plan:** Check `pdd/implementation/plan.md` and bounded context files
- **Specific features:** Check corresponding `pdd/implementation/bc0X-*.md` file
- **Note:** Ignore `pdd/rough-idea.md` (superseded by detailed-design.md)

**Trust these instructions.** Only perform additional searches if the information here is incomplete or found to be in error.
