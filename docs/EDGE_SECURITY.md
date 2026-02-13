# Edge Security and TLS Configuration

**BC09-002 Implementation**  
**Last Updated:** 2026-02-13

## Overview

This document describes the edge security posture for RegattaDesk v0.1, including TLS configuration, security headers, rate limiting, and abuse prevention controls implemented at the Traefik edge proxy.

## Security Architecture

```
┌─────────────┐
│   Internet  │
└──────┬──────┘
       │ HTTPS (TLS 1.2+)
       ▼
┌─────────────────────────────────────────┐
│   Traefik Edge Proxy (v3.0)             │
│   ┌─────────────────────────────────┐   │
│   │  Security Middlewares            │   │
│   │  • Rate Limiting                 │   │
│   │  • Security Headers              │   │
│   │  • Request Size Limits           │   │
│   │  • Timeouts                      │   │
│   │  • Compression                   │   │
│   └─────────────────────────────────┘   │
└──────┬──────────────────────────────────┘
       │
       ├──► Public Endpoints (no auth)
       ├──► Authelia (SSO)
       ├──► Staff Endpoints (ForwardAuth)
       └──► Operator Endpoints (ForwardAuth)
```

## TLS Configuration

### Production TLS (Recommended)

For production deployments, use Let's Encrypt ACME with Traefik:

```yaml
# Add to traefik service in docker-compose.yml
command:
  - "--certificatesresolvers.letsencrypt.acme.email=admin@regattadesk.example"
  - "--certificatesresolvers.letsencrypt.acme.storage=/letsencrypt/acme.json"
  - "--certificatesresolvers.letsencrypt.acme.httpchallenge.entrypoint=web"
  - "--entrypoints.websecure.http.tls.certresolver=letsencrypt"
  - "--entrypoints.websecure.http.tls.domains[0].main=regattadesk.example"
  - "--entrypoints.websecure.http.tls.domains[0].sans=*.regattadesk.example"

volumes:
  - letsencrypt-certs:/letsencrypt
```

**TLS Version Support:**
- ✅ TLS 1.3 (preferred)
- ✅ TLS 1.2 (minimum)
- ❌ TLS 1.1 (disabled)
- ❌ TLS 1.0 (disabled)
- ❌ SSLv3 (disabled)

**Cipher Suites (Recommended):**
```yaml
command:
  - "--entrypoints.websecure.http.tls.options=modern@file"

# In traefik/dynamic.yml:
tls:
  options:
    modern:
      minVersion: VersionTLS12
      maxVersion: VersionTLS13
      cipherSuites:
        - TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
        - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
        - TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
        - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
        - TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305
        - TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305
      curvePreferences:
        - CurveP521
        - CurveP384
      sniStrict: true
```

### Development TLS (Self-Signed)

For local development:

```bash
# Generate self-signed certificate
mkdir -p infra/compose/traefik/certs
cd infra/compose/traefik/certs

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout localhost.key \
  -out localhost.crt \
  -subj "/C=NL/ST=Noord-Holland/L=Amsterdam/O=RegattaDesk Dev/CN=localhost.local"

# Add to traefik/dynamic.yml:
tls:
  certificates:
    - certFile: /etc/traefik/certs/localhost.crt
      keyFile: /etc/traefik/certs/localhost.key
```

### TLS Validation Checklist

