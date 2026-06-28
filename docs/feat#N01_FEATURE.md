# feat#N01 Android: Wider Candidate Dismiss Button

## Source request

Requested directly by Jeremy on 2026-06-28 after #124 commenter `01disney` reinforced that the candidate-strip left `X` is too small/hard to press during thumb typing and asked whether it can be made the same size as the smiley icon.

## Goal

Make the Android candidate-strip dismiss / clear-code button easier to tap during normal thumb typing.

## Current Android behavior

The candidate strip has a dismiss / clear-code button at the left side of the candidate view. It is used to dismiss candidates or clear the current composing state, depending on current input state.

Relevant Android paths:

- `LimeStudio/app/src/main/res/layout/inputcandidate.xml`
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateInInputViewContainer.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateView.java`

## Intended Android behavior

Increase the candidate-strip dismiss / clear-code button hit target so users can tap it more reliably.

## Preserved behavior

- Preserve the existing candidate-dismiss / clear-composition action semantics.
- Do not change the keyboard delete/backspace key behavior.
- Do not reposition unrelated candidate-strip controls unless needed for the wider tap target.
- Do not change iOS behavior in this Android feature task.

## Implementation notes

Likely implementation is a layout/touch-target change around `candidate_dismiss` in `inputcandidate.xml`, with code review to confirm no candidate-view lifecycle behavior changes are introduced.

## Verification plan

- Inspect the final Android layout and confirm the dismiss button tap target is wider.
- Run `git diff --check`.
- Run `cd LimeStudio && ./gradlew :app:compileDebugJavaWithJavac`.
- Build a debug APK if Jeremy wants interactive Android testing.

## Public communication

No public GitHub comment is needed unless this feature is tied to a public issue later.
