package org.limeime.global;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RelatedParentValidatorTest {

    @Test
    public void acceptsExactlyOneHanCodePoint() {
        assertTrue(RelatedParentValidator.isValid("中"));
        assertTrue(RelatedParentValidator.isValid("〇"));
        assertTrue(RelatedParentValidator.isValid("𠀀"));
        assertTrue(RelatedParentValidator.isValid(" 中 "));
    }

    @Test
    public void rejectsMultipleHanAndNonHanInput() {
        assertFalse(RelatedParentValidator.isValid("台中"));
        assertFalse(RelatedParentValidator.isValid("add"));
        assertFalse(RelatedParentValidator.isValid("Ａ"));
        assertFalse(RelatedParentValidator.isValid("１"));
        assertFalse(RelatedParentValidator.isValid("，"));
        assertFalse(RelatedParentValidator.isValid(""));
        assertFalse(RelatedParentValidator.isValid(null));
    }

    @Test
    public void normalizesBeforeValidationAndStorage() {
        assertEquals("中", RelatedParentValidator.normalize(" 中 "));
        assertEquals("", RelatedParentValidator.normalize(null));
    }
}
