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
package org.koin.core.registry

import co.touchlab.stately.concurrency.AtomicInt
import org.koin.core.Koin
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import org.koin.core.definition.IndexKey
import org.koin.core.definition.Kind
import org.koin.core.definition._createDefinition
import org.koin.core.definition.indexKey
import org.koin.core.instance.ResolutionContext
import org.koin.core.instance.InstanceFactory
import org.koin.core.instance.InternalInstanceFactory
import org.koin.core.instance.NoClass
import org.koin.core.instance.OrderedInstanceFactory
import org.koin.core.instance.ScopedInstanceFactory
import org.koin.core.instance.SingleInstanceFactory
import org.koin.core.instance.isTaggedWith
import org.koin.core.module.Module
import org.koin.core.module.overrideError
import org.koin.core.parameter.ParametersHolder
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatformTools.safeHashMap
import kotlin.collections.set
import kotlin.collections.toTypedArray
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
@OptIn(KoinInternalApi::class)
class InstanceRegistry(val _koin: Koin) {
    private val instanceOrder = AtomicInt(0)

    private val _instances = safeHashMap<IndexKey, InstanceFactory<*>>()
    val instances: Map<IndexKey, InstanceFactory<*>>
        get() = _instances

    private val eagerInstances = safeHashMap<Int, SingleInstanceFactory<*>>()

    internal val internalInstances = safeHashMap<IndexKey, InstanceFactory<*>>()

    internal fun loadModules(modules: Set<Module>, allowOverride: Boolean) {
        modules.forEach { module ->
            loadModule(module, allowOverride)
            addAllEagerInstances(module)
        }
    }

    private fun addAllEagerInstances(module: Module) {
        module.eagerInstances.forEach { factory ->
            eagerInstances[factory.beanDefinition.hashCode()] = factory
        }
    }

    internal fun createAllEagerInstances() {
        val instances = arrayListOf(*eagerInstances.values.toTypedArray())
        eagerInstances.clear()
        createEagerInstances(instances)
    }

    private fun loadModule(module: Module, allowOverride: Boolean) {
        module.mappings.forEach { (mapping, factory) ->
            saveMapping(allowOverride, mapping, factory)
        }
    }

    @KoinInternalApi
    fun saveMapping(
        allowOverride: Boolean,
        mapping: IndexKey,
        factory: InstanceFactory<*>,
        logWarning: Boolean = true,
    ) {
        _instances[mapping]?.let {
            if (!allowOverride) {
                overrideError(factory, mapping)
            } else if (logWarning) {
                _koin.logger.warn("(+) override index '$mapping' -> '${factory.beanDefinition}'")
                // remove previous eager isntance too
                val existingFactory = eagerInstances.values.firstOrNull { it.beanDefinition == factory.beanDefinition }
                if (existingFactory != null) {
                    eagerInstances.remove(factory.beanDefinition.hashCode())
                }
            }
        }
        _koin.logger.debug("(+) index '$mapping' -> '${factory.beanDefinition}'")
        val instanceFactory = if (factory is OrderedInstanceFactory<*>) {
            factory.copy(order = instanceOrder.incrementAndGet())
        } else {
            factory
        }
        if (instanceFactory.isTaggedWith(InternalInstanceFactory::class)) {
            internalInstances[mapping] = instanceFactory
        } else {
            _instances[mapping] = instanceFactory
        }
    }

    private fun createEagerInstances(instances: Collection<SingleInstanceFactory<*>>) {
        val defaultContext = ResolutionContext(_koin.logger, _koin.scopeRegistry.rootScope, clazz = NoClass::class)
        instances.forEach { factory -> factory.get(defaultContext) }
    }

    internal fun resolveDefinition(
        clazz: KClass<*>,
        qualifier: Qualifier?,
        scopeQualifier: Qualifier,
        fromInternal: Boolean,
    ): InstanceFactory<*>? {
        val indexKey = indexKey(clazz, qualifier, scopeQualifier)
        return if (fromInternal) {
            internalInstances[indexKey] ?: _instances[indexKey]
        } else {
            _instances[indexKey]
        }
    }

    @KoinExperimentalAPI
    internal fun <T> resolveScopeArchetypeInstance(
        qualifier: Qualifier?,
        klass: KClass<*>,
        context: ResolutionContext
    ): T? {
        return context.scope.scopeArchetype?.let {
            context.scopeArchetype = it
            resolveInstance(qualifier, klass, it, context)
        }
    }

