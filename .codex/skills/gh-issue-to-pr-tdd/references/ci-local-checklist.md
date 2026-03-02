# CI-Equivalent Local Commands

Run from repo root:

```bash
# Backend required
(cd apps/backend && ./mvnw verify -Dformat.validate)
(cd apps/backend && ./mvnw clean package -DskipTests)
(cd apps/backend && ./mvnw test)

# Backend optional (non-gating in CI)
(cd apps/backend && ./mvnw verify -Pintegration || true)
(cd apps/backend && ./mvnw verify -Pcontract || true)

# Frontend required
(cd apps/frontend && npm_config_cache="$PWD/.npm-cache" npm ci)
(cd apps/frontend && npm run lint)
(cd apps/frontend && npm run build)

# Frontend optional (non-gating in CI)
(cd apps/frontend && npm run test || true)
(cd apps/frontend && npm run test:a11y || true)

# Dependency pinning required
test -f apps/frontend/package-lock.json
! grep -E '"\^|"~' apps/frontend/package.json
! grep -qE 'version>\s*(LATEST|RELEASE)' apps/backend/pom.xml
```
