# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-27

## Active issue follow-up

- #124 Android: v6.1.23 includes the targeted LINE/WeChat/Instagram bottom-composer composing/root-key and reverse-lookup popup placement fixes. Retained comment https://github.com/lime-ime/limeime/issues/124#issuecomment-4761898236 tells the Google Play closed-test reporter to update from Google Play, and the issue was reopened with https://github.com/lime-ime/limeime/issues/124#issuecomment-4761963945. After the reporter uploaded videos, `limeimetw` posted https://github.com/lime-ime/limeime/issues/124#issuecomment-4766516641 asking whether the temporary reverse-lookup hint duration and placement are acceptable, or whether a shorter duration / further inward keyboard placement is preferred; the public comment describes disappearance as roughly five seconds, but source/tests still pin the timed lime-toast timeout to `1400` ms, so any longer observed duration should be clarified against the exact build/path. The original reporter then proposed UX adjustments in https://github.com/lime-ime/limeime/issues/124#issuecomment-4779129986: place the reverse-lookup hint above the root display, show only lookup roots without repeating the committed character, and limit displayed lookup choices to the first or second option. Later commenter `Limeroshenko` edited https://github.com/lime-ime/limeime/issues/124#issuecomment-4788766570 with additional UX feedback: the hint can disappear too quickly to read, notifications are a workaround, removing the committed character raises a question about whether notifications would show only root codes, and lookup should keep all possible root-code solutions rather than only the first one or two. Keep #124 open for maintainer/product decision on this remaining popup readability/content behavior. A later `01disney` thread reports a likely separate Android 16 / POCO F6 Pro / Boshiamy language-switch / keyboard-dismiss symptom: after observing the behavior, the reporter says pressing `中` / `EN` can make the IME slide down as if the hide-key path was triggered. Handle that as a separate issue/follow-up unless maintainer evidence connects it to #124.

## Pending fixes

- None currently tracked.

## New features / product work

- feat#124 Android: from #124 commenter `01disney` in https://github.com/lime-ime/limeime/issues/124#issuecomment-4786153001 / https://github.com/lime-ime/limeime/issues/124#issuecomment-4788945640. On the English keyboard layout only, the `123` key should visibly show an ellipsis (`...`) affordance. Normal press should continue switching to the first symbol keyboard (`symbols1`), while long-pressing `123` should switch to the phonetic/simple numeric keyboard (`phone_simple`). Current state: confirmed feature request, pending Android design/implementation.
- feat#124 iOS: same #124 request and scope for iOS parity. On the English keyboard layout only, the `123` key should visibly show an ellipsis (`...`) affordance. Normal press should continue switching to the first symbol keyboard, while long-pressing `123` should switch to the phonetic/simple numeric keyboard. Current state: confirmed feature request, pending iOS design/implementation.
