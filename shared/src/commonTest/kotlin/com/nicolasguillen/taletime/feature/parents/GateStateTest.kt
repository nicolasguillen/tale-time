package com.nicolasguillen.taletime.feature.parents

import com.nicolasguillen.taletime.i18n.EnglishStrings
import com.nicolasguillen.taletime.i18n.SpanishStrings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GateStateTest {
    @Test
    fun theQuestionIsWrittenInWordsInTheAppsLanguage() {
        val q = GateQuestion(7, 4)
        assertEquals("seven times four", EnglishStrings.times(q.a, q.b))
        assertEquals("siete por cuatro", SpanishStrings.times(q.a, q.b))
        assertEquals("28", q.answer)
        for (a in 3..9) assertTrue(SpanishStrings.times(a, a).none { it.isDigit() }, "every factor is a word")
    }

    @Test
    fun questionsUseFactorsThreeToNine() {
        val random = Random(1)
        repeat(200) {
            val q = GateQuestion.random(random)
            assertTrue(q.a in 3..9 && q.b in 3..9)
        }
    }

    @Test
    fun theRightAnswerPasses() {
        val state = GateState(GateQuestion(7, 4)).type(2).type(8)
        assertTrue(state.passed)
    }

    @Test
    fun aWrongAnswerBringsANewQuestion() {
        val start = GateState(GateQuestion(7, 4))
        val wrong = start.type(2).type(9, Random(3))
        assertFalse(wrong.passed)
        assertEquals("", wrong.typed)
        assertEquals(1, wrong.wrongTries)
        assertNotEquals(start, wrong)
    }

    @Test
    fun eraseTakesBackADigit() {
        val state =
            GateState(GateQuestion(7, 4))
                .type(3)
                .erase()
                .type(2)
                .type(8)
        assertTrue(state.passed)
    }
}
