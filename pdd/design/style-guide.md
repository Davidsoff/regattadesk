Version: v2
Last Updated: 2026-02-06
Author: RegattaDesk Team

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
8. **Staff accessibility:** no hard requirement, but avoid obviously inaccessible patterns (focus visibility, contrast, touch targets).

---

## 2) Visual foundations (tokens)

### 2.1 Color system
- Default vibe: calm, trustworthy “instrumentation”.
- Public pages must meet **WCAG 2.2 AA**; aim **AAA** where feasible for key public results/schedule flows.
- Operator surface defaults to **high-contrast mode** for sunlight/outdoor use, with a toggle back to standard; persist per-device.

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
Note: entry status values are primary domain states (DSQ is a status, not a separate flag); “under_investigation”, “approved/immutable”, “offline_queued”, and “provisional/edited/official” are derived workflow/UI states.

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
- Result labels:
  - `provisional`: neutral outline + “clock” icon
  - `edited`: info + “edit” icon
  - `official`: success + “check/seal” icon

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
- Time precision: default display precision is milliseconds (`.mmm`); round half-up to the configured precision; rankings use the actual (unrounded) time.
- Time formats:
  - Scheduled times: `HH:mm` (24h).
  - Elapsed times: `M:SS.mmm` (or `H:MM:SS.mmm` when ≥1h).
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
  - **Decision: Option B - boat-type tabs + category list**
  - Rationale: Tabs provide better touch navigation than horizontal scroll, which can be awkward on mobile devices. This pattern scales better when the matrix has many boat types.
  - Implementation: Top-level boat-type tabs (e.g., 1x, 2x, 4+, 4x, 8+) with a vertical list of categories below the selected tab, showing event label + count.
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
  - linked-but-unapproved markers remain editable (move/unlink/delete)
  - approved/immutable markers show a lock and disable destructive controls
- Overview + detail:
  - overview strip with draggable detail window for fine alignment
  - selecting an unlinked marker recenters the detail view
- Detail loupe:
  - draggable magnifier window
  - fixed zoom steps (don’t rely on pinch-only)

### 3.9 Second-device PIN access request (non-interrupting)
- Present as toast/banner with a non-blocking “Show PIN” action (no Approve/Deny).
- Active station can reveal the matching PIN to complete handover; admin flow is a fallback if the active station can’t access the PIN.
- After handoff, the previous device is read-only and shows copy like “Read-only — re-auth to take control.”
- Token display never overlays the scan area; use a drawer or separate station screen.

### 3.10 Public results cards (mobile-first)
- Card rows: Rank, Crew/Club, Time, Delta, Status.
- Delta: time behind leader; compute from unrounded times, then round to display precision. Format `+M:SS.mmm` (or `+H:MM:SS.mmm` when >=1h); leader shows `+0:00.000`.
- Ordering/tie-breaks (public): rank by elapsed time including penalties; ties share rank; secondary sort by start time then bib; non-finish statuses appear after ranked entries.
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
- Finance: invoice-like layout; export actions grouped; audit trail for payment status changes.

### 4.2 Operator
- Capture: one primary task per screen; minimal chroming.
- Marker list: “unlinked first”; big “Link bib” CTA; fast correction.
- Offline queue: list with retry states; clear conflict resolution.
- Token view: separate screen/drawer; never blocks capture.

### 4.3 Public
- Schedule: scannable timeline; “now/next” highlight.
- Results: stable ordering; row highlight for updates; Live/Offline indicator (SSE only).
- withdrawn_before_draw entries are not shown on public schedule/results; staff-only via filters/audit history.
- Versioning: banner “New official results available → View” (non-modal).

### 4.4 Print (admin-generated PDFs, A4, mostly monochrome)
- Header on every page:
  - regatta name
  - generated timestamp (ISO 8601, e.g. `2026-02-06T14:30:00+01:00`; final display can be locale-specific)
  - draw/results revision
  - page number
- Typography: 10–11pt body, 8–9pt meta; avoid light weights.
- Use rules and light zebra striping; never rely on color.

---

## 5) Content guidelines
- Voice: calm, direct, operational (“Queued”, “Official”, “Needs approval”).
- Public: expand acronyms on first appearance, e.g. “DNS (Did Not Start)”.
- Confirmations: include consequence (“This will publish Results v13”).
- Screenshot placeholders in all PDD docs must use: `Screenshot: [Brief description] - TODO: Add image`

---

## 6) Accessibility checklist (public-first)
- Color contrast: WCAG 2.2 AA minimum for public; aim AAA for key schedule/results tables where feasible.
- Keyboard: all public functionality usable with keyboard; visible focus; no focus traps.
- Tables: correct semantics (caption/headers/scope), SR-friendly labels for dense data.
- Touch targets: ≥44px everywhere; operator defaults ≥52px.
- Reduced motion: respect `prefers-reduced-motion`.

