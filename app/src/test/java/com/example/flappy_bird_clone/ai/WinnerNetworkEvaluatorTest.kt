package com.example.flappy_bird_clone.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tanh

class WinnerNetworkEvaluatorTest {

    @Test
    fun evaluate_appliesWeightedSumAndActivationInEvaluationOrder() {
        val model = WinnerNetworkModel(
            metadata = WinnerNetworkMetadata(),
            inputOrder = listOf("a", "b"),
            outputOrder = listOf("flap_score"),
            inputNodeIndices = listOf(0, 1),
            outputNodeIndices = listOf(2),
            evaluationOrder = listOf(2),
            nodesCompact = listOf(
                WinnerNetworkNode(idx = 0, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 1, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 2, bias = 0.1, response = 1.0, activation = "tanh"),
            ),
            edgesCompact = listOf(
                WinnerNetworkEdge(srcIdx = 0, dstIdx = 2, weight = 1.0),
                WinnerNetworkEdge(srcIdx = 1, dstIdx = 2, weight = 1.0),
            ),
        )

        val evaluator = WinnerNetworkEvaluator(model)
        val outputs = evaluator.evaluate(floatArrayOf(0.2f, 0.3f))

        val expected = tanh(0.6f)
        assertEquals(expected, outputs[0], 1e-5f)
    }

    @Test
    fun evaluate_unknownActivationFallsBackToIdentity() {
        val model = WinnerNetworkModel(
            metadata = WinnerNetworkMetadata(),
            inputOrder = listOf("a"),
            outputOrder = listOf("flap_score"),
            inputNodeIndices = listOf(0),
            outputNodeIndices = listOf(1),
            evaluationOrder = listOf(1),
            nodesCompact = listOf(
                WinnerNetworkNode(idx = 0, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 1, bias = 0.0, response = 1.0, activation = "relu_like"),
            ),
            edgesCompact = listOf(
                WinnerNetworkEdge(srcIdx = 0, dstIdx = 1, weight = 2.0),
            ),
        )

        val evaluator = WinnerNetworkEvaluator(model)
        val outputs = evaluator.evaluate(floatArrayOf(0.25f))

        assertEquals(0.5f, outputs[0], 1e-5f)
    }

    @Test
    fun evaluate_requiresInputCountToMatchModel() {
        val model = WinnerNetworkModel(
            metadata = WinnerNetworkMetadata(),
            inputOrder = listOf("a", "b"),
            outputOrder = listOf("flap_score"),
            inputNodeIndices = listOf(0, 1),
            outputNodeIndices = listOf(2),
            evaluationOrder = listOf(2),
            nodesCompact = listOf(
                WinnerNetworkNode(idx = 0, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 1, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 2, bias = 0.0, response = 1.0, activation = "identity"),
            ),
            edgesCompact = emptyList(),
        )

        val evaluator = WinnerNetworkEvaluator(model)

        val error = runCatching { evaluator.evaluate(floatArrayOf(1f)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

