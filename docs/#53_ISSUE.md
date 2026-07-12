# Issue #53 — Phone-Keyboard Multi-Tap Symbol Key (= + - * /) Unreliable

**Status:** Fixed (easy route: single-code key + long-press popup)
**Date:** 2026-04-20
**Reporter:** SmithCCho

---

## Correction to original investigation

The investigation above incorrectly pointed at
[phone.xml:47](LimeStudio/app/src/main/res/xml/phone.xml#L47). That file is
the **T9 letter-sharing** phone pad (digit keys carry `2/a/b/c`, `3/d/e/f`,
etc.) and its multi-tap is expected behaviour for T9 typing. The layout the
reporter is actually using is the **plain phone pad**
[phone_simple.xml](LimeStudio/app/src/main/res/xml/phone_simple.xml), where
each digit key carries only its digit. The unreliable 5-code symbol key
lives there at
[phone_simple.xml:47](LimeStudio/app/src/main/res/xml/phone_simple.xml#L47) —
label `+-*/\n=`, which matches the screenshot in the issue.

All failure-mode hypotheses (H1–H4) still apply; they just apply to the
`phone_simple.xml` key, not `phone.xml`.

## Resolution (2026-04-21)

Chose the low-risk route: replace the unreliable 5-code multi-tap with a
single-code key (`=`) plus a long-press popup exposing `+ - * /`. This
side-steps every hypothesised failure mode (H1 bounce-move reset, H2 stale
key index, H3 tight timeout, H4 DELETE/insert IPC race) because no
DELETE-and-replace cycle is performed — each symbol is committed once via a
direct `onKey`. The same `popupKeyboard`/`popupCharacters` mechanism is
already used throughout the English/email layouts (see
[lime_english.xml:37-39](LimeStudio/app/src/main/res/xml/lime_english.xml#L37)),
so this reuses existing, battle-tested code paths and introduces no new
behaviour.

**Change:**
[phone_simple.xml:47](LimeStudio/app/src/main/res/xml/phone_simple.xml#L47)

```xml
<!-- Before -->
<Key limehd:codes="61,43,45,42,47" limehd:keyLabel="+-*/\n=" limehd:keyEdgeFlags="right"/>

<!-- After -->
<Key limehd:codes="61" limehd:keyLabel="+-*/\n="
     limehd:popupKeyboard="@xml/popup_template"
     limehd:popupCharacters="+-*/"
     limehd:keyEdgeFlags="right"/>
```

`phone.xml` (the T9 layout) is intentionally left unchanged — its multi-tap
on digit keys is required for T9 letter entry, not a bug.

**Trade-off accepted:** Users who previously relied on the multi-tap cycle
need to learn the long-press gesture. The long-press affordance is already
standard across the other layouts in this app, so this is consistent with
the rest of the IME's UX rather than a new convention to learn.

**Verification:**

1. Build & install debug APK.
2. Open an `EditText` (e.g. Messages), switch to the phone layout.
3. Tap the row-2 right key once → `=` is committed.
4. Long-press the same key → popup shows `+`, `-`, `*`, `/`. Slide/release
   onto any one → that symbol is committed. Original `=` is not inserted.
5. Rapid repeated taps → each tap commits one `=`, never skips or inserts a
   different symbol (confirms the original "跳著出字" bug can no longer
   occur because there is no multi-tap state).

---

---

## 1. User Report

On the **phone** keyboard (not the `phone_number` alternative), the bottom-right
symbol key on row 2 shows `=` and is expected to cycle through `= + - * /` when
multi-tapped. The user's complaint:

> 按一下顯示 `=`，連點是不是會連續切換 `+ - * /` 等符號？  
> 但連點有時會沒反應，或是跳著出字。  
> 這好像從舊版就這樣了。

i.e. multi-tap sometimes misses a tap entirely or skips to the wrong symbol.
Reported as a long-standing issue across versions.

---

## 2. Where This Is Defined

### 2a. Keyboard layout — the affected key

[`LimeStudio/app/src/main/res/xml/phone.xml:47`](LimeStudio/app/src/main/res/xml/phone.xml#L47)

```xml
<Key limehd:codes="61,43,45,42,47"
     limehd:iconPreview="@drawable/phone_cal_l"
     limehd:keyIcon="@drawable/phone_cal"
     limehd:keyEdgeFlags="right"/>
```

Codes map to ASCII: `61=` `43+` `45-` `42*` `47/`. This is the only row-2
right-edge key in `phone.xml` that carries 5 multi-tap codes; matches the
screenshot in the issue.

The alternative layout
[`LimeStudio/app/src/main/res/xml/phone_number.xml`](LimeStudio/app/src/main/res/xml/phone_number.xml)
defines each symbol as its own single-code key and is **not** what the reporter
is using (not affected by this bug).

### 2b. Multi-tap state machine

[`LimeStudio/app/src/main/java/org/limeime/keyboard/PointerTracker.java`](LimeStudio/app/src/main/java/org/limeime/keyboard/PointerTracker.java)

Relevant pieces:

| Symbol | Line | Purpose |
|---|---|---|
| `mMultiTapKeyTimeout` field | 51 | Holds the "second tap must arrive within N ms" threshold. |
| Loaded from resources | 189 | `res.getInteger(R.integer.config_multi_tap_key_timeout)` |
| `checkMultiTap` | 572–591 | Called from `onDownEvent` before the key is sent; decides whether this `DOWN` continues an existing multi-tap sequence. |
| `detectAndSendKey` | 506–549 | Called on `UP`; emits the chosen character and, if continuing, a preceding `KEYCODE_DELETE` to erase the previous preview char. |
| `resetMultiTap` | 565–570 | Clears `mTapCount`, `mLastSentIndex`, `mLastTapTime`, `mInMultiTap`. |
| Reset on slide | 357, 378 | Any non-minor `ACTION_MOVE` that crosses a key boundary resets multi-tap. |

### 2c. The timeout value

[`LimeStudio/app/src/main/res/values/config.xml:36`](LimeStudio/app/src/main/res/values/config.xml#L36)

```xml
<integer name="config_multi_tap_key_timeout">800</integer>
```

---

## 3. Code-Flow Trace (expected behaviour)

| Tap # | `onDownEvent` → `checkMultiTap` | `onUpEvent` → `detectAndSendKey` | Text field sees |
|---|---|---|---|
| 1 | `isMultiTap=false`; `mInMultiTap=true`, `mTapCount=-1` | `mTapCount==-1` → set to `0`; emit `codes[0]='='` | `=` |
| 2 (<800ms) | `isMultiTap=true`; `mTapCount=(0+1)%5=1` | `mTapCount!=-1` → emit `DELETE`, then `codes[1]='+'` | `+` |
| 3 (<800ms) | `mTapCount=2` | emit `DELETE`, `codes[2]='-'` | `-` |
| … | … | … | … |

So when it works, the key "owns" a single cell in the text buffer that it
overwrites via DELETE+insert on every subsequent tap within the window.

---

## 4. Hypothesised Failure Modes

None of these are confirmed yet — they are the specific things worth probing
with device logs before picking a fix.

### H1. Minor-move bounce kills multi-tap mid-sequence
[`PointerTracker.java:350, 357, 378`](LimeStudio/app/src/main/java/org/limeime/keyboard/PointerTracker.java#L350)

`onMoveEvent` calls `resetMultiTap()` whenever `!isMinorMoveBounce(...)` reports
the pointer left the key. On a small key with a quick double-tap, the user's
finger can drift a few px between taps; if that drift registers as a real move
(not bounce), the multi-tap state is wiped. Next `DOWN` then re-starts at
`codes[0]='='`, so the user sees "no response / skipped to `=`".

Worth checking: what is the bounce threshold, and is the symbol key at the
screen edge (where finger position is noisier)?

### H2. `keyIndex == mLastSentIndex` fails after layout change
[`PointerTracker.java:578`](LimeStudio/app/src/main/java/org/limeime/keyboard/PointerTracker.java#L578)

Multi-tap continuation requires the same **array index** as the last sent key.
If anything causes the keyboard array to be rebuilt between taps (theme reload,
window size change, orientation nudge, popup dismiss) `mLastSentIndex` becomes
stale → treated as new sequence → restarts at `=`.

### H3. 800 ms is too tight for "natural" multi-tap pace
[`config.xml:36`](LimeStudio/app/src/main/res/values/config.xml#L36)

Other Android IMEs use 900–1200 ms for T9-style multi-tap. Under load (first
tap on a cold IME, GC pause, heavy Chinese composition in the background)
`eventTime` deltas can easily exceed 800 ms between intentionally-rapid taps.
Result: cycle resets to `=`.

### H4. DELETE ordering race with the target app
[`PointerTracker.java:524-531, 541-544`](LimeStudio/app/src/main/java/org/limeime/keyboard/PointerTracker.java#L524)

Continuation emits `onKey(KEYCODE_DELETE)` and then `onKey(newCode)` back-to-back
on the UI thread. If the `InputConnection` for the target field is remote (most
apps) these become two IPC round-trips. Fast tap cycles can pile up; if either
call is dropped or re-ordered the user sees a duplicated or skipped symbol.
This is the most plausible source of the "跳著出字" (skip-ahead) symptom.

### H5. (Not likely) ACTION_CANCEL between taps
`onCancelEvent` (line 285) can be fired by the parent view stealing the gesture.
That resets state. Worth ruling out but no reason to suspect it on phone.xml.

---

## 5. Git History Note

`PointerTracker.java` has not been meaningfully edited since ~2015 (last substantive
commit predates the Android Studio migration). The multi-tap code is original
AOSP-derived. No prior issue/commit references this symbol-key behaviour. That
matches the reporter's "從舊版就這樣了".

---

## 6. Recommended Next Steps (investigation, not yet implementation)

Before changing code, capture one confirmed trace from a device, because each
hypothesis above calls for a different fix and they are not mutually exclusive.

1. **Enable `DEBUG` + `DEBUG_MOVE` in `PointerTracker.java`** (lines ~38–40 — to
   be confirmed on read) and reproduce on a physical device while typing into
   a plain `EditText` (e.g. Messages). Capture `logcat -s LIMEIME_PointerTracker`
   across a known-failing cycle of ~6 rapid taps.

2. From the trace decide:
   - If `resetMultiTap` fires mid-sequence from `onMoveEvent` → H1 (tighten
     bounce threshold or track last-sent **key code** instead of array index).
   - If `mLastSentIndex` matches but timing gap > 800 ms on a tap that *felt*
     fast → H3 (raise `config_multi_tap_key_timeout` to 1000–1200 ms).
   - If `onKey` pairs look correct in the log but the field still shows wrong
     char → H4 (consolidate DELETE+insert into a single
     `InputConnection.commitText` replacement, or use
     `setComposingText` during the cycle and `finishComposingText` on
     window-close / timeout).

3. **Pure-config first attempt (lowest risk)** if no trace is available: bump
   `config_multi_tap_key_timeout` from `800` → `1200`. This addresses H3 with
   zero code change and is easy to revert. Does not fix H1 or H4, so it is a
   partial mitigation, not a full fix.

---

## 7. Files That May Need To Change (depending on root cause)

| Hypothesis | File | Line(s) |
|---|---|---|
| H1 — sliding resets | [PointerTracker.java](LimeStudio/app/src/main/java/org/limeime/keyboard/PointerTracker.java) | 350, 357, 378 + `isMinorMoveBounce` |
| H2 — stale key index | [PointerTracker.java](LimeStudio/app/src/main/java/org/limeime/keyboard/PointerTracker.java) | 572–591 (compare key code instead of index) |
| H3 — tight timeout | [config.xml](LimeStudio/app/src/main/res/values/config.xml) | 36 |
| H4 — DELETE/insert race | [PointerTracker.java](LimeStudio/app/src/main/java/org/limeime/keyboard/PointerTracker.java) | 524–544, plus the `OnKeyboardActionListener` impl in `LIMEService` that handles the `DELETE`/code pair |

---

## 8. Out of Scope

- `phone_number.xml` layout. It uses one-code-per-key and cannot exhibit this
  bug; any "fix" there would be a redesign, not a bug fix.
- T9-style Chinese/English multi-tap on letter keys (`phone.xml` rows 1–3
  digit keys also carry multi-tap letters). No user report of problems there,
  and the letter keys are physically bigger so H1 is much less likely.
