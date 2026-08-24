#!/usr/bin/env python3
"""
Reference / cross-check implementation of the Java AnomalyDetector.

This is a line-for-line Python port of
  backend/.../detector/AnomalyDetector.java
using the SAME default parameters as application.yml. It exists so the detector's
behaviour can be verified without a JVM, and so reviewers can read the whole
algorithm in one screen. The Java code is the source of truth; this mirror is
kept identical to it.

Usage:
    python verify_detector.py ../data/log-data.csv
    python verify_detector.py ../data/generated-logs.csv
"""
import csv
import math
import sys
from bisect import bisect_left, bisect_right
from datetime import datetime, timezone

# --- Defaults, identical to app.detector.* in application.yml ---------------
THRESHOLD = 0.70
W_RARITY, W_SOURCE, W_SEVERITY, W_BURST = 0.85, 0.85, 0.30, 0.45
RARITY_COMMON_PROB, RARITY_RARE_PROB = 0.05, 0.001
VOL_SIGMA_LOW, VOL_SIGMA_HIGH = 3.0, 10.0
BURST_WINDOW_S, BURST_MIN_EVENTS = 600, 3

TS_FORMATS = ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S")


def parse_epoch(s):
    s = (s or "").strip()
    for fmt in TS_FORMATS:
        try:
            return int(datetime.strptime(s, fmt).replace(tzinfo=timezone.utc).timestamp())
        except ValueError:
            continue
    return None


def clamp01(v):
    return max(0.0, min(1.0, v))


def log2(v):
    return math.log(v) / math.log(2)


def rnd(v):
    return round(v * 1000.0) / 1000.0


class Detector:
    def fit(self, rows):
        self.total = len(rows)
        self.loc, self.ua, self.ip = {}, {}, {}
        ip_times = {}
        for r in rows:
            self.loc[r["location"]] = self.loc.get(r["location"], 0) + 1
            self.ua[r["ua"]] = self.ua.get(r["ua"], 0) + 1
            self.ip[r["ip"]] = self.ip.get(r["ip"], 0) + 1
            if r["epoch"] is not None:
                ip_times.setdefault(r["ip"], []).append(r["epoch"])
        self.ip_times = {k: sorted(v) for k, v in ip_times.items()}

        counts = list(self.ip.values())
        mean = sum(counts) / len(counts) if counts else 0.0
        var = sum((c - mean) ** 2 for c in counts) / len(counts) if counts else 0.0
        eff_std = max(math.sqrt(var), 0.5)
        self.vol_low = mean + VOL_SIGMA_LOW * eff_std
        self.vol_high = mean + VOL_SIGMA_HIGH * eff_std
        if self.vol_high <= self.vol_low:
            self.vol_high = self.vol_low + 1
        return self

    def _prob(self, counts, key):
        return 1.0 if self.total == 0 else counts.get(key, 0) / self.total

    def _rarity_surprisal(self, p):
        if p <= 0:
            p = 1e-9
        if p >= RARITY_COMMON_PROB:
            return 0.0
        s = -log2(p)
        s_lo = -log2(RARITY_COMMON_PROB)
        s_hi = -log2(RARITY_RARE_PROB)
        return 1.0 if s_hi <= s_lo else clamp01((s - s_lo) / (s_hi - s_lo))

    def score(self, r):
        p_loc = self._prob(self.loc, r["location"])
        p_ua = self._prob(self.ua, r["ua"])
        rarity = self._rarity_surprisal(min(p_loc, p_ua))

        count = self.ip.get(r["ip"], 0)
        source = clamp01((count - self.vol_low) / (self.vol_high - self.vol_low)) if count > self.vol_low else 0.0

        code = r["status"]
        severity = 1.0 if (code is not None and code >= 500) else (0.4 if (code is not None and code >= 400) else 0.0)

        burst = 0.0
        times = self.ip_times.get(r["ip"])
        if r["epoch"] is not None and times is not None and len(times) >= BURST_MIN_EVENTS:
            in_window = bisect_right(times, r["epoch"] + BURST_WINDOW_S) - bisect_left(times, r["epoch"] - BURST_WINDOW_S)
            if in_window >= BURST_MIN_EVENTS:
                burst = clamp01((in_window - (BURST_MIN_EVENTS - 1)) / 5.0)

        total = rarity * W_RARITY + source * W_SOURCE + severity * W_SEVERITY + burst * W_BURST
        s = min(1.0, total)
        return rnd(s), s >= THRESHOLD, {"rarity": rnd(rarity), "source": rnd(source),
                                       "severity": rnd(severity), "burst": rnd(burst)}


def load(path):
    rows = []
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            rows.append({
                "location": (r.get("Location") or "").strip(),
                "ua": (r.get("User_Agent") or "").strip(),
                "ip": (r.get("IP_Address") or "").strip(),
                "status": int(r["Status_Code"]) if (r.get("Status_Code") or "").strip().isdigit() else None,
                "epoch": parse_epoch(r.get("Timestamp")),
            })
    return rows


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "../data/log-data.csv"
    rows = load(path)
    det = Detector().fit(rows)
    flagged = []
    by_signal = {"rarity": 0, "source": 0, "severity": 0, "burst": 0}
    for r in rows:
        s, anom, parts = det.score(r)
        if anom:
            flagged.append((r, s, parts))
            for k, v in parts.items():
                if v > 0:
                    by_signal[k] += 1

    n = len(rows)
    print(f"File: {path}")
    print(f"Rows: {n}")
    print(f"Baseline: vol_low={det.vol_low:.2f}  vol_high={det.vol_high:.2f}  distinct_ips={len(det.ip)}")
    print(f"Flagged: {len(flagged)}  ({100.0*len(flagged)/n:.2f}%)")
    print(f"Signals firing among flagged: {by_signal}")
    locs = {}
    ips = {}
    for r, s, _ in flagged:
        locs[r["location"]] = locs.get(r["location"], 0) + 1
        ips[r["ip"]] = ips.get(r["ip"], 0) + 1
    print(f"Flagged by location: {dict(sorted(locs.items(), key=lambda x:-x[1]))}")
    top_ips = dict(sorted(ips.items(), key=lambda x: -x[1])[:5])
    print(f"Top flagged IPs: {top_ips}")


if __name__ == "__main__":
    main()
