/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa

import com.google.common.collect.HashMultimap

class LogicSystem(private val context: DataFlowInferenceContext) {
    private fun <E> List<Set<E>>.intersectSets(): Set<E> = takeIf { isNotEmpty() }?.reduce { x, y -> x.intersect(y) } ?: emptySet()

    fun or(storages: Collection<Flow>): Flow {
        storages.singleOrNull()?.let {
            return it
        }
        val approvedFacts = mutableMapOf<RealDataFlowVariable, FirDataFlowInfo>()
        storages.map { it.approvedInfos.keys }
            .intersectSets()
            .forEach { variable ->
                val infos = storages.map { it.approvedInfos[variable]!! }
                if (infos.isNotEmpty()) {
                    approvedFacts[variable] = context.or(infos)
                }
            }


        val notApprovedFacts = HashMultimap.create<DataFlowVariable, ConditionalFirDataFlowInfo>()
        storages.map { it.conditionalInfos.keySet() }
            .intersectSets()
            .forEach { variable ->
                val infos = storages.map { it.conditionalInfos[variable] }.intersectSets()
                if (infos.isNotEmpty()) {
                    notApprovedFacts.putAll(variable, infos)
                }
            }

        return Flow(approvedFacts, notApprovedFacts)
    }

    fun andForVerifiedFacts(
        left: Map<RealDataFlowVariable, FirDataFlowInfo>?,
        right: Map<RealDataFlowVariable, FirDataFlowInfo>?
    ): Map<RealDataFlowVariable, FirDataFlowInfo>? {
        if (left.isNullOrEmpty()) return right
        if (right.isNullOrEmpty()) return left

        val map = mutableMapOf<RealDataFlowVariable, FirDataFlowInfo>()
        for (variable in left.keys.union(right.keys)) {
            val leftInfo = left[variable]
            val rightInfo = right[variable]
            map[variable] = context.and(listOfNotNull(leftInfo, rightInfo))
        }
        return map
    }

    fun orForVerifiedFacts(
        left: Map<RealDataFlowVariable, FirDataFlowInfo>?,
        right: Map<RealDataFlowVariable, FirDataFlowInfo>?
    ): Map<RealDataFlowVariable, FirDataFlowInfo>? {
        if (left.isNullOrEmpty() || right.isNullOrEmpty()) return null
        val map = mutableMapOf<RealDataFlowVariable, FirDataFlowInfo>()
        for (variable in left.keys.intersect(right.keys)) {
            val leftInfo = left[variable]!!
            val rightInfo = right[variable]!!
            map[variable] = context.or(listOf(leftInfo, rightInfo))
        }
        return map
    }

    fun approveFactsInsideFlow(variable: DataFlowVariable, condition: Condition, flow: Flow): Pair<Flow, Collection<RealDataFlowVariable>> {
        val notApprovedFacts: Set<ConditionalFirDataFlowInfo> = flow.conditionalInfos[variable]
        if (notApprovedFacts.isEmpty()) {
            return flow to emptyList()
        }
        @Suppress("NAME_SHADOWING")
        val flow = flow.copyForBuilding()
        val newFacts = HashMultimap.create<RealDataFlowVariable, FirDataFlowInfo>()
        notApprovedFacts.forEach {
            if (it.condition == condition) {
                newFacts.put(it.variable, it.info)
            }
        }
        val updatedReceivers = mutableSetOf<RealDataFlowVariable>()

        newFacts.asMap().forEach { (variable, infos) ->
            @Suppress("NAME_SHADOWING")
            val infos = ArrayList(infos)
            flow.approvedInfos[variable]?.let {
                infos.add(it)
            }
            flow.approvedInfos[variable] = context.and(infos)
            if (variable.isThisReference) {
                updatedReceivers += variable
            }
        }
        return flow to updatedReceivers
    }

    fun approveFact(variable: DataFlowVariable, condition: Condition, flow: Flow): MutableMap<RealDataFlowVariable, FirDataFlowInfo> {
        val notApprovedFacts: Set<ConditionalFirDataFlowInfo> = flow.conditionalInfos[variable]
        if (notApprovedFacts.isEmpty()) {
            return mutableMapOf()
        }
        val newFacts = HashMultimap.create<RealDataFlowVariable, FirDataFlowInfo>()
        notApprovedFacts.forEach {
            if (it.condition == condition) {
                newFacts.put(it.variable, it.info)
            }
        }
        return newFacts.asMap().mapValuesTo(mutableMapOf()) { (_, infos) -> context.and(infos) }
    }
}