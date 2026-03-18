# BC05 Public Experience and Delivery

## Scope
Public-facing versioned content delivery, live updates, UX primitives, accessibility, localization, and printable outputs.

## Functional Features to Implement
- Implement public versions endpoint with draw/results revision tracking.
- Implement bootstrap flow: call `/versions`; on `401`, call `/public/session`; retry `/versions` once.
- Implement public site versioned route strategy using `/public/v{draw}-{results}`.
- Implement public schedule pages backed by draw-dependent read models.
- Implement public results pages backed by result-dependent read models.
- Implement regatta SSE stream with `snapshot`, `draw_revision`, and `results_revision` event types.
- Implement Live/Offline indicator driven only by SSE connection state.
- Implement design tokens and table primitives per `pdd/design/style-guide.md`.
- Implement i18n and formatting support (`nl`/`en`, locale display formatting, regatta-local timezone).
- Implement printable A4 PDF outputs with required monochrome-friendly header metadata.

## Non-Functional Features to Implement
- Enforce cache headers:
- `/public/session`: `Cache-Control: no-store`
- `/public/regattas/{regatta_id}/versions`: `Cache-Control: no-store, must-revalidate`
- `/public/v{draw}-{results}/...`: `Cache-Control: public, max-age=31536000, immutable`
- Enforce SSE reconnect strategy: min 100ms, base 500ms, cap 20s, full jitter, retry forever.
- Enforce deterministic SSE event IDs and per-client connection cap.
- Meet public accessibility target: WCAG 2.2 AA minimum (aim AAA).
- Enforce technical date format standard `YYYY-MM-DD` (ISO 8601) in docs/APIs.
- Use 24-hour formatting defaults where specified.
- Keep public versioned payloads immutable and CDN-friendly.

## Plan Coverage
- Step 7
- Step 8
- Step 9 (shared ownership with BC06 for operator-specific high-contrast behavior)
- Step 10
- Step 11
- Step 21
- Step 22
