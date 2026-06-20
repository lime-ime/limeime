package net.toload.main.hd.candidate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.data.Mapping;
import net.toload.main.hd.voice.DictationState;

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
        assertEquals(1400, CandidateView.LIME_TOAST_TIMEOUT_MS);
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
}
