# Docker Compose Stack - Build Issues

## SSL Certificate Issue in CI Environment

When building the backend image, Maven encounters SSL certificate validation errors:

```
javax.net.ssl.SSLHandshakeException: PKIX path building failed: 
unable to find valid certification path to requested target
```

This is a known issue in CI/build environments where corporate proxies or certificate chains are not properly configured.

## Solutions

### For Local Development:
The stack should build fine in a normal development environment with proper SSL certificates.

### For CI Environments:
1. Add CA certificates to the build environment
2. Configure Maven to use HTTP instead of HTTPS (not recommended for production)
3. Use a Maven repository manager (Nexus, Artifactory) within the network
4. Pre-build the backend image and push to a registry

## Testing Without Building

To test the infrastructure services without building backend/frontend:

```bash
cd infra/compose
docker compose up -d postgres minio traefik
```

All infrastructure services (PostgreSQL, MinIO, Traefik) start successfully.

## Next Steps

1. Resolve SSL certificate issues in build environment
2. Pre-build images or use a repository manager
3. Enable Authelia with proper HTTPS configuration
4. Complete end-to-end stack testing
