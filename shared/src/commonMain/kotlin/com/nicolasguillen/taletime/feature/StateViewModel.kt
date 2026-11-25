package com.nicolasguillen.taletime.feature

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Base for screen ViewModels: one immutable [S] state exposed as a [StateFlow], changed only through [onEvent].
 * Navigation stays in the screen composable (it gets callbacks), so ViewModels don't know about routes.
 */
abstract class StateViewModel<S, E>(
    initialState: S,
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    protected val currentState: S get() = mutableState.value

    protected fun setState(reduce: S.() -> S) = mutableState.update(reduce)

    abstract fun onEvent(event: E)
}
