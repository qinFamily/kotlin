/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer

import junit.framework.TestCase.fail
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.commonizer.mergedtree.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KCallable

fun assertIsDirectory(file: File) {
    if (!file.isDirectory)
        fail("Not a directory: $file")
}

@ExperimentalContracts
fun assertCommonizationPerformed(result: CommonizationResult) {
    contract {
        returns() implies (result is CommonizationPerformed)
    }

    if (result !is CommonizationPerformed)
        fail("$result is not instance of ${CommonizationPerformed::class}")
}

@ExperimentalContracts
fun assertModulesAreEqual(expected: ModuleDescriptor, actual: ModuleDescriptor, designatorMessage: String) {
    val visitor = ComparingDeclarationsVisitor(designatorMessage)
    val context = visitor.Context(actual)

    expected.accept(visitor, context)
}

@ExperimentalContracts
private class ComparingDeclarationsVisitor(
    val designatorMessage: String
) : DeclarationDescriptorVisitor<Unit, ComparingDeclarationsVisitor.Context> {

    inner class Context private constructor(
        private val actual: DeclarationDescriptor?,
        private val path: List<String>
    ) {
        constructor(actual: DeclarationDescriptor?) : this(actual, listOf(actual.toString()))

        fun nextLevel(nextActual: DeclarationDescriptor?) = Context(nextActual, path + nextActual.toString())

        fun nextLevel(customPathElement: String) = Context(actual, path + customPathElement)

        inline fun <reified T> getActualAs() = actual as T

        override fun toString() =
            """
            |Context: ${this@ComparingDeclarationsVisitor.designatorMessage}
            |Path: ${path.joinToString(separator = " ->\n\t")}"
            """.trimMargin()
    }

    override fun visitModuleDeclaration(expected: ModuleDescriptor, context: Context) {
        val actual = context.getActualAs<ModuleDescriptor>()

        context.assertEquals(expected.name, actual.name, "module names")

        fun collectPackageMemberScopes(module: ModuleDescriptor): Map<FqName, MemberScope> = mutableMapOf<FqName, MemberScope>().also {
            module.collectNonEmptyPackageMemberScopes { packageFqName, memberScope ->
                if (memberScope.getContributedDescriptors().isNotEmpty())
                    it[packageFqName] = memberScope
            }
        }

        val expectedPackageMemberScopes = collectPackageMemberScopes(expected)
        val actualPackageMemberScopes = collectPackageMemberScopes(actual)

        context.assertEquals(expectedPackageMemberScopes.keys, actualPackageMemberScopes.keys, "sets of packages")

        for (packageFqName in expectedPackageMemberScopes.keys) {
            val expectedMemberScope = expectedPackageMemberScopes.getValue(packageFqName)
            val actualMemberScope = actualPackageMemberScopes.getValue(packageFqName)

            visitMemberScopes(expectedMemberScope, actualMemberScope, context.nextLevel("package member scope [$packageFqName]"))
        }
    }

    fun visitMemberScopes(expected: MemberScope, actual: MemberScope, context: Context) {
        fun collectProperties(memberScope: MemberScope): Map<PropertyKey, PropertyDescriptor> =
            mutableMapOf<PropertyKey, PropertyDescriptor>().also {
                memberScope.collectProperties { propertyKey, property ->
                    it[propertyKey] = property
                }
            }

        val expectedProperties = collectProperties(expected)
        val actualProperties = collectProperties(actual)

        context.assertEquals(expectedProperties.keys, actualProperties.keys, "sets of properties")

        expectedProperties.forEach { (propertyKey, expectedProperty) ->
            val actualProperty = actualProperties.getValue(propertyKey)
            expectedProperty.accept(this, context.nextLevel(actualProperty))
        }

        fun collectFunctions(memberScope: MemberScope): Map<FunctionKey, SimpleFunctionDescriptor> =
            mutableMapOf<FunctionKey, SimpleFunctionDescriptor>().also {
                memberScope.collectFunctions { functionKey, function ->
                    it[functionKey] = function
                }
            }

        val expectedFunctions = collectFunctions(expected)
        val actualFunctions = collectFunctions(actual)

        context.assertEquals(expectedFunctions.keys, actualFunctions.keys, "sets of functions")

        expectedFunctions.forEach { (functionKey, expectedFunction) ->
            val actualFunction = actualFunctions.getValue(functionKey)
            expectedFunction.accept(this, context.nextLevel(actualFunction))
        }

        // FIXME: traverse the rest - classes, typealiases
    }


    override fun visitFunctionDescriptor(expected: FunctionDescriptor, context: Context) {
        @Suppress("NAME_SHADOWING")
        val expected = expected as SimpleFunctionDescriptor
        val actual = context.getActualAs<SimpleFunctionDescriptor>()

        visitAnnotations(expected.annotations, actual.annotations, context.nextLevel("Function annotations"))
        context.assertFieldsEqual(expected::getName, actual::getName)
        context.assertFieldsEqual(expected::getVisibility, actual::getVisibility)
        context.assertFieldsEqual(expected::getModality, actual::getModality)
        context.assertFieldsEqual(expected::getKind, actual::getKind)
        context.assertFieldsEqual(expected::isOperator, actual::isOperator)
        context.assertFieldsEqual(expected::isInfix, actual::isInfix)
        context.assertFieldsEqual(expected::isInline, actual::isInline)
        context.assertFieldsEqual(expected::isTailrec, actual::isTailrec)
        context.assertFieldsEqual(expected::isSuspend, actual::isSuspend)
        context.assertFieldsEqual(expected::isExternal, actual::isExternal)
        context.assertFieldsEqual(expected::isExpect, actual::isExpect)
        context.assertFieldsEqual(expected::isActual, actual::isActual)

        visitType(expected.returnType, actual.returnType, context.nextLevel("Function type"))

        visitValueParameterDescriptorList(expected.valueParameters, actual.valueParameters, context.nextLevel("Function value parameters"))

        visitReceiverParameterDescriptor(expected.extensionReceiverParameter, context.nextLevel(actual.extensionReceiverParameter))
        visitReceiverParameterDescriptor(expected.dispatchReceiverParameter, context.nextLevel(actual.dispatchReceiverParameter))
    }

    fun visitValueParameterDescriptorList(
        expected: List<ValueParameterDescriptor>,
        actual: List<ValueParameterDescriptor>,
        context: Context
    ) {
        context.assertEquals(expected.size, actual.size, "Size of value parameters list")

        expected.forEachIndexed { index, expectedParam ->
            val actualParam = actual[index]
            expectedParam.accept(this, context.nextLevel(actualParam))
        }
    }

    override fun visitValueParameterDescriptor(expected: ValueParameterDescriptor, context: Context) {
        val actual = context.getActualAs<ValueParameterDescriptor>()

        visitAnnotations(expected.annotations, actual.annotations, context.nextLevel("Value parameter annotations"))
        context.assertEquals(expected.name, actual.name, "Name")
        context.assertEquals(expected.index, actual.index, "Index")
        context.assertEquals(expected.declaresDefaultValue(), actual.declaresDefaultValue(), "Declares default value")
        context.assertEquals(expected.isCrossinline, actual.isCrossinline, "Crossinline")
        context.assertEquals(expected.isNoinline, actual.isNoinline, "Noinline")
        visitType(expected.type, actual.type, context.nextLevel("Value parameter type"))
        visitType(expected.varargElementType, actual.varargElementType, context.nextLevel("Value parameter vararg element type"))
    }

    override fun visitTypeParameterDescriptor(expected: TypeParameterDescriptor, context: Context) {
        TODO("not implemented")
    }

    override fun visitClassDescriptor(expected: ClassDescriptor, context: Context) {
        TODO("not implemented")
    }

    override fun visitConstructorDescriptor(expected: ConstructorDescriptor, context: Context) {
        TODO("not implemented")
    }

    override fun visitTypeAliasDescriptor(expected: TypeAliasDescriptor, context: Context) {
        TODO("not implemented")
    }

    override fun visitPropertyDescriptor(expected: PropertyDescriptor, context: Context) {
        val actual = context.getActualAs<PropertyDescriptor>()

        visitAnnotations(expected.annotations, actual.annotations, context.nextLevel("Property annotations"))
        context.assertFieldsEqual(expected::getName, actual::getName)
        context.assertFieldsEqual(expected::getVisibility, actual::getVisibility)
        context.assertFieldsEqual(expected::getModality, actual::getModality)
        context.assertFieldsEqual(expected::isVar, actual::isVar)
        context.assertFieldsEqual(expected::getKind, actual::getKind)
        context.assertFieldsEqual(expected::isLateInit, actual::isLateInit)
        context.assertFieldsEqual(expected::isConst, actual::isConst)
        context.assertFieldsEqual(expected::isExternal, actual::isExternal)
        context.assertFieldsEqual(expected::isExpect, actual::isExpect)
        context.assertFieldsEqual(expected::isActual, actual::isActual)
        @Suppress("DEPRECATION")
        context.assertFieldsEqual(expected::isDelegated, actual::isDelegated)
        visitAnnotations(
            expected.delegateField?.annotations,
            actual.delegateField?.annotations,
            context.nextLevel("Property delegate field annotations")
        )
        visitAnnotations(
            expected.backingField?.annotations,
            actual.backingField?.annotations,
            context.nextLevel("Property backing field annotations")
        )
        context.assertEquals(expected.compileTimeInitializer.isNull(), actual.compileTimeInitializer.isNull(), "compile-time initializers")
        visitType(expected.type, actual.type, context.nextLevel("Property type"))

        visitPropertyGetterDescriptor(expected.getter, context.nextLevel(actual.getter))
        visitPropertySetterDescriptor(expected.setter, context.nextLevel(actual.setter))

        visitReceiverParameterDescriptor(expected.extensionReceiverParameter, context.nextLevel(actual.extensionReceiverParameter))
        visitReceiverParameterDescriptor(expected.dispatchReceiverParameter, context.nextLevel(actual.dispatchReceiverParameter))
    }

    override fun visitPropertyGetterDescriptor(expected: PropertyGetterDescriptor?, context: Context) {
        val actual = context.getActualAs<PropertyGetterDescriptor?>()
        if (expected === actual) return

        check(actual != null && expected != null)

        visitAnnotations(expected.annotations, actual.annotations, context.nextLevel("Property getter annotations"))
        context.assertFieldsEqual(expected::isDefault, actual::isDefault)
        context.assertFieldsEqual(expected::isExternal, actual::isExternal)
        context.assertFieldsEqual(expected::isInline, actual::isInline)
    }

    override fun visitPropertySetterDescriptor(expected: PropertySetterDescriptor?, context: Context) {
        val actual = context.getActualAs<PropertySetterDescriptor?>()
        if (expected === actual) return

        check(actual != null && expected != null)

        visitAnnotations(expected.annotations, actual.annotations, context.nextLevel("Property setter annotations"))
        context.assertFieldsEqual(expected::isDefault, actual::isDefault)
        context.assertFieldsEqual(expected::isExternal, actual::isExternal)
        context.assertFieldsEqual(expected::isInline, actual::isInline)
        context.assertFieldsEqual(expected::getVisibility, actual::getVisibility)
        visitAnnotations(
            expected.valueParameters.single().annotations,
            actual.valueParameters.single().annotations,
            context.nextLevel("Property setter value parameter annotations")
        )
    }

    override fun visitReceiverParameterDescriptor(expected: ReceiverParameterDescriptor?, context: Context) {
        val actual = context.getActualAs<ReceiverParameterDescriptor?>()
        if (expected === actual) return

        check(actual != null && expected != null)

        visitType(expected.type, actual.type, context.nextLevel("Receiver parameter type"))
        visitAnnotations(expected.annotations, actual.annotations, context.nextLevel("Receiver parameter annotations"))
    }


    private fun visitAnnotations(expected: Annotations?, actual: Annotations?, context: Context) {
        if (expected === actual) return

        val expectedAnnotationFqNames = (expected ?: Annotations.EMPTY).map { it.fqName }.toSet()
        val actualAnnotationFqNames = (actual ?: Annotations.EMPTY).map { it.fqName }.toSet()

        context.assertEquals(expectedAnnotationFqNames, actualAnnotationFqNames, "annotations")
    }

    private fun visitType(expected: KotlinType?, actual: KotlinType?, context: Context) {
        if (expected === actual) return

        check(actual != null && expected != null)

        val expectedUnwrapped = expected.unwrap()
        val actualUnwrapped = actual.unwrap()

        if (expectedUnwrapped === actualUnwrapped) return

        val expectedFqName = expectedUnwrapped.constructor.declarationDescriptor!!.fqNameSafe
        val actualFqName = actualUnwrapped.constructor.declarationDescriptor!!.fqNameSafe

        context.assertEquals(expectedFqName, actualFqName, "type FQN")
    }

    private fun <T> Context.assertEquals(expected: T?, actual: T?, subject: String) {
        if (expected != actual)
            fail(
                buildString {
                    append("Comparing $subject:\n")
                    append("$expected is not equal to $actual\n")
                    append(this@assertEquals.toString())
                }
            )
    }

    private fun <T> Context.assertFieldsEqual(expected: KCallable<T>, actual: KCallable<T>) {
        val expectedValue = expected.call()
        val actualValue = actual.call()

        assertEquals(expectedValue, actualValue, "fields \"$expected\"")
    }

    override fun visitPackageViewDescriptor(expected: PackageViewDescriptor, context: Context) =
        fail("Comparison of package views not supported")

    override fun visitPackageFragmentDescriptor(expected: PackageFragmentDescriptor, context: Context) =
        fail("Comparison of package fragments not supported")

    override fun visitScriptDescriptor(expected: ScriptDescriptor, context: Context) =
        fail("Comparison of script descriptors not supported")

    override fun visitVariableDescriptor(expected: VariableDescriptor, context: Context) =
        fail("Comparison of variables not supported")
}
