# Public-Delivery Domain — Overview

## What This Domain Owns

- Versioned public routes, revision discovery, and immutable cache policy for public schedule and results pages.
- Server-Sent Events delivery for live revision updates.
- Public-facing presentation concerns including locale-aware formatting, design tokens, table primitives, and printable export surfaces.

## Boundaries

- It does not own the underlying regatta, draw, finance, or adjudication rules that feed public content.
- It relies on `identity-access` for public-session bootstrap rather than owning session security policy.
- It does not own line-scan ingest or operator workflows.

## Key Files

| File | Purpose |
|:-----|:--------|
| `apps/backend/src/main/java/com/regattadesk/public_api/PublicVersionsResource.java` | Revision discovery endpoint for public clients. |
| `apps/backend/src/main/java/com/regattadesk/public_api/PublicVersionedScheduleResource.java` | Immutable schedule route implementation. |
| `apps/backend/src/main/java/com/regattadesk/public_api/PublicVersionedResultsResource.java` | Immutable results route implementation. |
| `apps/backend/src/main/java/com/regattadesk/sse/RegattaSseResource.java` | SSE endpoint for live updates. |
| `apps/backend/src/main/java/com/regattadesk/formatting/DateTimeFormatters.java` | Backend date and time formatting utilities. |
| `apps/frontend/src/views/public/Schedule.vue` | Public schedule page. |
| `apps/frontend/src/views/public/Results.vue` | Public results page. |
| `apps/frontend/src/composables/useSseReconnect.js` | Client-side SSE reconnect policy. |

## Current State

- The backend already implements the public route families described in BC05, including version discovery and cache control.
- The frontend has dedicated public views, shared table primitives, print components, and formatting composables.
- Public-delivery documentation is spread across backend docs, style guide material, and testing docs, which makes this canonical domain documentation useful immediately.