/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.core

import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.commonizer.ir.CommonFunction
import org.jetbrains.kotlin.descriptors.commonizer.ir.ExtensionReceiver.Companion.toReceiverNoAnnotations
import org.jetbrains.kotlin.descriptors.commonizer.ir.Function

class FunctionCommonizer : CallableMemberCommonizer<SimpleFunctionDescriptor, Function>() {
    private val modifiers = FunctionModifiersCommonizer.default()
    private val valueParameters = ValueParameterListCommonizer.default()

    override val result: Function
        get() = when (state) {
            State.EMPTY, State.ERROR -> error("Can't commonize function")
            State.IN_PROGRESS -> CommonFunction(
                name = name!!,
                modality = modality.result,
                visibility = visibility.result,
                extensionReceiver = extensionReceiver.result?.toReceiverNoAnnotations(),
                returnType = returnType.result,
                modifiers = modifiers.result,
                valueParameters = valueParameters.result
            )
        }

    override fun canBeCommonized(next: SimpleFunctionDescriptor) = true

    override fun commonizeSpecifics(next: SimpleFunctionDescriptor): Boolean {

        // TODO: type parameters (for functions???)

        return modifiers.commonizeWith(next) && valueParameters.commonizeWith(next.valueParameters)
    }
}
