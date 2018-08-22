package org.koin.androidx.viewmodel.experimental.builder

import androidx.lifecycle.ViewModel
import org.koin.dsl.context.ModuleDefinition
import org.koin.experimental.builder.create

/**
 * Create ViewModel for Given type T
 * Allow to declare a vieModel - be later inject into Activity/Fragment with dedicated injector
 *
 * @author Arnaud Giuliani
 *
 * @param name - definition name
 * @param override - allow definition override
 */
inline fun <reified T : ViewModel> ModuleDefinition.viewModel(
    name: String = "",
    override: Boolean = false
) {
    factory(name, override) { create<T>() }.bind(ViewModel::class)
}