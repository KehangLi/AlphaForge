import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest

from security_reports import npm_summary, sanitize_gitleaks, trivy_summary


class SecurityReportsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.report = Path(self.directory.name) / "input.json"
        self.output = Path(self.directory.name) / "output.json"

    def write_report(self, report):
        self.report.write_text(json.dumps(report))

    def test_accepts_a_complete_npm_report_with_findings(self):
        self.write_report({
            "vulnerabilities": {"example-package": {"severity": "high"}},
            "metadata": {"vulnerabilities": {
                "info": 0, "low": 0, "moderate": 0, "high": 1, "critical": 0, "total": 1
            }},
        })
        with contextlib.redirect_stdout(io.StringIO()) as stdout:
            npm_summary(self.report)
        self.assertIn("high=1", stdout.getvalue())

    def test_rejects_npm_service_errors_and_incomplete_reports(self):
        for report in ({"error": {"code": "ENOTFOUND"}}, {}, {"vulnerabilities": {}}):
            with self.subTest(report=report):
                self.write_report(report)
                with self.assertRaises(ValueError):
                    npm_summary(self.report)

    def test_rejects_non_json_scanner_output(self):
        self.report.write_text("scanner did not complete")
        with self.assertRaises(ValueError):
            npm_summary(self.report)

    def test_rejects_empty_or_incomplete_backend_scans(self):
        for report in ({"Results": None}, {"Results": []}, {
            "Results": [{"Type": "jar", "Packages": [{"Name": "only-the-app"}]}]
        }):
            with self.subTest(report=report):
                self.write_report(report)
                with self.assertRaises(ValueError):
                    trivy_summary(self.report, ["expected-runtime-library"])

    def test_accepts_backend_scan_with_expected_nested_library(self):
        self.write_report({"Results": [{
            "Type": "jar", "Packages": [{"Name": "expected-runtime-library"}],
            "Vulnerabilities": [{"Severity": "HIGH"}],
        }]})
        with contextlib.redirect_stdout(io.StringIO()) as stdout:
            trivy_summary(self.report, ["expected-runtime-library"])
        self.assertIn("HIGH=1", stdout.getvalue())

    def test_secret_report_and_stdout_never_include_matched_content(self):
        sensitive = "sensitive-" + "test-value"
        self.write_report([{
            "File": "example.txt", "StartLine": 2, "EndLine": 2,
            "RuleID": "example-rule", "Commit": "a" * 40,
            "Secret": sensitive, "Match": "token=" + sensitive,
            "Description": sensitive, "UnexpectedFutureField": sensitive,
        }])
        with contextlib.redirect_stdout(io.StringIO()) as stdout:
            sanitize_gitleaks(self.report, self.output)
        self.assertNotIn(sensitive, self.output.read_text() + stdout.getvalue())
        self.assertEqual(set(json.loads(self.output.read_text())[0]), {
            "File", "StartLine", "EndLine", "RuleID", "Commit"
        })

    def test_empty_secret_report_is_valid(self):
        self.write_report([])
        with contextlib.redirect_stdout(io.StringIO()):
            sanitize_gitleaks(self.report, self.output)
        self.assertEqual(json.loads(self.output.read_text()), [])

    def test_invalid_secret_reports_do_not_create_publishable_output(self):
        for report in ({"error": "failed"}, ["unexpected"], [{"Secret": "example"}]):
            with self.subTest(report=report):
                self.write_report(report)
                with self.assertRaises(ValueError):
                    sanitize_gitleaks(self.report, self.output)
                self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
