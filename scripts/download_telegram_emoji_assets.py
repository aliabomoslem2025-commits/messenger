#!/usr/bin/env python3
"""Download the Lottie files referenced by the project's animated-emoji manifest."""
from __future__ import annotations

import concurrent.futures
import gzip
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path, PurePosixPath
from urllib.parse import quote

ASSET_BASE_URL = "https://saeedtahmtan.github.io/telemoji/"
MAX_WORKERS = 12
RETRIES = 4
USER_AGENT = "Messenger-Animated-Emoji-Asset-Bundler/1.0"


def read_asset_paths(manifest_path: Path) -> list[str]:
    with manifest_path.open("r", encoding="utf-8") as stream:
        rows = json.load(stream)
    paths: set[str] = set()
    for row in rows:
        if not isinstance(row, dict):
            continue
        value = row.get("file_lottie")
        if not isinstance(value, str) or not value.startswith("lottie/"):
            continue
        parsed = PurePosixPath(value)
        if parsed.is_absolute() or ".." in parsed.parts:
            continue
        paths.add(parsed.as_posix())
    if not paths:
        raise RuntimeError(f"No Lottie assets found in {manifest_path}")
    return sorted(paths)


def download_one(relative_path: str, output_dir: Path) -> tuple[str, int, str | None]:
    destination = output_dir / "telegram_emoji" / Path(relative_path)
    if destination.is_file() and destination.stat().st_size > 100:
        return relative_path, destination.stat().st_size, None
    destination.parent.mkdir(parents=True, exist_ok=True)
    url = ASSET_BASE_URL + quote(relative_path, safe="/")
    last_error: str | None = None
    for attempt in range(RETRIES):
        try:
            request = urllib.request.Request(
                url,
                headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
            )
            with urllib.request.urlopen(request, timeout=45) as response:
                payload = response.read()
            # raw.githubusercontent.com may return Content-Encoding: gzip. urllib
            # does not automatically decompress the body, so decode it before JSON.
            if payload.startswith(b"\x1f\x8b"):
                payload = gzip.decompress(payload)
            composition = json.loads(payload.decode("utf-8"))
            if not isinstance(composition, dict) or not isinstance(composition.get("layers"), list):
                raise ValueError("response is not a Lottie composition")
            temporary = destination.with_name(destination.name + ".tmp")
            temporary.write_bytes(payload)
            temporary.replace(destination)
            return relative_path, len(payload), None
        except (urllib.error.URLError, TimeoutError, OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
            last_error = str(error)
            if attempt + 1 < RETRIES:
                time.sleep(min(2 ** attempt, 8))
    return relative_path, 0, last_error or "download failed"


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: download_telegram_emoji_assets.py <manifest.json> <output-dir>", file=sys.stderr)
        return 2
    manifest_path = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    paths = read_asset_paths(manifest_path)

    # Skip downloads quickly when building offline. The app retains a runtime fallback.
    try:
        request = urllib.request.Request(
            ASSET_BASE_URL + quote(paths[0], safe="/"),
            headers={"User-Agent": USER_AGENT},
        )
        with urllib.request.urlopen(request, timeout=5) as response:
            response.read(64)
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        print(f"WARNING: animated-emoji mirror is unreachable ({error}); runtime fallback remains enabled.")
        return 0

    errors: list[tuple[str, str]] = []
    bundled = 0
    total_bytes = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = [executor.submit(download_one, path, output_dir) for path in paths]
        for future in concurrent.futures.as_completed(futures):
            relative_path, size, failure = future.result()
            total_bytes += size
            if failure:
                errors.append((relative_path, failure))
            else:
                bundled += 1

    if errors:
        preview = "\n".join(f"  {path}: {error}" for path, error in errors[:20])
        raise RuntimeError(
            f"Failed to bundle {len(errors)} of {len(paths)} Lottie assets.\n{preview}"
        )
    print(
        f"Bundled {bundled} Lottie assets ({total_bytes / (1024 * 1024):.1f} MiB) "
        f"into {output_dir}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