- [ ] TLS 1.2 or higher enabled
- [ ] Strong cipher suites configured
- [ ] Weak ciphers disabled (RC4, DES, 3DES)
- [ ] HSTS header enabled with appropriate max-age
- [ ] Certificate validity monitored (expiry alerts)
- [ ] Certificate auto-renewal configured (Let's Encrypt)
- [ ] SNI (Server Name Indication) enabled
- [ ] OCSP stapling enabled (if supported)

## Security Headers

Implemented in `traefik/dynamic.yml` via `security-headers` middleware:

### Standard Security Headers

| Header | Value | Purpose |
|--------|-------|---------|
| `X-Content-Type-Options` | `nosniff` | Prevent MIME type sniffing |
| `X-Frame-Options` | `SAMEORIGIN` | Prevent clickjacking |
| `X-XSS-Protection` | `1; mode=block` | Enable browser XSS protection |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | Enforce HTTPS |
| `Content-Security-Policy` | `default-src 'self'; ...` | Control resource loading |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Control referrer information |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=(), payment=()` | Disable sensitive features |

### Content Security Policy (CSP)

Current CSP:
```
default-src 'self';
script-src 'self' 'unsafe-inline' 'unsafe-eval';
style-src 'self' 'unsafe-inline';
img-src 'self' data: https:;
font-src 'self' data:;
connect-src 'self';
frame-ancestors 'self'
```

**Notes:**
- `unsafe-inline` and `unsafe-eval` permitted for development (v0.1)
- **TODO (post-v0.1):** Remove unsafe directives, use nonce-based CSP
- `img-src https:` allows external images (line-scan tiles from MinIO)

### Testing Security Headers

```bash
# Test security headers
curl -I http://localhost/api/health

# Should include:
# X-Content-Type-Options: nosniff
# X-Frame-Options: SAMEORIGIN
# X-XSS-Protection: 1; mode=block
# Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
# Content-Security-Policy: ...
# Referrer-Policy: strict-origin-when-cross-origin
# Permissions-Policy: ...
```

## Rate Limiting

Three rate limit profiles implemented:

### Public Endpoints
- **Average:** 100 requests/second
- **Burst:** 200 requests
- **Period:** 1 second
- **Applied to:** `/api/v1/public`, `/api/health`, `/q/health`, `/q/metrics`

### Staff Endpoints
- **Average:** 50 requests/second
- **Burst:** 100 requests
- **Period:** 1 second
- **Applied to:** `/api/v1/staff`

### Operator Endpoints
- **Average:** 30 requests/second
- **Burst:** 60 requests
- **Period:** 1 second
- **Applied to:** `/api/v1/regattas/*/operator`

### Rate Limit Response

When exceeded:
- **HTTP Status:** 429 Too Many Requests
- **Headers:** `X-Rate-Limit-*` (if configured)
- **Retry-After:** Indicates when to retry

### Tuning Rate Limits

Monitor and adjust based on:
1. **Traffic patterns** - Observe normal vs peak traffic
2. **User complaints** - Legitimate users getting 429s
3. **Attack patterns** - Abusive traffic characteristics
4. **Resource capacity** - Backend can handle sustained load

```bash
# Monitor rate limit hits
docker compose logs traefik | grep "429"

# Check request patterns
docker compose logs traefik | awk '{print $10}' | sort | uniq -c | sort -rn
```

## Request Size Limits

**Maximum request body size:** 10 MB

Protects against:
- Resource exhaustion attacks
- Memory overflow
- Disk space exhaustion
- Slow upload attacks

**Implementation:**
```yaml
request-size-limit:
  buffering:
    maxRequestBodyBytes: 10485760  # 10 MB
```

**Rejected with:**
- HTTP 413 Payload Too Large

### Exceptions

For endpoints requiring larger payloads (e.g., photo uploads), create specific middleware:

```yaml
large-upload-limit:
  buffering:
    maxRequestBodyBytes: 52428800  # 50 MB for photos
```

## Timeout Configuration

### Connection Timeouts

| Timeout | Value | Purpose |
|---------|-------|---------|
| `dialTimeout` | 30s | Time to establish TCP connection |
| `responseHeaderTimeout` | 60s | Time to receive response headers |
| `idleConnTimeout` | 90s | Time before idle connection closes |

### Special Cases

**SSE (Server-Sent Events):**
- `responseHeaderTimeout`: 0 (disabled for streaming)
- `idleConnTimeout`: 300s (5 minutes)

## Compression

Gzip compression enabled for text-based responses:

**Compressed:**
- `text/*`
- `application/json`
- `application/javascript`
- `application/xml`

**Excluded:**
- `image/jpeg`, `image/png`, `image/gif`
- `video/*`
- `application/octet-stream`

**Benefits:**
- Reduced bandwidth usage (50-70% for JSON)
- Faster load times for clients
- Lower CDN costs

## Middleware Chains

Pre-configured chains combining multiple protections:

### Public Chain
```yaml
public-chain:
  chain:
    middlewares:
      - security-headers
      - rate-limit-public
      - request-size-limit
      - request-timeout
      - compress-response
```

### Staff Chain
```yaml
staff-chain:
  chain:
    middlewares:
      - security-headers
      - rate-limit-staff
      - request-size-limit
      - request-timeout
      - compress-response
```

### Operator Chain
```yaml
operator-chain:
  chain:
    middlewares:
      - security-headers
      - rate-limit-operator
      - request-size-limit
      - request-timeout
      - compress-response
```

## Abuse Prevention

### DDoS Mitigation

**Layer 7 (Application) Protections:**
- ✅ Rate limiting per IP (via middleware)
- ✅ Request size limits
- ✅ Timeout limits
- ⚠️ IP-based blocking (manual, via firewall)
- ❌ Captcha challenges (not implemented in v0.1)

**Recommendations for Production:**
- Use Cloudflare or similar CDN with DDoS protection
- Enable IP reputation filtering
- Implement geo-blocking if appropriate
- Add fail2ban for automated IP blocking

### Slow POST/Slowloris Protection

**Mitigations:**
- Request timeout limits (60s response header timeout)
- Connection limits (OS-level: `ulimit`)
- Traefik connection limits (configure if needed)

### Bot Protection

**Current (v0.1):**
- Rate limiting (basic bot deterrent)
- User-Agent filtering (if needed, add custom middleware)

**Future Enhancements:**
- Bot detection (behavioral analysis)
- Challenge-response (Captcha)
- Authenticated API access with tokens

## Security Monitoring

### Metrics to Monitor

```bash
# Rate limit hits
docker compose logs traefik | grep "429" | wc -l

# Request size rejections
docker compose logs traefik | grep "413" | wc -l

# Timeout failures
docker compose logs traefik | grep "504" | wc -l

# Security header violations (if CSP reporting enabled)
docker compose logs backend | grep "csp-report"
```

### Alerts to Configure

| Alert | Threshold | Action |
|-------|-----------|--------|
| High rate limit hits | > 100/min for 5 min | Investigate traffic source |
| Large request rejections | > 10/min | Check for legitimate use case |
| Timeout spikes | > 20/min | Check backend performance |
| TLS handshake failures | > 50/min | Investigate client compatibility |

## Testing Edge Security

### Automated Tests

```bash
cd /home/runner/work/regattadesk/regattadesk/infra/compose
./edge-hardening-test.sh

# Tests:
# - Security headers presence
# - Rate limiting behavior
# - Request size limits
# - Timeout handling
# - Compression support
# - TLS configuration (if HTTPS)
```

### Manual Security Audit

```bash
# 1. Test security headers
curl -I https://regattadesk.example/api/health

# 2. Test rate limiting
for i in {1..200}; do curl -s -o /dev/null -w "%{http_code}\n" https://regattadesk.example/api/health; done

# 3. Test large payload rejection
dd if=/dev/zero bs=1M count=15 | curl -X POST --data-binary @- https://regattadesk.example/api/health

# 4. Test TLS configuration
nmap --script ssl-enum-ciphers -p 443 regattadesk.example

# Or use SSL Labs
# https://www.ssllabs.com/ssltest/analyze.html?d=regattadesk.example
```

## Security Best Practices

### Configuration Management

- ✅ Store secrets in environment variables (not in git)
- ✅ Use `.env.example` as template
- ✅ Rotate secrets regularly
- ✅ Use strong passwords (generated, not human-chosen)
- ✅ Limit secret access (principle of least privilege)

### Operational Security

- ✅ Keep software updated (dependencies, base images)
- ✅ Monitor security advisories
- ✅ Apply security patches promptly
- ✅ Regular security audits
- ✅ Incident response plan (see runbooks)

### Network Security

- ✅ Internal network for backend services
- ✅ Edge network for public-facing services
- ✅ No direct database access from internet
- ✅ Firewall rules (host-level)
- ⚠️ VPN for administrative access (recommended for production)

## Known Limitations (v0.1)

1. **CSP allows unsafe-inline/unsafe-eval** - Required for Vue.js dev mode
2. **No Web Application Firewall (WAF)** - Add in production
3. **Basic rate limiting** - Per-endpoint, not per-user
4. **No bot detection** - Only basic rate limiting
5. **Self-signed certs in dev** - Use Let's Encrypt in production

## Production Hardening Checklist

Before going to production:

- [ ] Replace self-signed certificates with Let's Encrypt
- [ ] Enable TLS 1.3 with strong ciphers only
- [ ] Remove CSP unsafe directives (use nonces)
- [ ] Configure Cloudflare or similar CDN
- [ ] Enable DDoS protection
- [ ] Set up WAF rules
- [ ] Configure IP reputation filtering
- [ ] Enable DNSSEC
- [ ] Set up security monitoring and alerting
- [ ] Conduct external security audit
- [ ] Perform penetration testing
- [ ] Review and test incident response procedures

## Related Documentation

- [Traefik Dynamic Configuration](../../infra/compose/traefik/dynamic.yml)
- [Docker Compose Configuration](../../infra/compose/docker-compose.yml)
- [Incident Runbooks](./runbooks/)
- [BC09-002 Implementation Summary](../../infra/compose/BC09-002-IMPLEMENTATION-SUMMARY.md)

## References

- [OWASP Security Headers](https://owasp.org/www-project-secure-headers/)
- [Mozilla TLS Configuration](https://ssl-config.mozilla.org/)
- [Traefik Security Documentation](https://doc.traefik.io/traefik/middlewares/http/headers/)
- [Content Security Policy Guide](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP)
