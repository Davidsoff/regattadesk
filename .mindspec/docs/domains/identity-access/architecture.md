# Identity-Access Domain — Architecture

## Key Patterns

- Authentication happens at the edge through Traefik and Authelia; business services trust identity only after that boundary.
- Backend security is split into extraction, sanitization, principal construction, and role enforcement.
- Public read flows use anonymous JWT cookies rather than personal accounts, with key rotation managed by the backend.

## Invariants

- Protected requests must only trust forwarded identity headers on explicitly trusted routes.
- Public-session cookies must remain `HttpOnly`, `Secure`, `SameSite=Lax`, and scoped to the configured path and TTL.
- JWT signing keys must support overlap so rotation does not break active public sessions.
- Role names must stay aligned across Authelia, backend filters, and route protection docs.

## Design Decisions

- The project centralizes staff authentication at the edge to keep backend services focused on role enforcement and domain logic.
- Anonymous public traffic uses a lightweight JWT bootstrap so public delivery can stay cache-friendly without introducing user accounts.
- Operator flows combine edge-protected staff routes and operator-token flows instead of a full account system for station users.