### 6.1 Accessibility compliance and testing requirements
- Compliance baseline: WCAG 2.1 AA for release gating.
- Target level: WCAG 2.2 AA where criteria apply, without regressing 2.1 AA compliance.
- Automated testing (required in CI):
  - Run `axe-core` checks on key public pages (schedule, results, event detail) and core staff/operator flows.
  - Run Lighthouse accessibility audits on public schedule and results pages.
  - Fail CI on critical or serious accessibility violations.
- Manual testing (required before release):
  - Keyboard-only navigation pass on public, staff, and operator critical flows.
  - Screen reader smoke test with NVDA (Windows) and VoiceOver (macOS/iOS) on public results and operator capture.
  - Zoom/reflow test at 200% and mobile viewport checks for clipping/overlap.
  - Contrast verification for normal and `data-contrast="high"` modes.
- Evidence and tracking:
  - Store automated scan reports as CI artifacts.
  - Track manual findings in release checklist with owner and remediation status.

---

## 7) Implementation notes for Vue
- Keep tokens in one global CSS file; theme via attributes on `<html>`:
  - `data-contrast="high"`
  - `data-density="compact"`
- Build a small headless component set:
  - `RdTable`, `RdChip`, `RdBanner`, `RdToast`, `RdDrawer`, `RdMatrix`
- Ensure numeric/time cells use tabular numerals and consistent alignment.

---

## 8) Staff Accessibility Pattern Guide

### Keyboard Navigation
- **Focus management**: Logical tab order matching visual layout
- **Focus indicators**: Visible focus ring (2px solid, high contrast)
- **Skip links**: Skip to main content, skip to filters on list pages
- **Keyboard shortcuts**:
  - `Esc`: Close modal/drawer, cancel action
  - `Enter`: Confirm action, open selected
  - `Arrow keys`: Navigate within tables, grids, and lists
  - `Ctrl/Cmd + F`: Focus search
- **Trap focus**: Modals trap focus; Escape releases to trigger element

### Screen Reader Support
- **ARIA labels**: All interactive elements have descriptive labels
- **Live regions**: Use `aria-live` for status updates (e.g., "12 entries selected")
- **Announcements**: Toast notifications announced via `role="status"`
- **Table accessibility**: Use proper `<th scope="col|row">` for matrices
- **Form labels**: All inputs have associated `<label>` or `aria-label`
- **Error messages**: Linked via `aria-describedby`

### High Contrast Mode
- **data-contrast="high"** attribute on `<html>`
- **Minimum contrast**: 4.5:1 for text, 3:1 for large text
- **Border visibility**: All borders visible in high contrast
- **Focus states**: Enhanced focus indicator in high contrast

### Reduced Motion
- **prefers-reduced-motion**: Respect user preference
- **Transitions**: Replace with instant state changes
- **Animations**: Disable auto-playing animations
- **Flashing content**: Avoid; max 3 flashes per second

### Touch Accessibility (Mobile/Tablet)
- **Touch targets**: Minimum 44x44 CSS pixels
- **Spacing**: 8px minimum between touch targets
- **Gesture alternatives**: All gestures have tap/double-tap alternatives
- **Drag and drop**: Always provide alternative (e.g., "Move to" button)

### Color Independence
- **No color-only communication**: Use icons + text + patterns
- **Status indicators**: "Error", "Warning", "Success" labels alongside colors
- **Charts/graphs**: Include data labels or accessible tables
- **Form validation**: Icons + text error messages

### Focus Management Patterns

| Pattern | Implementation |
|---------|----------------|
| Page load | Focus to main heading or first interactive element |
| Modal open | Focus to first focusable element in modal |
| Modal close | Focus back to trigger element |
| Filter applied | Announce results count via live region |
| Navigation | Current page indicated in nav with `aria-current="page"` |
| Data loading | Show loading state, announce when complete |
| Error state | Focus to error summary or first error |

### Staff-Specific Accessibility Considerations

**Operator Timekeeping Interface:**
- Large touch targets for marker placement
- Keyboard shortcuts for common actions (add marker, link, approve)
- Screen reader announcements for marker creation/linking
- High contrast mode for outdoor visibility

**Staff Admin Interfaces:**
- Full keyboard navigation for all CRUD operations
- Error messages linked to form fields
- Confirmation dialogs announced to screen readers
- Tables with proper headers and row descriptions

**Public-Facing Components:**
- Results tables: Sortable columns with `aria-sort`
- Event matrix: Cell descriptions via `aria-label`
- Live status: Text-based "Live" indicator (not just color)

### Testing Accessibility
- **Automated**: axe-core in CI pipeline
- **Manual**: Keyboard navigation testing weekly
- **Screen reader testing**: NVDA (Windows), VoiceOver (macOS)
- **User testing**: Include users with disabilities in QA
