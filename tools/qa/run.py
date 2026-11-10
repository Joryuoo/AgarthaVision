"""Cross-platform commands and actual execution evidence (Python 3.11+)."""
import datetime as dt
import html
import importlib.util
import json
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PYTHON = sys.executable
GRADLE = [str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))]
if not os.environ.get("JAVA_HOME") and Path("C:/Program Files/Android/Android Studio/jbr").is_dir():
    os.environ["JAVA_HOME"] = "C:/Program Files/Android/Android Studio/jbr"

# Explicitly classified local integration coverage, not deployed Supabase verification.
INTEGRATION = ["com.agarthavision.data.local.dao.*", "com.agarthavision.data.repository.*",
               "com.agarthavision.domain.usecase.sync.*", "com.agarthavision.domain.sync.*"]


# Pre-push skips are OFF until the CI check is a required status check on
# development/staging/main (ClickUp 14zcqntvucd). Until then the local hook is the only gate.
PUSH_SKIPS_ENABLED = False

PREFLIGHT_RUNS = {"pre-push", "api", "check", "evidence", "verify"}
INSTALL_COMMAND = "python -m pip install -r inference/requirements-test.txt"
IMPORT_NAMES = {"pyjwt": "jwt", "pillow": "PIL", "pyyaml": "yaml", "python-multipart": "multipart", "opencv-python": "cv2",
                "scikit-learn": "sklearn"}
SCREENSHOT_HINT = ("Screenshot verification failed. If the UI change is intentional or a new screenshot test has no "
                   "reference image, run: bun run screenshots:record, review the changed images, and commit them.")
ZERO_SHA = re.compile(r"0+")


def gradle(*args):
    return GRADLE + list(args)


def requirement_import_names(text):
    """(requirement, import name) pairs; tolerates comments, extras, version pins, markers and options."""
    result = []
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line or line.startswith("-"):
            continue
        match = re.match(r"[A-Za-z0-9][A-Za-z0-9._-]*", line)
        if not match:
            continue
        requirement = match.group(0)
        key = re.sub(r"[-_.]+", "-", requirement).lower()
        result.append((requirement, IMPORT_NAMES.get(key, re.sub(r"[-.]+", "_", requirement))))
    return result


def importable(module):
    try:
        return importlib.util.find_spec(module) is not None
    except (ImportError, ValueError, AttributeError):
        return False


def missing_packages(requirements_text=None):
    if requirements_text is None:
        requirements_text = (ROOT / "inference/requirements-test.txt").read_text(encoding="utf-8")
    return [req for req, module in requirement_import_names(requirements_text) if not importable(module)]


def preflight_message(missing):
    return ("QA preflight failed: missing Python packages in the interpreter running the hooks "
            f"({PYTHON}): {', '.join(missing)}\n"
            f"  Install them with: {INSTALL_COMMAND}\n"
            "  Hooks use the `python` found on PATH, so the right virtual environment must be active where git runs "
            "(IDE git clients may not inherit your shell's venv).\n"
            "No checks were run.")


def parse_push_refs(text):
    """Lines of `<local ref> <local sha> <remote ref> <remote sha>` from git's pre-push stdin."""
    refs = []
    for line in (text or "").splitlines():
        parts = line.split()
        if len(parts) == 4:
            refs.append(tuple(parts))
    return refs


def push_changed_paths(refs, changes):
    """Set of changed paths, or None when everything must run (unknown/new/force-push/no refs).

    `changes(remote_sha, local_sha)` returns a list of paths, or None if the range is unknown or not a fast-forward.
    """
    live = [ref for ref in refs if not ZERO_SHA.fullmatch(ref[1])]  # deleted refs are ignored
    if not live:
        return None
    paths = set()
    for _, local, _, remote in live:
        if ZERO_SHA.fullmatch(remote):  # new branch
            return None
        found = changes(remote, local)
        if found is None:
            return None
        paths.update(found)
    return paths


