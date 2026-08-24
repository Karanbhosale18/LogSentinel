#!/usr/bin/env python3
"""
Generate a synthetic web-server access log for the Smart Log Analyzer.

The output matches the schema the app ingests:
    Timestamp,IP_Address,Request_Type,Status_Code,User_Agent,Session_ID,Location

Unlike a uniformly-random file, this generator produces a *mostly-normal*
baseline (most traffic is HTTP 200 from a pool of ordinary IPs and common
countries) and then injects two clearly anomalous patterns so you can see the
detector light up:

  1. RARE LOCATION  - a small number of requests from a country that almost
     never appears in normal traffic  -> triggers the "rarity" signal.
  2. HIGH-VOLUME IP - a single IP that fires far more requests than any normal
     client, clustered in time      -> triggers the "source volume" + "burst"
     signals (a credential-stuffing / scraping style pattern).

Usage:
    python generate_dataset.py                       # 10,000 rows -> ./data/generated-logs.csv
    python generate_dataset.py --rows 5000 --seed 7  # custom size / seed
    python generate_dataset.py --out /tmp/logs.csv   # custom path

The result is intentionally reproducible for a given --seed.
"""
import argparse
import csv
import random
from datetime import datetime, timedelta

# --- Normal-traffic vocabularies -------------------------------------------

COMMON_LOCATIONS = [
    ("United States", 26), ("India", 18), ("Germany", 12), ("United Kingdom", 10),
    ("Brazil", 8), ("France", 7), ("Canada", 6), ("Japan", 5),
    ("Australia", 4), ("Netherlands", 4),
]
REQUEST_TYPES = [("GET", 70), ("POST", 20), ("PUT", 5), ("DELETE", 5)]
USER_AGENTS = [
    ("Chrome", 40), ("Safari", 20), ("Firefox", 15), ("Edge", 12),
    ("Mobile-Safari", 8), ("Bot", 5),
]
# Mostly-healthy status mix: ~90% success, a realistic tail of errors.
NORMAL_STATUS = [
    (200, 78), (301, 4), (302, 4), (404, 6), (403, 3), (401, 2), (500, 2), (503, 1),
]


def weighted(pairs):
    values = [v for v, _ in pairs]
    weights = [w for _, w in pairs]
    return values, weights


def random_ip(rng):
    return f"{rng.randint(11, 223)}.{rng.randint(0, 255)}.{rng.randint(0, 255)}.{rng.randint(1, 254)}"


def main():
    ap = argparse.ArgumentParser(description="Generate a synthetic access-log CSV.")
    ap.add_argument("--rows", type=int, default=10000, help="total data rows (default 10000)")
    ap.add_argument("--seed", type=int, default=42, help="RNG seed for reproducibility")
    ap.add_argument("--out", default="../data/generated-logs.csv", help="output CSV path")
    ap.add_argument("--rare-location", default="North Korea", help="injected rare-location label")
    ap.add_argument("--rare-count", type=int, default=10, help="rows for the rare location")
    ap.add_argument("--flood-count", type=int, default=49, help="rows for the high-volume IP")
    args = ap.parse_args()

    rng = random.Random(args.seed)

    loc_v, loc_w = weighted(COMMON_LOCATIONS)
    req_v, req_w = weighted(REQUEST_TYPES)
    ua_v, ua_w = weighted(USER_AGENTS)
    st_v, st_w = weighted(NORMAL_STATUS)

    # A modest pool of "normal" client IPs, each seen only a handful of times.
    # Near-unique client IPs (like real web traffic): most appear only 1-3 times,
    # so a flooding IP stands out sharply against the per-source baseline.
    ip_pool = [random_ip(rng) for _ in range(max(2000, int(args.rows * 0.9)))]

    start = datetime(2023, 1, 1, 0, 0, 0)
    rows = []
    ts = start
    for _ in range(args.rows):
        rows.append({
            "Timestamp": ts.strftime("%Y-%m-%d %H:%M:%S"),
            "IP_Address": rng.choice(ip_pool),
            "Request_Type": rng.choices(req_v, req_w)[0],
            "Status_Code": rng.choices(st_v, st_w)[0],
            "User_Agent": rng.choices(ua_v, ua_w)[0],
            "Session_ID": rng.randint(1000, 9999),
            "Location": rng.choices(loc_v, loc_w)[0],
        })
        ts += timedelta(minutes=1)

    # --- Anomaly 1: rare location, scattered through the timeline ----------
    for idx in rng.sample(range(len(rows)), args.rare_count):
        rows[idx]["Location"] = args.rare_location

    # --- Anomaly 2: one IP flooding requests in a tight time cluster -------
    flood_ip = "15.6.62.53"
    flood_positions = rng.sample(range(len(rows)), args.flood_count)
    for i, idx in enumerate(flood_positions):
        rows[idx]["IP_Address"] = flood_ip
        # cluster ~half of them into a short burst to also trip the burst signal
        if i % 2 == 0:
            rows[idx]["Timestamp"] = (start + timedelta(days=3, seconds=i * 20)).strftime("%Y-%m-%d %H:%M:%S")
            rows[idx]["Status_Code"] = 401  # looks like repeated failed auth

    with open(args.out, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=[
            "Timestamp", "IP_Address", "Request_Type", "Status_Code",
            "User_Agent", "Session_ID", "Location",
        ])
        w.writeheader()
        w.writerows(rows)

    print(f"Wrote {len(rows)} rows to {args.out}")
    print(f"  Injected rare location '{args.rare_location}': {args.rare_count} rows")
    print(f"  Injected high-volume IP '{flood_ip}': {args.flood_count} rows")


if __name__ == "__main__":
    main()
