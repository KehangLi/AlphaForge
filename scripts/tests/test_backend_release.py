import importlib.util
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import tarfile
import unittest
from unittest.mock import patch
import urllib.error


SPEC = importlib.util.spec_from_file_location("backend_release", Path(__file__).parents[1] / "backend_release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
IMAGE = release.IMAGE_REPOSITORY + "@sha256:" + "a" * 64
OLD_IMAGE = release.IMAGE_REPOSITORY + "@sha256:" + "b" * 64
TARGET = {"image": IMAGE, "version": "v0.2.0", "commit": "a" * 40}
PREVIOUS = {"image": OLD_IMAGE, "version": None, "commit": None}


def app(image=OLD_IMAGE):
    return {"name": "alphaforge-backend", "state": "Succeeded", "mode": "Single", "maxReplicas": 1,
            "fqdn": "alphaforge-backend.example.canadacentral.azurecontainerapps.io",
            "latest": "backend--latest", "ready": "backend--latest",
            "containers": [{"name": "backend", "image": image}]}


class Response(io.BytesIO):
    status = 200

    def __init__(self, body=b"", headers=None):
        super().__init__(body)
        self.headers = headers or {}


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def file(self, name, data):
        path = self.directory / name
        release.write_json(path, data)
        return str(path)

    def archive(self, name, config):
        path = self.directory / name
        with tarfile.open(path, 'w') as archive:
            for filename, raw in [('manifest.json', b'[{"Config":"config.json"}]'), ('config.json', config)]:
                member = tarfile.TarInfo(filename)
                member.size = len(raw)
                archive.addfile(member, io.BytesIO(raw))
        return str(path)

    def test_rejects_unsafe_versions_and_non_digest_or_foreign_images(self):
        for version in ("v01.2.0", "v1.2", "v1.2.3; echo unsafe", "--help", "v1.2.3\n"):
            with self.subTest(version=version), self.assertRaises(release.ReleaseError):
                release.valid_version(version)
        for image in (release.IMAGE_REPOSITORY + ":latest", "ghcr.io/other/backend@sha256:" + "a" * 64, IMAGE + "\n"):
            with self.subTest(image=image), self.assertRaises(release.ReleaseError):
                release.valid_image(image)

    @patch.dict(os.environ, {"GITHUB_REF": "refs/heads/feature"})
    @patch.object(release, "github_get")
    def test_non_main_source_cannot_reach_registry_or_azure(self, github):
        with self.assertRaises(release.ReleaseError):
            release.preflight("v0.2.0")
        github.assert_not_called()

    @patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main", "GITHUB_REPOSITORY": "KehangLi/AlphaForge", "GITHUB_SHA": "a" * 40})
    @patch.object(release, "github_get", return_value={"ref": "existing"})
    def test_existing_git_version_is_not_reused(self, _github):
        with self.assertRaisesRegex(release.ReleaseError, "already exists"):
            release.preflight("v0.2.0")

    @patch.dict(os.environ, {"GH_TOKEN": "test-only", "GITHUB_ACTOR": "kehangli"})
    @patch.object(release.urllib.request, "urlopen")
    def test_registry_auth_failure_is_not_mistaken_for_unused_tag(self, urlopen):
        urlopen.side_effect = [Response(b'{"token":"test-only"}'),
                               urllib.error.HTTPError("https://ghcr.io", 403, "denied", {}, None)]
        with self.assertRaisesRegex(release.ReleaseError, "HTTP 403"):
            release.check_image_tag("v0.2.0")

    @patch.dict(os.environ, {"GH_TOKEN": "test-only", "GITHUB_ACTOR": "kehangli"})
    @patch.object(release.urllib.request, "urlopen")
    def test_existing_registry_tag_is_never_overwritten(self, urlopen):
        urlopen.side_effect = [Response(b'{"token":"test-only"}'), Response()]
        with self.assertRaisesRegex(release.ReleaseError, "already exists"):
            release.check_image_tag("v0.2.0")

    @patch.object(release, "run")
    def test_selects_amd64_runtime_manifest_and_ignores_attestation(self, run):
        run.side_effect = [json.dumps({"manifests": [
            {"platform": {"os": "unknown", "architecture": "unknown"}, "digest": "sha256:" + "c" * 64},
            {"platform": {"os": "linux", "architecture": "amd64"}, "digest": "sha256:" + "a" * 64},
        ]}), json.dumps({"os": "linux", "architecture": "amd64", "config": {"Labels": {
            "org.opencontainers.image.source": "https://github.com/KehangLi/AlphaForge",
            "org.opencontainers.image.version": "v0.2.0", "org.opencontainers.image.revision": "a" * 40,
        }}})]
        self.assertEqual(release.describe_image(release.IMAGE_REPOSITORY + ":v0.2.0"), TARGET)
        self.assertEqual(run.call_args_list[1].args[0][-1], IMAGE)

    @patch.object(release, "run")
    def test_initial_recovery_does_not_treat_inherited_jre_labels_as_an_app_release(self, run):
        run.side_effect = [json.dumps({"schemaVersion": 2}), json.dumps({"digest": "sha256:" + "b" * 64}),
                           json.dumps({"os": "linux", "architecture": "amd64", "config": {"Labels": {
                               "org.opencontainers.image.source": "https://github.com/adoptium/containers",
                               "org.opencontainers.image.version": "21.0.12", "org.opencontainers.image.revision": "b" * 40,
                           }}})]
        self.assertEqual(release.describe_image(release.IMAGE_REPOSITORY + ":0.1.0"), PREVIOUS)

    @patch.object(release, "run", return_value='{"manifests":[{"platform":{"os":"linux","architecture":"arm64"}}]}')
    def test_arm_only_image_is_rejected(self, _run):
        with self.assertRaisesRegex(release.ReleaseError, "linux/amd64"):
            release.describe_image(release.IMAGE_REPOSITORY + ":v0.2.0")

    def test_scan_requires_matching_image_os_coverage_and_backend_libraries(self):
        archive = self.archive("image.tar", b'{}')
        image_id = "sha256:" + hashlib.sha256(b'{}').hexdigest()
        report = {"Metadata": {"ImageID": image_id, "OS": {"Family": "ubuntu"}}, "Results": [
            {"Class": "os-pkgs", "Packages": [{"Name": "libc6"}]},
            {"Type": "jar", "Packages": [{"Name": "org.springframework.boot:spring-boot"}, {"Name": "org.postgresql:postgresql"}]},
        ]}
        path = self.file("scan.json", report)
        release.check_scan(path, archive)
        with self.assertRaisesRegex(release.ReleaseError, "built image"):
            release.check_scan(path, self.archive("other.tar", b'{"other":true}'))
        report["Results"][0]["Vulnerabilities"] = [{"Severity": "HIGH"}]
        with self.assertRaisesRegex(release.ReleaseError, "HIGH"):
            release.check_scan(self.file("high.json", report), archive)
        report["Results"] = []
        with self.assertRaisesRegex(release.ReleaseError, "OS packages"):
            release.check_scan(self.file("empty.json", report), archive)

    def test_rejects_scale_out_and_multi_revision_while_limiter_is_in_memory(self):
        for key, value in (("maxReplicas", 2), ("mode", "Multiple")):
            current = app()
            current[key] = value
            with self.assertRaisesRegex(release.ReleaseError, "single revision"):
                release.validate_app(current)

    @patch.object(release.urllib.request, "build_opener")
    def test_old_healthy_process_does_not_count_as_new_release(self, build_opener):
        opener = build_opener.return_value
        opener.open.return_value = Response(headers={"X-Alphaforge-Version": "v0.1.0", "X-Alphaforge-Commit": "b" * 40})
        with self.assertRaisesRegex(release.ReleaseError, "different release"):
            release.smoke("https://backend.example", TARGET)
        self.assertEqual(opener.open.call_count, 1)

    @patch.object(release.time, "sleep")
    @patch.object(release, "snapshot", return_value=app(IMAGE))
    @patch.object(release, "smoke", side_effect=[release.ReleaseError("old serving commit"), None])
    def test_waits_past_an_old_healthy_process_until_the_target_serves(self, smoke, _snapshot, sleep):
        self.assertEqual(release.wait_ready(TARGET, timeout=1)["containers"][0]["image"], IMAGE)
        self.assertEqual(smoke.call_count, 2)
        sleep.assert_called_once()

    @patch.object(release.time, "monotonic", side_effect=[0, 0, 2])
    @patch.object(release.time, "sleep")
    @patch.object(release, "snapshot", return_value=app())
    @patch.object(release, "smoke")
    def test_timeout_never_accepts_an_old_azure_image(self, smoke, _snapshot, _sleep, _clock):
        with self.assertRaisesRegex(release.ReleaseError, "timed out"):
            release.wait_ready(TARGET, timeout=1)
        smoke.assert_not_called()

    @patch.object(release, "snapshot")
    @patch.object(release, "describe_image")
    @patch.object(release, "wait_ready", return_value=app(IMAGE))
    @patch.object(release, "run", return_value="")
    def test_image_only_update_preserves_configuration_and_records_previous_before_mutation(self, run, _wait, describe, snapshot):
        snapshot.return_value = app()
        describe.side_effect = [TARGET, PREVIOUS]
        target_file = self.file("target.json", TARGET)
        report = self.directory / "deployment.json"
        environment = {"AZURE_SUBSCRIPTION_ID": "subscription", "AZURE_RESOURCE_GROUP": "AlphaForge", "AZURE_CONTAINER_APP": "alphaforge-backend"}

        def update(arguments, **_kwargs):
            saved = json.loads(report.read_text())
            self.assertEqual(saved["previous"], PREVIOUS)
            self.assertEqual(saved["status"], "deploying")
            self.assertEqual(arguments[arguments.index("--image") + 1], IMAGE)
            self.assertNotIn("--set-env-vars", arguments)
            self.assertNotIn("--replace-env-vars", arguments)
            self.assertNotIn("--yaml", arguments)
            return ""

        run.side_effect = update
        with patch.dict(os.environ, environment):
            release.deploy(target_file, str(report))
        self.assertEqual(json.loads(report.read_text())["status"], "succeeded")

    @patch.object(release, "snapshot", return_value=app())
    @patch.object(release, "describe_image", side_effect=[TARGET, PREVIOUS])
    @patch.object(release, "run", side_effect=release.ReleaseError("update rejected"))
    def test_failed_azure_update_keeps_recovery_record_and_never_claims_success(self, _run, _describe, _snapshot):
        report = self.directory / "failed.json"
        with patch.dict(os.environ, {"AZURE_SUBSCRIPTION_ID": "s", "AZURE_RESOURCE_GROUP": "g", "AZURE_CONTAINER_APP": "a"}):
            with self.assertRaises(release.ReleaseError):
                release.deploy(self.file("target.json", TARGET), str(report))
        record = json.loads(report.read_text())
        self.assertEqual(record["status"], "failed")
        self.assertEqual(record["previous"]["image"], OLD_IMAGE)

    @patch.object(release, "snapshot", side_effect=[app(), {**app(), "state": "Updating"}])
    @patch.object(release, "describe_image", side_effect=[TARGET, PREVIOUS])
    @patch.object(release, "run")
    def test_competing_manual_deployment_is_detected_before_update(self, run, _describe, _snapshot):
        with self.assertRaisesRegex(release.ReleaseError, "changed"):
            release.deploy(self.file("target.json", TARGET), str(self.directory / "race.json"))
        run.assert_not_called()

    @patch.object(release, "snapshot", return_value=app(IMAGE))
    @patch.object(release, "describe_image", side_effect=[TARGET, TARGET])
    @patch.object(release, "run")
    def test_retry_does_not_overwrite_rollback_baseline_with_the_same_image(self, run, _describe, _snapshot):
        with self.assertRaisesRegex(release.ReleaseError, "already configured"):
            release.deploy(self.file("target.json", TARGET), str(self.directory / "retry.json"))
        run.assert_not_called()

    @patch.object(release, "run")
    def test_failed_deployment_cannot_be_published_as_release(self, run):
        with self.assertRaisesRegex(release.ReleaseError, "verified deployments"):
            release.publish("v0.2.0", "a" * 40, self.file("failed.json", {"status": "failed"}))
        run.assert_not_called()

    @patch.object(release, "run")
    def test_recovery_record_is_attached_before_publishing_draft(self, run):
        path = self.file("deployment.json", {"status": "succeeded", "target": TARGET, "previous": PREVIOUS,
                                             "finishedAt": "2026-10-30T00:00:00Z", "runUrl": "https://github.com/example/run"})
        release.publish("v0.2.0", "a" * 40, path)
        commands = [call.args[0] for call in run.call_args_list]
        self.assertIn("--draft", commands[0])
        self.assertEqual(commands[1][:3], ["gh", "release", "upload"])
        self.assertEqual(commands[2][-1], "--draft=false")
        self.assertEqual(json.loads((self.directory / "release.json").read_text())["previous"], PREVIOUS)

    @patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main", "GITHUB_REPOSITORY": "KehangLi/AlphaForge"})
    @patch.object(release, "github_get", return_value={"path": ".github/workflows/ci.yml", "head_branch": "main", "event": "workflow_dispatch", "status": "completed"})
    @patch.object(release, "run")
    def test_cannot_use_unrelated_workflow_artifacts_for_rollback(self, run, _github):
        with self.assertRaisesRegex(release.ReleaseError, "Release Backend"):
            release.rollback_target("", "123", "previous", str(self.directory / "target.json"))
        run.assert_not_called()

    @patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main", "GITHUB_REPOSITORY": "KehangLi/AlphaForge"})
    @patch.object(release, "describe_image", return_value=PREVIOUS)
    @patch.object(release, "github_get")
    @patch.object(release, "run")
    def test_failed_run_recovery_uses_earliest_saved_baseline_across_reruns(self, run, github, _describe):
        github.side_effect = [
            {"path": ".github/workflows/release-backend.yml", "head_branch": "main", "event": "workflow_dispatch", "status": "completed"},
            {"artifacts": [
                {"name": "backend-deployment-123-2", "expired": False},
                {"name": "backend-deployment-123-1", "expired": False},
            ]},
        ]

        def download(arguments, **_kwargs):
            self.assertEqual(arguments[arguments.index("--name") + 1], "backend-deployment-123-1")
            folder = arguments[arguments.index("--dir") + 1]
            release.write_json(Path(folder) / "deployment.json", {"schemaVersion": 1, "target": TARGET, "previous": PREVIOUS})
            return ""

        run.side_effect = download
        destination = str(self.directory / "target.json")
        release.rollback_target("", "123", "previous", destination)
        self.assertEqual(json.loads(Path(destination).read_text()), PREVIOUS)

    @patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main", "GITHUB_REPOSITORY": "KehangLi/AlphaForge"})
    @patch.object(release, "describe_image", return_value=TARGET)
    @patch.object(release, "github_get", return_value={"draft": False})
    @patch.object(release, "run")
    def test_published_release_can_restore_its_exact_recorded_image(self, run, _github, _describe):
        def download(arguments, **_kwargs):
            folder = arguments[arguments.index("--dir") + 1]
            release.write_json(Path(folder) / "release.json", {"schemaVersion": 1, **TARGET, "previous": PREVIOUS})
            return ""

        run.side_effect = download
        destination = str(self.directory / "target.json")
        release.rollback_target("v0.2.0", "", "release", destination)
        self.assertEqual(json.loads(Path(destination).read_text()), TARGET)

    @patch.object(release.subprocess, "run")
    def test_cli_errors_cannot_echo_secret_values_into_logs(self, command):
        command.return_value.returncode = 1
        command.return_value.stderr = 'connection failed: DB_PASSWORD=do-not-print'
        with self.assertRaises(release.ReleaseError) as error:
            release.run(["az", "containerapp", "update"])
        self.assertNotIn("do-not-print", str(error.exception))

    def test_no_secrets_are_selected_in_azure_snapshot(self):
        self.assertNotIn("secrets", release.APP_QUERY.lower())
        self.assertNotIn("env", release.APP_QUERY.lower())


if __name__ == "__main__":
    unittest.main()
