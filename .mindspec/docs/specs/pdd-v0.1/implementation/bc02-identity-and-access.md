# BC02 Identity and Access

## Scope
Authentication, authorization, identity propagation, and anonymous public-session security model.

## Functional Features to Implement
- Integrate Authelia SSO at Traefik edge for staff/operator/admin surfaces.
- Implement forwarded identity propagation from edge to backend APIs.
- Implement role mapping model: `regatta_admin`, `head_of_jury`, `info_desk`, `financial_manager`, and global `super_admin`.
- Implement `POST /public/session` anonymous session bootstrap endpoint returning `204`.
- Implement anonymous JWT cookie issuance and refresh behavior for public clients.
- Implement key-rotation model using `kid` and two active keys with required overlap.
- Implement bootstrap retry path where `/versions` `401` triggers `/public/session` then one retry.

## Non-Functional Features to Implement
- Enforce secure cookie attributes: `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, `Max-Age=5d`.
- Enforce idempotent refresh window at 20% of 5-day TTL.
- Enforce JWT signing constraints: `HS256` with overlapping active keys for safe rotation.
- Maintain least-privilege authorization boundaries by role.
- Keep authentication behavior deterministic and safe under retries.

## Plan Coverage
- Step 2
- Step 6
- Shared dependency for Step 7 and Step 22 bootstrap flow