def plan_push_skips(paths):
    """{'gradle': reason, 'api': reason} for the steps that can be skipped; empty means run everything."""
    if not paths:
        return {}
    skips = {}
    if all(p.endswith(".md") or p.startswith(("docs/", "inference/")) for p in paths):
        skips["gradle"] = "only Markdown, docs/ and inference/ files changed"
    if not any(p.startswith("inference/") for p in paths):
        skips["api"] = "no files under inference/ changed"
    return skips


def git_push_changes(remote, local):
    try:
        ancestor = subprocess.run(["git", "merge-base", "--is-ancestor", remote, local], cwd=ROOT, capture_output=True)
        if ancestor.returncode != 0:
            return None
        out = subprocess.run(["git", "diff", "--name-only", "-z", f"{remote}..{local}"], cwd=ROOT, capture_output=True)
        if out.returncode != 0:
            return None
        return [p for p in out.stdout.decode("utf-8", errors="replace").split("\0") if p]
    except OSError:
        return None


def pre_push_skips(stdin_text, changes=None):
    """Never raises: any problem means no skips."""
    if not PUSH_SKIPS_ENABLED:
        return {}
    try:
        paths = push_changed_paths(parse_push_refs(stdin_text), changes or git_push_changes)
        return plan_push_skips(paths) if paths is not None else {}
    except Exception:  # noqa: BLE001 - a skip decision must never break the hook
        return {}


def read_push_stdin():
    try:
        if sys.stdin is None or sys.stdin.isatty():
            return ""
        return sys.stdin.read()
    except (OSError, ValueError):
        return ""


STAGED_TEST_FILE = re.compile(r"app/src/test/(java|kotlin)/.+\.(kt|java)")
CLASS_DECLARATION = re.compile(r"^(?:(?:public|internal|open|final)\s+)*class\s+([A-Za-z_]\w*)", re.MULTILINE)
TEST_MARKER = re.compile(r"@(?:\w+\.)*(?:Test|ParameterizedTest|RunWith)\b")


def concrete_test_classes(text):
    """Fully-qualified concrete top-level test classes, or None when they cannot be determined safely."""
    package = re.search(r"^\s*package\s+([A-Za-z_][\w.]*)", text, re.MULTILINE)
    if not package or not TEST_MARKER.search(text):
        return None
    names = CLASS_DECLARATION.findall(text)
    if len(names) > 1:  # helper classes would make a filter match nothing
        names = [n for n in names if re.search(r"(Tests?|Spec|IT)$", n)]
    return [f"{package.group(1)}.{n}" for n in names] or None


def read_staged(path):
    try:
        return subprocess.check_output(["git", "show", f":{path}"], cwd=ROOT, stderr=subprocess.DEVNULL).decode("utf-8", errors="replace")
    except (subprocess.CalledProcessError, OSError):
        try:
            return (ROOT / path).read_text(encoding="utf-8")
        except OSError:
            return None


def select_staged_test_classes(test_files, reader=None):
    """Class filters for every staged test file, or None (use the broader fallback) if any file is uncertain."""
    reader = reader or read_staged
    selected = []
    for path in test_files:
        text = reader(path)
        classes = concrete_test_classes(text) if text is not None else None
        if not classes:
            return None
        selected.extend(c for c in classes if c not in selected)
    return selected or None


def warn_missing_staged_unit_tests(files, changed_files):
    """Advisory per subsystem, not a coverage/dependency analysis or a commit blocker."""
    android_source = lambda path: bool(re.fullmatch(r"app/src/(main|debug|release)/(java|kotlin)/.+\.(kt|java)", path))
    android_test = lambda path: bool(re.fullmatch(r"app/src/test(?:Debug|Release)?/(java|kotlin)/.+Tests?\.(kt|java)", path))
    inference_source = lambda path: path.startswith("inference/") and path.endswith(".py") and not path.startswith(("inference/tests/", "inference/export/"))
    inference_test = lambda path: path.startswith("inference/tests/") and path.endswith(".py") and (Path(path).name.startswith("test_") or Path(path).name.endswith("_test.py"))
    for label, source, test in (("Android", android_source, android_test), ("inference", inference_source, inference_test)):
        production = [path for path in files if source(path)]
        if production and not any(test(path) for path in changed_files):
            print(f"WARNING: Staged {label} production code changes have no added/modified staged {label} unit test files. Commit checks will continue.")
            print("  Review whether test updates are needed; this file-based reminder does not measure coverage.")


