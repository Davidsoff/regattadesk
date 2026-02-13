# Infrastructure - Docker Compose

This directory contains Docker Compose configurations for the RegattaDesk v0.1 runtime stack.

## Services

The full Docker Compose stack includes:

- **Backend**: Quarkus application (Java 25)
- **Frontend**: Vue.js application served via Nginx
- **PostgreSQL**: Database (version 16+)
- **Traefik**: Reverse proxy and load balancer (version 3.0+)
- **Authelia**: SSO/Authentication service (version 4.38+, DB-only mode, no Redis)
- **MinIO**: S3-compatible object storage for line-scan tiles/manifests

## Architecture

```
┌─────────────┐
│   Traefik   │ ◄─── HTTP Entry Point (Port 80/443)
│ Reverse Proxy│
└─────┬───────┘
      │
      ├──► Frontend (Vue.js/Nginx)
      │
      ├──► Backend (Quarkus) ◄──┬──► PostgreSQL (Main DB)
      │                         │
      └──► Authelia ────────────┘
           (ForwardAuth)        └──► PostgreSQL (Authelia DB)
                                │
                                └──► MinIO (Object Storage)
```

## Network Architecture

- **regattadesk-edge**: External network for services exposed via Traefik
- **regattadesk-internal**: Internal network (isolated) for backend services

## Prerequisites

- Docker Engine 24.0+ or Docker Desktop
- Docker Compose 2.24+
- At least 4GB RAM available for Docker
- Ports 80, 443, 5432, 8080, 9000, 9001 available on host

## Building the Backend Image

The backend uses the Quarkus Container Image Jib extension to build the container image. The configuration uses Quarkus defaults for maximum compatibility.

**Default Image Name:** `${user.name}/regattadesk-backend:0.1.0-SNAPSHOT`

To build the image:

```bash
cd apps/backend
./mvnw clean package -Dquarkus.container-image.build=true
```

Or using the Quarkus CLI:

```bash
cd apps/backend
quarkus image build
```

This creates a container image using Jib, which optimizes the image layers without requiring a Docker daemon during the build process. The image name uses Quarkus defaults based on your username and the application name/version.

## Quick Start

1. **Build the backend image:**
   ```bash
   cd apps/backend
   ./mvnw clean package -Dquarkus.container-image.build=true
   cd ../../infra/compose
   ```

2. **Copy the environment template:**
   ```bash
   cd infra/compose
   cp .env.example .env
   ```

3. **Edit `.env` and set secure passwords:**
   ```bash
   # Generate secure secrets with:
   openssl rand -base64 32
   
   # Update these values in .env:
   # - POSTGRES_PASSWORD
   # - MINIO_ROOT_PASSWORD
   # - AUTHELIA_JWT_SECRET (min 32 chars)
   # - AUTHELIA_SESSION_SECRET (min 32 chars)
   # - AUTHELIA_STORAGE_ENCRYPTION_KEY (min 32 chars)
   ```

4. **Start the stack:**
   ```bash
   docker compose up -d
   ```

5. **Check service health:**
   ```bash
   docker compose ps
   docker compose logs -f
   ```

6. **Access the application:**
   - Frontend: http://localhost
   - Backend API: http://localhost/api
   - Traefik Dashboard: http://localhost:8080
   - MinIO Console: http://localhost:9001

## Service Details

### PostgreSQL

- **Image**: `postgres:16-alpine`
- **Port**: 5432 (configurable via `POSTGRES_PORT`)
- **Databases**: 
  - `regattadesk` - Main application database
  - `authelia` - Authelia authentication database
- **Data**: Persistent volume `regattadesk-postgres-data`

### MinIO

- **Image**: `minio/minio:latest`
- **API Port**: 9000
- **Console Port**: 9001
- **Buckets**: 
  - `line-scan-tiles` - Line-scan camera tile storage
  - `line-scan-manifests` - Line-scan manifest storage
- **Data**: Persistent volume `regattadesk-minio-data`

### Authelia

- **Image**: `authelia/authelia:4.38`
- **Mode**: DB-only (no Redis required)
- **Storage**: PostgreSQL
- **Default User**: 
  - Username: `admin`
  - Password: `changeme` (change in production!)
- **Configuration**: `./authelia/configuration.yml`

### Traefik

- **Image**: `traefik:v3.0`
- **HTTP Port**: 80
- **HTTPS Port**: 443
- **Dashboard Port**: 8080
- **Features**:
  - Automatic service discovery
  - ForwardAuth integration with Authelia
  - Security headers middleware

### Backend

- **Build**: Quarkus Jib container image from `apps/backend`
- **Java**: 25
- **Framework**: Quarkus 3.8.6
- **Port**: 8080 (internal)
- **Health**: `/q/health/ready`

### Frontend

- **Build**: Multi-stage Docker build from `apps/frontend`
- **Runtime**: Nginx 1.25
- **Framework**: Vue.js 3 + Vite
- **Port**: 80 (internal)
- **Features**: SPA routing, static asset caching, gzip compression

