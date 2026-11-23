"""Embed the authored app and its static assets into one macOS command file."""
import argparse
import base64
import gzip
import io
from pathlib import Path
import tarfile
import textwrap

WORK = Path(__file__).resolve().parent
ASSETS = (
    "server.py",
    "device.py",
    "index.html",
    "maplibre-gl.js",
    "maplibre-gl.css",
    "maplibre-license.txt",
    "requirements.lock",
    "LICENSE",
    "THIRD_PARTY_NOTICES.md",
)

parser = argparse.ArgumentParser()
parser.add_argument("output", type=Path)
args = parser.parse_args()
bundle = io.BytesIO()
with (
    gzip.GzipFile(fileobj=bundle, mode="wb", mtime=0) as compressed,
    tarfile.open(mode="w", fileobj=compressed) as archive,
):
    for name in ASSETS:
        content = (WORK / name).read_bytes()
        member = tarfile.TarInfo(name)
        member.size = len(content)
        member.mode = 0o600
        archive.addfile(member, io.BytesIO(content))

encoded = base64.b64encode(bundle.getvalue()).decode("ascii")
script = (WORK / "launcher.zsh").read_text().replace(
    "__ASSET_BUNDLE__", "\n".join(textwrap.wrap(encoded, 100))
)
args.output.write_text(script, encoding="utf-8")
args.output.chmod(0o755)
