"""Focused stdlib verification of command routing, report provenance and failure handling."""
import importlib.util
import html
import io
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
    def setUp(self):
        patcher = patch.object(qa, "missing_packages", return_value=[])
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_production_without_staged_unit_tests_warns_and_keeps_existing_commands(self):
        for path, subsystem in (("app/src/main/java/com/agarthavision/Feature.kt", "Android"), ("app/src/release/kotlin/com/agarthavision/Feature.kt", "Android"), ("inference/server.py", "inference")):
            with self.subTest(path=path), patch.object(qa.subprocess, "check_output", return_value=(path + "\0").encode()), patch("sys.stdout", new_callable=io.StringIO) as output:
                commands = qa.commands("pre-commit", Path("output"))
                self.assertIn(f"Staged {subsystem} production code", output.getvalue())
                self.assertIn("Commit checks will continue", output.getvalue())
                self.assertIn("does not measure coverage", output.getvalue())
                self.assertEqual(commands, [qa.gradle(":app:compileDebugKotlin", ":app:ktlintCheck", ":app:detekt"), qa.gradle(":app:testDebugUnitTest", "--tests", "com.agarthavision.domain.*")])

    def test_matching_staged_unit_tests_suppress_only_their_subsystem_warning(self):
        android = "app/src/main/java/com/agarthavision/Feature.kt"
        inference = "inference/server.py"
        cases = [
            ([android, "app/src/test/java/com/agarthavision/FeatureTest.kt"], []),
            ([android, "app/src/testDebug/kotlin/com/agarthavision/FeatureTests.kt"], []),
            ([inference, "inference/tests/test_server.py"], []),
            ([inference, "inference/tests/server_test.py"], []),
            ([android, inference, "app/src/test/java/com/agarthavision/FeatureTest.kt"], ["inference"]),
            ([android, inference, "inference/tests/test_server.py"], ["Android"]),
        ]
        for paths, warnings in cases:
            with self.subTest(paths=paths), patch("sys.stdout", new_callable=io.StringIO) as output:
                qa.warn_missing_staged_unit_tests(paths, paths)
                self.assertEqual(output.getvalue().count("WARNING:"), len(warnings))
                for subsystem in warnings:
                    self.assertIn(f"Staged {subsystem} production code", output.getvalue())

    def test_docs_config_resources_and_test_only_changes_do_not_warn(self):
        paths = ["README.md", "docs/commands.md", "app/build.gradle.kts", "app/src/main/AndroidManifest.xml",
                 "app/src/main/res/values/strings.xml", "inference/requirements.txt", "inference/export/export_mobile.py",
                 "inference/tests/test_server.py", "inference/tests/conftest.py", "app/src/test/java/FeatureTest.kt",
                 "app/src/androidTest/java/FeatureTest.kt", "tools/qa/run.py"]
        with patch("sys.stdout", new_callable=io.StringIO) as output:
            qa.warn_missing_staged_unit_tests(paths, paths)
            self.assertEqual(output.getvalue(), "")

    def test_deleted_unstaged_helper_or_device_tests_do_not_suppress_reminder(self):
        android = "app/src/main/java/com/agarthavision/Feature.kt"
        cases = [([], []),  # An unstaged test never appears in staged paths.
                 (["app/src/test/java/com/agarthavision/FeatureTest.kt"], []),  # deleted
                 (["app/src/test/java/com/agarthavision/Fixture.kt"], ["app/src/test/java/com/agarthavision/Fixture.kt"]),
                 (["app/src/androidTest/java/FeatureTest.kt"], ["app/src/androidTest/java/FeatureTest.kt"])]
        for tests, modified_tests in cases:
            with self.subTest(tests=tests), patch("sys.stdout", new_callable=io.StringIO) as output:
                qa.warn_missing_staged_unit_tests([android, *tests], [android, *modified_tests])
                self.assertIn("WARNING: Staged Android", output.getvalue())

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
        test = b"app/src/test/java/com/agarthavision/FooTest.kt\0"
        source = "package com.agarthavision\n\nclass FooTest {\n    @Test fun a() {}\n}\n"
        with patch.object(qa.subprocess, "check_output", side_effect=[test, test, b""]), patch.object(qa, "read_staged", return_value=source):
            command = qa.commands("pre-commit", Path("output"))[-1]
        self.assertEqual(command[-2:], ["--tests", "com.agarthavision.FooTest"])

    def test_deleted_or_renamed_staged_tests_use_safe_fallback(self):
        old = b"app/src/test/java/com/agarthavision/OldTest.kt\0"
        new = b"app/src/test/java/com/agarthavision/NewTest.kt\0"
        cases = [
            (old, b"", old),  # deleted test only
            (old + new, new, old),  # deleted test plus a live added/modified test
            (new, new, new),  # Git reports only destination for detected rename
            (old + new, new, old + new),  # rename represented as delete + add
        ]
        for all_files, changed_files, removed_or_renamed in cases:
            with self.subTest(all_files=all_files), patch.object(qa.subprocess, "check_output", side_effect=[all_files, changed_files, removed_or_renamed]) as git:
                commands = qa.commands("pre-commit", Path("output"))
            self.assertEqual(commands[-1], qa.gradle(":app:testDebugUnitTest", "--tests", "com.agarthavision.domain.*"))
            self.assertIn("--diff-filter=DR", git.call_args_list[2].args[0])
            self.assertIn("--find-renames", git.call_args_list[2].args[0])

    def test_redacts_jwt_and_configured_secrets(self):
        with patch.dict(qa.os.environ, {"QA_SMOKE_TOKEN": "very-secret-value"}):
            self.assertEqual(qa.redact("Bearer eyJabc.def.ghi very-secret-value"), "Bearer [REDACTED_JWT] [REDACTED]")

    def test_staging_limitations_follow_actual_smoke_mode_and_result(self):
        cases = [
            ({"mode": "designated-staging", "status": "pass", "revision": "deployed-123"}, [], 0, "Staging API smoke passed"),
            ({"mode": "designated-staging", "status": "fail", "revision": None, "records": [{"case_id": "SMOKE-SETUP", "status": "fail"}]}, [], 1, "Staging smoke did not pass"),
            ({"mode": "designated-staging", "status": "pass", "revision": "deployed-123"}, [], 1, "Staging smoke did not pass"),
            ({"mode": "localhost-fake-model", "status": "pass"}, ["--local"], 0, "SKIP deployed backend/staging"),
            (None, [], 1, "Staging smoke has no usable structured outcome"),
        ]
        for report, arguments, code, expected_note in cases:
            with self.subTest(report=report), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)

                def smoke_command(name, output):
                    if report is not None:
                        (output / "smoke.json").write_text(json.dumps(report), encoding="utf-8")
                    return [[sys.executable, "-c", f"raise SystemExit({code})"]]

                with patch.object(qa.platform, "platform", return_value="test-environment"), patch.object(qa, "ROOT", root), patch.object(sys, "argv", ["run.py", "smoke", *arguments]), patch.object(qa.subprocess, "check_output", side_effect=["revision\n", b""]), patch.object(qa, "commands", side_effect=smoke_command):
                    self.assertEqual(qa.main(), code)
                summary = json.loads(next(root.rglob("summary.json")).read_text())
                self.assertTrue(any(expected_note in note for note in summary["limitations"]))
                if report and report["mode"] == "designated-staging":
                    self.assertFalse(any("SKIP deployed backend/staging" in note for note in summary["limitations"]))
                    self.assertEqual(summary["smoke"]["status"], "pass" if report["status"] == "pass" and code == 0 else "fail")
                    self.assertEqual(summary["smoke"]["deployed_revision"], report["revision"])

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

    def test_device_reports_preserve_case_status_artifacts_and_redaction(self):
        secret = "synthetic-device-token"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def device_results(name, output):
                results = root / "app/build/outputs/androidTest-results/connected/debug"
                results.mkdir(parents=True)
                (results / "TEST-Pixel_6a.xml").write_text(
                    '<testsuite><testcase classname="Device" name="passes"/>'
                    f'<testcase classname="Device" name="fails"><failure>{secret}</failure></testcase>'
                    '<testcase classname="Device" name="skips"><skipped message="fixture unavailable"/></testcase></testsuite>', encoding="utf-8")
                report = root / "app/build/reports/androidTests/connected/debug"
                report.mkdir(parents=True)
                (report / "index.html").write_text(f"<html>{secret}</html>", encoding="utf-8")
                return [[sys.executable, "-c", "raise SystemExit(1)"]]

            with patch.dict(qa.os.environ, {"QA_SMOKE_TOKEN": secret}), patch.object(qa.platform, "platform", return_value="test-environment"), patch.object(qa, "ROOT", root), patch.object(sys, "argv", ["run.py", "device"]), patch.object(qa.subprocess, "check_output", side_effect=["revision\n", b""]), patch.object(qa, "commands", side_effect=device_results):
                self.assertEqual(qa.main(), 1)
            output = next((root / "build/qa").iterdir())
            summary = json.loads((output / "summary.json").read_text())
            cases = {case["case_id"]: case for case in summary["junit"]}
            self.assertEqual({name: case["status"] for name, case in cases.items()}, {"Device.passes": "pass", "Device.fails": "fail", "Device.skips": "skip"})
            for case in cases.values():
                self.assertEqual(case["requirement_id"], "QA-device-instrumentation")
                self.assertTrue((output / case["artifact"]).is_file())
                self.assertTrue(case["updated_during_run"])
            self.assertIn("[REDACTED]", next(output.rglob("index.html")).read_text())
            self.assertNotIn(secret, next(output.rglob("TEST-Pixel_6a.xml")).read_text())
            self.assertFalse(any("not executed" in note for note in summary["limitations"]))
            self.assertTrue(any("System capture/offline" in note for note in summary["limitations"]))

    def test_device_without_case_reports_does_not_claim_case_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(qa.platform, "platform", return_value="test-environment"), patch.object(qa, "ROOT", root), patch.object(sys, "argv", ["run.py", "device"]), patch.object(qa.subprocess, "check_output", side_effect=["revision\n", b""]), patch.object(qa, "commands", return_value=[[sys.executable, "-c", "pass"]]):
                self.assertEqual(qa.main(), 0)
            summary = json.loads(next(root.rglob("summary.json")).read_text())
            self.assertEqual(summary["junit"], [])
            self.assertTrue(any("No individual instrumentation results" in note for note in summary["limitations"]))

    def test_staging_missing_credentials_fails_and_writes_report(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "smoke.json"
            env = {k: v for k, v in qa.os.environ.items() if not k.startswith("QA_SMOKE") and k != "QA_DEPLOYED_REVISION"}
            process = subprocess.run([sys.executable, str(Path(__file__).with_name("smoke.py")), "--output", str(output)], env=env, capture_output=True)
            self.assertEqual(process.returncode, 1)
            self.assertEqual(json.loads(output.read_text())["status"], "fail")


FALLBACK = qa.gradle(":app:testDebugUnitTest", "--tests", "com.agarthavision.domain.*")


class SelectionTests(unittest.TestCase):
    def select(self, path, source):
        files = (path + "\0").encode()
        with patch.object(qa.subprocess, "check_output", side_effect=[files, files, b""]), patch.object(qa, "read_staged", return_value=source), patch("sys.stdout", new_callable=io.StringIO):
            return qa.commands("pre-commit", Path("output"))[-1]

    def test_class_name_differs_from_file_name(self):
        source = "package com.agarthavision.x\n\nclass SomethingElseTest {\n  @Test fun a() {}\n}\n"
        self.assertEqual(self.select("app/src/test/java/com/agarthavision/x/FooTest.kt", source)[-2:], ["--tests", "com.agarthavision.x.SomethingElseTest"])

    def test_package_differs_from_directory(self):
        source = "package com.other.pkg\n\nclass FooTest {\n  @Test fun a() {}\n}\n"
        self.assertEqual(self.select("app/src/test/kotlin/com/agarthavision/FooTest.kt", source)[-2:], ["--tests", "com.other.pkg.FooTest"])

    def test_java_and_multiple_classes(self):
        source = "package a.b;\npublic class OneTest {\n @Test void x() {}\n}\nclass TwoTests {\n}\nclass Helper {\n}\n"
        self.assertEqual(self.select("app/src/test/java/a/b/OneTest.java", source)[-4:], ["--tests", "a.b.OneTest", "--tests", "a.b.TwoTests"])

    def test_abstract_object_interface_or_unparseable_fall_back(self):
        cases = ["package a\n\nabstract class BaseTest {\n @Test fun x() {}\n}\n",
                 "package a\n\nobject Fixtures {\n}\n",
                 "package a\n\ninterface Contract {\n @Test fun x()\n}\n",
                 "class NoPackageTest {\n @Test fun x() {}\n}\n",
                 "package a\n\nclass NoTestsHere {\n}\n",
                 "package a\n\nenum class Mode { A }\ndata class D(val x: Int)\n"]
        for source in cases:
            with self.subTest(source=source):
                self.assertEqual(self.select("app/src/test/java/a/FooTest.kt", source), FALLBACK)

    def test_any_uncertain_file_forces_fallback(self):
        good = "package a\nclass GoodTest {\n @Test fun x() {}\n}\n"
        bad = "package a\nabstract class Base {\n}\n"
        self.assertIsNone(qa.select_staged_test_classes(["g", "b"], reader=lambda p: good if p == "g" else bad))
        self.assertIsNone(qa.select_staged_test_classes(["g"], reader=lambda p: None))
        self.assertEqual(qa.select_staged_test_classes(["g"], reader=lambda p: good), ["a.GoodTest"])


class PreflightTests(unittest.TestCase):
    TEXT = "# comment\nfastapi\nuvicorn[standard]>=0.3  # pin\npillow\nPyJWT[crypto]==2.0\n-r other.txt\nhttpx\npytest\n"

    def test_requirement_names_map_to_imports(self):
        self.assertEqual([m for _, m in qa.requirement_import_names(self.TEXT)], ["fastapi", "uvicorn", "PIL", "jwt", "httpx", "pytest"])

    def test_lists_all_missing_at_once(self):
        absent = {"PIL", "jwt", "httpx"}
        with patch.object(qa, "importable", side_effect=lambda m: m not in absent):
            self.assertEqual(qa.missing_packages(self.TEXT), ["pillow", "PyJWT", "httpx"])

    def test_main_short_circuits_before_any_command_and_records_failure(self):
        for name in sorted(qa.PREFLIGHT_RUNS - {"verify"}):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                with patch.object(qa, "ROOT", root), patch.object(sys, "argv", ["run.py", name]), patch.object(qa.subprocess, "check_output", side_effect=["revision\n", b""]), \
                        patch.object(qa, "missing_packages", return_value=["pillow", "PyJWT"]), patch.object(qa.subprocess, "Popen") as popen, \
                        patch.object(qa, "commands") as commands, patch("sys.stdout", new_callable=io.StringIO) as out:
                    self.assertEqual(qa.main(), 1)
                popen.assert_not_called()
                commands.assert_not_called()
                self.assertEqual(out.getvalue().count("QA preflight failed"), 1)
                for text in ("pillow", "PyJWT", "python -m pip install -r inference/requirements-test.txt", "PATH"):
                    self.assertIn(text, out.getvalue())
                summary = json.loads(next(root.rglob("summary.json")).read_text())
                self.assertEqual(summary["exit_code"], 1)
                self.assertEqual(summary["records"][0]["case_id"], "QA-PREFLIGHT")
                self.assertEqual(summary["records"][0]["status"], "fail")
                self.assertIn("pillow, PyJWT", summary["records"][0]["actual"])

    def test_non_gated_runs_skip_preflight(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(qa, "ROOT", Path(directory)), patch.object(sys, "argv", ["run.py", "unit"]), \
                patch.object(qa.subprocess, "check_output", side_effect=["revision\n", b""]), patch.object(qa, "missing_packages", side_effect=AssertionError), \
                patch.object(qa, "commands", return_value=[[sys.executable, "-c", "pass"]]):
            self.assertEqual(qa.main(), 0)


class PushSkipTests(unittest.TestCase):
    ZERO = "0" * 40
    A = "a" * 40
    B = "b" * 40

    def ref(self, local=None, remote=None):
        return f"refs/heads/x {local or self.B} refs/heads/x {remote or self.A}\n"

    def plan(self, text, changes):
        with patch.object(qa, "PUSH_SKIPS_ENABLED", True):
            return qa.pre_push_skips(text, changes)

    def test_disabled_by_default_constant(self):
        self.assertFalse(qa.PUSH_SKIPS_ENABLED)
        self.assertEqual(qa.pre_push_skips(self.ref(), lambda r, l: ["README.md"]), {})

    def test_skip_rules(self):
        cases = [
            (["README.md", "docs/a.png", "inference/x.py"], {"gradle"}),
            (["README.md"], {"gradle", "api"}),
            (["docs/commands.md"], {"gradle", "api"}),
            (["inference/server.py"], {"gradle"}),
            (["inference/server.py", "README.md"], {"gradle"}),
            (["app/src/main/A.kt"], {"api"}),
            (["app/src/main/A.kt", "inference/x.py"], set()),
            (["tools/qa/run.py"], {"api"}),
        ]
        for paths, expected in cases:
            with self.subTest(paths=paths):
                self.assertEqual(set(self.plan(self.ref(), lambda r, l: paths)), expected)

    def test_run_everything_cases_never_crash(self):
        def boom(r, l):
            raise RuntimeError("git exploded")
        cases = {
            "new branch": (self.ref(remote=self.ZERO), lambda r, l: ["README.md"]),
            "force push or unknown rev": (self.ref(), lambda r, l: None),
            "deleted branch": (self.ref(local=self.ZERO), lambda r, l: ["README.md"]),
            "no stdin": ("", lambda r, l: ["README.md"]),
            "garbage": ("nonsense\n", lambda r, l: ["README.md"]),
            "git error": (self.ref(), boom),
        }
        for label, (text, changes) in cases.items():
            with self.subTest(label):
                self.assertEqual(self.plan(text, changes), {})

    def test_deleted_ref_ignored_next_to_live_ref(self):
        text = self.ref(local=self.ZERO) + self.ref()
        self.assertEqual(set(self.plan(text, lambda r, l: ["README.md"])), {"gradle", "api"})

    def test_new_branch_among_refs_runs_everything(self):
        self.assertEqual(self.plan(self.ref() + self.ref(remote=self.ZERO), lambda r, l: ["README.md"]), {})

    def test_git_push_changes_unknown_or_non_ancestor_is_none(self):
        failing = subprocess.CompletedProcess([], 128, b"", b"")
        with patch.object(qa.subprocess, "run", return_value=failing):
            self.assertIsNone(qa.git_push_changes(self.A, self.B))
        with patch.object(qa.subprocess, "run", side_effect=OSError):
            self.assertIsNone(qa.git_push_changes(self.A, self.B))

    def test_commands_drop_skipped_steps(self):
        out = Path("output")
        self.assertEqual(len(qa.commands("pre-push", out, {"gradle": "r"})), 1)
        self.assertIn("inference/tests", qa.commands("pre-push", out, {"gradle": "r"})[0])
        self.assertEqual(len(qa.commands("pre-push", out, {"api": "r"})), 1)
        self.assertIn(":app:verifyRoborazziDebug", qa.commands("pre-push", out, {"api": "r"})[0])
        self.assertEqual(qa.commands("pre-push", out, {"gradle": "r", "api": "r"}), [])

    def test_main_reports_skips_in_console_and_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(qa, "ROOT", root), patch.object(qa, "PUSH_SKIPS_ENABLED", True), patch.object(sys, "argv", ["run.py", "pre-push"]), \
                    patch.object(qa, "missing_packages", return_value=[]), patch.object(qa, "read_push_stdin", return_value=self.ref()), \
                    patch.object(qa, "git_push_changes", return_value=["README.md"]), patch.object(qa.subprocess, "check_output", side_effect=["revision\n", b""]), \
                    patch.object(qa.subprocess, "Popen") as popen, patch("sys.stdout", new_callable=io.StringIO) as out:
                self.assertEqual(qa.main(), 0)
            popen.assert_not_called()
            self.assertIn("SKIP pre-push Gradle/Roborazzi", out.getvalue())
            self.assertIn("SKIP pre-push Python API tests", out.getvalue())
            summary = json.loads(next(root.rglob("summary.json")).read_text())
            self.assertEqual([r["status"] for r in summary["records"]], ["skip", "skip"])
            self.assertTrue(any("SKIP pre-push Gradle" in n for n in summary["limitations"]))


class ScreenshotHintTests(unittest.TestCase):
    def run_main(self, name, fail):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            command = [sys.executable, "-c", f"raise SystemExit({1 if fail else 0})", ":app:verifyRoborazziDebug"]
            with patch.object(qa, "ROOT", root), patch.object(sys, "argv", ["run.py", name]), patch.object(qa, "missing_packages", return_value=[]), \
                    patch.object(qa.subprocess, "check_output", side_effect=["revision\n", b""]), patch.object(qa, "commands", return_value=[command]), \
                    patch("sys.stdout", new_callable=io.StringIO) as out:
                code = qa.main()
            summary = json.loads(next(root.rglob("summary.json")).read_text())
            return code, out.getvalue(), summary, next(root.rglob("1.log")).read_text()

    def test_hint_printed_and_recorded_on_verify_failure(self):
        code, out, summary, log = self.run_main("check", True)
        self.assertEqual(code, 1)
        self.assertIn(qa.SCREENSHOT_HINT, out)
        self.assertIn("bun run screenshots:record", out)
        self.assertIn(qa.SCREENSHOT_HINT, log)
        self.assertEqual(summary["records"][0]["hint"], qa.SCREENSHOT_HINT)

    def test_no_hint_when_verify_passes(self):
        code, out, summary, log = self.run_main("pre-push", False)
        self.assertEqual(code, 0)
        self.assertNotIn("screenshots:record", out)
        self.assertNotIn("hint", summary["records"][0])


if __name__ == "__main__":
    unittest.main()
