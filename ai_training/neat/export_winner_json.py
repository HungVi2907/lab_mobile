"""Export NEAT winner genome to flattened feed-forward JSON for Android inference."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import pickle
from typing import Any, Dict, List, Tuple

import neat

from simulator import Difficulty, OBSERVATION_ORDER, OUTPUT_ORDER


def _activation_name(func: Any) -> str:
    raw = getattr(func, "__name__", "unknown")
    if raw.endswith("_activation"):
        return raw[: -len("_activation")]
    return raw


def _aggregation_name(func: Any) -> str:
    raw = getattr(func, "__name__", "unknown")
    if raw.endswith("_aggregation"):
        return raw[: -len("_aggregation")]
    return raw


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent

    parser = argparse.ArgumentParser(description="Export winner genome to flattened JSON")
    parser.add_argument("--config", type=Path, default=script_dir / "config-feedforward.txt")
    parser.add_argument("--winner", type=Path, default=script_dir / "artifacts" / "winner_genome.pkl")
    parser.add_argument("--out", type=Path, default=script_dir / "artifacts" / "winner_network.json")
    parser.add_argument("--difficulty", type=str, default="normal", choices=["easy", "normal", "hard"])
    parser.add_argument("--width", type=float, default=1080.0)
    parser.add_argument("--height", type=float, default=1920.0)
    parser.add_argument("--flap-threshold", type=float, default=0.5)
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    config = neat.config.Config(
        neat.DefaultGenome,
        neat.DefaultReproduction,
        neat.DefaultSpeciesSet,
        neat.DefaultStagnation,
        str(args.config),
    )

    with args.winner.open("rb") as handle:
        winner = pickle.load(handle)

    network = neat.nn.FeedForwardNetwork.create(winner, config)

    input_nodes = list(network.input_nodes)
    output_nodes = list(network.output_nodes)

    node_evals = list(network.node_evals)
    eval_targets = [node_id for node_id, *_ in node_evals]

    hidden_nodes = [
        node_id
        for node_id in eval_targets
        if node_id not in output_nodes
    ]

    ordered_nodes: List[int] = []
    for node_id in input_nodes + hidden_nodes + output_nodes:
        if node_id not in ordered_nodes:
            ordered_nodes.append(node_id)

    index_by_node = {node_id: idx for idx, node_id in enumerate(ordered_nodes)}

    eval_map: Dict[int, Dict[str, Any]] = {}
    for node_id, act_fn, agg_fn, bias, response, links in node_evals:
        eval_map[node_id] = {
            "activation": _activation_name(act_fn),
            "aggregation": _aggregation_name(agg_fn),
            "bias": float(bias),
            "response": float(response),
            "links": [(int(source), float(weight)) for source, weight in links],
        }

    nodes_compact: List[Dict[str, Any]] = []
    for node_id in ordered_nodes:
        if node_id in input_nodes:
            nodes_compact.append(
                {
                    "idx": index_by_node[node_id],
                    "id": int(node_id),
                    "kind": "input",
                    "bias": 0.0,
                    "response": 1.0,
                    "activation": "identity",
                    "aggregation": "sum",
                },
            )
            continue

        node_info = eval_map[node_id]
        nodes_compact.append(
            {
                "idx": index_by_node[node_id],
                "id": int(node_id),
                "kind": "output" if node_id in output_nodes else "hidden",
                "bias": node_info["bias"],
                "response": node_info["response"],
                "activation": node_info["activation"],
                "aggregation": node_info["aggregation"],
            },
        )

    edges_compact: List[Dict[str, Any]] = []
    for node_id in eval_targets:
        node_info = eval_map[node_id]
        dst_idx = index_by_node[node_id]
        for source_id, weight in node_info["links"]:
            if source_id not in index_by_node:
                continue
            edges_compact.append(
                {
                    "src_idx": index_by_node[source_id],
                    "dst_idx": dst_idx,
                    "weight": float(weight),
                },
            )

    edges_compact.sort(key=lambda item: (item["dst_idx"], item["src_idx"]))

    payload = {
        "metadata": {
            "format_version": 1,
            "exported_from": "neat-python",
            "difficulty": Difficulty.from_string(args.difficulty).value,
            "screen_width_px": args.width,
            "screen_height_px": args.height,
            "flap_threshold": args.flap_threshold,
        },
        "input_order": OBSERVATION_ORDER,
        "output_order": OUTPUT_ORDER,
        "input_node_indices": [index_by_node[node_id] for node_id in input_nodes],
        "output_node_indices": [index_by_node[node_id] for node_id in output_nodes],
        "evaluation_order": [index_by_node[node_id] for node_id in eval_targets],
        "nodes_compact": nodes_compact,
        "edges_compact": edges_compact,
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    print(f"Exported flattened network JSON to: {args.out}")
    print(f"Nodes: {len(nodes_compact)} | Edges: {len(edges_compact)}")


if __name__ == "__main__":
    main()
