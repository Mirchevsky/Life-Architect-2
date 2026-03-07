package com.mirchevsky.lifearchitect2.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mirchevsky.lifearchitect2.data.AppRepository

/**
 * Factory for creating [AnalyticsViewModel] with its [AppRepository] dependency.
 *
 * Required because ViewModels with constructor parameters cannot be instantiated
 * by the default [ViewModelProvider].
 *
 * @param repository The [AppRepository] to inject into the created [AnalyticsViewModel].
 */
class AnalyticsViewModelFactory(
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
