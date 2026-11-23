"""Build the Windows ZIP using only the Python standard library."""
import argparse
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parent
APP_FOLDER = "Hangzhou-iPhone-Location-Windows"
FILES = (
    "Start-Hangzhou-iPhone-Location.cmd",
    "README-Windows.md",
    "LICENSE",
    "THIRD_PARTY_NOTICES.md",
    "device.py",
    "server.py",
    "index.html",
    "maplibre-gl.js",
    "maplibre-gl.css",
    "maplibre-license.txt",
    "requirements.lock",
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="Path to the resulting ZIP file")
    args = parser.parse_args()
    with ZipFile(args.output, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for name in FILES:
            archive.write(ROOT / name, f"{APP_FOLDER}/{name}")


if __name__ == "__main__":
    main()
