package com.edunexus.api.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class AnswerNormalizer {

    private static final Pattern LEADING_TEXTUAL_PREFIX =
            Pattern.compile("^[\\p{IsHan}A-Z0-9]{0,24}(数学表达式|表达式|公式)(是|为)");
    private static final Pattern TEXT_SEPARATORS = Pattern.compile("[\\s,，。；：、（）()【】《》“”‘’·]+");

    private AnswerNormalizer() {}

    public static boolean isCorrect(String questionType, String correctAnswer, String userAnswer) {
        if (isFreeTextQuestion(questionType)) {
            return isEquivalentFreeText(correctAnswer, userAnswer);
        }
        return normalizeForComparison(questionType, correctAnswer)
                .equalsIgnoreCase(normalizeForComparison(questionType, userAnswer));
    }

    public static String normalizeForComparison(String questionType, String answer) {
        String cleaned = normalizeUnicode(answer);
        if (cleaned.isBlank()) {
            return "";
        }

        if ("MULTIPLE_CHOICE".equalsIgnoreCase(questionType)) {
            return normalizeChoiceAnswer(cleaned);
        }

        if ("SINGLE_CHOICE".equalsIgnoreCase(questionType)
                || "TRUE_FALSE".equalsIgnoreCase(questionType)) {
            return cleaned.toUpperCase(Locale.ROOT);
        }

        return normalizeFreeTextAnswer(cleaned);
    }

    public static String normalizeChoiceAnswer(String answer) {
        String cleaned = normalizeUnicode(answer).toUpperCase(Locale.ROOT);
        if (cleaned.isBlank()) {
            return "";
        }

        TreeSet<String> labels = new TreeSet<>();
        for (int i = 0; i < cleaned.length(); i++) {
            char current = cleaned.charAt(i);
            if (current >= 'A' && current <= 'Z') {
                labels.add(String.valueOf(current));
            }
        }
        return String.join("", labels);
    }

    private static boolean isFreeTextQuestion(String questionType) {
        return !"MULTIPLE_CHOICE".equalsIgnoreCase(questionType)
                && !"SINGLE_CHOICE".equalsIgnoreCase(questionType)
                && !"TRUE_FALSE".equalsIgnoreCase(questionType);
    }

    private static boolean isEquivalentFreeText(String correctAnswer, String userAnswer) {
        String normalizedCorrect = normalizeFreeTextAnswer(correctAnswer);
        String normalizedUser = normalizeFreeTextAnswer(userAnswer);
        if (normalizedCorrect.isBlank() || normalizedUser.isBlank()) {
            return normalizedCorrect.equals(normalizedUser);
        }
        if (normalizedCorrect.equals(normalizedUser)) {
            return true;
        }
        return (normalizedUser.length() >= 6 && normalizedUser.contains(normalizedCorrect))
                || (normalizedCorrect.length() >= 6 && normalizedCorrect.contains(normalizedUser));
    }

    private static String normalizeFreeTextAnswer(String answer) {
        String normalized = normalizeUnicode(answer).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }

        normalized =
                normalized
                        .replace("作用在物体上的合力", "合力")
                        .replace("物体上的合力", "合力")
                        .replace("物体的质量", "质量")
                        .replace("物体的加速度", "加速度")
                        .replace("答案是", "")
                        .replace("结果是", "");
        normalized = LEADING_TEXTUAL_PREFIX.matcher(normalized).replaceFirst("");
        return TEXT_SEPARATORS.matcher(normalized).replaceAll("");
    }

    private static String normalizeUnicode(String answer) {
        return Normalizer.normalize(answer == null ? "" : answer, Normalizer.Form.NFKC).trim();
    }
}
