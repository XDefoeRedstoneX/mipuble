#!/usr/bin/env python3
"""Generate the canonical series-name catalog the EPUB importer matches against.

NOT shipped in the app. Run on a dev machine (or CI) to refresh the bundled
catalog; the app only ever reads the resulting .txt from assets.

Source: the AniList GraphQL API (https://anilist.co) — free, no API key,
clean English/romaji titles for manga and light novels, sortable by
popularity. We take the most popular `--pages` x 50 series, prefer the English
title when present, dedupe case-insensitively, and write one name per line.

The bundled list is only a *seed*: the app grows its own overlay as the user
confirms/adds names, so it doesn't need to be exhaustive.

Usage:
    python3 tools/generate_catalog.py [--pages 50] [--out app/src/main/assets/catalog/series.txt]
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

API_URL = "https://graphql.anilist.co"

# AniList has no NOVEL *type*; light novels are type MANGA with format NOVEL.
# Sorting all of type MANGA by popularity buries light novels behind manga, so
# we run a dedicated NOVEL pass and merge it in to guarantee LN coverage. The
# English title is preferred (it's the official localized name, e.g. "Spice &
# Wolf"); romaji is the fallback only when no English title exists.
def build_query(novel_only: bool) -> str:
    fmt = ", format: NOVEL" if novel_only else ""
    return f"""
    query ($page: Int, $perPage: Int) {{
      Page(page: $page, perPage: $perPage) {{
        pageInfo {{ hasNextPage }}
        media(type: MANGA, sort: POPULARITY_DESC{fmt}) {{
          title {{ english romaji }}
        }}
      }}
    }}
    """


def fetch_page(page: int, per_page: int, query: str) -> dict:
    body = json.dumps({"query": query, "variables": {"page": page, "perPage": per_page}}).encode()
    req = urllib.request.Request(
        API_URL,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            # Some egress proxies reject the default urllib UA with a 403.
            "User-Agent": "Mozilla/5.0 (mipuble-catalog-generator)",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def collect(pages: int, per_page: int, novel_only: bool, seen: dict[str, str]) -> None:
    """Fetches popular series into [seen] (lowercase key -> first-seen name)."""
    query = build_query(novel_only)
    label = "novels" if novel_only else "manga"
    for page in range(1, pages + 1):
        for attempt in range(5):
            try:
                payload = fetch_page(page, per_page, query)
                break
            except urllib.error.HTTPError as e:
                # 429 = rate limited; back off and retry.
                wait = int(e.headers.get("Retry-After", 2 ** attempt))
                print(f"  {label} page {page}: HTTP {e.code}, waiting {wait}s", file=sys.stderr)
                time.sleep(wait)
            except urllib.error.URLError as e:
                print(f"  {label} page {page}: {e}, retrying", file=sys.stderr)
                time.sleep(2 ** attempt)
        else:
            print(f"  {label} page {page}: giving up after retries", file=sys.stderr)
            continue

        data = payload.get("data", {}).get("Page", {})
        for media in data.get("media", []):
            title = media.get("title") or {}
            name = (title.get("english") or title.get("romaji") or "").strip()
            if not name:
                continue
            seen.setdefault(name.lower(), name)

        print(f"  {label} page {page}: {len(seen)} unique so far", file=sys.stderr)
        if not data.get("pageInfo", {}).get("hasNextPage", False):
            break
        time.sleep(0.8)  # stay well under AniList's ~90 req/min limit


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pages", type=int, default=40, help="manga pages of 50 (default 40 = 2000)")
    parser.add_argument("--novel-pages", type=int, default=20, help="light-novel pages of 50 (default 20 = 1000)")
    parser.add_argument("--per-page", type=int, default=50, help="AniList caps this at 50")
    parser.add_argument(
        "--out",
        default=os.path.join("app", "src", "main", "assets", "catalog", "series.txt"),
    )
    args = parser.parse_args()

    per_page = min(args.per_page, 50)
    seen: dict[str, str] = {}
    print(f"Fetching up to {args.pages * per_page} manga from AniList…", file=sys.stderr)
    collect(args.pages, per_page, novel_only=False, seen=seen)
    print(f"Fetching up to {args.novel_pages * per_page} light novels from AniList…", file=sys.stderr)
    collect(args.novel_pages, per_page, novel_only=True, seen=seen)
    names = sorted(seen.values(), key=str.casefold)
    if not names:
        print("No names fetched — aborting so an empty catalog isn't committed.", file=sys.stderr)
        return 1

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    header = (
        "# Canonical series names for mipuble's importer (manga + light novels).\n"
        "# Generated by tools/generate_catalog.py from AniList; do not hand-edit —\n"
        "# add names in-app instead (they go to the writable user overlay).\n"
        "# One series per line; blank lines and lines starting with # are ignored.\n"
    )
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(header)
        for name in names:
            f.write(name + "\n")

    print(f"Wrote {len(names)} series to {args.out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
