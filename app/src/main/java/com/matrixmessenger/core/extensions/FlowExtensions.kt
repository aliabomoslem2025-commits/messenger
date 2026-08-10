package com.matrixmessenger.core.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Extension functions for Flow to handle common patterns
 */

/**
 * Maps a Flow of Result to a Flow of T, emitting only successful results
 */
fun <T> Flow<Result<T>>.mapSuccess(): Flow<T> = this.map { result ->
    result.getOrThrow()
}

/**
 * Provides loading state emissions before and after the flow
 */
fun <T> Flow<T>.withLoading(): Flow<UiState<T>> = this
    .onStart { emit(UiState.Loading) }
    .map<T, UiState<T>> { UiState.Success(it) }
    .catch { emit(UiState.Error(it)) }

/**
 * Sealed class representing UI state
 */
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val exception: Throwable) : UiState<Nothing>()
}

/**
 * Check if UI state is loading
 */
fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading

/**
 * Check if UI state has data
 */
fun <T> UiState<T>.hasData(): Boolean = this is UiState.Success

/**
 * Get data from UI state or null
 */
fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Success)?.data

/**
 * Get data from UI state or default value
 */
fun <T> UiState<T>.dataOrDefault(default: T): T = dataOrNull() ?: default
