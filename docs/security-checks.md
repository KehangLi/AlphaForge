# Security checks

`.github/workflows/security.yml` is manually triggered. It first runs the shared
Report Helper Tests. Once they pass, the three scanning jobs run in parallel.
If the shared tests fail, all three scanning jobs are skipped.

| Job | Scope | Failure policy |
| --- | --- | --- |
| Report Helper Tests | Shared report validation, coverage checks, and secret sanitization | Any failed test prevents the three scanning jobs from starting. |
| Frontend Dependencies | npm lockfile, including direct and transitive dependencies | High or critical production dependency findings fail the job. All severities and development dependencies remain in the full report. |
| Backend Dependencies | Libraries inside the packaged Spring Boot JAR | High or critical findings fail the job, including findings without an available fix. |
| Secrets | Git history across all fetched branches and tags | Any potential secret finding fails the job pending review. |

Scanner, network, build, and report-validation errors also fail the relevant job.
The backend uses Trivy's archive-capable `rootfs` mode and requires the report to
identify both Spring Boot and PostgreSQL libraries inside the JAR. An empty scan
cannot pass as a vulnerability-free result.
These jobs report findings; dependency upgrades are reviewed separately and validated
with the application's tests and build. The separate backend release workflow
scans the exact container image, including OS packages, before uploading/deploying
it; see [backend-release.md](backend-release.md). CodeQL remains separate work.

## Run the workflow

After the workflow is present on the repository's default branch, open **Actions →
Security Checks → Run workflow**, select a branch, and run it. There are no automatic
pull request, push, or scheduled triggers in this first version.

The workflow uses Node.js from `.nvmrc`, Java 21, Trivy 0.76.1, and Gitleaks 8.30.1.
It uses the scanners' command-line tools and does not require GitHub's paid code or
secret scanning features or an NVD API key. GitHub Actions usage is subject to the
repository's normal runner and artifact allowances.

## Reports

Each scanning job publishes its reports as a GitHub Actions artifact, even when findings make
the job fail. Artifacts are retained for seven days:

- `frontend-dependency-reports`: full and production-only npm audit JSON reports.
- `backend-dependency-report`: all detected packaged dependency vulnerabilities.
- `secret-scan-locations`: only file paths, line numbers, rule identifiers, and commit IDs.

The secret scanner runs with redaction enabled. Its raw report stays in a separate
temporary directory and is never selected for artifact upload. A report sanitizer
copies only the allowed location fields; matched text, secret values, author details,
and unexpected fields are excluded. Empty or malformed scanner output is not treated
as a clean scan.

Report helper checks can be run locally with:

```sh
python3 -B -m unittest discover -s .github/scripts -p 'test_*.py'
```

## Respond to findings

For dependency findings, review the affected version, dependency path, fix, and whether
the vulnerable behavior is used by this application. Upgrade and run the normal frontend
or backend checks. Development-only and lower-severity findings still need review and a
recorded follow-up; they are not production release gates in this initial policy.

For real leaked credentials, revoke or rotate them first. Deleting the current file
does not remove the finding from Git history. Any exception for a confirmed false
positive or revoked historical credential must be narrowly scoped and documented;
the initial workflow does not add exclusions or a blanket baseline.

CI's secret job scans committed Git history. An ignored, uncommitted local `.env` is
not included in a remote checkout and cannot be scanned by this workflow. Keep real
credentials out of commits, workflow logs, and published build artifacts.
