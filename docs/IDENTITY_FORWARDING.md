# Identity Forwarding Contract

## Overview

RegattaDesk uses Authelia SSO at the Traefik edge for authentication and authorization. Authenticated identity and role claims are forwarded to backend services via HTTP headers, establishing a trust boundary between edge authentication and backend services.

## Trust Boundary

### Edge Authentication (Traefik + Authelia)
- **Responsibility**: Authenticate users, verify credentials, enforce access control policies
- **Output**: Forwarded identity headers to trusted backend services
- **Trust Level**: Full authority over authentication and session management

### Backend Services (Quarkus)
- **Responsibility**: Consume forwarded identity headers, enforce business-level authorization
- **Input**: Trusted identity headers from edge (NOT directly from clients)
- **Trust Level**: Trust headers only when received from Traefik (internal network)

## Forwarded Header Contract

When a request is authenticated by Authelia, the following headers are forwarded to backend services:

### Header Specifications

| Header Name | Description | Format | Example |
|------------|-------------|--------|---------|
| `Remote-User` | Username/login identifier | String | `regattaadmin` |
| `Remote-Groups` | User's group memberships (roles) | Comma-separated list | `regatta_admin` |
| `Remote-Name` | User's display name | String | `Regatta Admin` |
| `Remote-Email` | User's email address | Email string | `regattaadmin@regattadesk.local` |

### Header Presence

- **Authenticated requests**: All headers are present
- **Unauthenticated requests**: Headers are absent (request is blocked by Traefik/Authelia before reaching backend)
- **Forged headers**: Blocked by network architecture (backend only accessible via Traefik on internal network)

## Role Mapping Model

RegattaDesk defines the following roles in the `Remote-Groups` header:

### Regatta-Scoped Roles

- **`regatta_admin`**: Full access within a specific regatta scope
  - Manage regatta configuration, events, entries
  - Perform draw, publish results
  - Admin-level operations within regatta

- **`head_of_jury`**: Jury and adjudication authority
  - Approve entries
  - Close investigations
  - Assign penalties, exclusions, disqualifications
  - Approve final results

- **`info_desk`**: Information desk operations
  - Crew mutations
  - Missing/changed bibs
  - Withdrawals (DNS, DNF status updates)

- **`financial_manager`**: Financial operations
  - Mark entries as paid/unpaid
  - Bulk payment status updates per club
  - Generate invoices

- **`operator`**: Line-scan camera operators
  - Access operator PWA interface
  - Create and link timing markers
  - Offline-capable capture workflows

### Global Roles

- **`super_admin`**: Global administrative authority
  - Access to all regattas
  - Promote regatta-owned rulesets to global selection
  - System-wide administrative operations

## Backend Integration

### Header Extraction

Backend services MUST:
1. Extract identity headers from incoming requests
2. Validate that headers are present (for protected endpoints)
3. Parse `Remote-Groups` to extract role list
4. Build an application principal/security context

### Authorization Enforcement

Backend services enforce authorization using:
1. **Edge-level**: Traefik + Authelia block unauthenticated/unauthorized requests
2. **Endpoint-level**: Backend validates required roles per endpoint
3. **Business-level**: Domain logic enforces regatta-scoped permissions

Example authorization flows:

#### Staff Endpoint
```
Client → Traefik → Authelia (verify session) → Backend
                    ↓ (if authenticated)
                Forward headers → Backend validates role
```

#### Public Endpoint (Bypass)
```
Client → Traefik → Backend (no Authelia, no headers)
```

### Deny-by-Default Behavior

- Protected routes require authentication by default
- Unauthenticated requests are rejected at the edge (HTTP 401)
- Unauthorized roles are rejected at the edge (HTTP 403)
- Backend assumes all requests with forwarded headers are authenticated

## Security Considerations

### Network Isolation

- Backend services are on `regattadesk-internal` network (isolated)
- Only Traefik can route to backend services
- External clients cannot directly access backend (no port exposure to host)

### Header Validation

Backend services MUST:
- Only trust headers when received via internal network from Traefik
- Reject or strip client-supplied headers matching the identity header names
- Use a middleware/filter to enforce this at the application entry point

### Role-Based Access Control (RBAC)

Backend services MUST:
- Validate required roles for each endpoint
- Support regatta-scoped authorization (e.g., `regatta_admin` for regatta X, not Y)
- Honor `super_admin` as global override for regatta-scoped operations

## Testing Strategy

### Edge Integration Tests

1. **Authenticated Access**: Valid session with appropriate role
   - Verify request succeeds
   - Verify headers are forwarded correctly

2. **Unauthenticated Access**: No session
   - Verify request is blocked (HTTP 401)
   - Verify no headers are forwarded

3. **Unauthorized Role**: Valid session with wrong role
   - Verify request is blocked (HTTP 403)

4. **Forged Headers**: Direct request with forged identity headers
   - Verify headers are stripped/rejected (network isolation prevents this scenario)

### Backend Unit Tests

1. **Header Parsing**: Validate principal extraction from headers
2. **Role Validation**: Verify endpoint guards for each role
3. **Missing Headers**: Verify safe handling when headers are absent

## References

- [Authelia Configuration](../infra/compose/authelia/configuration.yml)
- [Traefik Dynamic Configuration](../infra/compose/traefik/dynamic.yml)
- [Docker Compose Stack](../infra/compose/docker-compose.yml)
- [BC02 Identity and Access](../pdd/implementation/bc02-identity-and-access.md)
- [Detailed Design - Authentication](../pdd/design/detailed-design.md#authentication-and-authorization)

## Status

✅ **Implemented** - BC02-001 Complete

- Edge SSO integration with Authelia
- Role mapping model defined
- Identity header forwarding configured
- Deny-by-default behavior enforced
- Documentation complete
