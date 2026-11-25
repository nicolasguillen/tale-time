package com.nicolasguillen.taletime.feature.parents

import kotlin.random.Random

/**
 * The parental gate's question (plan.md §9): a multiplication written out in words (`Strings.times`, in the app's
 * language), answered on a number pad. A child who can't read yet can't solve it, and random tapping almost never
 * gets a two-digit answer right.
 */
data class GateQuestion(
    val a: Int,
    val b: Int,
) {
    val answer: String get() = (a * b).toString()

    companion object {
        /** Both factors 3..9, so every answer has two digits except 3 × 3. */
        fun random(random: Random = Random.Default): GateQuestion = GateQuestion(random.nextInt(3, 10), random.nextInt(3, 10))
    }
}

/**
 * What's been typed so far. A wrong answer brings a new question, so guessing digit by digit doesn't work.
 */
data class GateState(
    val question: GateQuestion,
    val typed: String = "",
    val wrongTries: Int = 0,
) {
    val passed: Boolean get() = typed == question.answer

    fun type(
        digit: Int,
        random: Random = Random.Default,
    ): GateState {
        if (passed) return this
        val next = typed + digit
        return when {
            next == question.answer -> copy(typed = next)
            next.length >= question.answer.length -> GateState(GateQuestion.random(random), wrongTries = wrongTries + 1)
            else -> copy(typed = next)
        }
    }

    fun erase(): GateState = copy(typed = typed.dropLast(1))
}
