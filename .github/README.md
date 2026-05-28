# GitHub Workflows

## quality-gate.yml

Automated quality monitoring for FlossWare projects.

### What It Does

- ✅ Runs on every push/PR
- ✅ Executes all Maven quality checks
- ✅ Comments quality metrics on PRs
- ✅ Automatically creates issues when quality gates fail:
  - Code coverage drops below threshold
  - SpotBugs finds bugs
  - PMD detects violations
  - Checkstyle errors
  - Security vulnerabilities (OWASP)
- ✅ Daily security scans (2 AM UTC)

### Quality Gates

| Tool | Threshold | Fail Condition |
|------|-----------|---------------|
| JaCoCo | 93% instruction, 86% branch | Below threshold |
| SpotBugs | 0 bugs | Any bugs found |
| PMD | 0 violations | Any violations |
| Checkstyle | 0 errors | Any errors |
| OWASP | 0 critical/high | Critical or high vulnerabilities |

### Issue Labels

Auto-created issues are tagged with:
- `quality-gate` - All quality gate issues
- `automated` - Auto-created
- Specific: `coverage`, `spotbugs`, `pmd`, `checkstyle`, `security`

### Configuration

Edit `quality-gate.yml` to:
- Adjust coverage thresholds
- Change schedule
- Modify issue templates
- Add custom checks

### Resources

- [Maven Quality Requirements](../build-tools/MAVEN-QUALITY-REQUIREMENTS.md)
- [Test Coverage Guide](../build-tools/TEST-COVERAGE.md)
- [FlossWare Build Tools](https://github.com/FlossWare/build-tools)
