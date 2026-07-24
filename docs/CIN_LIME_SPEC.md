# CIN and LIME Text Import Format Specification

This document describes the `.cin` and `.lime` text formats accepted by the current LimeIME importers.

This file is the format contract only. Implementation tasks and test coverage are tracked in [CIN_LIME_IMPROVE_PLAN.md](CIN_LIME_IMPROVE_PLAN.md).

## 1. `.cin` Format

`.cin` is the traditional CIN input method format. Files ending in `.cin` are imported as CIN format, with mapping records read from `%chardef begin` through `%chardef end`.

### 1.1 Recommended Structure

```text
%version My IM Version
%cname My IM Display Name
%selkey 123456789
%endkey abcdefghijklmnopqrstuvwxyz
%limeendkey ;/
%spacestyle 0
%keyname begin
a A
b B
%keyname end
%chardef begin
a 測
b 試
%chardef end
```

### 1.2 Encoding

Use UTF-8 text. Importers accept a UTF-8 BOM on the first line. Source files edited in this repo should follow repo policy and be saved as UTF-8 with BOM when modified.

### 1.3 Metadata Lines

Supported CIN metadata:

```text
%version My IM Version
%cname My IM Display Name
%selkey 123456789
%endkey ...
%limeendkey ...
%spacestyle ...
```

Behavior:

- `%version` stores the IM version metadata.
- `%cname` stores the display name and is also used as version fallback when `%version` is absent.
- When `%cname` is absent, importers store the built-in full name for the target IM table as the display name. Unknown target tables fall back to the import filename.
- `%selkey`, `%endkey`, and `%spacestyle` store conventional CIN selection/end/space behavior metadata.
- `%limeendkey` stores LimeIME's runtime end-key commit triggers. Empty or absent Lime end-key metadata means no Lime runtime end-key commit triggers for the table.
- `%endkey` remains import/export compatibility metadata and does not by itself enable LimeIME's runtime end-key commit path.
- Metadata values may contain spaces after the metadata key.

### 1.3.1 Lime Runtime End-Key Behavior

`%limeendkey` is a LimeIME runtime extension. It is intentionally separate from conventional CIN `%endkey`.

When a pressed key is listed in `%limeendkey`, LimeIME uses the table's `imkeys` metadata to decide how to finish composition:

- If the key is also present in `imkeys`, LimeIME appends the key to the active composing buffer, resolves candidates for the full composing code, commits the highlighted candidate, and consumes the trigger key.
- If the key is not present in `imkeys`, LimeIME first commits the highlighted candidate for the active composing buffer, then processes the trigger key as a fresh composing key. If that fresh key resolves to table candidates, LimeIME commits the highlighted candidate for that key as well. If it has no table candidate, normal raw-key fallback applies.

This rule is generic. It must not depend on Chinese punctuation-specific handling.

### 1.4 `%keyname` Block

```text
%keyname begin
a ㄅ
b ㄆ
%keyname end
```

CIN key names are preserved as IM metadata:

- `imkeys`: concatenated lowercased key codes
- `imkeynames`: display names joined by `|`

### 1.5 `%chardef` Block

```text
%chardef begin
code word
%chardef end
```

Rules:

- Mapping records are imported only inside `%chardef begin/end`.
- Import stops reading CIN mappings at `%chardef end`.
- Lines beginning with `#` inside the block are skipped as comments.
- Records shorter than 3 characters are ignored.

### 1.6 Mapping Records

Common CIN record:

```text
code word
```

CIN records may also be tab-delimited and may include optional score fields:

```text
code<TAB>word<TAB>score<TAB>basescore
code word score basescore
```

Field behavior:

