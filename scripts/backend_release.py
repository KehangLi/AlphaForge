"""Backend release bookkeeping and image-only Azure deployment. Python standard library only."""

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request


IMAGE_REPOSITORY = "ghcr.io/kehangli/alphaforge-backend"
VERSION = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}\Z")
APP_QUERY = (
    "{name:name,state:properties.provisioningState,"
    "mode:properties.configuration.activeRevisionsMode,"
    "maxReplicas:properties.template.scale.maxReplicas,"
    "fqdn:properties.configuration.ingress.fqdn,"
    "latest:properties.latestRevisionName,ready:properties.latestReadyRevisionName,"
    "containers:properties.template.containers[].{name:name,image:image}}"
)


class ReleaseError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise ReleaseError(message)


def valid_version(value):
    require(isinstance(value, str) and VERSION.fullmatch(value), "Version must look like v0.2.0 (no leading zeros).")
    return value


def valid_commit(value):
    require(isinstance(value, str) and COMMIT.fullmatch(value), "Expected a full lowercase Git commit SHA.")
    return value


def valid_image(value):
    prefix = IMAGE_REPOSITORY + "@"
    require(isinstance(value, str) and value.startswith(prefix) and DIGEST.fullmatch(value[len(prefix):]),
            "Deployment requires an immutable digest from the AlphaForge backend GHCR package.")
    return value


def run(arguments, timeout=120):
    try:
        result = subprocess.run(arguments, capture_output=True, text=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise ReleaseError(f"{arguments[0]} could not complete; check installation, login and connectivity.") from error
    # Do not echo CLI output: Azure errors or debug output can contain configuration values.
    require(result.returncode == 0, f"{arguments[0]} command failed; inspect the relevant service's operation status.")
    return result.stdout


def write_json(path, value):
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(value, indent=2) + "\n")


def now():
    return datetime.now(timezone.utc).isoformat()


