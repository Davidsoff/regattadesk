# Dependency Governance

## Overview

This document defines the dependency governance policies and procedures for RegattaDesk, ensuring supply chain security, reproducible builds, and controlled update cadence as required by the v0.1 implementation plan.

## Principles

1. **Pinned Dependencies**: All dependencies must be pinned to exact versions in manifests and lockfiles
2. **Automated Scanning**: Weekly vulnerability scans run automatically to detect security issues
3. **Controlled Updates**: Regular updates follow a defined cadence with testing gates
4. **Security Fast-Path**: Critical security vulnerabilities bypass normal cadence with expedited process
5. **Reproducible Builds**: Same input always produces same output across environments

## Dependency Pinning Policy

### Backend (Maven/Quarkus)

**Requirements:**
- All dependency versions must be explicitly defined in `pom.xml`
- Do not use `LATEST` or `RELEASE` version specifiers
- Quarkus platform BOM version must be pinned
- Plugin versions must be explicitly specified

**Validation:**
- CI pipeline automatically checks for unpinned versions
- Build fails if `LATEST` or `RELEASE` keywords are found

**Example:**
```xml
<properties>
    <quarkus.platform.version>3.8.6</quarkus.platform.version>
    <compiler-plugin.version>3.12.1</compiler-plugin.version>
</properties>
```

### Frontend (npm/Vue.js)

**Requirements:**
- All dependencies in `package.json` must use exact versions (no `^` or `~` prefixes)
- `package-lock.json` must be committed to version control
- Use `npm ci` in CI/CD (not `npm install`)
- `engines` field must specify minimum Node.js version

**Validation:**
- CI pipeline checks for caret (`^`) or tilde (`~`) in `package.json`
- Build fails if `package-lock.json` is missing or out of sync

**Example:**
```json
{
  "dependencies": {
    "vue": "3.5.25"
  },
  "engines": {
    "node": ">=22.0.0"
  }
}
```

### Docker Images

**Requirements:**
- Base images must use specific version tags (no `latest` in production)
- Pin to minor or patch versions for stability
- Document update rationale in commit messages

**Example:**
```yaml
services:
  postgres:
    image: postgres:16-alpine  # Major version pinned
```

## Vulnerability Scanning

### Automated Scans

**Schedule:**
- Weekly scans run every Monday at 06:00 UTC
- Triggered automatically via GitHub Actions
- Can be manually triggered via workflow dispatch

**Coverage:**
- Backend: OWASP Dependency Check for Maven dependencies
- Frontend: npm audit for JavaScript dependencies
- Docker: Trivy scans for container image vulnerabilities
- Infrastructure: Base image vulnerability assessment

**Artifacts:**
- HTML reports for backend (OWASP Dependency Check)
- JSON reports for frontend (npm audit)
- SARIF reports for Docker images (Trivy)
- Consolidated summary markdown file

**Retention:**
- Scan artifacts retained for 90 days
- Historical trends tracked via artifact comparison

### Triage Process

When vulnerabilities are detected:

1. **Review** the automated scan artifacts in GitHub Actions
2. **Assess** severity and applicability:
   - Critical (CVSS 9.0-10.0): Immediate action required
   - High (CVSS 7.0-8.9): Action within 7 days
   - Medium (CVSS 4.0-6.9): Action within 30 days
   - Low (CVSS 0.1-3.9): Action at next regular update
3. **Decide** on action:
   - Apply security fast-path if critical/high
   - Schedule for next update cycle if medium/low
   - Suppress if false positive or accepted risk
4. **Track** via GitHub issue if action required
5. **Document** suppressions in suppression file with justification

### Suppression Management

**File:** `apps/security/dependency-check-suppressions.xml`

