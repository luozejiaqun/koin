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
package org.koin.core.instance

import org.koin.core.scope.Scope
import org.koin.core.registry.InstanceRegistry
import kotlin.reflect.KClass

/**
 * Add tag Interface to InstanceFactory, emulate dynamic language
 * @author luozejiaqun
 */
internal interface TaggedInstanceFactory {
    val tags: Set<KClass<*>>
}

internal fun InstanceFactory<*>.isTaggedWith(tag: KClass<*>): Boolean =
    (this as? TaggedInstanceFactory)?.tags?.contains(tag) == true

/**
 * Mark InstanceFactory as internal, this is it will be added to [InstanceRegistry.internalInstances]
 */
internal interface InternalInstanceFactory

internal fun <T> InstanceFactory<T>.withTag(tag: KClass<*>): InstanceFactory<T> {
    val instanceFactory = this
    return object : InstanceFactory<T>(instanceFactory.beanDefinition), TaggedInstanceFactory {
        override val tags: Set<KClass<*>> =
            setOf(tag, instanceFactory::class) + (instanceFactory as? TaggedInstanceFactory)?.tags.orEmpty()

        override fun isCreated(context: ResolutionContext?): Boolean =
            instanceFactory.isCreated(context)

        override fun drop(scope: Scope?) {
            instanceFactory.drop(scope)
        }

        override fun dropAll() {
            instanceFactory.dropAll()
        }

        override fun create(context: ResolutionContext): T =
            instanceFactory.create(context)

        override fun get(context: ResolutionContext): T =
            instanceFactory.get(context)
    }
}