- `code`: required; trimmed; lowercased before insert unless it is metadata.
- `word`: required; trimmed.
- `score`: optional integer; defaults to `0`.
- `basescore`: optional integer. When the field is **present** — including an explicit `0` — it is authoritative and stored unchanged (`0` means "low base priority, not a frequent word"). Only an **absent** basescore field is filled from the Han base-frequency converter at import. See [§2.5.1 Base Score Rule](#251-base-score-rule).

For the phonetic table, `code3r` is derived by removing tone characters `[3467 ]` from `code`.

## 2. `.lime` Format

`.lime` is LimeIME's delimiter-separated text format for regular IM table export/import.

### 2.1 Recommended Portable Structure

```text
@version@|My IM Version
@cname@|My IM Display Name
@selkey@|123456789
@endkey@|abcdefghijklmnopqrstuvwxyz
@limeendkey@|abcdefghijklmnopqrstuvwxyz
@spacestyle@|0
%chardef begin
code|word|score|basescore
aa|測|0|123
ab|試|0|456
%chardef end
```

Importers accept `.lime` mapping lines with or without `%chardef begin/end`. Exporters include the block for readability and compatibility.

Lines beginning with `#` are skipped as comments and are ignored during delimiter detection.

### 2.2 Encoding

Exporters write UTF-8. Use UTF-8 text for imports. Importers accept a UTF-8 BOM on the first line.

### 2.3 Delimiter Detection

For portable files, prefer pipe-delimited records. Importers recognize these delimiters:

- comma: `,`
- tab: `\t`
- pipe: `|`
- space: ` `

The safest delimiter is `|` when code and word fields do not contain literal `|`.

### 2.4 Metadata Lines

LIME-style metadata uses the active delimiter. With pipe delimiter:

```text
@format@|lime-text-v2
@version@|My IM Version
@cname@|My IM Display Name
@selkey@|123456789
@endkey@|...
@limeendkey@|...
@spacestyle@|...
```

An import line is metadata when the parsed `code` field starts with `@`. Supported metadata keys:

- `@version@`
- `@cname@`
- `@selkey@`
- `@endkey@`
- `@limeendkey@`
- `@spacestyle@`
- `@imkeys@`
- `@imkeynames@`
- `@format@`

These lines are not inserted as mappings.

Metadata meaning:

- `@format@|lime-text-v2` enables escaped field parsing for the rest of the file.
- `@version@` stores the IM version metadata.
- `@cname@` stores the IM display name, equivalent to CIN `%cname`.
- `@selkey@`, `@endkey@`, and `@spacestyle@` store conventional IM selection/end/space behavior metadata.
- `@limeendkey@` stores LimeIME's runtime end-key commit triggers.
- `@imkeys@` and `@imkeynames@` store the same key mapping metadata as the `imkeys` and `imkeynames` rows in the `im` table.

`@endkey@` remains import/export compatibility metadata and does not by itself enable LimeIME's runtime end-key commit path. Empty or absent `@limeendkey@` metadata means no Lime runtime end-key commit triggers for the table.
When `@cname@` is absent, importers store the built-in full name for the target IM table as the display name. Unknown target tables fall back to the import filename.

When both `@version@` and `@cname@` are present, `@version@` remains the version value and `@cname@` is the display name value.

`@imkeynames@` often contains literal `|` separators inside the value. When exporting such values, use `@format@|lime-text-v2` and escape those literal pipes as `\|`.

Example conversion from a CIN `%keyname` block:

```text
%keyname begin
a 日
b 月
c 金
d 木
e 水
f 火
g 土
h 竹
i 戈
j 十
k 大
l 中
m 一
n 弓
o 人
p 心
q 手
r 口
s 尸
t 廿
u 山
v 女
w 田
x 難
y 卜
z 重
%keyname end
```

Portable `.lime` v2 metadata:

```text
@format@|lime-text-v2
@imkeys@|abcdefghijklmnopqrstuvwxyz
@imkeynames@|日\|月\|金\|木\|水\|火\|土\|竹\|戈\|十\|大\|中\|一\|弓\|人\|心\|手\|口\|尸\|廿\|山\|女\|田\|難\|卜\|重
```

Notes:

- `@imkeys@` stores the key sequence as one metadata value, so the keys are concatenated as `abcdefghijklmnopqrstuvwxyz`, not separated as `a|b|c|...`.
- `@imkeynames@` stores one metadata value whose internal display-name list uses `|`, so in `lime-text-v2` those literal pipes are escaped as `\|`.

`@limeendkey@` uses the same runtime behavior as CIN `%limeendkey`: keys also present in `@imkeys@` are appended before committing; keys absent from `@imkeys@` first finish the active composing buffer and are then processed as a fresh key.

### 2.4.1 Escaped v2 Fields

Files without `@format@|lime-text-v2` use legacy unescaped parsing. In v2, fields are split on the active delimiter while ignoring escaped delimiter characters, then escapes are decoded.

Supported v2 escapes:

```text
\\ = literal backslash
\| = literal pipe when `|` is the delimiter
\@ = literal at-sign
\% = literal percent
\t = tab
\n = newline
```

Exporters may keep the legacy v1 format when no field needs escaping. They should write `@format@|lime-text-v2` before records when a value contains the active delimiter, backslash, tab/newline, or when a mapping code would otherwise be mistaken for metadata.

### 2.5 Mapping Records

Canonical record:

```text
code|word|score|basescore
```

Minimum accepted record:

```text
code|word
```

Field behavior:

- `code`: required; trimmed; lowercased before insert.
- `word`: required; trimmed.
- `score`: optional integer; defaults to `0`.
- `basescore`: optional integer. When the field is **present** — including an explicit `0` — it is authoritative and stored unchanged (`0` means "low base priority, not a frequent word"). Only an **absent** basescore field is filled from the Han base-frequency converter at import. See [§2.5.1 Base Score Rule](#251-base-score-rule).

Regular mappings are inserted into:

```text
code, word, score, basescore
```

### 2.5.1 Base Score Rule

`basescore` uses a field-presence rule shared by `.cin` and `.lime` on both Android and iOS:

- When a record **carries a basescore field** — a 4-field `.lime` record `code|word|score|basescore`, or a `.cin` `code<TAB>word<TAB>score<TAB>basescore` record — that value is **authoritative and stored unchanged**, including an explicit `0`. A `basescore` of `0` is a legitimate value meaning "low base priority" (an entry that is not a frequently used word and should rank low unless promoted by user learning).
- When a record **omits the basescore field** — a `.cin` `code word` line, or a `.lime` minimum record `code|word` — the base score is **filled from the bundled Han base-frequency table** (`hanconvertv2.db` `TCSC.score`) at import: a known character resolves to its frequency, an unknown multi-character word defaults to `1`, and an unknown single character resolves to `0`.

The distinction is **field presence, not field value**: an explicit `0` is preserved, while a genuinely absent field is filled. This lets a traditional `.cin` (which never carries base scores) receive frequencies so runtime phrase composition works, while still allowing an author to pin an entry at low priority with an explicit `0`.

**Round-trip.** Because regular-table export always writes all four fields (`code|word|score|basescore`), a `.lime` export→re-import preserves `basescore` exactly (the field is always present on re-import) — the `.lime` text format is round-trip lossless for base scores. `.limedb` (the zipped database backup) likewise copies raw rows unchanged.

### 2.6 Space-Delimited `.lime`

Space-delimited records are supported, but fragile:

```text
code word score basescore
```

Runs of two to five spaces may be collapsed before parsing. Space-delimited records cannot safely contain spaces inside the code or word field.

## 3. Related Phrase Text Format

For the `related` table, pipe-delimited import uses:

```text
pword|cword|basescore|userscore
```

Legacy format is still accepted:

```text
pword+cword|basescore|userscore
```

The legacy importer splits the first field heuristically into parent and child words, so new exports should use the four-field format.

## 4. Export Format

### 4.1 Regular Table Export

Regular table export writes:

```text
@format@|lime-text-v2
@version@|...
@cname@|...
@selkey@|...
@endkey@|...
@limeendkey@|...
@spacestyle@|...
@imkeys@|...
@imkeynames@|...
%chardef begin
code|word|score|basescore
%chardef end
```

`@format@|lime-text-v2` is written only when at least one exported field needs escaping.

### 4.2 Related Table Export

Related table export writes:

```text
@format@|lime-text-v2
%chardef begin
pword|cword|basescore|userscore
%chardef end
```

`@format@|lime-text-v2` is written only when at least one exported field needs escaping.

## 5. Legacy Format Limitations

Legacy text files without `@format@|lime-text-v2` have no escaping or quoting layer.

Important consequences:

- In pipe-delimited `.lime`, literal `|` cannot safely appear in `code`, `word`, `pword`, or `cword`.
- In comma-delimited `.lime`, literal `,` cannot safely appear inside fields.
- In tab-delimited `.lime`, literal tab cannot safely appear inside fields.
- In space-delimited `.cin` or `.lime`, literal spaces cannot safely appear inside mapping fields.
- A `.lime` mapping whose `code` begins with `@` is treated as LIME metadata and skipped.
- A CIN mapping whose `code` begins with `%version`, `%cname`, `%selkey`, `%endkey`, `%limeendkey`, or `%spacestyle` is treated as metadata and skipped.

Literal `@` is safe in a `.lime` `word` field today, but not as the first character of the parsed `code` field.

## 6. Compatibility Rules for New Files

For `.cin` files:

1. Use UTF-8 text.
2. Put metadata before `%chardef begin`.
3. Use `%version ...` and `%cname ...` for version/name metadata.
4. Put mappings inside `%chardef begin/end`.
5. Avoid spaces inside `code` and `word`.

For `.lime` files:

1. Use UTF-8 text.
2. Prefer `|` as delimiter unless code or word values need literal `|`.
3. Include `%chardef begin` and `%chardef end`.
4. Put metadata before `%chardef begin`.
5. Use `@version@|...` for version metadata.
6. Use `@cname@|...` for display-name metadata.
7. Avoid literal delimiter characters in `code` and `word`.
