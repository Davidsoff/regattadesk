# Dependency Inventory

This document tracks all third-party dependencies used in RegattaDesk v0.1.

**Last Updated:** 2026-02-13

## Backend Dependencies (Maven/Quarkus)

### Core Framework

| Component | Version | License | Purpose | Notes |
|-----------|---------|---------|---------|-------|
| Quarkus BOM | 3.8.6 | Apache-2.0 | Backend framework platform | Supersonic Subatomic Java |
| Java | 25 | GPL-2.0 with Classpath Exception | Runtime platform | Using Temurin distribution |

### Quarkus Extensions

| Extension | Version | License | Purpose |
|-----------|---------|---------|---------|
| quarkus-smallrye-health | (from BOM) | Apache-2.0 | Health check endpoints |
| quarkus-resteasy-reactive | (from BOM) | Apache-2.0 | REST API framework |
| quarkus-resteasy-reactive-jackson | (from BOM) | Apache-2.0 | JSON serialization |
| quarkus-arc | (from BOM) | Apache-2.0 | Dependency injection |
| quarkus-container-image-jib | (from BOM) | Apache-2.0 | Container image building |
| quarkus-junit5 | (from BOM) | Apache-2.0 | Testing framework |

### Build Tools

| Tool | Version | Purpose |
|------|---------|---------|
| Maven | 3.9+ (via wrapper) | Build and dependency management |
| maven-compiler-plugin | 3.12.1 | Java compilation |
| maven-surefire-plugin | 3.2.5 | Test execution |

## Frontend Dependencies (npm/Vue.js)

### Core Framework

| Component | Version | License | Purpose | Notes |
|-----------|---------|---------|---------|-------|
| Vue.js | 3.5.25 | MIT | Frontend framework | Composition API |
| Node.js | 22+ | MIT | JavaScript runtime | LTS version required |

### Build Tools

| Tool | Version | License | Purpose |
|------|---------|---------|---------|
| Vite | 7.3.1 | MIT | Build tool and dev server |
| @vitejs/plugin-vue | 6.0.2 | MIT | Vue 3 support for Vite |

## Infrastructure Dependencies (Docker)

### Base Images

| Image | Tag/Version | Purpose | Update Frequency | Last Reviewed |
|-------|-------------|---------|------------------|---------------|
| postgres | 16-alpine | Database server | Quarterly for minor, annually for major | 2026-02-01 |
| minio/minio | latest → specific tag needed | Object storage (S3-compatible) | Monthly | 2026-02-01 |
| traefik | v3.0 | Reverse proxy and edge router | Quarterly | 2026-02-01 |
| authelia/authelia | latest → specific tag needed | Authentication and SSO | Monthly | 2026-02-01 |

**Action Items:**
- [ ] Pin MinIO to specific stable tag instead of `latest`
- [ ] Pin Authelia to specific stable tag instead of `latest`
- [ ] Document rationale for version choices

### Runtime Components

| Component | Purpose | Configuration |
|-----------|---------|---------------|
| PostgreSQL 16 | Primary database | Single instance, DB-only Authelia backing |
| MinIO | Object storage | Line-scan tiles and manifests |
| Traefik v3 | Edge router | TLS termination, ForwardAuth integration |
| Authelia | SSO provider | DB-backed session storage |

## Security Scanning Coverage

| Component Type | Scanning Tool | Frequency | Report Location |
|----------------|---------------|-----------|-----------------|
| Maven dependencies | OWASP Dependency Check | Weekly | GitHub Actions artifacts |
| npm dependencies | npm audit | Weekly | GitHub Actions artifacts |
| Docker images | Trivy | Weekly | GitHub Actions artifacts |
| Container registry | Trivy (future) | On push | TBD |

## License Compliance

### Approved Licenses
- **Permissive:** Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause
- **Copyleft (acceptable with conditions):** GPL-2.0 with Classpath Exception, LGPL-2.1+, MPL-2.0

### License Review Process
1. New dependencies require license approval before merge
2. Quarterly review of all licenses
3. SPDX license identifiers preferred
4. Document any non-standard licenses

## End-of-Life (EOL) Tracking

| Component | Current Version | EOL Date | Migration Plan |
|-----------|----------------|----------|----------------|
| Java 25 | 25 | March 2026 (estimated) | Repository baseline uses Java 25; monitor Quarkus compatibility for Java 25 LTS or plan migration to next LTS version |
| Node.js 22 | 22 (LTS) | April 2027 | Plan upgrade cycle for Node.js 24 LTS |
| PostgreSQL 16 | 16 | November 2028 | Monitor PostgreSQL 17+ compatibility |
| Quarkus 3.x | 3.8.6 | TBD | Stay on minor update cadence |

## Version Pinning Strategy

### Backend (Maven)
- **Quarkus Platform:** Pin to specific minor version (3.8.x), update monthly for patches
- **Plugins:** Pin to specific versions, update quarterly
- **Java:** Require Java 25 (as specified in pom.xml compiler release)

### Frontend (npm)
- **Vue.js:** Pin exact version, update monthly for patches
- **Build tools:** Pin exact versions, update as needed
- **Node.js:** Require minimum version (22+), test with latest LTS

### Infrastructure (Docker)
- **Base images:** Pin to major or minor versions, avoid `latest` in production
- **Update strategy:** Test updates in staging first, roll out incrementally

## Update History

### 2026-02-13
- Initial dependency inventory created
- Documented current versions for v0.1 baseline
- Identified action items for image tag pinning

### Future Updates
- Update this section with each dependency change
- Link to relevant PRs and issues
- Document migration rationale

## Review Schedule

- **Weekly:** Automated vulnerability scanning
- **Monthly:** Review patch updates and security advisories
- **Quarterly:** Comprehensive dependency review and EOL assessment
- **Annually:** Major version update planning and license audit

## Contact

For questions about dependencies:
- **General:** Repository maintainers
- **Security:** Follow security fast-path process in `dependency-governance.md`
- **Licensing:** Consult with legal/compliance team

---

**Note:** This inventory is maintained automatically where possible and manually reviewed regularly. See `docs/dependency-governance.md` for the complete governance policy.
