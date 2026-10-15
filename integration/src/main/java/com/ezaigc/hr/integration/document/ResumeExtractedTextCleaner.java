package com.ezaigc.hr.integration.document;

/** 对本地提取、OCR 和多模态返回的简历文本执行保守的版式噪声归一化。 */
final class ResumeExtractedTextCleaner {

    private ResumeExtractedTextCleaner() {}

    static String clean(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        boolean pendingSpace = false;
        boolean previousCarriageReturn = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (codePoint == '\r') {
                appendLineBreak(normalized);
                pendingSpace = false;
                previousCarriageReturn = true;
                continue;
            }
            if (codePoint == '\n') {
                if (!previousCarriageReturn) {
                    appendLineBreak(normalized);
                }
                pendingSpace = false;
                previousCarriageReturn = false;
                continue;
            }
            previousCarriageReturn = false;

            if (isDiscardableFormatting(codePoint)) {
                continue;
            }
            if (isLineBreak(codePoint)) {
                appendLineBreak(normalized);
                pendingSpace = false;
                continue;
            }
            if (isHorizontalWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                pendingSpace = normalized.length() > 0 && normalized.charAt(normalized.length() - 1) != '\n';
                continue;
            }
            if (pendingSpace) {
                normalized.append(' ');
                pendingSpace = false;
            }
            normalized.appendCodePoint(codePoint);
        }
        return collapseBlankLines(normalized).strip();
    }

    static boolean isReadableCodePoint(int codePoint) {
        if (codePoint == '\n' || codePoint == '\t') {
            return true;
        }
        int type = Character.getType(codePoint);
        return !Character.isISOControl(codePoint)
                && type != Character.FORMAT
                && type != Character.PRIVATE_USE
                && type != Character.SURROGATE
                && type != Character.UNASSIGNED
                && codePoint != 0xFFFD;
    }

    private static boolean isDiscardableFormatting(int codePoint) {
        return codePoint == 0x00AD
                || codePoint == 0x061C
                || codePoint == 0x200B
                || codePoint == 0x200C
                || codePoint == 0x200D
                || codePoint == 0x200E
                || codePoint == 0x200F
                || codePoint == 0x202A
                || codePoint == 0x202B
                || codePoint == 0x202C
                || codePoint == 0x202D
                || codePoint == 0x202E
                || codePoint == 0x2060
                || (codePoint >= 0x2066 && codePoint <= 0x2069)
                || codePoint == 0xFEFF;
    }

    private static boolean isLineBreak(int codePoint) {
        return codePoint == '\u000B'
                || codePoint == '\f'
                || codePoint == 0x0085
                || codePoint == 0x2028
                || codePoint == 0x2029;
    }

    private static boolean isHorizontalWhitespace(int codePoint) {
        return codePoint == '\t'
                || Character.getType(codePoint) == Character.SPACE_SEPARATOR;
    }

    private static void appendLineBreak(StringBuilder output) {
        while (!output.isEmpty() && output.charAt(output.length() - 1) == ' ') {
            output.setLength(output.length() - 1);
        }
        output.append('\n');
    }

    private static String collapseBlankLines(StringBuilder text) {
        StringBuilder collapsed = new StringBuilder(text.length());
        int consecutiveLineBreaks = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\n') {
                consecutiveLineBreaks++;
                if (consecutiveLineBreaks <= 2) {
                    collapsed.append(character);
                }
            } else {
                consecutiveLineBreaks = 0;
                collapsed.append(character);
            }
        }
        return collapsed.toString();
    }
}
