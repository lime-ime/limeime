#!/usr/bin/env python3
# build_dictionary_db.py
#
# Build the bundled English `dictionary.db` payload for LimeIME's Android scored
# dictionary, AND patch the bundled `lime.db` seed(s) to ship the empty scored
# `dictionary(word, basescore, score)` table (replacing the legacy fts3(word) table).
#
# Design + rationale: docs/ENG_AUTO_COMPLETION.md ("Scored Dictionary (Android)",
# "Seed strategy", "Payload: dictionary.db"). The dictionary is Android-only and
# self-versioned via an `im(code='dictionary', title='version')` row written at
# runtime import time — NOT via lime.db user_version (which stays 104).
#
# Sources (--source):
#   ngrams    (default, ships the payload) Google Books Ngrams English 1-grams
#             (CC BY 3.0): streams the 24 sharded .gz files (~10 GB total, decompressed
#             on the fly so the full set is never all on disk), sums modern match counts
#             per [a-z']-word, takes top --limit, and LOG-SCALES counts to basescore
#             (0..BASESCORE_MAX). CC BY 3.0 attribution is recorded in LICENSE.md.
#   wordlist  Bootstrap from the committed word list (scripts/data/english_dictionary_words.txt),
#             which carries only frequency ORDER (no counts), so basescore is a Zipf
#             approximation, log-scaled to the same range. No download; offline fallback.
#
# Usage (real payload, downloads ~10 GB of Ngrams and streams it):
#   python3 scripts/build_dictionary_db.py \
#       --source ngrams \
#       --out    LimeStudio/app/src/main/res/raw/dictionary.db \
#       --patch-seeds LimeStudio/app/src/main/res/raw/lime.db \
#       --version 1.0 --limit 100000
#
# Bootstrap (no download; uses the committed word list, Zipf-approximated basescore):
#   python3 scripts/build_dictionary_db.py --source wordlist \
#       --out LimeStudio/app/src/main/res/raw/dictionary.db \
#       --patch-seeds LimeStudio/app/src/main/res/raw/lime.db
#
# The two patched seeds are written to be byte-identical (LIME_DB_103/104 contract).

import argparse
import os
import sqlite3
import sys


BASESCORE_MAX = 10000  # basescore range 0..BASESCORE_MAX (broader than LatinIME's 0..255)


def log(msg):
    print(f"[build_dictionary_db] {msg}", file=sys.stderr)


def read_wordlist(wordlist_path):
    """Return [(word, basescore)] from a committed frequency-ordered word list.

    The list (scripts/data/english_dictionary_words.txt) is one word per line, ordered
    most-frequent first (line 1 = 'the'). It carries only frequency ORDER, no counts, so
    basescore is a Zipf approximation (count ~ 1/rank) log-scaled to 0..BASESCORE_MAX —
    same scale + curve shape as the ngrams path, but approximated. The ngrams path uses
    real corpus counts and is the source of the shipped payload.

    This is re-runnable: the source is the committed text file, NOT the seed being patched.
    """
    if not os.path.exists(wordlist_path):
        log(f"ERROR: word list not found: {wordlist_path}")
        sys.exit(1)

    words = []
    seen = set()
    with open(wordlist_path, encoding="utf-8") as f:
        for line in f:
            w = line.strip()
            if not w:
                continue
            key = w.lower()
            if key in seen:
                continue
            seen.add(key)
            words.append(w)

    # The wordlist has NO real counts, only frequency ORDER. Approximate a log-frequency
    # via Zipf's law (frequency ~ 1/rank), then log-scale to 0..BASESCORE_MAX so the
    # bootstrap payload uses the same scale + curve shape as the ngrams path. This is an
    # approximation; the ngrams path uses real corpus counts.
    import math
    n = len(words)
    if n == 0:
        return []
    # Zipf: count(rank) ~ 1/(rank+1); log-count = -log(rank+1). Normalize to 0..MAX.
    max_log = 0.0                      # -log(1) for rank 0 (most frequent)
    min_log = -math.log(n)            # -log(n) for the last word
    span = max_log - min_log
    out = []
    for i, w in enumerate(words):
        logc = -math.log(i + 1)
        base = round(BASESCORE_MAX * (logc - min_log) / span) if span > 0 else BASESCORE_MAX
        out.append((w, base))
    return out


