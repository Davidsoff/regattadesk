# RegattaDesk Style Guide - LLM Prompt

You are an expert product designer + design-systems lead. Your job is to create a practical, implementable style guide for **RegattaDesk**, a web app for managing rowing head races (single distance) with three UX surfaces:

- **Staff Web (authenticated, desktop-first):** organizers, jury, info desk, finance.
- **Operator PWA (token/QR, offline-capable, field use):** start/finish line-scan camera UI for marker capture and linking markers to bibs.
- **Public Web (anonymous, high-traffic, cacheable):** schedule + live-updating results.

Backend is Quarkus + Postgres; frontend is Vue. The system emphasizes auditability (event sourcing), approvals, and real-time updates (SSE). Operators may work with unstable internet; actions can queue and sync later.

## Decisions already made (do not re-ask)

- No existing brand assets; the style guide must define the visual identity from scratch.
- Brand vibe: approachable, but dependable (calm, trustworthy, not "extreme sports"). Provide multiple visual directions before recommending one.
- Operator context: phone or tablet; may be outside (sun glare) or inside.
- Staff devices: tablet or laptop.
- Public audience devices: mostly phone, with some tablet/laptop.
- Logo direction: icon + wordmark.
- Operator platform support: iOS and Android; must be usable on all modern phones.
- Minimum supported phone size: iPhone SE class devices.
- Implementation preference: reusable components (design tokens + component specs). No UI stack preference; choose a sensible approach and justify it (e.g., CSS variables + headless components, or Tailwind + tokens).
- Accessibility: public pages should be WCAG 2.2 AA for all flows, with WCAG 2.2 AAA where feasible (especially key public results/schedule flows). Admin UI has no hard requirement (but avoid obviously inaccessible choices).
- Internationalization/locales:
  - Start with Dutch (nl) and English (en).
  - Future: French, German, Spanish, Italian, Polish (and potentially more).
  - 24h time.
  - Dates formatted as dd-MM-yyyy.
  - Time zones are regatta-local; future may add a toggle between viewer-local and regatta-local.
- Density: comfortable default with a compact/dense toggle.
- A known useful pattern: time-team.nl has an event/field selection "matrix" with boat types on the horizontal axis and gender/age categories on the vertical axis. Use this as an inspiration pattern for RegattaDesk event selection where relevant.
- Naming constraints: freeform.
- Printing: noticeboard printables are generated from the admin UI (not from the public site). Include design + style guidance for these printed pages. Assume A4 paper and mostly monochrome printers.

## Hard requirements

- You MUST **research competing products first** (web research). Do not guess; cite sources.
- After research, you MUST ask clarifying questions that are necessary to tailor the style guide.
- Then produce the style guide. If I do not answer questions, proceed with clearly-labeled assumptions.
- Optimize for: clarity under pressure, fast scanning of dense tabular data, error prevention, and accessibility.
- Accessibility target: WCAG 2.2 AA minimum for all public flows; aim for WCAG 2.2 AAA where feasible. State any exceptions explicitly.

## Domain context (from the product definition)

RegattaDesk v0.1 supports:

- Head races, sequential class starts; finishes can interleave.
- Draw is random (v0.1) with a stored seed; publishing draw increments `draw_revision`.
- Results publishing increments `results_revision`. Public URLs are versioned: `/public/v{draw_revision}-{results_revision}/...`.
- Public site uses SSE per regatta with snapshot-on-connect and revision "ticks". UI should show a minimal Live/Offline indicator (SSE state only).
- Entries have statuses: `active`, `withdrawn_before_draw`, `withdrawn_after_draw`, `dns`, `dnf`, `excluded`, `dsq`.
- Jury investigations: per entry; outcomes include no action, penalty (seconds), exclusion, DSQ; approvals gate what becomes immutable.
- Operator station model: one active station per token; a second device can request access via a PIN flow; token display must not interrupt active UI.
- Line-scan camera UI: overview image + draggable detail window; markers can be created/moved/deleted; linking markers to bibs is quick and correctable; approved/assigned markers become immutable.

## Step 1 - Competitor research (do this FIRST)

Research existing solutions in these categories (include at least 2-3 per category):

1) Rowing regatta/entries/draw/results systems (rowing-specific if possible).
2) Race timing + photo-finish / camera-based timing UIs (even if not rowing-specific).
3) Public live results / live timing experiences (mobile-first, high-read traffic).

You should explicitly include and review **time-team.nl** and its event/field selection matrix UI pattern.

For each competitor, capture:

- Product name, positioning, typical users
- Screenshots or UI descriptions (what the interface emphasizes)
- Notable patterns (tables, filters, status chips, workflows, error handling)
- Weaknesses / risks to avoid
- Design ideas worth borrowing (and why)

Deliverable: a short research summary plus a comparison matrix. Include citations/links for each competitor and for any claims that depend on sources.

## Step 2 - Clarifying questions (ask after research)

Ask only questions that materially change the style guide. Cover at least:

- Any remaining brand direction preferences within "approachable but dependable" (e.g., nautical hints vs neutral SaaS)?
- Target devices: glove use and any stylus support expectations?
- UI framework constraints: Tailwind vs CSS variables + headless components vs a component library (Vuetify, etc.)?
- Any other i18n constraints: translation tone (formal/informal), abbreviations for rowing classes, and how to handle long names in tables?
- Any must-have public a11y features beyond AA/AAA targets (e.g., skip links, keyboard-first table navigation, high-contrast mode)?
- Printing: any required headers/footers (event name, revision, timestamp) or federation compliance marks?

If I answer, adapt. If not, proceed with assumptions and provide 2 viable visual directions.

## Step 3 - Produce the style guide

Create a style guide that a Vue team can implement immediately. Include:

### 3.1 Design principles
- 5-8 principles tied to this domain (auditability, high-pressure ops, outdoor readability, etc.).

### 3.1.1 Visual directions (before finalizing)
Propose **3 distinct visual directions** that fit "approachable but dependable" (e.g., neutral editorial, subtle nautical, modern instrumentation). For each direction include:

- A short name + 2-3 sentence rationale
- A small palette (neutrals + 1-2 accents) with accessibility notes
- Typography pairing recommendation
- Example usage notes for Staff vs Operator vs Public

Then recommend one direction for v0.1 and explain why.

### 3.2 Visual foundations (with tokens)
- Color system: neutrals + accents, semantic colors (success/warn/error/info), and specific guidance for "status" badges (entry statuses + investigation/approval states).
- Typography: font recommendations, sizes, weights, line heights; include guidance for numeric/time data (tabular numerals).
- Spacing + layout: grid, spacing scale, breakpoints, density modes (staff vs operator vs public).
- Iconography: style, stroke/filled rules, when to use text labels.
- Motion: restrained rules (SSE updates, offline sync), durations/easings, reduced-motion behavior.
- Elevation + surfaces: cards/modals/drawers; focus/hover states.

Provide tokens as:

- CSS variables (preferred), and
- an optional Tailwind theme mapping (if Tailwind is chosen).

### 3.3 Components (domain-specific)
At minimum define guidance for:

- Tables (sorting, sticky headers, row density, zebra/hover, empty states)
- "Event selection matrix" component inspired by time-team.nl (boat type x gender/age grid): interaction, accessibility, and mobile behavior
- Filters/search, bulk actions with warnings (DNS batch warnings)
- Status badges/chips (entry status, "under investigation", "approved", "immutable", "offline queued")
- Forms + validation (409 optimistic concurrency, conflict messaging)
- Notifications/toasts/banners (operator warnings, public "Live/Offline")
- Timelines/audit views (event sourcing: "who/what/when", reversible actions)
- Operator line-scan UI patterns (marker creation, selection, lock/immutable state, detail window controls)
- PIN access request flow (second device) without interrupting active station
- Public results cards + ranking display (mobile-first)

### 3.4 Page-level patterns
Define patterns for these key areas:

- Staff: regatta dashboard; entries list; draw publish; investigations; approvals; finance.
- Operator: capture screen; marker list; link-to-bib flow; offline queue + sync state; token view (non-intrusive).
- Public: schedule; results; versions/live update behavior (including how to update URL versions without confusing users).
- Print (admin-generated PDFs): schedule/draw printouts, notices, results sheets; typography/spacing rules for paper.

### 3.5 Content guidelines
- Voice/tone, terminology (DNS/DNF/DSQ), error message style, confirmation wording.

### 3.6 Accessibility checklist
- Contrast targets, focus states, keyboard navigation, screen-reader labeling for dense tables, touch target sizes for operator UI, sunlight readability tips.

### 3.7 Implementation notes for Vue
- How to structure tokens, component APIs, theming approach, and a phased adoption plan.

## Output format

1) Competitor research summary + matrix (with citations).
2) Clarifying questions (bulleted).
3) Style guide (structured sections, with tokens in code blocks).
4) Assumptions + alternatives (if any).

Do not include marketing copy; keep it implementable and specific.
