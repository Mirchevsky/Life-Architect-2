package com.mirchevsky.lifearchitect2.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mirchevsky.lifearchitect2.data.AppRepository
import com.mirchevsky.lifearchitect2.data.TrendsRepository

/**
 * Factory for creating [MainViewModel] with its dependencies.
 * Required because ViewModels with constructor parameters cannot be instantiated
 * by the default ViewModelProvider.
 */
class MainViewModelFactory(
    private val repository: AppRepository,
    private val trendsRepository: TrendsRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, trendsRepository, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
