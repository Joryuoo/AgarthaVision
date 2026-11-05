"""Focused stdlib verification of command routing, report provenance and failure handling."""
import importlib.util
import html
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("qa_run", Path(__file__).with_name("run.py"))
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)


class CommandTests(unittest.TestCase):
    def test_pre_push_runs_roborazzi_once_then_api(self):
        commands = qa.commands("pre-push", Path("output"))
        self.assertEqual(len(commands), 2)
        self.assertIn(":app:verifyRoborazziDebug", commands[0])
        self.assertNotIn(":app:testDebugUnitTest", commands[0])
        self.assertIn("inference/tests", commands[1])

    def test_production_change_uses_documented_domain_fallback(self):
        with patch.object(qa.subprocess, "check_output", return_value=b"app/src/main/java/Changed.kt\0"):
            command = qa.commands("pre-commit", Path("output"))[-1]
        self.assertEqual(command[-2:], ["--tests", "com.agarthavision.domain.*"])

    def test_staged_test_only_selects_exact_class(self):
        with patch.object(qa.subprocess, "check_output", return_value=b"app/src/test/java/com/agarthavision/FooTest.kt\0"):
            command = qa.commands("pre-commit", Path("output"))[-1]
        self.assertEqual(command[-2:], ["--tests", "com.agarthavision.FooTest"])

    def test_redacts_jwt_and_configured_secrets(self):
        with patch.dict(qa.os.environ, {"QA_SMOKE_TOKEN": "very-secret-value"}):
            self.assertEqual(qa.redact("Bearer eyJabc.def.ghi very-secret-value"), "Bearer [REDACTED_JWT] [REDACTED]")

    def test_failure_is_reported_and_later_checks_still_execute(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(qa.platform, "platform", return_value="test-environment"), patch.object(qa, "ROOT", root), patch.object(sys, "argv", ["run.py", "check"]), patch.object(qa.subprocess, "check_output", side_effect=["revision\n", b""]), patch.object(qa, "commands", return_value=[[sys.executable, "-c", "raise SystemExit(7)"], [sys.executable, "-c", "print('next check')"]]):
                self.assertEqual(qa.main(), 7)
            summary = json.loads(next(root.rglob("summary.json")).read_text())
            self.assertEqual([r["status"] for r in summary["records"]], ["fail", "pass"])

    def test_published_evidence_redacts_gradle_and_direct_api_reports_but_preserves_png(self):
        secret = "synthetic-token<&-12345"
        jwt = "eyJsynthetic.payload.signature"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / "app/build/reports/tests"
            reports.mkdir(parents=True)
            xml = f'<testsuite><testcase name="case"><failure>{html.escape(secret)} {jwt}</failure></testcase></testsuite>'
            (reports / "result.xml").write_text(xml, encoding="utf-8")
            (reports / "index.html").write_text(html.escape(secret), encoding="utf-8")
            # Gradle binary output can include arbitrary logs, so exclude it entirely.
            (reports / "output.bin").write_bytes(secret.encode())
            png = b"\x89PNG\r\n\x1a\nsynthetic-screenshot"
            (reports / "difference.png").write_bytes(png)

            def command_with_api_report(name, output):
                (output / "api.xml").write_text(xml, encoding="utf-8")
                return [[sys.executable, "-c", "pass"]]

            with patch.dict(qa.os.environ, {"QA_SMOKE_TOKEN": secret}), patch.object(qa.platform, "platform", return_value="test-environment"), patch.object(qa, "ROOT", root), patch.object(sys, "argv", ["run.py", "check"]), patch.object(qa.subprocess, "check_output", side_effect=["revision\n", b""]), patch.object(qa, "commands", side_effect=command_with_api_report):
                self.assertEqual(qa.main(), 0)
            output = next((root / "build/qa").iterdir())
            for path in output.rglob("*"):
                if path.is_file() and path.suffix != ".png":
                    text = path.read_text(encoding="utf-8")
                    self.assertNotIn(secret, text)
                    self.assertNotIn(html.escape(secret), text)
                    self.assertNotIn(jwt, text)
            self.assertIn("[REDACTED]", (output / "api.xml").read_text())
            self.assertEqual(next(output.rglob("difference.png")).read_bytes(), png)
            self.assertFalse(list(output.rglob("*.bin")))
            self.assertEqual((reports / "result.xml").read_text(), xml)  # originals stay local
            summary = json.loads((output / "summary.json").read_text())
            self.assertEqual(len(summary["junit"]), 2)
            self.assertEqual(summary["junit"][0]["status"], "fail")
            self.assertEqual(summary["excluded_artifacts"][0]["reason"], "unsupported binary or unreadable report; raw content not published")

    def test_ci_uploads_only_collected_evidence(self):
        workflow = Path(__file__).resolve().parents[2] / ".github/workflows/build-and-test.yml"
        text = workflow.read_text(encoding="utf-8")
        self.assertEqual(text.count("path: build/qa/"), 2)
        self.assertNotIn("            app/build/", text)

    def test_staging_missing_credentials_fails_and_writes_report(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "smoke.json"
            env = {k: v for k, v in qa.os.environ.items() if not k.startswith("QA_SMOKE") and k != "QA_DEPLOYED_REVISION"}
            process = subprocess.run([sys.executable, str(Path(__file__).with_name("smoke.py")), "--output", str(output)], env=env, capture_output=True)
            self.assertEqual(process.returncode, 1)
            self.assertEqual(json.loads(output.read_text())["status"], "fail")


if __name__ == "__main__":
    unittest.main()

