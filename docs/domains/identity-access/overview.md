# Identity-Access Domain — Overview

## What This Domain Owns

- Edge authentication and authorization through Traefik and Authelia.
- Trusted identity propagation into the backend through forwarded `Remote-*` headers and backend security filters.
- Anonymous public-session JWT issuance, key rotation, and bootstrap behavior for the public site.

## Boundaries

- It does not own regatta business rules, public caching semantics, or operator capture logic.
- It authenticates and authorizes requests, but domain-specific permission outcomes still belong to the domain handling the request.
- It provides the security boundary for operator tokens and staff roles, but not the business workflows those actors execute.

## Key Files

| File | Purpose |
|:-----|:--------|
| `apps/backend/src/main/java/com/regattadesk/security/IdentityHeaderSanitizer.java` | Enforces the backend trust boundary for forwarded identity headers. |
| `apps/backend/src/main/java/com/regattadesk/security/RoleAuthorizationFilter.java` | Applies role-based authorization to protected endpoints. |
| `apps/backend/src/main/java/com/regattadesk/jwt/JwtTokenService.java` | Issues and validates anonymous public-session JWTs. |
| `infra/compose/authelia/configuration.yml` | Edge authentication and access control configuration. |
| `docs/core/IDENTITY_FORWARDING.md` | Canonical identity forwarding contract. |
| `docs/core/EDGE_SECURITY.md` | Edge TLS, header, and hardening posture. |

## Current State

- Identity forwarding, role mapping, and trust-boundary documentation are already present and aligned with the backend package layout.
- Public-session JWT support is implemented in the backend and referenced by the public bootstrap flow.
- This domain is mature enough to document as a stable interface for the rest of the repo.
