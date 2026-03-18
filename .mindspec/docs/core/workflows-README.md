# GitHub Actions Workflows

This directory contains CI/CD workflows for RegattaDesk.

## Workflows

### CI (`ci.yml`)

**Triggers:**
- Pull requests to `master`/`main`
- Pushes to `master`/`main`
- Manual trigger via workflow dispatch

**Jobs:**
1. **backend-lint** - Validates backend code formatting
2. **backend-build** - Builds backend with Maven
3. **backend-test** - Runs backend unit tests
4. **frontend-lint** - Runs frontend linting
5. **frontend-build** - Builds frontend production bundle
6. **dependency-pinning** - Validates all dependencies are pinned
7. **build-images** - Builds Docker container images (on push/dispatch only)
8. **compose-smoke** - Smoke tests the Docker Compose stack (on push/dispatch only)
9. **all-checks** - Aggregates all check results for branch protection

**Branch Protection:**
The `all-checks` job should be required for merge. It ensures all critical checks pass.

### Security Scan (`security-scan.yml`)

**Triggers:**
- Scheduled: Every Monday at 06:00 UTC
- Manual trigger via workflow dispatch

**Jobs:**
1. **backend-security-scan** - OWASP Dependency Check for Maven dependencies
2. **frontend-security-scan** - npm audit for JavaScript dependencies
3. **docker-image-scan** - Trivy vulnerability scans for base images
4. **consolidate-results** - Creates summary report and uploads all artifacts

**Artifacts:**
- Backend dependency check HTML reports (90-day retention)
- Frontend npm audit JSON reports (90-day retention)
- Docker image Trivy SARIF reports (90-day retention)
- Consolidated security summary (90-day retention)

**Follow-up:**
Review artifacts weekly and triage vulnerabilities according to the security fast-path policy in `docs/dependency-governance.md`.

### Release (`release.yml`)

**Triggers:**
- Git tags matching `v*.*.*` (e.g., `v0.1.0`)
- Manual trigger via workflow dispatch with version and environment selection

**Jobs:**
1. **build-and-package** - Builds release artifacts
   - Backend Docker image
   - Frontend Docker image
   - Docker Compose stack tarball
   - Version file and checksums
2. **deploy-staging** - Deploys to staging environment (skeleton)
3. **deploy-production** - Deploys to production environment (skeleton)
4. **create-github-release** - Creates GitHub release with artifacts

**Artifacts:**
- Container images as compressed tarballs
- Docker Compose configuration bundle
- SHA256 checksums for verification
- VERSION file

**Notes:**
- Deployment jobs are skeletons in v0.1 (manual deployment documented)
- Production requires manual approval via GitHub environments
- Release candidates (tags with `-rc`) deploy to staging
- Stable releases deploy to production

## Environment Variables

All workflows use these common environment variables:

```yaml
env:
  JAVA_VERSION: '25'
  JAVA_DISTRIBUTION: 'temurin'
  NODE_VERSION: '22'
```

## Required Secrets

For full automation (future):
- `DOCKER_HUB_TOKEN` - Docker Hub authentication
- `STAGING_DEPLOY_KEY` - SSH key for staging deployment
- `PRODUCTION_DEPLOY_KEY` - SSH key for production deployment

For v0.1, no secrets are required as deployment is manual.

## Testing Workflows Locally

### Using act

You can test workflows locally using [act](https://github.com/nektos/act):

```bash
# Test CI workflow
act pull_request

# Test security scan
act schedule

# Test release workflow
act -j build-and-package
```

### Manual Validation

```bash
# Validate YAML syntax
yamllint .github/workflows/*.yml

# Test dependency pinning checks
.github/workflows/ci.yml # Extract the dependency-pinning job logic

# Test builds
make build
make test
```

## Monitoring and Alerts

- **Failed CI runs:** Blocks PR merges automatically
- **Security scans:** Review artifacts manually or set up GitHub Issues integration
- **Release failures:** Manual monitoring required in v0.1

## Adding New Checks

When adding new checks to CI:

1. Add the new job to `ci.yml`
2. Update the `all-checks` job to depend on your new job
3. Test with a PR before merging
4. Update branch protection rules if needed
5. Document the check in this README

## Workflow Maintenance

- Review workflow efficiency quarterly
- Update action versions monthly (via Dependabot or manual review)
- Optimize caching strategies as repository grows
- Monitor workflow run times and costs

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [RegattaDesk Dependency Governance](../../docs/dependency-governance.md)
- [AGENTS.md](../../AGENTS.md) - CI/CD best practices