## Common Operations

### View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
```

### Restart Services

```bash
# Restart all
docker compose restart

# Restart specific service
docker compose restart backend
```

### Rebuild and Restart

```bash
# Rebuild backend image after code changes
cd apps/backend
./mvnw clean package -Dquarkus.container-image.build=true
cd ../../infra/compose
docker compose up -d backend

# Rebuild frontend after code changes
docker compose up -d --build frontend
```

### Stop and Remove

```bash
# Stop services
docker compose stop

# Stop and remove containers (keeps volumes)
docker compose down

# Stop, remove containers, and remove volumes
docker compose down -v
```

### Access Service Shells

```bash
# Backend shell
docker compose exec backend /bin/sh

# PostgreSQL shell
docker compose exec postgres psql -U regattadesk

# MinIO client
docker compose exec minio mc --help
```

## Health Checks

All services include health checks. Check status with:

```bash
docker compose ps
```

Services show as "healthy" when ready.

## Development Workflow

1. **Make code changes** in `apps/backend` or `apps/frontend`
2. **Rebuild the service:**
   ```bash
   cd apps/backend
   ./mvnw clean package -Dquarkus.container-image.build=true
   cd ../../infra/compose
   docker compose up -d backend
   # frontend
   docker compose up -d --build frontend
   ```
3. **View logs:**
   ```bash
   docker compose logs -f backend
   ```
4. **Test the changes** via http://localhost

## Production Considerations

### Security

1. **Change all default passwords** in `.env`
2. **Use strong secrets** (min 32 characters) for Authelia
3. **Configure HTTPS** with proper TLS certificates in Traefik
4. **Update Authelia users** or integrate with LDAP/OIDC
5. **Restrict Traefik dashboard** access
6. **Review access control rules** in `authelia/configuration.yml`

### Performance

1. **Allocate sufficient resources**: Min 4GB RAM, 2 CPU cores
2. **Configure PostgreSQL** connection pooling in backend
3. **Enable CDN** for public static assets
4. **Monitor resource usage**: `docker stats`

### Backup

```bash
# Backup PostgreSQL
docker compose exec postgres pg_dump -U regattadesk regattadesk > backup.sql

# Backup MinIO data
docker compose exec minio mc mirror regattadesk/line-scan-tiles /backup/tiles
```

### Monitoring

- **Traefik Dashboard**: http://localhost:8080
- **Backend Health**: http://localhost/q/health
- **PostgreSQL**: Connect with standard tools on port 5432
- **MinIO Console**: http://localhost:9001

## Troubleshooting

### Services Won't Start

1. **Check logs**: `docker compose logs`
2. **Check ports**: Ensure 80, 443, 5432, 8080, 9000, 9001 are available
3. **Check .env**: Ensure all required secrets are set
4. **Check resources**: Ensure Docker has enough memory

### Backend Fails to Connect to Database

1. **Check PostgreSQL is healthy**: `docker compose ps postgres`
2. **Check credentials**: Verify `POSTGRES_PASSWORD` in `.env`
3. **Check logs**: `docker compose logs backend`
4. **Wait for startup**: Backend has 60s startup period

### Frontend Shows 404 Errors

1. **Check Traefik routing**: `docker compose logs traefik`
2. **Check frontend is healthy**: `docker compose ps frontend`
3. **Rebuild frontend**: `docker compose up -d --build frontend`

### Authelia Issues

1. **Check database**: Authelia requires PostgreSQL to be healthy
2. **Check secrets**: All three Authelia secrets must be set in `.env`
3. **Check config**: Review `authelia/configuration.yml`
4. **Check logs**: `docker compose logs authelia`

## CI/CD Integration

This compose stack is designed for both local development and production deployment.

### Automated Testing

```bash
# Start services
docker compose up -d

# Wait for health checks
sleep 30

# Run tests against the stack
# (Tests to be implemented in BC09)

# Cleanup
docker compose down -v
```

### Configuration Validation

```bash
# Validate compose file syntax
docker compose config

# Check for configuration errors
docker compose config --quiet
```

## References

- [Implementation Plan](../../pdd/implementation/plan.md) - Step 1
- [BC01 Platform Spec](../../pdd/implementation/bc01-platform-and-delivery.md)
- [Detailed Design](../../pdd/design/detailed-design.md)

## Status

✅ **Implemented** - BC01-002 Complete

All required services are configured and operational:
- ✅ PostgreSQL with automatic database initialization
- ✅ MinIO with automatic bucket creation
- ✅ Authelia in DB-only mode (no Redis)
- ✅ Traefik with ForwardAuth integration
- ✅ Backend with health checks and proper dependencies
- ✅ Frontend with optimized Nginx configuration
- ✅ Comprehensive health checks and startup ordering
- ✅ Persistent volumes for data
- ✅ Network segmentation (edge/internal)
