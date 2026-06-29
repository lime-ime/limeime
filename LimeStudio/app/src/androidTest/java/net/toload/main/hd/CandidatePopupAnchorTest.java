/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.candidate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CandidatePopupAnchorTest {

    @Test
    public void popupBaseXUsesLeftScreenEdgeNotReservingDismissWidth() {
        // Issue #124 follow-up: the composing/root-key hint popup and the reverse-lookup lime
        // toast anchor at the left screen edge; they must NOT be offset by the candidate
        // dismiss-button width.
        assertEquals(100, CandidateView.popupBaseX(100, 21));
        assertEquals(0, CandidateView.popupBaseX(0, 21));
    }

    @Test
    public void expandedPopupDoesNotReserveLegacyBottomCloseButtonHeight() {
        assertEquals(240, CandidateView.popupContentHeight(240));
    }

    @Test
    public void expandedPopupFirstRowStartsAfterLeadingDismissButton() {
        assertEquals(21, CandidateExpandedView.rowStartX(0, 21));
        assertEquals(0, CandidateExpandedView.rowStartX(1, 21));
    }

    @Test
    public void expandedPopupFirstRowReservesTrailingCollapseButton() {
        assertEquals(452, CandidateExpandedView.rowEndX(0, 500, 48));
        assertEquals(500, CandidateExpandedView.rowEndX(1, 500, 48));
    }

    @Test
    public void expandedPopupRowsUseLiveCandidateBarHeightAndPadding() {
        assertEquals(34, CandidateExpandedView.rowHeight(30, 4));
        assertEquals(27, CandidateExpandedView.rowBaseline(34, 28f, -24f));
        assertEquals(2f, CandidateExpandedView.rowLineTop(0, 34, 4), 0f);
        assertEquals(32f, CandidateExpandedView.rowLineBottom(0, 34, 4), 0f);
    }

    @Test
    public void expandedPopupUsesLiveCandidateTextSizeAndMinimumWordWidth() {
        assertEquals(25.2f, CandidateView.liveCandidateTextSize(28f), 0.01f);

        Paint paint = new Paint();
        paint.setTextSize(28f);
        int punctuationWidth = (int) paint.measureText("。") + 8;
        assertEquals(punctuationWidth, CandidateExpandedView.wordWidth(paint, "d", 4));
    }

    @Test
    public void expandedPopupUsesFrameLayoutParamsForScrollableContent() {
        ViewGroup.LayoutParams params = CandidateView.popupFrameContentLayoutParams(240);

        assertTrue(params instanceof FrameLayout.LayoutParams);
        assertTrue(params instanceof ViewGroup.MarginLayoutParams);
        assertEquals(240, params.height);
    }

    @Test
    public void popupHeightShrinksToContentOnlyWhenKeyboardViewIsHidden() {
        assertEquals(90, CandidateView.popupHeight(120, 90, true));
        assertEquals(120, CandidateView.popupHeight(120, 90, false));
    }

    @Test
    public void visibleKeyboardPopupHeightCoversCandidateBarAndKeyboard() {
        assertEquals(520, CandidateView.visibleKeyboardPopupHeight(400, 120));
    }

    @Test
    public void hiddenKeyboardPopupWindowCoversLiveCandidateBottomArea() {
        assertEquals(210, CandidateView.hiddenKeyboardPopupWindowHeight(90, 120));
        assertEquals(90, CandidateView.hiddenKeyboardPopupWindowHeight(90, 0));
    }

    @Test
    public void visibleKeyboardPopupYStartsBelowCandidateBar() {
        assertEquals(630, CandidateView.visibleKeyboardPopupY(600, 30));
    }

    @Test
    public void composingPopupDoesNotShowWhileExpanded() {
        assertTrue(CandidateView.shouldShowComposingPopup(false, true));
        assertFalse(CandidateView.shouldShowComposingPopup(true, true));
        assertFalse(CandidateView.shouldShowComposingPopup(false, false));
    }

    @Test
    public void rightActionAcceptsButtonAndParentClicks() {
        View rightButton = new View(InstrumentationRegistry.getInstrumentation().getTargetContext());
        View rightParent = new View(InstrumentationRegistry.getInstrumentation().getTargetContext());
        View other = new View(InstrumentationRegistry.getInstrumentation().getTargetContext());

        assertTrue(CandidateInInputViewContainer.isRightActionClick(rightButton, rightButton, rightParent));
        assertTrue(CandidateInInputViewContainer.isRightActionClick(rightParent, rightButton, rightParent));
        assertFalse(CandidateInInputViewContainer.isRightActionClick(other, rightButton, rightParent));
    }

    @Test
    public void candidateStripRightEdgeTapDoesNotStealLastCandidate() {
        assertFalse(CandidateView.isExpandEdgeTap(455, 500, 48, 640));
        assertFalse(CandidateView.isExpandEdgeTap(451, 500, 48, 640));
        assertFalse(CandidateView.isExpandEdgeTap(455, 500, 48, 500));
    }

    @Test
    public void inputContainerRightEdgeActionIsLimitedToCandidateRow() {
        assertTrue(CandidateInInputViewContainer.isRightEdgeActionTap(1235, 80, 1280, 120, 96));
        assertFalse(CandidateInInputViewContainer.isRightEdgeActionTap(1183, 80, 1280, 120, 96));
        assertFalse(CandidateInInputViewContainer.isRightEdgeActionTap(1235, 121, 1280, 120, 96));
    }

    @Test
    public void rightActionGlyphFollowsExpansionDirection() {
        assertFalse(CandidateInInputViewContainer.shouldShowCollapseGlyph(false, false, false));
        assertTrue(CandidateInInputViewContainer.shouldShowCollapseGlyph(false, true, false));
        assertFalse(CandidateInInputViewContainer.shouldShowCollapseGlyph(false, false, true));
        assertFalse(CandidateInInputViewContainer.shouldShowCollapseGlyph(false, true, true));
        assertFalse(CandidateInInputViewContainer.shouldShowCollapseGlyph(true, true, false));
    }

    @Test
    public void idleToolsWaitForRevealDelayAndNoComposition() {
        assertFalse(CandidateInInputViewContainer.shouldShowIdleTools(false, true, false));
        assertFalse(CandidateInInputViewContainer.shouldShowIdleTools(true, false, false));
        assertFalse(CandidateInInputViewContainer.shouldShowIdleTools(true, true, true));
        assertTrue(CandidateInInputViewContainer.shouldShowIdleTools(true, true, false));
    }

    @Test
    public void activeChromeStaysVisibleDuringDelayedEmptyTransition() {
        assertTrue(CandidateInInputViewContainer.shouldShowActiveChrome(false, false, false));
        assertTrue(CandidateInInputViewContainer.shouldShowActiveChrome(true, false, false));
        assertFalse(CandidateInInputViewContainer.shouldShowActiveChrome(true, true, true));
    }
}
