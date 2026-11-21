"""Verification of the shared commit subject rule, the CLI modes and the hook wiring."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("commit_format.py")
ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("commit_format", SCRIPT)
cf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cf)


def cli(*args, cwd=None):
    return subprocess.run([sys.executable, str(SCRIPT), *args], capture_output=True, text=True, cwd=cwd)


class CheckTests(unittest.TestCase):
    def test_valid_subjects(self):
        for subject in ("[feat][86d4a6mpt][Ledon]: Add the settings screen", "[fix][gh-42][Your_name]: Fix crash"):
            with self.subTest(subject=subject):
                self.assertTrue(cf.check(subject))

    def test_every_type_accepted(self):
        for name in cf.TYPES:
            with self.subTest(type=name):
                self.assertTrue(cf.check(f"[{name}][abc123][Ledon]: Title"))

    def test_invalid_subjects(self):
        for subject in ("Add the settings screen", "[feat][86d4a6mpt][Ledon] Missing colon", "[feat][86d4a6mpt][Ledon]: ",
                        "[bogus][86d4a6mpt][Ledon]: Title", "[feat][][Ledon]: Title", "[feat][a b][Ledon]: Title", "", " [feat][a][b]: x"):
            with self.subTest(subject=subject):
                self.assertFalse(cf.check(subject))

    def test_exemptions(self):
        for subject in ("Merge pull request #1 from x/y", "Revert \"[feat][a][b]: x\"", "fixup! anything", "squash! anything"):
            with self.subTest(subject=subject):
                self.assertTrue(cf.check(subject))

    def test_help_text_lists_types_example_and_subject(self):
        text = cf.help_text("bad subject")
        self.assertIn("bad subject", text)
        self.assertIn(cf.EXAMPLE, text)
        for name in cf.TYPES:
            self.assertIn(name, text)


class CliTests(unittest.TestCase):
    def test_file_mode(self):
        with tempfile.TemporaryDirectory() as tmp:
            good, bad = Path(tmp, "good"), Path(tmp, "bad")
            good.write_text("[feat][abc][Ledon]: Ok\n\nbody", encoding="utf-8")
            bad.write_text("nope\n", encoding="utf-8")
            self.assertEqual(cli("--file", str(good)).returncode, 0)
            result = cli("--file", str(bad))
            self.assertEqual(result.returncode, 1)
            self.assertIn("nope", result.stdout)

    def test_range_mode_lists_offenders(self):
        with tempfile.TemporaryDirectory() as tmp:
            def git(*args):
                return subprocess.run(["git", "-c", "user.name=T", "-c", "user.email=t@example.com", *args],
                                      cwd=tmp, capture_output=True, text=True, check=True).stdout.strip()
            git("init", "-q")
            git("commit", "-q", "--allow-empty", "-m", "base")
            base = git("rev-parse", "HEAD")
            git("commit", "-q", "--allow-empty", "-m", "[feat][abc][Ledon]: Good one")
            git("commit", "-q", "--allow-empty", "-m", "bad subject one")
            bad_sha = git("rev-parse", "HEAD")[:8]
            git("commit", "-q", "--allow-empty", "-m", "fixup! whatever")
            result = cli("--range", f"{base}..HEAD", cwd=tmp)
            self.assertEqual(result.returncode, 1)
            self.assertIn(bad_sha, result.stdout)
            self.assertIn("bad subject one", result.stdout)
            self.assertNotIn("Good one", result.stdout)
            self.assertNotIn("fixup!", result.stdout)
            self.assertEqual(cli("--range", f"{base}..{base}", cwd=tmp).returncode, 0)


class HookTests(unittest.TestCase):
    def test_hook_delegates_and_has_no_hardcoded_types(self):
        hook = (ROOT / ".husky" / "commit-msg").read_text(encoding="utf-8")
        self.assertIn("commit_format.py", hook)
        self.assertNotIn("--no-verify", hook)
        for name in cf.TYPES:
            self.assertNotIn(f"{name}|", hook)
            self.assertNotIn(f"|{name}", hook)


if __name__ == "__main__":
    unittest.main()
