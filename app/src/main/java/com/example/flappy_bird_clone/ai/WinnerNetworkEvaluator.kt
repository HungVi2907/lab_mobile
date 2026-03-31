package com.example.flappy_bird_clone.ai

import kotlin.math.tanh

class WinnerNetworkEvaluator(
    private val model: WinnerNetworkModel,
) {
    val inputOrder: List<String> = model.inputOrder
    val outputOrder: List<String> = model.outputOrder

    private val nodesByIdx: Map<Int, WinnerNetworkNode> = model.nodesCompact.associateBy { it.idx }
    private val incomingEdgesByDst: Map<Int, List<WinnerNetworkEdge>> = model.edgesCompact.groupBy { it.dstIdx }
    private val maxNodeIndex: Int = model.nodesCompact.maxOfOrNull { it.idx } ?: 0

    init {
        require(model.inputOrder.size == model.inputNodeIndices.size) {
            "input_order and input_node_indices size mismatch"
        }
        require(model.outputOrder.size == model.outputNodeIndices.size) {
            "output_order and output_node_indices size mismatch"
        }
    }

    fun evaluate(inputs: FloatArray): FloatArray {
        require(inputs.size == model.inputNodeIndices.size) {
            "Expected ${model.inputNodeIndices.size} inputs, got ${inputs.size}"
        }

        val values = FloatArray(maxNodeIndex + 1)

        model.inputNodeIndices.forEachIndexed { index, nodeIdx ->
            if (nodeIdx in values.indices) {
                values[nodeIdx] = inputs[index]
            }
        }

        model.evaluationOrder.forEach { nodeIdx ->
            val node = nodesByIdx[nodeIdx] ?: return@forEach
            val edgeInputs = incomingEdgesByDst[nodeIdx].orEmpty()

            var sum = node.bias.toFloat()
            edgeInputs.forEach { edge ->
                if (edge.srcIdx in values.indices) {
                    sum += values[edge.srcIdx] * edge.weight.toFloat()
                }
            }

            val activated = activate(node.activation, sum * node.response.toFloat())
            if (nodeIdx in values.indices) {
                values[nodeIdx] = activated
            }
        }

        return FloatArray(model.outputNodeIndices.size) { index ->
            val nodeIdx = model.outputNodeIndices[index]
            if (nodeIdx in values.indices) values[nodeIdx] else 0f
        }
    }

    private fun activate(activation: String, value: Float): Float {
        return when (activation.lowercase()) {
            "tanh" -> tanh(value)
            "identity" -> value
            else -> value
        }
    }
}