    internal fun <T> resolveInstance(
        qualifier: Qualifier?,
        clazz: KClass<*>,
        scopeQualifier: Qualifier,
        instanceContext: ResolutionContext,
    ): T? {
        return resolveDefinition(
            clazz,
            qualifier,
            scopeQualifier,
            instanceContext.fromInternal
        )?.get(instanceContext) as? T
    }

    @PublishedApi
    internal inline fun <reified T> scopeDeclaredInstance(
        instance: T,
        scopeQualifier: Qualifier,
        scopeID: ScopeID,
        qualifier: Qualifier? = null,
        secondaryTypes: List<KClass<*>> = emptyList(),
        allowOverride: Boolean = true,
        holdInstance: Boolean
    ) {
        val primaryType = T::class
        val indexKey = indexKey(primaryType, qualifier, scopeQualifier)
        val existingFactory = instances[indexKey] as? ScopedInstanceFactory<T>
        if (existingFactory != null) {
            existingFactory.saveValue(scopeID, instance)
        } else {
            val definitionFunction: Scope.(ParametersHolder) -> T =
                if (!holdInstance) ({ error("Declared definition of type '$primaryType' shouldn't be executed") }) else ({ instance })
            val def: BeanDefinition<T> =
                _createDefinition(Kind.Scoped, qualifier, definitionFunction, secondaryTypes, scopeQualifier)
            val factory = ScopedInstanceFactory(def, holdInstance = holdInstance)
            saveMapping(allowOverride, indexKey, factory)
            def.secondaryTypes.forEach { clazz ->
                val index = indexKey(clazz, def.qualifier, def.scopeQualifier)
                saveMapping(allowOverride, index, factory)
            }
            factory.saveValue(scopeID, instance)
        }
    }

    @PublishedApi
    internal inline fun <reified T> declareRootInstance(
        instance: T,
        qualifier: Qualifier? = null,
        secondaryTypes: List<KClass<*>> = emptyList(),
        allowOverride: Boolean = true,
    ) {
        val rootQualifier = _koin.scopeRegistry.rootScope.scopeQualifier
        val def =
            _createDefinition(Kind.Scoped, qualifier, { instance }, secondaryTypes, rootQualifier)
        val factory = SingleInstanceFactory(def)
        val indexKey = indexKey(def.primaryType, def.qualifier, def.scopeQualifier)
        saveMapping(allowOverride, indexKey, factory)
        def.secondaryTypes.forEach { clazz ->
            val index = indexKey(clazz, def.qualifier, def.scopeQualifier)
            saveMapping(allowOverride, index, factory)
        }
    }

    internal fun dropScopeInstances(scope: Scope) {
        dropScopeInstances(_instances, scope)
        dropScopeInstances(internalInstances, scope)
    }

    private fun dropScopeInstances(instances: MutableMap<IndexKey, InstanceFactory<*>>, scope: Scope) {
        val factories = instances.values.toTypedArray()
        factories.forEach { factory ->
            if (factory is ScopedInstanceFactory ||
                factory.isTaggedWith(ScopedInstanceFactory::class)) {
                factory.drop(scope)
            }
        }
    }

    internal fun close() {
        dropAll(_instances)
        dropAll(internalInstances)
    }

    private fun dropAll(instances: MutableMap<IndexKey, InstanceFactory<*>>) {
        val factories = instances.values.toTypedArray()
        factories.forEach { factory -> factory.dropAll() }
        instances.clear()
    }

    internal fun <T> getAll(clazz: KClass<*>, instanceContext: ResolutionContext): List<T> {
        return (if (instanceContext.fromInternal) internalInstances else _instances).values
            .filter { factory ->
                factory.beanDefinition.scopeQualifier == instanceContext.scope.scopeQualifier &&
                    (factory.beanDefinition.primaryType == clazz || factory.beanDefinition.secondaryTypes.contains(clazz))
            }
            .distinct()
            .sortedIfNecessary()
            .mapNotNull { it.get(instanceContext) as? T }
    }

    private fun List<InstanceFactory<*>>.sortedIfNecessary(): List<InstanceFactory<*>> {
        return if (all { it is OrderedInstanceFactory<*> }) {
            this as List<OrderedInstanceFactory<*>>
            this.sorted()
        } else {
            this
        }
    }

    internal fun unloadModules(modules: Set<Module>) {
        modules.forEach { unloadModule(it) }
    }

    private fun unloadModule(module: Module) {
        module.mappings.keys.forEach { mapping ->
            (_instances[mapping] ?: internalInstances[mapping])?.dropAll()
            _instances.remove(mapping)
            internalInstances.remove(mapping)
        }
    }

    fun size(): Int {
        return _instances.size
    }


}