NGRAMS_BASE = "http://storage.googleapis.com/books/ngrams/books/20200217/eng"
NGRAMS_FILES = 24  # English 1-grams are sharded 1-00000-of-00024 .. 1-00023-of-00024
NGRAMS_MIN_YEAR = 1950  # ignore archaic-only spellings; favor modern usage
import gzip
import io
import re
import urllib.request

# A valid completion word: lowercase ASCII letters, optional internal apostrophes.
_WORD_RE = re.compile(r"^[a-z]+(?:'[a-z]+)*$")


def _aggregate_ngram_stream(stream, counts):
    """Sum match_count (per year >= NGRAMS_MIN_YEAR) into counts[word_lower].

    Each line: WORD<TAB>YEAR,match_count,volume_count<TAB>... . The WORD may carry a
    POS suffix like 'book_NOUN' — strip it. Case-folded; only [a-z'] words kept.
    """
    for raw in stream:
        line = raw.decode("utf-8", "replace")
        tab = line.find("\t")
        if tab <= 0:
            continue
        token = line[:tab]
        # strip POS tag (book_NOUN -> book; _NUM_, _START_ etc. become empty -> dropped)
        us = token.find("_")
        if us >= 0:
            token = token[:us]
        word = token.lower()
        if not _WORD_RE.match(word):
            continue
        total = 0
        for tri in line[tab + 1:].rstrip("\n").split("\t"):
            parts = tri.split(",")
            if len(parts) != 3:
                continue
            try:
                year = int(parts[0])
                if year < NGRAMS_MIN_YEAR:
                    continue
                total += int(parts[1])
            except ValueError:
                continue
        if total:
            counts[word] = counts.get(word, 0) + total


def read_ngrams(args):
    """Download + aggregate Google Books Ngrams English 1-grams (CC BY 3.0).

    Streams each of the 24 sharded .gz files (decompressing on the fly so the full
    ~10 GB is never all on disk), sums modern (year >= NGRAMS_MIN_YEAR) match counts
    per [a-z']-word, takes the top --limit by frequency, and maps the raw counts to a
    LOG-SCALED basescore in 0..BASESCORE_MAX. LICENSE.md must carry the CC BY 3.0
    attribution.

    basescore is log-scaled frequency (the LatinIME approach), NOT a rank: it preserves
    relative magnitude (e.g. 'the' >> 'of' >> 'salt'), unlike a dense rank which flattens
    the ~10^7:1 frequency range into evenly-spaced integers. Normalized so the most
    frequent kept word -> BASESCORE_MAX and the least frequent kept word -> 0.
    """
    counts = {}
    for i in range(NGRAMS_FILES):
        url = f"{NGRAMS_BASE}/1-{i:05d}-of-{NGRAMS_FILES:05d}.gz"
        log(f"streaming shard {i + 1}/{NGRAMS_FILES}: {url}")
        try:
            with urllib.request.urlopen(url, timeout=120) as resp:
                with gzip.GzipFile(fileobj=io.BufferedReader(resp)) as gz:
                    _aggregate_ngram_stream(gz, counts)
        except Exception as e:
            log(f"ERROR streaming shard {i}: {e}")
            sys.exit(3)
        log(f"  cumulative distinct words: {len(counts)}")

    # Top N by frequency.
    top = sorted(counts.items(), key=lambda kv: kv[1], reverse=True)[: args.limit]
    n = len(top)
    log(f"selected top {n} words (of {len(counts)} distinct)")
    if not top:
        return []

    import math
    max_log = math.log(top[0][1])           # log(count) of the most frequent word
    min_log = math.log(top[-1][1])          # log(count) of the least frequent kept word
    span = max_log - min_log
    log(f"count range: max={top[0][1]} ({top[0][0]}), min={top[-1][1]} ({top[-1][0]})")

    out = []
    for word, cnt in top:
        if span <= 0:
            base = BASESCORE_MAX
        else:
            base = round(BASESCORE_MAX * (math.log(cnt) - min_log) / span)
        out.append((word, base))
    return out