def commands(name, output, skips=None):
    api = [PYTHON, "-m", "pytest", "inference/tests", "-q", f"--junitxml={output / 'api.xml'}"]
    full = gradle(":app:verifyRoborazziDebug")
    options = {
        "unit": [gradle(":app:testDebugUnitTest")],
        "screenshots": [full],
        "record": [gradle(":app:recordRoborazziDebug")],
        "api": [api],
        "integration": [gradle(":app:testDebugUnitTest", *sum((["--tests", p] for p in INTEGRATION), []))],
        "device": [gradle(":app:connectedDebugAndroidTest")],
        "smoke": [[PYTHON, "tools/qa/smoke.py", "--output", str(output / "smoke.json"), *sys.argv[2:]]],
        "pre-push": [full, api],
        "check": [[PYTHON, "-m", "unittest", "discover", "-s", "tools/qa", "-p", "test_*.py", "-v"], gradle(":app:compileDebugKotlin", ":app:ktlintCheck", ":app:detekt", "assembleDebug"), full, api],
    }
    if name == "pre-commit":
        # A changed production symbol can affect tests anywhere. No guessed dependency mapping.
        files = subprocess.check_output(["git", "diff", "--cached", "--name-only", "-z"], cwd=ROOT).decode().split("\0")
        changed_files = subprocess.check_output(["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"], cwd=ROOT).decode().split("\0")
        removed_or_renamed = subprocess.check_output(["git", "diff", "--cached", "--name-only", "--diff-filter=DR", "--find-renames", "-z"], cwd=ROOT).decode().split("\0")
        warn_missing_staged_unit_tests(files, changed_files)
        tests = [f for f in changed_files if STAGED_TEST_FILE.fullmatch(f)]
        # Deleted classes cannot be selected; a renamed file may still declare its old class.
        # Use the domain fallback for either status rather than guessing runnable class names.
        safe = tests and not any(removed_or_renamed) and all(not f or f in tests or f.endswith(".md") for f in files)
        classes = select_staged_test_classes(tests) if safe else None  # parsed from content, never from file names
        selection = sum((["--tests", c] for c in classes), []) if classes else ["--tests", "com.agarthavision.domain.*"]
        print("Focused selection: staged test classes" if classes else "Broader fast fallback: all domain JVM tests; full regression remains pre-push")
        return [gradle(":app:compileDebugKotlin", ":app:ktlintCheck", ":app:detekt"), gradle(":app:testDebugUnitTest", *selection)]
    if name == "evidence":
        return options["check"] + [[PYTHON, "tools/qa/smoke.py", "--local", "--output", str(output / "smoke.json")]]
    if name == "pre-push" and skips:
        return ([] if "gradle" in skips else [full]) + ([] if "api" in skips else [api])
    return options[name]


