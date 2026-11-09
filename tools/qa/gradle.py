"""Resolve the platform wrapper; preserve every Gradle argument and exit status."""
import os
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
if not os.environ.get("JAVA_HOME") and Path("C:/Program Files/Android/Android Studio/jbr").is_dir():
    os.environ["JAVA_HOME"] = "C:/Program Files/Android/Android Studio/jbr"
sys.exit(subprocess.call([str(root / ("gradlew.bat" if os.name == "nt" else "gradlew")), *sys.argv[1:]], cwd=root))
