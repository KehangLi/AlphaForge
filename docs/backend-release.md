# Versioned backend releases and rollback

Backend releases start manually in GitHub Actions. One fixed commit is tested,
built into an AMD64 image, scanned, uploaded to GHCR and deployed by digest.
The serving commit and database session operations are verified before creating
the Git tag and publishing the GitHub Release. Vercel deploys independently.

## One-time page setup

Merge the workflow changes into `main` before looking for **Run workflow**.
Merging alone does not deploy the backend. Vercel may independently rebuild the
frontend on that push, according to its existing Git integration.

### 1. Allow the repository to publish the existing GHCR package

Open **GitHub profile → Packages → alphaforge-backend → Package settings**:

1. Connect the package to **KehangLi/AlphaForge** if it is not already linked.
2. Under **Manage Actions access**, add **AlphaForge** with **Write** permission,
   or verify its inherited access already permits publishing.
3. Keep the package private. Azure still needs its existing `read:packages` pull
   credential; rotate that Azure-stored PAT before it expires.

The workflow uses the automatically supplied `GITHUB_TOKEN` with job-specific
permissions. No personal write PAT is required. If Actions policy restricts
third-party actions, allow the pinned GitHub, Docker and Azure actions used here.

### 2. Create the GitHub deployment environment

Open **AlphaForge → Settings → Environments → New environment**, named exactly:

```text
backend-production
```

Under **Deployment branches and tags**, choose **Selected branches and tags**
and allow the **main branch only**. Azure trusts this environment identity, so
its Git branch restriction must remain in place. Workflows also check `main`.
No required reviewer is necessary for the first version: **Run workflow** is the
manual release decision. Reviewer/environment availability depends on GitHub
plan and repository visibility; private repositories need an eligible plan such
as GitHub Pro (potentially available through student benefits).

### 3. Obtain the exact public OIDC identity

Open **Actions → Show Deployment Identity → Run workflow → main**. It does not
deploy or log in to Azure. The run **Summary** displays `iss`, `aud`, and `sub`,
plus repository and branch. Copy these public values for the next step.

Do not guess `sub`: newer GitHub repository identities include immutable owner
and repository IDs. The workflow decodes the token only to display these public
claims and never prints the token itself.

### 4. Create an Azure application with federation

Open **Azure Portal → Microsoft Entra ID → App registrations → New registration**:

1. Name it **alphaforge-github-deploy**, for example.
2. Choose **Accounts in this organizational directory only**, leave redirect URI
   empty, and register.
3. From **Overview**, record **Application (client) ID** and **Directory (tenant)
   ID**. These are identifiers, not passwords.
4. Open **Certificates & secrets → Federated credentials → Add credential**.
5. Choose **Other issuer** / manual issuer-and-subject configuration. Paste the
   exact `iss`, `sub`, and `aud` from the GitHub run summary. The audience is
   `api://AzureADTokenExchange`. Name it **github-backend-production** and save.

Do not create a client secret or certificate. OIDC grants short-lived access
after Azure verifies the GitHub identity. If the university tenant blocks app
registrations, resolve that tenant permission first; a user-assigned managed
identity with federation is another supported setup path.

### 5. Authorize the identity on the existing Azure resources

Open **Container Apps → alphaforge-backend → Access control (IAM) → Add role
assignment**. Select **Container Apps Contributor**, assign access to **User,
group, or service principal**, and select **alphaforge-github-deploy**. Assign
at this **Container App's scope**, rather than subscription scope.

On the app's existing **Container Apps Environment**, also grant this identity
**Reader** so Azure CLI can inspect the environment during updates. No database,
Owner, subscription-wide Contributor or role-assignment privileges are needed.

The built-in Container Apps Contributor role can read app secrets, although
these workflows do not request or print them. Stricter separation requires a
reviewed custom role excluding `listSecrets/action` while retaining image-update
operations.

### 6. Add GitHub environment variables

Return to **GitHub → Settings → Environments → backend-production → Environment
variables → Add environment variable**:

| Name | Value |
| --- | --- |
| `AZURE_CLIENT_ID` | Application (client) ID from step 4 |
| `AZURE_TENANT_ID` | Directory (tenant) ID |
| `AZURE_SUBSCRIPTION_ID` | Actual subscription ID of Azure for Students |
| `AZURE_RESOURCE_GROUP` | `AlphaForge` |
| `AZURE_CONTAINER_APP` | `alphaforge-backend` |

Use **Variables**. GitHub does not need `AZURE_CREDENTIALS` JSON, a client password,
`DB_PASSWORD`, or `TRUSTED_PROXY_SECRET`. Deployment changes only the image;
existing Azure environment variables, secret references, registry authentication,
ingress and resources remain configured in Azure.

## First-release checks

- Use **Single** revision mode and **max replicas 1** while rate-limit counters
  are stored in JVM memory. Deployments/cold starts reset those counters.
- Production configuration must already exist, including database settings and
  `TRUSTED_PROXY_SECRET`. Do not manually override `ALPHAFORGE_RELEASE_VERSION`
  or `ALPHAFORGE_RELEASE_COMMIT` in Azure: the image supplies them.
