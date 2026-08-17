package org.limeime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.limeime.data.Mapping;

import java.util.Arrays;

public class CandidateSelectionPolicyTest {

    @Test
    public void incompleteCodeKeepsRawComposingTextHighlighted() {
        Mapping composing = mapping("12345", "12345", MappingType.COMPOSING);
        Mapping partial = mapping("1", "一", MappingType.PARTIAL);
        java.util.List<Mapping> suggestions = Arrays.asList(composing, partial);

        assertEquals(0, LIMEService.defaultHighlightedCandidateIndex(suggestions, false));
        assertSame(composing, LIMEService.defaultServiceSelectedCandidate(suggestions, false));
    }

    @Test
    public void endkeyResolutionKeepsRawTextForIncompleteCode() {
        Mapping composing = mapping("12345", "12345", MappingType.COMPOSING);
        Mapping partial = mapping("1", "一", MappingType.PARTIAL);
        java.util.List<Mapping> suggestions = Arrays.asList(composing, partial);

        assertSame(composing, LIMEService.endkeyCommitCandidateForSuggestions(suggestions));
    }

    @Test
    public void exactCodeStillSelectsMappedCandidate() {
        Mapping composing = mapping("1", "1", MappingType.COMPOSING);
        Mapping exact = mapping("1", "一", MappingType.EXACT);
        java.util.List<Mapping> suggestions = Arrays.asList(composing, exact);

        assertEquals(1, LIMEService.defaultHighlightedCandidateIndex(suggestions, false));
        assertSame(exact, LIMEService.defaultServiceSelectedCandidate(suggestions, false));
    }

    @Test
    public void fullCodePunctuationCandidateStillWinsByCodeEquality() {
        Mapping composing = mapping(".", ".", MappingType.COMPOSING);
        Mapping punctuation = mapping(".", "。", MappingType.PUNCTUATION);
        java.util.List<Mapping> suggestions = Arrays.asList(composing, punctuation);

        assertEquals(1, LIMEService.defaultHighlightedCandidateIndex(suggestions, false));
        assertSame(punctuation, LIMEService.defaultServiceSelectedCandidate(suggestions, false));
    }

    private static Mapping mapping(String code, String word, MappingType type) {
        Mapping mapping = new Mapping();
        mapping.setCode(code);
        mapping.setWord(word);
        switch (type) {
            case COMPOSING:
                mapping.setComposingCodeRecord();
                break;
            case PARTIAL:
                mapping.setPartialMatchToCodeRecord();
                break;
            case EXACT:
                mapping.setExactMatchToCodeRecord();
                break;
            case PUNCTUATION:
                mapping.setChinesePunctuationSymbolRecord();
                break;
        }
        return mapping;
    }

    private enum MappingType {
        COMPOSING,
        PARTIAL,
        EXACT,
        PUNCTUATION
    }
}