/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

internal fun <T> Sequence<T>.toList(expectedCapacity: Int): List<T> {
    val result = ArrayList<T>(expectedCapacity)
    toCollection(result)
    return result
}

internal inline fun <reified T> Iterable<T?>.firstNonNull() = firstIsInstance<T>()

internal val KotlinType.fqName: FqName
    get() = constructor.declarationDescriptor!!.fqNameSafe

internal fun Any?.isNull() = this == null
