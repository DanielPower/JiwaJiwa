#!/usr/bin/env python3
"""Pin the newest JMdict, KANJIDIC2 and JmdictFurigana releases in sources.json.

The EDRDG licence asks for the bundled JMdict and KANJIDIC data to be updated
regularly, so .github/workflows/update-dictionary.yml runs this monthly and
opens a pull request with the result. Set GITHUB_TOKEN to avoid API rate limits.

    python3 tools/dictionary/update_sources.py   # rewrite sources.json, print what changed
"""

import hashlib
import io
import json
import os
import sys
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCES = HERE / "sources.json"

# sources.json key -> (GitHub repo, asset name prefix, asset name suffix)
RELEASES = {
    "jmdict": ("scriptin/jmdict-simplified", "jmdict-eng-", ".json.zip"),
    "kanjidic": ("scriptin/jmdict-simplified", "kanjidic2-en-", ".json.zip"),
    "furigana": ("Doublevil/JmdictFurigana", "JmdictFurigana", ".json"),
}


def get(url: str, api: bool = False) -> bytes:
    headers = {"Accept": "application/vnd.github+json"} if api else {}
    token = os.environ.get("GITHUB_TOKEN")
    if api and token:
        headers["Authorization"] = f"Bearer {token}"
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers)) as response:
        return response.read()


def latest_asset(repo: str, prefix: str, suffix: str) -> str:
    release = json.loads(get(f"https://api.github.com/repos/{repo}/releases/latest", api=True))
    names = [a for a in release["assets"] if a["name"].startswith(prefix) and a["name"].endswith(suffix)]
    # jmdict-eng-3.6.2+….json.zip, not jmdict-eng-common-3.6.2+….json.zip
    names = [a for a in names if a["name"][len(prefix)].isdigit()] if suffix == ".json.zip" else names
    if len(names) != 1:
        raise SystemExit(f"expected one {prefix}*{suffix} asset in {repo} {release['tag_name']}, found {len(names)}")
    return names[0]["browser_download_url"]


def main() -> int:
    spec = json.loads(SOURCES.read_text(encoding="utf-8"))
    changed = []
    for key, (repo, prefix, suffix) in RELEASES.items():
        url = latest_asset(repo, prefix, suffix)
        if spec[key]["url"] == url:
            continue
        data = get(url)
        spec[key]["url"] = url
        spec[key]["sha256"] = hashlib.sha256(data).hexdigest()
        if "zip_member" in spec[key]:
            members = [n for n in zipfile.ZipFile(io.BytesIO(data)).namelist() if n.endswith(".json")]
            if len(members) != 1:
                raise SystemExit(f"expected one .json file in {url}, found {members}")
            spec[key]["zip_member"] = members[0]
        changed.append(f"{key}: {url}")
    SOURCES.write_text(json.dumps(spec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("\n".join(changed) or "already up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main())
