#!/usr/bin/env bash
# ios-emoji-fts.sh — I2.1 one-time asset prep for the FA re-arch.
# 1) Augments LimeStudio/app/src/main/res/raw/emoji.db with a prebuilt FTS5
#    index (schema identical to LimeDB.createEmojiTables' fts5 branch) so the
#    keyboard can ATTACH it read-only/immutable with search working.
# 2) Strips the EMPTY legacy emoji tables from the default lime.db so the
#    attached schema is never shadowed by main on fresh installs.
# Idempotent. Run from repo root (worktree).
set -euo pipefail
EMOJI="LimeStudio/app/src/main/res/raw/emoji.db"
LIME="LimeStudio/app/src/main/res/raw/lime.db"

echo "== emoji.db: prebuild FTS5 =="
sqlite3 "$EMOJI" <<'SQL'
DROP TABLE IF EXISTS emoji_fts;
CREATE VIRTUAL TABLE emoji_fts USING fts5(
    name_en, name_tw, tags_en, tags_tw,
    content='emoji_data', content_rowid='rowid',
    tokenize='unicode61 remove_diacritics 1'
);
INSERT INTO emoji_fts(emoji_fts) VALUES('rebuild');
CREATE INDEX IF NOT EXISTS idx_emoji_group ON emoji_data(group_name, sort_order);
VACUUM;
SQL
sqlite3 "$EMOJI" "SELECT 'emoji_data rows: '||COUNT(*) FROM emoji_data; SELECT 'fts hits for grinning: '||COUNT(*) FROM emoji_fts WHERE emoji_fts MATCH 'grinning';"

echo "== default lime.db: drop empty legacy emoji tables =="
sqlite3 "$LIME" <<'SQL'
DROP TABLE IF EXISTS emoji_fts;
DROP TABLE IF EXISTS emoji_fts_docsize;
DROP TABLE IF EXISTS emoji_fts_segdir;
DROP TABLE IF EXISTS emoji_fts_segments;
DROP TABLE IF EXISTS emoji_fts_stat;
DROP TABLE IF EXISTS emoji_user;
DROP TABLE IF EXISTS emoji_data;
VACUUM;
SQL
sqlite3 "$LIME" "SELECT 'remaining emoji tables: '||COUNT(*) FROM sqlite_master WHERE name LIKE 'emoji%';"
echo "DONE"
