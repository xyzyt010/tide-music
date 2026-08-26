package com.example.tidemusic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tidemusic.di.ServiceLocator

/**
 * Tiny adapter that builds a ViewModel from the manual DI graph via [ServiceLocator] and a
 * zero-arg constructor reference.
 *
 * Replaces `androidx.hilt.navigation.compose.hiltViewModel()` since the Hilt Gradle plugin
 * doesn't yet support AGP 9. Equivalent semantics: the VM is scoped to the current
 * ViewModelStoreOwner (Activity / Nav entry).
 */
@Composable
inline fun <reified VM : ViewModel> rememberTideViewModel(crossinline create: (ServiceLocator) -> VM): VM {
    val factory = remember {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create(ServiceLocator) as T
        }
    }
    return viewModel(factory = factory)
}
