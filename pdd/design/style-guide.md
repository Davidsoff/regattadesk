Version: v1 (2026-02-01)

# RegattaDesk v0.1 Style Guide (Implementation Spec)

This document defines the v0.1 visual identity, design tokens, and component/page patterns for RegattaDesk’s three UX surfaces:

- **Staff Web (authenticated, desktop-first):** organizers, jury, info desk, finance.
- **Operator PWA (token/QR, offline-capable):** start/finish line-scan capture and marker→bib linking, used outdoors and on small devices.
- **Public Web (anonymous, high-traffic):** schedule + live-updating results (cacheable, versioned URLs).

Assumptions (confirmed):
- Use the **“Calm Instrument”** direction for v0.1.
- Use **CSS variables + headless Vue components** (no heavy UI library requirement).
- No “alternatives”/multiple visual directions yet.

---

## 1) Design principles
1. **Evidence over assertion:** show *why* a status/result is what it is (audit trail, immutable markers, approvals).
2. **Scan first:** dense tables are primary; optimize for fast parsing (alignment, sticky headers, tabular numerals).
3. **State is explicit:** provisional vs official vs queued/offline must be unmistakable.
4. **Prevent errors:** block invalid actions; confirm destructive/batch actions; show consequences.
5. **Outdoor-readable:** operator UI defaults to high contrast, large targets, and minimal glare-prone chrome.
6. **Non-interrupting flows:** operator capture must not be blocked by token/PIN flows.
7. **Accessible is faster:** strong focus and predictable navigation reduce mistakes under pressure.

---

## 2) Visual foundations (tokens)

### 2.1 Color system
- Default vibe: calm, trustworthy “instrumentation”.
- Public pages must meet **WCAG 2.2 AA**; aim **AAA** where feasible for key public results/schedule flows.
- Operator should expose a **high-contrast mode** for sunlight/outdoor use.

#### 2.1.1 CSS variables (canonical)
```css
:root {
  /* Neutrals */
  --rd-bg: #ffffff;
  --rd-surface: #f8fafc;
  --rd-surface-2: #eef2f7;
  --rd-border: #d0d7e2;
  --rd-text: #0b1220;
  --rd-text-muted: #475569;

  /* Accents */
  --rd-accent: #2563eb;     /* primary */
  --rd-accent-2: #0f766e;   /* secondary */

  /* Semantic */
  --rd-info: #2563eb;
  --rd-success: #166534;
  --rd-warn: #a16207;
  --rd-danger: #b91c1c;

  /* Focus */
  --rd-focus: #2563eb;
  --rd-focus-ring: 0 0 0 3px rgba(37, 99, 235, 0.35);

  /* Elevation (restrained) */
  --rd-shadow-1: 0 1px 2px rgba(15, 23, 42, 0.08);

  /* Spacing */
  --rd-space-1: 4px;
  --rd-space-2: 8px;
  --rd-space-3: 12px;
  --rd-space-4: 16px;
  --rd-space-5: 24px;
  --rd-space-6: 32px;

  /* Touch targets */
  --rd-hit: 44px;           /* minimum touch target */
  --rd-hit-operator: 52px;  /* operator default */
}

/* Operator “sunlight/high-contrast” mode */
:root[data-contrast="high"] {
  --rd-bg: #ffffff;
  --rd-surface: #ffffff;
  --rd-surface-2: #f1f5f9;
  --rd-border: #0b1220;
  --rd-text: #0b1220;
  --rd-text-muted: #111827;
  --rd-focus-ring: 0 0 0 4px rgba(37, 99, 235, 0.45);
}

/* Density */
:root[data-density="compact"] {
  --rd-space-1: 3px;
  --rd-space-2: 6px;
  --rd-space-3: 10px;
  --rd-space-4: 14px;
}
```

#### 2.1.2 Status chip mapping (domain)
All chips must be understandable in monochrome print: **label + icon (optional) + shape**, not color alone.

- Entry statuses (v0.1):
  - `active`: neutral outline
  - `withdrawn_before_draw`: muted (“not racing”)
  - `withdrawn_after_draw`: warn
  - `dns`: warn
  - `dnf`: warn
  - `excluded`: danger
  - `dsq`: danger (strongest)
- Investigation/approval/immutability:
  - `under_investigation`: info + “investigation” icon
  - `approved` / `immutable`: success + lock icon
  - `offline_queued`: muted + “queued” icon (never green)

### 2.2 Typography
- UI font: `Inter` (fallback: system-ui). Keep weights conservative (400/500/600).
- Numeric/time font: `JetBrains Mono` (fallback: ui-monospace).
- All numeric/time/bib columns: `font-variant-numeric: tabular-nums;`.
- Suggested base sizing (base 16px):
  - Staff: 14–16 body, 12–13 meta
  - Operator: 16–18 body, 14–16 meta, large buttons
  - Public: 16 body, 18–20 section headers

### 2.3 Layout, density, and breakpoints
- Minimum supported width: **320px** (iPhone SE class).
- Breakpoints: `sm 480`, `md 768`, `lg 1024`, `xl 1280`.
- Default density: comfortable; provide compact toggle via `data-density="compact"`.
- Operator default hit target: `--rd-hit-operator` (52px).

### 2.4 Iconography
- Consistent 2px stroke set; filled icons reserved for critical statuses.
- Icons must have adjacent text labels in tables unless meaning is unambiguous and there is an accessible tooltip.

### 2.5 Motion
- Motion only to clarify state transitions (SSE reconnect, queue sync).
- Keep durations short (120–180ms) and respect `prefers-reduced-motion`.
- Do not animate result re-ordering by default; instead, briefly highlight changed rows.

