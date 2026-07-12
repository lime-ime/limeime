package org.limeime.candidate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.limeime.data.Mapping;
import org.limeime.voice.DictationState;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CandidateViewTest {

    @Test
    public void shouldShowLimeToastWhenAnchorIsAttachedEvenIfCandidateRowIsHidden() {
        assertTrue(CandidateView.shouldShowLimeToast(true, "大易"));
    }

    @Test
    public void shouldNotShowLimeToastWithoutAttachedAnchorOrText() {
        assertFalse(CandidateView.shouldShowLimeToast(false, "大易"));
        assertFalse(CandidateView.shouldShowLimeToast(true, null));
        assertFalse(CandidateView.shouldShowLimeToast(true, ""));
    }

    @Test
    public void limeToastYAlignsWithComposingPopupHeight() {
        assertEquals(80, CandidateView.limeToastYAlignedWithComposingPopup(100, 32, 20));
        assertEquals(68, CandidateView.limeToastYAlignedWithComposingPopup(100, 32, 0));
    }

    @Test
    public void limeToastKeepsShortTimeout() {
        assertEquals(3000, CandidateView.LIME_TOAST_TIMEOUT_MS);
    }

    @Test
    public void candidateActionButtonsStayTransparentOnThemedRow() {
        int darkCandidateBackground = Color.rgb(16, 16, 16);

        assertEquals(darkCandidateBackground,
                CandidateInInputViewContainer.actionRowBackgroundColor(darkCandidateBackground));
        assertEquals(Color.TRANSPARENT,
                CandidateInInputViewContainer.actionButtonBackgroundColor());
        assertEquals(Color.TRANSPARENT,
                CandidateInInputViewContainer.dismissButtonBackgroundColor());
    }

    @Test
    public void dictationDisplayTextReflectsStateAndPartialText() {
        assertEquals("請開始說話", CandidateView.dictationDisplayText(DictationState.LISTENING, null));
        assertEquals("這是測試", CandidateView.dictationDisplayText(DictationState.PARTIAL, "這是測試"));
        assertEquals("辨識完成中", CandidateView.dictationDisplayText(DictationState.FINALIZING, null));
        assertEquals("語音輸入錯誤", CandidateView.dictationDisplayText(DictationState.ERROR, null));
        assertEquals("", CandidateView.dictationDisplayText(DictationState.IDLE, null));
    }

    @Test
    public void dictationTextIsCenteredInCandidateWidth() {
        assertEquals(120.0f, CandidateView.dictationTextLeft(300, 60.0f), 0.001f);
        assertEquals(0.0f, CandidateView.dictationTextLeft(40, 60.0f), 0.001f);
    }

    @Test
    public void dictationErrorUsesTwoSecondCandidateStatusAndCanDismissEarly() {
        CandidateView candidateView = new CandidateView(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), null);

        candidateView.showDictationErrorTemporarily();

        assertEquals(2000, CandidateView.DICTATION_ERROR_TIMEOUT_MS);
        assertTrue(candidateView.isShowingDictationStatus());
        assertTrue(candidateView.clearDictationErrorIfShowing());
        assertFalse(candidateView.isShowingDictationStatus());
        assertFalse(candidateView.clearDictationErrorIfShowing());
    }

    @Test
    public void dismissDuringDictationCancelsVoiceInsteadOfComposing() {
        CandidateView candidateView = new CandidateView(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        org.limeime.LIMEService service =
                org.mockito.Mockito.mock(org.limeime.LIMEService.class);
        candidateView.setService(service);
        candidateView.showDictationStatus(DictationState.LISTENING, null);

        assertFalse(candidateView.isEmpty());
        candidateView.dismissComposingFromCandidate();

        org.mockito.Mockito.verify(service).cancelInlineDictation();
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).dismissCandidateComposing();
    }

    @Test
    public void clampPopupYKeepsToastFromRisingAboveCandidateRowIntoHostInput() {
        // Issue #124: the reverse-lookup toast / composing popup naturally anchor at
        // (candidateTop - popupHeight), i.e. above the candidate row. In bottom-composer apps
        // (LINE/WeChat/Instagram) that area is the host message input field, so the popup must be
        // clamped down to the candidate row top (start of the IME-owned area).
        int candidateTopInWindow = 0;
        int popupHeight = 80;
        int desiredY = candidateTopInWindow - popupHeight; // -80, above the candidate row

        assertEquals(candidateTopInWindow,
                CandidateView.clampPopupYToImeArea(desiredY, candidateTopInWindow));
    }

    @Test
    public void clampPopupYLeavesNaturalPositionWhenAlreadyInsideImeArea() {
        // When the candidate row is not at the window top (e.g. floating candidate bar) and the
        // popup already sits at/below the row top, the clamp must not push it down further.
        int candidateTopInWindow = 40;
        int desiredY = 120;

        assertEquals(120, CandidateView.clampPopupYToImeArea(desiredY, candidateTopInWindow));
    }

    @Test
    public void clampPopupYAtCandidateTopIsUnchanged() {
        // Boundary: a popup already flush with the candidate row top stays put.
        assertEquals(50, CandidateView.clampPopupYToImeArea(50, 50));
    }

    @Test
    public void setSuggestionsWithoutHighlightLeavesNoSelectedCandidate() {
        CandidateView candidateView = new CandidateView(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        Mapping composing = new Mapping();
        composing.setWord("salt");
        composing.setComposingCodeRecord();
        List<Mapping> suggestions = new ArrayList<>();
        suggestions.add(composing);

        candidateView.setSuggestionsWithoutHighlight(suggestions, false, "1234567890");

        assertEquals(-1, candidateView.mSelectedIndex);
    }

    @Test
    public void setSuggestionsDropsOnlyEntriesThatCannotBeDrawn() {
        CandidateView candidateView = new CandidateView(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        Mapping first = new Mapping();
        first.setWord("first");
        Mapping missingWord = new Mapping();
        Mapping last = new Mapping();
        last.setWord("last");
        List<Mapping> suggestions = new ArrayList<>();
        suggestions.add(first);
        suggestions.add(null);
        suggestions.add(missingWord);
        suggestions.add(last);

        candidateView.setSuggestions(suggestions, false);

        assertEquals(2, candidateView.mSuggestions.size());
        assertSame(first, candidateView.mSuggestions.get(0));
        assertSame(last, candidateView.mSuggestions.get(1));
    }

    @Test
    public void detachedCandidateViewDoesNotStartPopupExpansion() {
        CandidateView candidateView = new CandidateView(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        org.limeime.LIMEService service =
                org.mockito.Mockito.mock(org.limeime.LIMEService.class);
        Mapping mapping = new Mapping();
        mapping.setWord("candidate");
        List<Mapping> suggestions = new ArrayList<>();
        suggestions.add(mapping);
        candidateView.setService(service);
        candidateView.setSuggestions(suggestions, false);

        candidateView.doUpdateCandidatePopup();

        assertFalse(candidateView.isCandidateExpanded());
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
