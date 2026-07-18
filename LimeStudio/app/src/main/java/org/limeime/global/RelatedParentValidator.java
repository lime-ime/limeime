package org.limeime.global;

/** Validation rules for the parent key used by related-word records. */
public final class RelatedParentValidator {
    public static final String ERROR_MESSAGE = "首字只能輸入一個中文字";

    private RelatedParentValidator() {}

    public static String normalize(String parentWord) {
        return parentWord == null ? "" : parentWord.trim();
    }

    public static boolean isValid(String parentWord) {
        String value = normalize(parentWord);
        if (value.codePointCount(0, value.length()) != 1) return false;
        return isHanCodePoint(value.codePointAt(0));
    }

    private static boolean isHanCodePoint(int codePoint) {
        return codePoint == 0x3007
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
                || (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
                || (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
                || (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
                || (codePoint >= 0x2CEB0 && codePoint <= 0x2EBEF)
                || (codePoint >= 0x2EBF0 && codePoint <= 0x2EE5F)
                || (codePoint >= 0x2F800 && codePoint <= 0x2FA1F)
                || (codePoint >= 0x30000 && codePoint <= 0x323AF);
    }
}
