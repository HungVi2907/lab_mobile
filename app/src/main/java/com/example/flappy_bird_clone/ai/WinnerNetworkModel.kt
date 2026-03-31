package com.example.flappy_bird_clone.ai

data class WinnerNetworkMetadata(
    val flapThreshold: Float? = null,
)

data class WinnerNetworkNode(
    val idx: Int,
    val bias: Double,
    val response: Double,
    val activation: String,
)

data class WinnerNetworkEdge(
    val srcIdx: Int,
    val dstIdx: Int,
    val weight: Double,
)

data class WinnerNetworkModel(
    val metadata: WinnerNetworkMetadata,
    val inputOrder: List<String>,
    val outputOrder: List<String>,
    val inputNodeIndices: List<Int>,
    val outputNodeIndices: List<Int>,
    val evaluationOrder: List<Int>,
    val nodesCompact: List<WinnerNetworkNode>,
    val edgesCompact: List<WinnerNetworkEdge>,
)

