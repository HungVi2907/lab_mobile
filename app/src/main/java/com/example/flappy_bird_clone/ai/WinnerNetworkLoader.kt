package com.example.flappy_bird_clone.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object WinnerNetworkLoader {

    fun loadFromAssets(context: Context, assetPath: String): Result<WinnerNetworkModel> {
        return runCatching {
            val jsonText = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            parse(jsonText)
        }
    }

    fun parse(jsonText: String): WinnerNetworkModel {
        val root = JSONObject(jsonText)

        val metadataJson = root.optJSONObject("metadata")
        val metadata = WinnerNetworkMetadata(
            flapThreshold = metadataJson?.optDouble("flap_threshold")
                ?.takeUnless { it.isNaN() }
                ?.toFloat(),
        )

        val inputOrder = root.getJSONArray("input_order").toStringList()
        val outputOrder = root.getJSONArray("output_order").toStringList()
        val inputNodeIndices = root.getJSONArray("input_node_indices").toIntList()
        val outputNodeIndices = root.getJSONArray("output_node_indices").toIntList()
        val evaluationOrder = root.getJSONArray("evaluation_order").toIntList()

        val nodes = root.getJSONArray("nodes_compact").toNodeList()
        val edges = root.getJSONArray("edges_compact").toEdgeList()

        return WinnerNetworkModel(
            metadata = metadata,
            inputOrder = inputOrder,
            outputOrder = outputOrder,
            inputNodeIndices = inputNodeIndices,
            outputNodeIndices = outputNodeIndices,
            evaluationOrder = evaluationOrder,
            nodesCompact = nodes,
            edgesCompact = edges,
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return List(length()) { index -> getString(index) }
    }

    private fun JSONArray.toIntList(): List<Int> {
        return List(length()) { index -> getInt(index) }
    }

    private fun JSONArray.toNodeList(): List<WinnerNetworkNode> {
        return List(length()) { index ->
            val node = getJSONObject(index)
            WinnerNetworkNode(
                idx = node.getInt("idx"),
                bias = node.optDouble("bias", 0.0),
                response = node.optDouble("response", 1.0),
                activation = node.optString("activation", "identity"),
            )
        }
    }

    private fun JSONArray.toEdgeList(): List<WinnerNetworkEdge> {
        return List(length()) { index ->
            val edge = getJSONObject(index)
            WinnerNetworkEdge(
                srcIdx = edge.getInt("src_idx"),
                dstIdx = edge.getInt("dst_idx"),
                weight = edge.optDouble("weight", 0.0),
            )
        }
    }
}

