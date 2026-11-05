"""Cross-platform commands and actual execution evidence (Python 3.11+)."""
import datetime as dt
import html
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


def gradle(*args):
    return GRADLE + list(args)


def commands(name, output):
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
        "check": [[PYTHON, "-m", "unittest", "discover", "-s", "tools/qa", "-p", "test_run.py", "-v"], gradle(":app:compileDebugKotlin", ":app:ktlintCheck", ":app:detekt", "assembleDebug"), full, api],
    }
    if name == "pre-commit":
        # A changed production symbol can affect tests anywhere. No guessed dependency mapping.
        files = subprocess.check_output(["git", "diff", "--cached", "--name-only", "-z"], cwd=ROOT).decode().split("\0")
        tests = [f for f in files if f.endswith("Test.kt") and f.startswith("app/src/test/java/")]
        safe = tests and all(not f or f in tests or f.endswith(".md") for f in files)
        selection = sum((["--tests", f.removeprefix("app/src/test/java/").removesuffix(".kt").replace("/", ".")] for f in tests), []) if safe else ["--tests", "com.agarthavision.domain.*"]
        print("Focused selection: staged test classes" if safe else "Broader fast fallback: all domain JVM tests; full regression remains pre-push")
        return [gradle(":app:compileDebugKotlin", ":app:ktlintCheck", ":app:detekt"), gradle(":app:testDebugUnitTest", *selection)]
    if name == "evidence":
        return options["check"] + [[PYTHON, "tools/qa/smoke.py", "--local", "--output", str(output / "smoke.json")]]
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
                   "SKIP device/system capture/offline: dedicated device and synthetic workflow required; not run by aggregate checks.",
                   "SKIP deployed backend/staging: deployment revision, URL and test-user token required.",
                   "SKIP usability: participant observations and follow-up not supplied.",
                   "SKIP acceptance: requirement owner/evaluator sign-off not supplied.",
                   "SKIP real-model/device performance: controlled workload and server/device telemetry required."],
               "defects": []}
    failed = 0
    started = time.time()
    for index, command in enumerate(commands(name, output)):
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
        if code == 130:
            summary["limitations"].append("Remaining command steps were not executed after interruption.")
            break
    # Keep existing reports too, with provenance: Gradle may reuse UP-TO-DATE outputs.
    # A failed command never turns an old report into a passing result.
    for source in (ROOT / "app/build/reports/tests", ROOT / "app/build/test-results", ROOT / "app/build/outputs/roborazzi", ROOT / "app/build/reports/roborazzi"):
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
    summary["junit"] = []
    for path in output.rglob("*.xml"):
        try:
            tree = ET.parse(path)
            for case in tree.iter("testcase"):
                status = "fail" if case.find("failure") is not None or case.find("error") is not None else "skip" if case.find("skipped") is not None else "pass"
                artifact = str(path.relative_to(output))
                provenance = next((a["updated_during_run"] for a in summary["artifacts"] if a["path"] == artifact), True)
                summary["junit"].append({"case_id": case.get("classname", "") + "." + case.get("name", ""), "requirement_id": "QA-regression", "expected": "assertions pass", "actual": status, "status": status, "artifact": artifact, "updated_during_run": provenance})
        except ET.ParseError:
            pass
    summary["exit_code"] = failed
    (output / "summary.json").write_text(redact(json.dumps(summary, indent=2)), encoding="utf-8")
    print(f"QA evidence: {output}")
    return failed


if __name__ == "__main__":
    sys.exit(main())
