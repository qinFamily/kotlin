/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.serialization.compiler.diagnostic

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.extensions.CodegenApplicabilityCheckerExtension
import org.jetbrains.kotlin.extensions.isNoInlineKtClassWithSomeAnnotations
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlinx.serialization.idea.getIfEnabledOn

class SerializationPluginIDEDeclarationChecker : SerializationPluginDeclarationChecker(), CodegenApplicabilityCheckerExtension {
    override fun syntheticPartsCouldBeGenerated(declaration: KtDeclaration, descriptor: Lazy<DeclarationDescriptor?>): Boolean {

        if (!declaration.isNoInlineKtClassWithSomeAnnotations()) return false

        return (descriptor.value as? ClassDescriptor)?.let {
            getIfEnabledOn(it) { canBeSerializedInternally(it, trace = null) }
        } ?: false
    }

    override fun serializationPluginEnabledOn(descriptor: ClassDescriptor): Boolean {
        // In the IDE, plugin is always in the classpath, but enabled only if corresponding compiler settings
        // were imported into project model from Gradle.
        return getIfEnabledOn(descriptor) { true } == true
    }
}