**When to suppress:**
- False positives (vulnerability doesn't apply to our usage pattern)
- Accepted risks (risk is understood and accepted with justification)
- Pending fixes (temporary suppression while waiting for upstream patch)

**Requirements:**
- Must include reason in `<notes>` section
- Must specify expiry date in `<until>` field
- Must link to tracking issue if applicable
- Review suppressions monthly

**Example:**
```xml
<suppress>
   <notes><![CDATA[
   CVE-2024-12345 affects server-side usage only.
   We only use this library in client-side context.
   Review: 2026-03-01
   ]]></notes>
   <packageUrl regex="true">^pkg:maven/com\.example/library@.*$</packageUrl>
   <cve>CVE-2024-12345</cve>
   <until>2026-03-01</until>
</suppress>
```

## Update Cadence

### Regular Updates

**Monthly Cycle:**
- **Week 1**: Review dependency status and available updates
- **Week 2**: Test updates in development environment
- **Week 3**: Deploy to staging and validate
- **Week 4**: Deploy to production (if no issues)

**Scope:**
- Patch version updates (e.g., 3.8.5 → 3.8.6)
- Minor version updates with low risk assessment
- Security patches (coordinated with security fast-path if urgent)

**Testing Requirements:**
- All CI checks must pass
- Manual smoke testing in development
- Automated integration tests
- Staging environment validation

### Quarterly Reviews

**Every 3 months:**
- Review major version updates
- Assess EOL status of current dependencies
- Plan migrations for deprecated dependencies
- Update dependency inventory

**Deliverables:**
- Updated dependency inventory table
- Migration plan for major updates
- Risk assessment for continued use of EOL versions

## Security Fast-Path Process

### When to Use

The security fast-path bypasses normal update cadence for:
- **Critical vulnerabilities** (CVSS ≥ 9.0) affecting production
- **High vulnerabilities** (CVSS ≥ 7.0) with active exploits
- **Emergency patches** for critical defects causing service disruption

### Process Steps

1. **Detection**
   - Automated scan identifies vulnerability
   - Security advisory or CVE published
   - Manual report from security researcher

2. **Assessment** (within 4 hours)
   - Verify vulnerability affects our deployment
   - Assess exploitability and impact
   - Determine urgency level

3. **Decision** (within 8 hours)
   - Approve security fast-path if critical/high risk
   - Assign to on-call engineer
   - Create tracking issue with `security` and `critical` labels

4. **Implementation** (within 24 hours for critical, 7 days for high)
   - Create hotfix branch from production
   - Update affected dependency to patched version
   - Run full CI suite
   - Document changes and testing in PR

5. **Deployment** (expedited approval)
   - Deploy to staging for smoke test
   - Fast-track production deployment
   - Monitor error rates and performance
   - Document in incident log

6. **Communication**
   - Notify team via designated channel
   - Update tracking issue with resolution
   - Create post-incident review if critical

### Approval Authority

- **Critical (CVSS ≥ 9.0)**: Product owner or technical lead approval required
- **High (CVSS 7.0-8.9)**: Any senior engineer may approve
- **Out-of-hours**: On-call engineer may approve and inform afterward

## Dependency Inventory

### Maintained Inventory

**Location:** `docs/dependencies.md`

**Content:**
- Backend dependencies with versions and licenses
- Frontend dependencies with versions and licenses
- Docker base images with versions
- Infrastructure dependencies

**Update Frequency:**
- Automatic: CI pipeline updates versions on each build
- Manual: Review and add context quarterly

**Example Entry:**
```markdown
| Component | Version | License | Purpose | Last Updated |
|-----------|---------|---------|---------|--------------|
| Quarkus | 3.8.6 | Apache-2.0 | Backend framework | 2026-02-01 |
| Vue.js | 3.5.25 | MIT | Frontend framework | 2026-01-15 |
```

## CI/CD Integration

### Pull Request Checks

**Required Checks:**
- Dependency pinning validation (must pass)
- Build with exact versions (must pass)
- Test suite (must pass)
- No new high/critical vulnerabilities introduced

**Process:**
- CI runs on every PR
- Blocks merge if checks fail
- Developer must fix issues before merge

### Branch Protection

**Rules:**
- Require status checks to pass before merging
- Require review from code owner for dependency changes
- Require signed commits for security-critical changes

## Roles and Responsibilities

### Repository Maintainers
- Review and approve dependency updates
- Manage suppression file
- Coordinate security fast-path responses
- Maintain dependency governance documentation

### Engineers
- Follow pinning policy in all changes
- Investigate and triage vulnerabilities
- Implement security patches
- Document dependency rationale in PRs

### Security Team (if applicable)
- Review security scan results
- Provide guidance on vulnerability triage
- Approve security fast-path requests
- Conduct quarterly security reviews

## Compliance and Audit

### Audit Trail

All dependency changes must have:
- Git commit with clear message
- PR with description and testing notes
- CI/CD logs showing successful builds
- Code review approval

### Periodic Reviews

**Monthly:**
- Review open vulnerability issues
- Check suppression expiry dates
- Verify CI pipeline health

**Quarterly:**
- Full dependency inventory review
- EOL status assessment
- Update policy documentation if needed

**Annually:**
- Security posture review
- Process improvement assessment
- Training needs evaluation

## Tools and Resources

### Scanning Tools
- **OWASP Dependency Check**: Backend Maven dependencies
- **npm audit**: Frontend JavaScript dependencies
- **Trivy**: Container image vulnerabilities
- **GitHub Dependabot**: Automated PR creation (optional)

### Documentation
- OWASP Dependency Check: https://jeremylong.github.io/DependencyCheck/
- npm audit: https://docs.npmjs.com/cli/v9/commands/npm-audit
- Trivy: https://aquasecurity.github.io/trivy/
- NIST NVD: https://nvd.nist.gov/

### Internal Resources
- Suppression file: `apps/security/dependency-check-suppressions.xml`
- Dependency inventory: `docs/dependencies.md`
- CI workflows: `.github/workflows/`
- Runbooks: `docs/runbooks/`

## Updates to This Policy

This policy is a living document and should be reviewed quarterly. Updates require:
- PR with proposed changes
- Review by repository maintainers
- Approval by technical lead or product owner
- Communication to all contributors

**Last Updated:** 2026-02-13  
**Next Review:** 2026-05-13  
**Owner:** BC01 Platform and Delivery Team