def redact(text):
    text = re.sub(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", "[REDACTED_JWT]", text)
    for key, value in os.environ.items():
        if len(value) >= 8 and any(s in key.upper() for s in ("TOKEN", "SECRET", "PASSWORD", "API_KEY")):
            for representation in (value, html.escape(value), html.escape(value, quote=False), json.dumps(value)[1:-1]):
                text = text.replace(representation, "[REDACTED]")
    return text


TEXT_REPORTS = {".xml", ".html", ".htm", ".json", ".log", ".txt", ".csv", ".js", ".css", ".svg", ".md"}
IMAGE_REPORTS = {".png", ".jpg", ".jpeg", ".gif", ".webp"}


def copy_report(source, destination):
    """Publish only redacted text or screenshot images; never opaque Gradle output.bin logs."""
    suffix = source.suffix.lower()
    if suffix not in TEXT_REPORTS | IMAGE_REPORTS:
        return False
    if suffix in TEXT_REPORTS:
        # Decode before creating the destination so unreadable text cannot leave a raw copy.
        content = redact(source.read_text(encoding="utf-8-sig"))
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")
    else:
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
    return True


def main():
    name = sys.argv[1]
    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-" + name
    output = ROOT / "build/qa" / run_id
    output.mkdir(parents=True)
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT))
    summary = {"run_id": run_id, "date": dt.datetime.now(dt.timezone.utc).isoformat(), "commit": revision,
               "working_tree_dirty": dirty, "environment": {"os": platform.platform(), "python": platform.python_version()},
               "records": [], "artifacts": [], "excluded_artifacts": [], "limitations": [
                   "Local fake-model API and Room/fake-HTTP integration do not verify deployed model accuracy, Supabase RLS or migrations.",
                   "System capture/offline workflow requires separate synthetic end-to-end evidence; instrumentation alone does not establish it.",
                   "SKIP usability: participant observations and follow-up not supplied.",
                   "SKIP acceptance: requirement owner/evaluator sign-off not supplied.",
                   "SKIP real-model/device performance: controlled workload and server/device telemetry required."],
               "defects": []}
    if name != "device":
        summary["limitations"].append("Device instrumentation was not executed by this command; any collected device reports retain their earlier/cached provenance.")
    failed = 0
    if name in PREFLIGHT_RUNS:
        missing = missing_packages()
        if missing:
            message = preflight_message(missing)
            print(message)
            (output / "preflight.log").write_text(message + "\n", encoding="utf-8")
            summary["records"].append({"case_id": "QA-PREFLIGHT", "requirement_id": "QA-repeatable-checks", "command": [PYTHON, "-c", "import <requirements>"],
                                       "expected": "all packages in inference/requirements-test.txt importable",
                                       "actual": "missing packages: " + ", ".join(missing) + f"; install with: {INSTALL_COMMAND}",
                                       "status": "fail", "seconds": 0, "artifact": "preflight.log"})
            summary["limitations"].append("Preflight failed: no command was executed.")
            summary["exit_code"] = 1
            (output / "summary.json").write_text(redact(json.dumps(summary, indent=2)), encoding="utf-8")
            print(f"QA evidence: {output}")
            return 1
    skips = pre_push_skips(read_push_stdin()) if name == "pre-push" else {}
    for step, reason in skips.items():
        label = "Gradle/Roborazzi" if step == "gradle" else "Python API tests"
        print(f"SKIP pre-push {label}: {reason}")
        summary["records"].append({"case_id": f"QA-PRE-PUSH-SKIP-{step.upper()}", "requirement_id": "QA-repeatable-checks", "command": [],
                                   "expected": "step runs or is skipped with reason", "actual": f"skipped: {reason}", "status": "skip", "seconds": 0, "artifact": None})
        summary["limitations"].append(f"SKIP pre-push {label}: {reason}.")
    started = time.time()
    for index, command in enumerate(commands(name, output, skips) if skips else commands(name, output)):
        log = output / f"{index + 1}.log"
        begin = time.monotonic()
        try:
            with log.open("w", encoding="utf-8") as stream:
                process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
                try:
                    for line in process.stdout:
                        line = redact(line)
                        stream.write(line)
                        stream.flush()
                        print(line, end="", flush=True)
                    code = process.wait()
                except KeyboardInterrupt:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                    code = 130
                    stream.write("Interrupted; child process stopped.\n")
                finally:
                    process.stdout.close()
        except OSError as error:
            code = 127
            log.write_text(redact(str(error)), encoding="utf-8")
        failed = failed or code
        summary["records"].append({"case_id": f"QA-{name.upper()}-{index + 1}", "requirement_id": "QA-repeatable-checks",
                                   "command": command, "expected": "exit 0", "actual": f"exit {code}",
                                   "status": "pass" if code == 0 else "fail", "seconds": round(time.monotonic() - begin, 3), "artifact": log.name})
        if code not in (0, 130) and ":app:verifyRoborazziDebug" in command:
            print(SCREENSHOT_HINT)
            with log.open("a", encoding="utf-8") as stream:
                stream.write(SCREENSHOT_HINT + "\n")
            summary["records"][-1]["hint"] = SCREENSHOT_HINT
            summary["limitations"].append(SCREENSHOT_HINT)
        if code == 130:
            summary["limitations"].append("Remaining command steps were not executed after interruption.")
            break
    # Keep existing reports too, with provenance: Gradle may reuse UP-TO-DATE outputs.
    # A failed command never turns an old report into a passing result.
    for source in (ROOT / "app/build/reports/tests", ROOT / "app/build/test-results", ROOT / "app/build/outputs/roborazzi", ROOT / "app/build/reports/roborazzi",
                   ROOT / "app/build/outputs/androidTest-results", ROOT / "app/build/reports/androidTests"):
        if source.exists():
            for path in source.rglob("*"):
                if path.is_file():
                    dest = output / "artifacts" / path.relative_to(ROOT)
                    try:
                        copied = copy_report(path, dest)
                    except (UnicodeError, OSError):
                        copied = False
                        failed = failed or 1
                    if not copied:
                        summary["excluded_artifacts"].append({"path": str(path.relative_to(ROOT)), "reason": "unsupported binary or unreadable report; raw content not published"})
                        continue
                    summary["artifacts"].append({"path": str(dest.relative_to(output)), "updated_during_run": path.stat().st_mtime >= started - 1})
    # pytest writes api.xml directly into the run directory. Sanitize it and all other
    # generated text before parsing case names or publishing any evidence.
    for path in output.rglob("*"):
        if path.is_file() and path.suffix.lower() in TEXT_REPORTS:
            try:
                path.write_text(redact(path.read_text(encoding="utf-8-sig")), encoding="utf-8")
            except (UnicodeError, OSError):
                path.unlink(missing_ok=True)
                failed = failed or 1
                summary["excluded_artifacts"].append({"path": str(path.relative_to(output)), "reason": "sanitization failed; report removed"})
    smoke_path = output / "smoke.json"
    try:
        smoke = json.loads(smoke_path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        smoke = {}
    if smoke.get("mode") == "designated-staging":
        summary["smoke"] = {"mode": smoke["mode"], "status": "pass" if smoke.get("status") == "pass" and not failed else "fail", "deployed_revision": smoke.get("revision"), "artifact": "smoke.json"}
        if smoke.get("status") != "pass" or failed:
            summary["limitations"].append("Staging smoke did not pass; smoke.json and command logs distinguish missing prerequisites/setup failures from failed assertions.")
        else:
            summary["limitations"].append("Staging API smoke passed for the recorded deployed revision; it does not establish Supabase RLS/migration or full system acceptance evidence.")
    elif name == "smoke" and "--local" not in sys.argv[2:]:
        summary["limitations"].append("Staging smoke has no usable structured outcome; inspect command failure/logs before claiming any deployed scenario executed.")
    else:
        summary["limitations"].append("SKIP deployed backend/staging: this invocation did not run designated staging smoke; a deployment revision, URL and test-user token are required.")
    summary["junit"] = []
    for path in output.rglob("*.xml"):
        try:
            tree = ET.parse(path)
            for case in tree.iter("testcase"):
                status = "fail" if case.find("failure") is not None or case.find("error") is not None else "skip" if case.find("skipped") is not None else "pass"
                artifact = str(path.relative_to(output))
                provenance = next((a["updated_during_run"] for a in summary["artifacts"] if a["path"] == artifact), True)
                device_report = "androidTest-results" in path.parts or "androidTests" in path.parts
                summary["junit"].append({"case_id": case.get("classname", "") + "." + case.get("name", ""), "requirement_id": "QA-device-instrumentation" if device_report else "QA-regression", "expected": "assertions pass", "actual": status, "status": status, "artifact": artifact, "updated_during_run": provenance})
        except ET.ParseError:
            pass
    if name == "device" and not any(case["requirement_id"] == "QA-device-instrumentation" for case in summary["junit"]):
        summary["limitations"].append("No individual instrumentation results were available; inspect the device command exit code and log before claiming any device case passed.")
    summary["exit_code"] = failed
    (output / "summary.json").write_text(redact(json.dumps(summary, indent=2)), encoding="utf-8")
    print(f"QA evidence: {output}")
    return failed


if __name__ == "__main__":
    sys.exit(main())
