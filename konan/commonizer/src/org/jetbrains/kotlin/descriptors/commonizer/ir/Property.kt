/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.ir

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.descriptors.commonizer.ir.Getter.Companion.toGetter
import org.jetbrains.kotlin.descriptors.commonizer.ir.Setter.Companion.toSetter
import org.jetbrains.kotlin.resolve.constants.ConstantValue

interface Property : CallableMember, Declaration {
    val isVar: Boolean
    val lateInit: Boolean
    val isConst: Boolean
    val isDelegate: Boolean
    val getter: Getter?
    val setter: Setter?
    val backingFieldAnnotations: Annotations? // null assumes no backing field
    val delegateFieldAnnotations: Annotations? // null assumes no backing field
    val compileTimeInitializer: ConstantValue<*>?
}

data class CommonProperty(
    override val name: Name,
    override val modality: Modality,
    override val visibility: Visibility,
    override val isExternal: Boolean,
    override val extensionReceiver: ExtensionReceiver?,
    override val returnType: UnwrappedType,
    override val setter: Setter?
) : CommonCallableMember(), Property {
    override val isVar: Boolean get() = setter != null
    override val lateInit: Boolean get() = false
    override val isConst: Boolean get() = false
    override val isDelegate: Boolean get() = false
    override val getter: Getter get() = Getter.DEFAULT_NO_ANNOTATIONS
    override val backingFieldAnnotations: Annotations? get() = null
    override val delegateFieldAnnotations: Annotations? get() = null
    override val compileTimeInitializer: ConstantValue<*>? get() = null
}

class TargetProperty(descriptor: PropertyDescriptor) : TargetCallableMember<PropertyDescriptor>(descriptor), Property {
    override val isVar: Boolean get() = descriptor.isVar
    override val lateInit: Boolean get() = descriptor.isLateInit
    override val isConst: Boolean get() = descriptor.isConst
    @Suppress("DEPRECATION")
    override val isDelegate: Boolean get() = descriptor.isDelegated
    override val getter: Getter? get() = descriptor.getter?.toGetter()
    override val setter: Setter? get() = descriptor.setter?.toSetter()
    override val backingFieldAnnotations: Annotations? get() = descriptor.backingField?.annotations
    override val delegateFieldAnnotations: Annotations? get() = descriptor.delegateField?.annotations
    override val compileTimeInitializer: ConstantValue<*>? get() = descriptor.compileTimeInitializer
}

interface PropertyAccessor {
    val annotations: Annotations
    val isDefault: Boolean
    val isExternal: Boolean
    val isInline: Boolean
}

data class Getter(
    override val annotations: Annotations,
    override val isDefault: Boolean,
    override val isExternal: Boolean,
    override val isInline: Boolean
) : PropertyAccessor {
    companion object {
        val DEFAULT_NO_ANNOTATIONS = Getter(Annotations.EMPTY, isDefault = true, isExternal = false, isInline = false)

        fun PropertyGetterDescriptor.toGetter() =
            if (isDefault && annotations.isEmpty())
                DEFAULT_NO_ANNOTATIONS
            else
                Getter(annotations, isDefault, isExternal, isInline)
    }
}

data class Setter(
    override val annotations: Annotations,
    val parameterAnnotations: Annotations,
    val visibility: Visibility,
    override val isDefault: Boolean,
    override val isExternal: Boolean,
    override val isInline: Boolean
) : PropertyAccessor {
    companion object {
        fun createDefaultNoAnnotations(visibility: Visibility) = Setter(
            Annotations.EMPTY,
            Annotations.EMPTY,
            visibility,
            isDefault = visibility == Visibilities.PUBLIC,
            isExternal = false,
            isInline = false
        )

        fun PropertySetterDescriptor.toSetter() = Setter(
            annotations,
            valueParameters.single().annotations,
            visibility,
            isDefault,
            isExternal,
            isInline
        )
    }
}
