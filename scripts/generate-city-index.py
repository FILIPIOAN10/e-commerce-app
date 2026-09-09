#!/usr/bin/env python3
"""Regenerate the backend's city index from the country-state-city npm package.

The checkout address form used to resolve cities in the browser from that
package, whose city.json is 7.9 MB — 92% of an 8.7 MB lazy chunk that every
customer downloaded on opening the address form. Cities now come from
GET /api/public/geo/cities instead, and this produces the file that endpoint
reads.

The reshape does three things to the source data:
  * groups records by "<countryCode>-<stateCode>", so those codes are stored
    once per state instead of once per city;
  * keeps only the name, dropping the latitude and longitude nothing uses;
  * deduplicates and sorts each state's list.

7.7 MB in, ~2.0 MB out, and it lands in a shape the service can look up
directly with no indexing pass at startup.

Run after upgrading country-state-city:

    npm --prefix ecom-frontend install
    python scripts/generate-city-index.py

then commit the regenerated JSON. Run from the repository root.
"""

import collections
import json
import os
import sys

SOURCE = os.path.join(
    "ecom-frontend", "node_modules", "country-state-city", "lib", "assets", "city.json"
)
TARGET = os.path.join(
    "ecom-backend", "src", "main", "resources", "geo", "cities-by-state.json"
)


def main() -> int:
    if not os.path.exists(SOURCE):
        print(f"error: {SOURCE} not found — run npm install in ecom-frontend first", file=sys.stderr)
        return 1

    with open(SOURCE, encoding="utf-8") as handle:
        rows = json.load(handle)

    # Each row is [name, countryCode, stateCode, latitude, longitude].
    by_state = collections.defaultdict(set)
    for name, country_code, state_code, *_ in rows:
        by_state[f"{country_code}-{state_code}"].add(name)

    grouped = {key: sorted(names) for key, names in by_state.items()}

    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w", encoding="utf-8") as handle:
        # ensure_ascii=False keeps "Aghireșu" one character rather than six;
        # separators drops the whitespace, which is ~10% of the file.
        json.dump(grouped, handle, ensure_ascii=False, sort_keys=True, separators=(",", ":"))

    source_mb = os.path.getsize(SOURCE) / 1024 / 1024
    target_mb = os.path.getsize(TARGET) / 1024 / 1024
    print(f"{SOURCE}  {source_mb:.2f} MB")
    print(f"{TARGET}  {target_mb:.2f} MB")
    print(f"{len(grouped)} states, {sum(len(v) for v in grouped.values())} cities")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
