# Identity-Access Domain — Interfaces

## Contracts

- Forwarded header contract: `Remote-User`, `Remote-Groups`, `Remote-Name`, and `Remote-Email` on trusted routes.
- Public-session bootstrap contract: `POST /public/session` returns `204` and issues the anonymous JWT cookie used by public clients.
- Backend role contract: `regatta_admin`, `head_of_jury`, `info_desk`, `financial_manager`, `operator`, and `super_admin`.

## Integration Points

- `core-regatta`, `adjudication`, and `finance` consume staff identity through backend security filters on `/api/v1/...` routes.
- `public-delivery` depends on the public-session bootstrap before version discovery and immutable content fetches.
- `operator-capture` depends on protected operator endpoints plus operator-token validation for station flows.
