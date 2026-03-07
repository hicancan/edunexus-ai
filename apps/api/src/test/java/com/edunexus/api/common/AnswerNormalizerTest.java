package com.edunexus.api.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnswerNormalizerTest {

    @Test
    void shortAnswer_shouldTreatEquivalentFormulaExplanationAsCorrect() {
        assertTrue(
                AnswerNormalizer.isCorrect(
                        "SHORT_ANSWER",
                        "F=MA，其中F表示合力，M表示物体的质量，A表示物体的加速度。",
                        "牛顿第二定律的数学表达式是 F = ma，其中 F 表示合力，m 表示质量，a 表示加速度。"));
    }

    @Test
    void shortAnswer_shouldIgnoreFormattingNoiseForUnits() {
        assertTrue(AnswerNormalizer.isCorrect("SHORT_ANSWER", "10N", "10 N"));
    }

    @Test
    void shortAnswer_shouldRejectDifferentPhysicalResult() {
        assertFalse(AnswerNormalizer.isCorrect("SHORT_ANSWER", "10N", "12N"));
    }

    @Test
    void multipleChoice_shouldNormalizeLetterOrder() {
        assertTrue(AnswerNormalizer.isCorrect("MULTIPLE_CHOICE", "AC", "CA"));
    }
}