- If V5 has not run online, perform the read-only duplicate checks in
  [import-integrity.md](import-integrity.md) and confirm backup/recovery first.
  The workflow does not automatically remove or repair trading data.
- Check database compatibility with the previous image. Image rollback does not
  undo Flyway migrations, restore data, rotate secrets or restore old settings.
- Keep prior GHCR images. A manually uploaded image without release labels is
  supported as an initial recovery baseline, with weaker HTTP identity checks.

## Release a version

1. Merge the intended changes into `main`.
2. Open **Actions → Release Backend → Run workflow**, select **main**, and enter
   an unused version such as **v0.2.0**. Do not create its tag manually first.
3. The workflow fixes the source commit at dispatch time, runs the Java suite
   against disposable PostgreSQL, and builds `linux/amd64` using `build-image.sh`.
4. Trivy scans the exact saved image. Missing OS/JAR coverage, scanner errors and
   HIGH/CRITICAL vulnerabilities block publishing and deployment.
5. That scanned image is pushed to GHCR. The AMD64 runtime digest is resolved,
   even if the index also contains an attestation manifest, and labels must match
   the selected version/commit.
6. Before updating Azure, `deployment.json` saves the previous resolved image and
   safe app metadata. The script checks for a competing metadata change, updates
   **only the image**, and waits up to ten minutes for Azure readiness, ready
   revision metadata where Azure reports it, `/health` version/commit
   headers, and database-backed CSRF/session/logout checks.
7. Only after verification does it create the Git tag and a draft GitHub Release,
   attach `release.json` and `deployment.json`, then publish the release.

Version tags in Git, GitHub Releases and GHCR are not intentionally overwritten.
A failed run after upload can consume a version: choose a new one for a new full
attempt. Avoid manual pushes to the same package/version during a release.
No test user or trading activity is created by the database probe. Failed probes
can leave an anonymous session to expire normally; no cookies/tokens are logged.

Release assets record source commit, deployed digest, previous digest, run URL
and included migration filenames. Maven's development SNAPSHOT version is not
changed, and the tag does not claim a coordinated frontend deployment.
Image/scan artifacts are retained for 30 days; deployment/rollback records for
90 days, subject to repository retention limits. Published release assets are
the durable recovery record; keep the corresponding GHCR images too.

The initial image verification found CVE-2026-111497 in the previous pinned
runtime's `libbrotli1`. The runtime base pin was refreshed while keeping Java 21
and Ubuntu Noble. Review future scanner findings and update the relevant image
or dependencies; the release workflow does not silently ignore HIGH findings.
See [Ubuntu's fix status](https://ubuntu.com/security/CVE-2026-111497).

## Manual rollback and failed releases

Release and rollback share one concurrency group and do not cancel an active
deployment. GitHub can replace an older pending request with a newer one; do not
use the portal to deploy concurrently. There is no automatic rollback, because
the old image may be incompatible with an already-applied database migration.

Open **Actions → Roll Back Backend → Run workflow → main**:

- **Published release:** fill `release_version` (e.g. `v0.2.0`), leave
  `deployment_run_id` empty. Select **previous** to restore the image running
  before that release, or **release** to redeploy the release's own image.
- **Failed release without publication:** leave `release_version` empty and set
  `deployment_run_id` from its URL (`/actions/runs/123456`). Choose **previous**.
  Only completed Release Backend runs from `main` are accepted; the earliest
  retained snapshot across its attempts identifies the original prior image.
  If no snapshot artifact exists, that run
  should not have reached the Azure mutation.
- Confirm `database_compatible` only after checking the target supports the
  current schema, then run. The same deployment checks and outcome recording run.

If Azure succeeded but GitHub publication failed, the new code may already be
live. Check the deployment record and `/health` headers first. Finish a matching
draft release and its assets through GitHub's Release UI, or recover the recorded
tag/version manually; alternatively roll back using the run ID. A red workflow
does not imply Azure stayed on the old image.

The deployment script refuses a target already configured in Azure, so a retry
cannot replace the original rollback baseline with the same image. After a
partial failure, review the first snapshot, recover if needed, and use a fresh
version for a new full release.

Older baseline images have no version/commit health headers. Their recovery
check validates the requested Azure digest and database/session flow, but lacks
the new HTTP serving-commit proof. Later releases use both checks.

## Local verification

```sh
python3 -B -m unittest discover -s scripts/tests -p 'test_*.py'
python3 -B -m unittest discover -s .github/scripts -p 'test_*.py'
bash -n scripts/build-image.sh
./mvnw --batch-mode --no-transfer-progress clean verify
```

Helper tests replace external commands and cover failure recovery, wrong serving
commits, invalid image platforms, scan coverage and release metadata. Java tests
use isolated Testcontainers databases. Real OIDC, package upload and Azure release
verification require the page setup and first manual run; local tests do not
claim those external actions happened.

## References

- [GitHub OIDC with Azure](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-azure)
- [GitHub OIDC subject formats](https://docs.github.com/en/actions/reference/security/oidc)
- [GitHub environments and plan availability](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [GHCR Actions permissions](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [Azure image updates](https://learn.microsoft.com/en-us/cli/azure/containerapp#az-containerapp-update)
- [Container Apps RBAC and secret access](https://learn.microsoft.com/en-us/azure/container-apps/manage-secrets)