def write_payload(out_path, entries, version):
    """Write dictionary.db: one `dictionary_data(word, basescore, version)` table."""
    if os.path.exists(out_path):
        os.remove(out_path)
    con = sqlite3.connect(out_path)
    try:
        con.execute("""
            CREATE TABLE dictionary_data (
                word      TEXT PRIMARY KEY,
                basescore INTEGER NOT NULL,
                version   TEXT NOT NULL
            )
        """)
        con.executemany(
            "INSERT OR REPLACE INTO dictionary_data(word, basescore, version) "
            "VALUES (?, ?, ?)",
            [(w, b, version) for (w, b) in entries],
        )
        con.commit()
    finally:
        con.close()
    log(f"wrote {out_path} ({len(entries)} words, version={version})")


def patch_seed(seed_path, reference_seed):
    """Rewrite a lime.db seed to ship the empty scored dictionary table.

    Drops the legacy fts3 `dictionary` + its shadow tables, creates the empty
    scored table + indexes, leaves user_version unchanged, and does NOT insert an
    `im` dictionary version row (its absence triggers the first-open import).
    """
    if not os.path.exists(seed_path):
        log(f"ERROR: seed to patch not found: {seed_path}")
        sys.exit(1)
    con = sqlite3.connect(seed_path)
    try:
        before = con.execute("PRAGMA user_version").fetchone()[0]
        # Drop legacy fts3 dictionary (its shadow tables go with it when fts3 loads).
        try:
            con.execute("DROP TABLE IF EXISTS dictionary")
        except sqlite3.OperationalError:
            pass
        # Defensive: remove any orphan dictionary shadow tables left behind.
        for shadow in ("dictionary_content", "dictionary_segments",
                       "dictionary_segdir", "dictionary_docsize",
                       "dictionary_stat"):
            con.execute(f"DROP TABLE IF EXISTS {shadow}")
        # Create the empty scored table + indexes (must match LimeDB.ensureDictionarySchema).
        con.execute("""
            CREATE TABLE dictionary (
                word      TEXT    PRIMARY KEY,
                basescore INTEGER NOT NULL DEFAULT 0,
                score     INTEGER NOT NULL DEFAULT 0
            )
        """)
        con.execute("CREATE INDEX IF NOT EXISTS dictionary_word_idx ON dictionary(word)")
        con.execute("CREATE INDEX IF NOT EXISTS dictionary_score_basescore_idx "
                    "ON dictionary(score DESC, basescore DESC)")
        # Do NOT insert im(code='dictionary', title='version') — absence triggers import.
        # Leave user_version as-is (stays 104).
        con.execute(f"PRAGMA user_version = {before}")
        con.commit()
        con.execute("VACUUM")
    finally:
        con.close()
    log(f"patched seed {seed_path} (user_version={before}, empty scored dictionary)")


def main():
    ap = argparse.ArgumentParser(description="Build LimeIME dictionary.db payload and patch seeds")
    ap.add_argument("--source", choices=["wordlist", "ngrams"], default="wordlist")
    ap.add_argument("--wordlist", default="scripts/data/english_dictionary_words.txt",
                    help="committed frequency-ordered word list (wordlist source). "
                         "Re-runnable: this is NOT the seed being patched.")
    ap.add_argument("--out", required=True, help="output dictionary.db payload path")
    ap.add_argument("--patch-seeds", nargs="*", default=[],
                    help="lime.db seed file(s) to rewrite to the empty scored table")
    ap.add_argument("--version", default="1.0", help="payload version string")
    ap.add_argument("--limit", type=int, default=100000, help="(ngrams) top-N words")
    args = ap.parse_args()

    if args.source == "wordlist":
        entries = read_wordlist(args.wordlist)
    else:
        entries = read_ngrams(args)

    write_payload(args.out, entries, args.version)

    for seed in args.patch_seeds:
        patch_seed(seed, args.wordlist)

    if len(args.patch_seeds) >= 2:
        # Verify byte-identical (LIME_DB_103/104 contract).
        import hashlib
        digests = {}
        for seed in args.patch_seeds:
            with open(seed, "rb") as f:
                digests[seed] = hashlib.sha256(f.read()).hexdigest()
        uniq = set(digests.values())
        if len(uniq) != 1:
            log("WARNING: patched seeds are NOT byte-identical:")
            for s, d in digests.items():
                log(f"  {d}  {s}")
        else:
            log("patched seeds are byte-identical: " + next(iter(uniq)))


if __name__ == "__main__":
    main()
