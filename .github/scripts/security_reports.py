"""Validate audit output and publish secret findings without matched content."""

import argparse
from collections import Counter
import json
from pathlib import Path
import sys


def npm_summary(report_path):
    report = json.loads(Path(report_path).read_text())
    if not isinstance(report, dict) or "error" in report:
        raise ValueError("npm audit returned an error instead of a complete audit")
    if not isinstance(report.get("vulnerabilities"), dict):
        raise ValueError("npm audit report is missing its vulnerability details")
    counts = report.get("metadata", {}).get("vulnerabilities", {})
    levels = ("info", "low", "moderate", "high", "critical", "total")
    if any(type(counts.get(level)) is not int or counts[level] < 0 for level in levels):
        raise ValueError("npm audit report is missing valid vulnerability counts")
    print("npm audit: " + ", ".join(f"{level}={counts[level]}" for level in levels))


def sanitize_gitleaks(report_path, output_path):
    findings = json.loads(Path(report_path).read_text())
    if not isinstance(findings, list):
        raise ValueError("Gitleaks did not produce a findings list")
    safe_findings = []
    for finding in findings:
        if not isinstance(finding, dict):
            raise ValueError("Gitleaks returned an invalid finding")
        for field in ("File", "RuleID", "Commit"):
            if not isinstance(finding.get(field), str):
                raise ValueError("Gitleaks finding is missing location or rule information")
        if any(type(finding.get(field)) is not int for field in ("StartLine", "EndLine")):
            raise ValueError("Gitleaks finding is missing line numbers")
        safe_findings.append({
            field: finding[field]
            for field in ("File", "StartLine", "EndLine", "RuleID", "Commit")
        })
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(safe_findings, indent=2) + "\n")
    print(f"Gitleaks: {len(safe_findings)} potential secret findings; matched content omitted")


def trivy_summary(report_path, required_packages):
    report = json.loads(Path(report_path).read_text())
    if not isinstance(report, dict) or not isinstance(report.get("Results"), list):
        raise ValueError("Trivy did not report scanned libraries")
    packages = {
        package["Name"]
        for result in report["Results"]
        if result.get("Type") == "jar"
        for package in result.get("Packages", [])
        if isinstance(package.get("Name"), str)
    }
    if not packages or not set(required_packages).issubset(packages):
        raise ValueError("Trivy did not identify the required packaged dependencies")
    counts = Counter(
        vulnerability["Severity"]
        for result in report["Results"]
        for vulnerability in result.get("Vulnerabilities", [])
    )
    print(f"Trivy: {len(packages)} distinct JAR libraries identified; " +
          ", ".join(f"{level}={counts[level]}" for level in ("UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL")))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    npm = commands.add_parser("npm-summary")
    npm.add_argument("report")
    trivy = commands.add_parser("trivy-summary")
    trivy.add_argument("report")
    trivy.add_argument("--require-package", action="append", required=True)
    secrets = commands.add_parser("sanitize-gitleaks")
    secrets.add_argument("report")
    secrets.add_argument("output")
    args = parser.parse_args()
    try:
        if args.command == "npm-summary":
            npm_summary(args.report)
        elif args.command == "trivy-summary":
            trivy_summary(args.report, args.require_package)
        else:
            sanitize_gitleaks(args.report, args.output)
    except (OSError, ValueError, TypeError, AttributeError, KeyError) as error:
        print(f"Security report validation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