### 2.6 Elevation + surfaces
- Prefer flat surfaces with subtle borders; reserve shadows for overlays (modals/drawers).
- Focus rings must always be visible and not depend on background color.

---

## 3) Component specs (domain-specific)

### 3.1 Tables (Staff + Public)
- Sticky header; optional sticky first column (bib/crew).
- Numeric columns right-aligned; times in tabular numerals.
- Row states: hover (desktop), focus-visible ring (keyboard), selected (bulk actions).
- Empty state: “No entries match filters” + “Clear filters”.
- Loading state: skeleton rows (keep column alignment stable).

### 3.2 Event selection matrix (boat type × category grid)
Purpose: very fast event selection for experienced users while remaining accessible.

- Semantics: a real `<table>` with `<th scope="col|row">`.
- Cell contents: event label (short) + count, e.g. `J18 2x (52)`.
- Desktop interaction:
  - Hover highlights row/column.
  - Arrow keys move cell focus; Enter opens.
- Mobile behavior:
  - Option A: horizontal scroll with sticky left header.
  - Option B: boat-type tabs + category list (preferred if matrix becomes too wide).
- Accessibility:
  - Each cell has an `aria-label` like “Junior 18 Double Sculls, 52 entries”.
  - Strong focus ring and clear selected state.

### 3.3 Filters/search + bulk actions
- Filters visible by default on staff list pages; collapsible only on small screens.
- Bulk actions must show a consequence summary (“Mark 12 entries as DNS?”).
- For destructive batch changes: 2-step confirm or typed confirmation (`DNS`, `DSQ`).

### 3.4 Status chips/badges
- Standard anatomy: optional icon + label + optional count.
- Always include a text label in dense tables (no icon-only chips).
- Must support additional states:
  - “Provisional”
  - “Edited”
  - “Official”

### 3.5 Forms + validation (incl. 409 conflicts)
- Inline validation for format; server-side errors summarized at the top.
- On 409 optimistic concurrency:
  - explain “This changed since you opened it”
  - show what changed (field diff if available)
  - actions: Reload / Apply anyway (only when safe) / Copy my edits

### 3.6 Notifications/toasts/banners
- Operator: persistent top banner for **Offline (queued: N)** with tap-to-open queue.
- Public: minimal **Live / Offline** indicator reflecting **SSE state only** (no claims about freshness beyond connection state).

### 3.7 Timelines/audit views
- Default: chronological list with “who / what / when”.
- Provide copyable event id and “show related changes”.

### 3.8 Operator line-scan capture UI
- Layout: full-screen evidence + bottom action bar (thumb-reachable).
- Marker workflow:
  - tap to create; drag handles for adjustments; large hit areas
  - “Undo” always visible
  - linked/approved markers show lock and disable destructive controls
- Detail loupe:
  - draggable magnifier window
  - fixed zoom steps (don’t rely on pinch-only)

### 3.9 Second-device PIN access request (non-interrupting)
- Present as toast/banner with countdown and “Approve / Deny”.
- Token display never overlays the scan area; use a drawer or separate station screen.

### 3.10 Public results cards (mobile-first)
- Card rows: Rank, Crew/Club, Time, Delta, Status.
- Provide quick filters (Event, Category, Club search).
- Version banner shows “Draw vX, Results vY” and links to the canonical versioned URL.

---

## 4) Page patterns (key screens)

### 4.1 Staff
- Dashboard: high-signal tiles (entries, started/finished, investigations pending, approvals pending, offline operators).
- Entries list: table + filters + bulk status changes + audit sidebar.
- Draw publish: preview + diff to previous + explicit “Publishing increments draw_revision”.
- Investigations: queue + per-entry evidence + outcome, gated by approvals.
- Approvals: “inbox” view for pending approvals; irreversible actions labeled.
- Finance: invoice-like layout; export actions grouped; audit trail for refunds/withdrawals.

### 4.2 Operator
- Capture: one primary task per screen; minimal chroming.
- Marker list: “unlinked first”; big “Link bib” CTA; fast correction.
- Offline queue: list with retry states; clear conflict resolution.
- Token view: separate screen/drawer; never blocks capture.

### 4.3 Public
- Schedule: scannable timeline; “now/next” highlight.
- Results: stable ordering; row highlight for updates; Live/Offline indicator (SSE only).
- Versioning: banner “New official results available → View” (non-modal).

### 4.4 Print (admin-generated PDFs, A4, mostly monochrome)
- Header on every page:
  - regatta name
  - generated timestamp (`dd-MM-yyyy HH:mm`)
  - draw/results revision
  - page number
- Typography: 10–11pt body, 8–9pt meta; avoid light weights.
- Use rules and light zebra striping; never rely on color.

---

## 5) Content guidelines
- Voice: calm, direct, operational (“Queued”, “Official”, “Needs approval”).
- Public: expand acronyms on first appearance, e.g. “DNS (Did Not Start)”.
- Confirmations: include consequence (“This will publish Results v13”).

---

## 6) Accessibility checklist (public-first)
- Color contrast: WCAG 2.2 AA minimum for public; aim AAA for key schedule/results tables where feasible.
- Keyboard: all public functionality usable with keyboard; visible focus; no focus traps.
- Tables: correct semantics (caption/headers/scope), SR-friendly labels for dense data.
- Touch targets: ≥44px everywhere; operator defaults ≥52px.
- Reduced motion: respect `prefers-reduced-motion`.

---

## 7) Implementation notes for Vue
- Keep tokens in one global CSS file; theme via attributes on `<html>`:
  - `data-contrast="high"`
  - `data-density="compact"`
- Build a small headless component set:
  - `RdTable`, `RdChip`, `RdBanner`, `RdToast`, `RdDrawer`, `RdMatrix`
- Ensure numeric/time cells use tabular numerals and consistent alignment.