def github_get(path):
    token = os.environ.get("GH_TOKEN")
    require(bool(token), "GH_TOKEN is required for GitHub metadata checks.")
    request = urllib.request.Request("https://api.github.com/" + path, headers={
        "Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    })
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise ReleaseError(f"GitHub metadata check failed (HTTP {error.code}).") from error


def preflight(version):
    valid_version(version)
    require(os.environ.get("GITHUB_REF") == "refs/heads/main", "Run releases and rollbacks from main only.")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    require(repository.lower() == "kehangli/alphaforge", "Unexpected source repository.")
    valid_commit(os.environ.get("GITHUB_SHA", ""))
    for path in (f"git/ref/tags/{version}", f"releases/tags/{version}"):
        require(github_get(f"repos/{repository}/{path}") is None, "This Git tag or release already exists; choose an unused version.")
    print(f"Release {version} targets commit {os.environ['GITHUB_SHA']}.")


def check_image_tag(version):
    valid_version(version)
    token = os.environ.get("GH_TOKEN")
    actor = os.environ.get("GITHUB_ACTOR")
    require(bool(token and actor), "GH_TOKEN and GITHUB_ACTOR are required for GHCR checks.")
    basic = base64.b64encode(f"{actor}:{token}".encode()).decode()
    request = urllib.request.Request(
        "https://ghcr.io/token?service=ghcr.io&scope=repository:kehangli/alphaforge-backend:pull",
        headers={"Authorization": "Basic " + basic})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            bearer = json.load(response)["token"]
        request = urllib.request.Request(
            f"https://ghcr.io/v2/kehangli/alphaforge-backend/manifests/{version}", method="HEAD",
            headers={"Authorization": "Bearer " + bearer, "Accept": ", ".join((
                "application/vnd.oci.image.index.v1+json", "application/vnd.oci.image.manifest.v1+json",
                "application/vnd.docker.distribution.manifest.list.v2+json",
                "application/vnd.docker.distribution.manifest.v2+json"))})
        try:
            with urllib.request.urlopen(request, timeout=30):
                pass
        except urllib.error.HTTPError as error:
            if error.code == 404:
                print(f"GHCR tag {version} is unused.")
                return
            raise
    except urllib.error.HTTPError as error:
        raise ReleaseError(f"Cannot verify unused GHCR tag (HTTP {error.code}); check package Actions access.") from error
    raise ReleaseError("This GHCR version tag already exists. Do not overwrite it; choose a new version.")


def describe_image(reference):
    require(reference.startswith(IMAGE_REPOSITORY + ":") or reference.startswith(IMAGE_REPOSITORY + "@"),
            "Image must belong to the AlphaForge backend GHCR package.")
    manifest = json.loads(run(["docker", "buildx", "imagetools", "inspect", "--raw", reference]))
    if "manifests" in manifest:
        candidates = [item for item in manifest["manifests"]
                      if item.get("platform", {}).get("os") == "linux"
                      and item.get("platform", {}).get("architecture") == "amd64"]
        require(len(candidates) == 1, "Image index must contain exactly one linux/amd64 runtime image.")
        digest = candidates[0].get("digest", "")
    else:
        descriptor = json.loads(run(["docker", "buildx", "imagetools", "inspect", "--format", "{{json .Manifest}}", reference]))
        digest = descriptor.get("digest", "")
    image = valid_image(IMAGE_REPOSITORY + "@" + digest)
    config = json.loads(run(["docker", "buildx", "imagetools", "inspect", "--format", "{{json .Image}}", image]))
    require(config.get("os") == "linux" and config.get("architecture") == "amd64", "Image is not linux/amd64.")
    labels = config.get("config", {}).get("Labels", {}) or {}
    version = labels.get("org.opencontainers.image.version", "")
    commit = labels.get("org.opencontainers.image.revision", "")
    # Old images inherit Temurin's OCI labels. Those identify the JRE, not this application.
    source = labels.get("org.opencontainers.image.source", "").lower()
    if (source != "https://github.com/kehangli/alphaforge"
            or not VERSION.fullmatch(version) or not COMMIT.fullmatch(commit)):
        version = commit = None
    return {"image": image, "version": version, "commit": commit}


def image_record(reference, version, commit, output):
    record = describe_image(reference)
    require(record["version"] == valid_version(version) and record["commit"] == valid_commit(commit),
            "Published image labels do not match the tested commit and release version.")
    write_json(output, record)


def validate_target(target):
    valid_image(target.get("image"))
    if target.get("version") is not None:
        valid_version(target["version"])
    if target.get("commit") is not None:
        valid_commit(target["commit"])
    return {key: target.get(key) for key in ("image", "version", "commit")}


def load_target(path):
    return validate_target(json.loads(Path(path).read_text()))


def azure_arguments():
    names = ("AZURE_SUBSCRIPTION_ID", "AZURE_RESOURCE_GROUP", "AZURE_CONTAINER_APP")
    for name in names:
        require(bool(os.environ.get(name)), f"Set {name} before deployment.")
    return ["--subscription", os.environ[names[0]], "--resource-group", os.environ[names[1]],
            "--name", os.environ[names[2]], "--only-show-errors"]


def snapshot():
    return json.loads(run(["az", "containerapp", "show", *azure_arguments(), "--query", APP_QUERY, "--output", "json"]))


def validate_app(app):
    require(app.get("mode") == "Single" and app.get("maxReplicas") == 1,
            "Keep single revision mode and max replicas = 1 while the limiter uses in-memory counters.")
    require(len(app.get("containers", [])) == 1, "Expected exactly one backend container.")
    host = app.get("fqdn", "")
    require(re.fullmatch(r"[a-z0-9.-]+\.azurecontainerapps\.io", host), "Expected an Azure-managed HTTPS application domain.")
    return "https://" + host


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


def smoke(base_url, target):
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPCookieProcessor(jar))
    with opener.open(base_url + "/health", timeout=20) as response:
        require(response.status == 200, "Health check failed.")
        for field in ("version", "commit"):
            if target.get(field) is not None:
                require(response.headers.get("X-Alphaforge-" + field.title()) == target[field],
                        "Health response still identifies a different release.")
    # Creates only a temporary anonymous session, then invalidates it. No test user or trades.
    with opener.open(base_url + "/api/auth/csrf", timeout=20) as response:
        require(response.status == 200, "CSRF/database session check failed.")
        csrf = json.load(response)
    require(csrf.get("headerName") == "X-CSRF-TOKEN" and isinstance(csrf.get("token"), str) and csrf["token"],
            "CSRF response is invalid.")
    require(any(cookie.name == "ALPHAFORGE_SESSION" for cookie in jar), "Session cookie was not returned.")
    request = urllib.request.Request(base_url + "/api/auth/logout", method="POST", data=b"",
                                     headers={csrf["headerName"]: csrf["token"]})
    with opener.open(request, timeout=20) as response:
        require(response.status == 204, "Temporary session cleanup failed.")


def wait_ready(target, timeout=600):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        app = snapshot()
        base_url = validate_app(app)
        if (app.get("state") == "Succeeded" and app["containers"][0]["image"] == target["image"]
                and (not app.get("ready") or app.get("latest") == app.get("ready"))):
            try:
                smoke(base_url, target)
                return app
            except (ReleaseError, OSError, ValueError):
                pass
        print("Waiting for the target image, serving commit, and database session check...", flush=True)
        time.sleep(10)
    raise ReleaseError("Deployment verification timed out. Check Azure logs; no automatic rollback was attempted.")


def deploy(target_file, report_file):
    target = load_target(target_file)
    actual = describe_image(target["image"])
    require(actual == target, "Target image metadata differs from the recorded release.")
    before = snapshot()
    validate_app(before)
    previous = describe_image(before["containers"][0]["image"])
    require(previous["image"] != target["image"],
            "Target image is already configured. Review the prior run; use a new release version or a different recovery image.")
    record = {"schemaVersion": 1, "startedAt": now(), "status": "prepared", "target": target,
              "previous": previous, "before": before, "runUrl": os.environ.get("RELEASE_RUN_URL", "")}
    write_json(report_file, record)  # Save rollback information before any Azure mutation.
    try:
        current = snapshot()
        require(current == before, "Azure application metadata changed during preparation; restart after reviewing the change.")
        record["status"] = "deploying"
        write_json(report_file, record)
        run(["az", "containerapp", "update", *azure_arguments(), "--container-name", before["containers"][0]["name"],
             "--image", target["image"], "--no-wait", "--output", "none"])
        record["after"] = wait_ready(target)
        record["status"] = "succeeded"
    except Exception:
        record["status"] = "failed"
        raise
    finally:
        record["finishedAt"] = now()
        write_json(report_file, record)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
                summary.write(f"## Backend deployment: {record['status']}\n\n"
                              f"- Target: `{target['image']}`\n"
                              f"- Version: `{target['version'] or 'legacy'}`\n"
                              f"- Commit: `{target['commit'] or 'unknown'}`\n"
                              f"- Previous image: `{previous['image']}`\n\n"
                              "Image rollback does not reverse database migrations.\n")
    print(f"Verified deployment: {target['image']}")


def image_config_id(archive_path):
    # Docker's image inspect ID can identify an OCI index on newer engines;
    # Trivy uses the image configuration digest. Bind the report to the saved config.
    with tarfile.open(archive_path) as archive:
        manifests = json.load(archive.extractfile("manifest.json"))
        require(len(manifests) == 1, "Expected one saved release image.")
        member = archive.getmember(manifests[0]["Config"])
        require(member.isfile() and member.size <= 1024 * 1024, "Invalid saved image configuration.")
        return "sha256:" + hashlib.sha256(archive.extractfile(member).read()).hexdigest()


def check_scan(report_path, image_archive):
    from collections import Counter
    report = json.loads(Path(report_path).read_text())
    require(report.get("Metadata", {}).get("ImageID") == image_config_id(image_archive), "Scan does not belong to the built image.")
    require(report.get("Metadata", {}).get("OS", {}).get("Family") == "ubuntu", "Image scan did not identify the Ubuntu runtime.")
    results = report.get("Results", [])
    require(any(item.get("Class") == "os-pkgs" and item.get("Packages") for item in results),
            "Image scan did not cover OS packages.")
    packages = {package.get("Name") for item in results if item.get("Type") == "jar" for package in item.get("Packages", [])}
    require({"org.springframework.boot:spring-boot", "org.postgresql:postgresql"}.issubset(packages),
            "Image scan did not cover the packaged backend libraries.")
    counts = Counter(v.get("Severity") for item in results for v in item.get("Vulnerabilities", []))
    require(not counts["HIGH"] and not counts["CRITICAL"], "Release image contains HIGH or CRITICAL vulnerabilities.")
    print("Release image scan covers the OS and backend libraries, with no HIGH/CRITICAL findings.")


def publish(version, commit, report_file):
    valid_version(version)
    valid_commit(commit)
    deployment = json.loads(Path(report_file).read_text())
    require(deployment.get("status") == "succeeded", "Only verified deployments can become published releases.")
    target = validate_target(deployment["target"])
    require(target["version"] == version and target["commit"] == commit, "Release metadata does not match deployment.")
    record = {"schemaVersion": 1, **target, "previous": validate_target(deployment["previous"]),
              "deployedAt": deployment["finishedAt"], "runUrl": deployment["runUrl"]}
    release_file = Path(report_file).with_name("release.json")
    write_json(release_file, record)
    notes = release_file.with_name("release-notes.md")
    migrations = sorted(p.name for p in Path("src/main/resources/db/migration").glob("V*__*.sql"))
    notes.write_text(f"Backend release {version}\n\nCommit: `{commit}`\n\nImage: `{target['image']}`\n\n"
                     f"Previous image: `{record['previous']['image']}`\n\n"
                     f"Verified Azure deployment: {record['runUrl']}\n\n"
                     "Frontend is deployed independently by Vercel. Database migrations are not undone by image rollback.\n\n"
                     "Migrations included: " + ", ".join(migrations) + "\n")
    # Draft first: attach the recovery record before publishing (also supports immutable releases).
    run(["gh", "release", "create", version, "--target", commit, "--title", f"Backend {version}",
         "--notes-file", str(notes), "--draft"])
    run(["gh", "release", "upload", version, str(release_file), report_file])
    run(["gh", "release", "edit", version, "--draft=false"])
    print(f"Published GitHub release {version}.")


def rollback_target(version, run_id, selection, output):
    require(bool(version) != bool(run_id), "Choose either a published release version or a deployment run ID.")
    require(os.environ.get("GITHUB_REF") == "refs/heads/main", "Run releases and rollbacks from main only.")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    require(repository.lower() == "kehangli/alphaforge", "Unexpected source repository.")
    destination = Path(output).parent / "downloaded-release"
    destination.mkdir(parents=True, exist_ok=True)
    if version:
        valid_version(version)
        release = github_get(f"repos/{repository}/releases/tags/{version}")
        require(release is not None and not release.get("draft"), "Choose an existing published release.")
        run(["gh", "release", "download", version, "--pattern", "release.json", "--dir", str(destination)])
        record = json.loads((destination / "release.json").read_text())
        require(record.get("schemaVersion") == 1 and record.get("version") == version, "Unsupported or mismatched release record.")
        target = validate_target(record if selection == "release" else record["previous"])
    else:
        require(re.fullmatch(r"[1-9][0-9]*", run_id), "Deployment run ID must be a positive integer.")
        execution = github_get(f"repos/{repository}/actions/runs/{run_id}")
        require(execution and execution.get("path") == ".github/workflows/release-backend.yml"
                and execution.get("head_branch") == "main" and execution.get("event") == "workflow_dispatch"
                and execution.get("status") == "completed", "Choose a completed Release Backend run from main.")
        artifacts = github_get(f"repos/{repository}/actions/runs/{run_id}/artifacts?per_page=100") or {}
        prefix = f"backend-deployment-{run_id}-"
        candidates = [item for item in artifacts.get("artifacts", []) if not item.get("expired")
                      and re.fullmatch(re.escape(prefix) + r"[1-9][0-9]*", item.get("name", ""))]
        candidates.sort(key=lambda item: int(item["name"][len(prefix):]))
        record = None
        # Preserve the original pre-mutation baseline across reruns of failed jobs.
        for artifact in candidates:
            folder = destination / artifact["name"]
            folder.mkdir(exist_ok=True)
            run(["gh", "run", "download", run_id, "--name", artifact["name"], "--dir", str(folder)])
            if (folder / "deployment.json").is_file():
                record = json.loads((folder / "deployment.json").read_text())
                break
        require(record is not None, "No retained deployment snapshot exists for this run; inspect its artifacts.")
        require(record.get("schemaVersion") == 1, "Unsupported deployment record.")
        target = validate_target(record["target"] if selection == "release" else record["previous"])
    require(describe_image(target["image"]) == target, "Stored recovery image no longer matches its record.")
    write_json(output, target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser("preflight")
    check.add_argument("version")
    tag = commands.add_parser("check-image-tag")
    tag.add_argument("version")
    image = commands.add_parser("image-record")
    for name in ("reference", "version", "commit", "output"):
        image.add_argument(name)
    scan = commands.add_parser("check-scan")
    scan.add_argument("report")
    scan.add_argument("image_archive")
    deployment = commands.add_parser("deploy")
    deployment.add_argument("target")
    deployment.add_argument("report")
    publication = commands.add_parser("publish")
    for name in ("version", "commit", "report"):
        publication.add_argument(name)
    rollback = commands.add_parser("rollback-target")
    rollback.add_argument("selection", choices=("release", "previous"))
    rollback.add_argument("output")
    rollback.add_argument("--version", default="")
    rollback.add_argument("--run-id", default="")
    args = parser.parse_args()
    try:
        if args.command == "preflight":
            preflight(args.version)
        elif args.command == "check-image-tag":
            check_image_tag(args.version)
        elif args.command == "image-record":
            image_record(args.reference, args.version, args.commit, args.output)
        elif args.command == "check-scan":
            check_scan(args.report, args.image_archive)
        elif args.command == "deploy":
            deploy(args.target, args.report)
        elif args.command == "publish":
            publish(args.version, args.commit, args.report)
        else:
            rollback_target(args.version, args.run_id, args.selection, args.output)
    except (ReleaseError, OSError, ValueError, KeyError, TypeError, AttributeError, tarfile.TarError) as error:
        # JSON/CLI response contents and credentials are intentionally omitted.
        message = str(error) if isinstance(error, ReleaseError) else "Invalid release metadata, network response, or local file."
        print(f"Backend release failed: {message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
