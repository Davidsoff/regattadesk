# Public-Delivery Domain — Architecture

## Key Patterns

- Public clients first discover the current revision tuple, then fetch immutable content under `/public/v{draw}-{results}/...`.
- Live updates are delivered through SSE rather than page-level polling.
- Formatting and print behavior are shared across backend and frontend through explicit utilities and design guidance.

## Invariants

- `/public/session` and `/public/regattas/{id}/versions` must stay non-cacheable while versioned content remains immutable and long-lived.
- Public schedule and results URLs must reflect the current `draw_revision` and `results_revision` tuple.
- SSE clients must tolerate reconnects using the documented backoff behavior.
- Locale, timezone, and print formatting must remain consistent with the PDD and shared docs.

## Design Decisions

- The public surface separates bootstrap and content fetches so CDN caching can be aggressive without invalidation APIs.
- SSE is used for low-friction live updates while preserving cacheable page payloads.
- Formatting and print behaviors live beside the public surface because they are part of the product contract exposed to users.