/*
 * Copyright 2017-Present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.koin.dsl

import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.*
import org.koin.core.qualifier.Qualifier

/**
 * DSL Scope Definition
 */
@OptIn(KoinInternalApi::class)
@Suppress("UNUSED_PARAMETER")
@KoinDslMarker
class ScopeDSL(val scopeQualifier: Qualifier, val module: Module) {

    inline fun <reified T> scoped(
        qualifier: Qualifier? = null,
        noinline definition: Definition<T>,
    ): KoinDefinition<T> {
        val def = _scopedInstanceFactory(qualifier, definition, scopeQualifier)
        module.indexPrimaryType(def)
        return KoinDefinition(module, def)
    }

    inline fun <reified T> factory(
        qualifier: Qualifier? = null,
        noinline definition: Definition<T>,
    ): KoinDefinition<T> {
        return module.factory(qualifier, definition, scopeQualifier)
    }

    /**
     * Declare a scoped Map<K, V> definition, the key type [K] can't be null
     * @param qualifier can't be null
     * @param asDefaultMapMultibinding - declare this map multibinding as default
     * @param elementDefinition - call `intoMap` to inject elements
     *
     * ```
     * // default map multibinding example
     *
     * class OtherComponent(val map: Map<K, V>)
     *
     * module {
     *   declareMapMultibinding<K, V>(asDefaultMapMultibinding = true)
     *   scopedOf(::OtherComponent)
     *   // instead of
     *   scoped { OtherComponent(getMapMultibinding<K, V>()) }
     *   // please make sure [OtherComponent.map] is the default map multibinding you want to inject
     * }
     * ```
     */
    inline fun <reified K : Any, reified V : Any> declareMapMultibinding(
        qualifier: Qualifier = mapMultibindingQualifier<K, V>(),
        asDefaultMapMultibinding: Boolean = false,
        elementDefinition: MapMultibindingElementDefinition<K, V>.() -> Unit = {},
    ): MapMultibindingElementDefinition<K, V> {
        if (asDefaultMapMultibinding) {
            scoped(defaultMapMultibinding) { qualifier }
        }
        scoped<Map<K, V>>(qualifier) { parametersHolder ->
            MapMultibinding(
                createdAtStart = false,
                scope = this,
                qualifier = qualifier,
                keyClass = K::class,
                valueClass = V::class,
                parametersHolder = parametersHolder,
            )
        }
        return MapMultibindingElementDefinition<K, V>(
            multibindingQualifier = qualifier,
            keyClass = K::class,
            elementClass = V::class,
            declareModule = module,
            scopeQualifier = scopeQualifier,
        ).apply {
            elementDefinition(this)
        }
    }

    /**
     * Declare a scoped Set<E> definition
     * @param qualifier can't be null
     * @param asDefaultSetMultibinding - declare this set multibinding as default
     * @param elementDefinition - call `intoSet` to inject elements
     *
     * ```
     * // default set multibinding example
     *
     * class OtherComponent(val set: Set<E>)
     *
     * module {
     *   declareSetMultibinding<E>(asDefaultSetMultibinding = true)
     *   scopedOf(::OtherComponent)
     *   // instead of
     *   scoped { OtherComponent(getSetMultibinding<E>()) }
     *   // please make sure [OtherComponent.set] is the default set multibinding you want to inject
     * }
     * ```
     */
    inline fun <reified E : Any> declareSetMultibinding(
        qualifier: Qualifier = setMultibindingQualifier<E>(),
        asDefaultSetMultibinding: Boolean = false,
        elementDefinition: SetMultibindingElementDefinition<E>.() -> Unit = {},
    ): SetMultibindingElementDefinition<E> {
        if (asDefaultSetMultibinding) {
            scoped(defaultSetMultibinding) { qualifier }
        }
        scoped<Set<E>>(qualifier) { parametersHolder ->
            SetMultibinding<E>(false, this, qualifier, E::class, parametersHolder)
        }
        return SetMultibindingElementDefinition<E>(
            multibindingQualifier = qualifier,
            elementClass = E::class,
            declareModule = module,
            scopeQualifier = scopeQualifier,
        ).apply {
            elementDefinition(this)
        }
    }
}
