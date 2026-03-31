"""Batch-evaluate winner_network.json models and pick the most stable candidate.

This script ranks models primarily by first-pipe pass stability, then by score.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Sequence

from simulator import Difficulty, FlappySimulator, OBSERVATION_ORDER


SUPPORTED_ACTIVATIONS = {"identity", "tanh"}


@dataclass(frozen=True)
class ModelNode:
    idx: int
    bias: float
    response: float
    activation: str


@dataclass(frozen=True)
class ModelEdge:
    src_idx: int
    dst_idx: int
    weight: float


@dataclass(frozen=True)
class WinnerModel:
    path: Path
    input_order: List[str]
    output_order: List[str]
    input_node_indices: List[int]
    output_node_indices: List[int]
    evaluation_order: List[int]
    nodes: List[ModelNode]
    edges: List[ModelEdge]
    flap_threshold: float


@dataclass(frozen=True)
class EpisodeResult:
    score: int
    passed_first_pipe: bool
    survived_steps: int


@dataclass(frozen=True)
class CandidateMetrics:
    path: Path
    episodes: int
    pass_count: int
    first_pipe_pass_rate: float
    first_pipe_pass_wilson_low95: float
    mean_score: float
    std_score: float
    mean_survived_steps: float
    threshold_used: float


class FlattenedNetworkEvaluator:
    def __init__(self, model: WinnerModel) -> None:
        self.model = model
        self.nodes_by_idx: Dict[int, ModelNode] = {node.idx: node for node in model.nodes}
        self.incoming_edges_by_dst: Dict[int, List[ModelEdge]] = {}
        for edge in model.edges:
            self.incoming_edges_by_dst.setdefault(edge.dst_idx, []).append(edge)

        self.max_node_idx = max((node.idx for node in model.nodes), default=0)

        if len(model.input_order) != len(model.input_node_indices):
            raise ValueError("input_order and input_node_indices length mismatch")
        if len(model.output_order) != len(model.output_node_indices):
            raise ValueError("output_order and output_node_indices length mismatch")

    def evaluate(self, ordered_inputs: Sequence[float]) -> List[float]:
        if len(ordered_inputs) != len(self.model.input_node_indices):
            raise ValueError(
                f"Expected {len(self.model.input_node_indices)} inputs, got {len(ordered_inputs)}"
            )

        values = [0.0 for _ in range(self.max_node_idx + 1)]

        for i, node_idx in enumerate(self.model.input_node_indices):
            if 0 <= node_idx < len(values):
                values[node_idx] = float(ordered_inputs[i])

        for node_idx in self.model.evaluation_order:
            node = self.nodes_by_idx.get(node_idx)
            if node is None:
                continue

            total = node.bias
            for edge in self.incoming_edges_by_dst.get(node_idx, []):
                if 0 <= edge.src_idx < len(values):
                    total += values[edge.src_idx] * edge.weight

            values[node_idx] = _activate(node.activation, total * node.response)

        outputs: List[float] = []
        for output_idx in self.model.output_node_indices:
            if 0 <= output_idx < len(values):
                outputs.append(values[output_idx])
            else:
                outputs.append(0.0)
        return outputs


def _activate(name: str, x: float) -> float:
    normalized = name.strip().lower()
    if normalized == "tanh":
        return math.tanh(x)
    return x


def _extract_flap_score(outputs: Sequence[float], output_order: Sequence[str]) -> float:
    if not outputs:
        return 0.0
    try:
        flap_idx = list(output_order).index("flap_score")
    except ValueError:
        flap_idx = 0
    if 0 <= flap_idx < len(outputs):
        return float(outputs[flap_idx])
    return float(outputs[0])


def _wilson_lower_bound(successes: int, total: int, z: float = 1.96) -> float:
    if total <= 0:
        return 0.0
    p_hat = successes / total
    denominator = 1.0 + (z * z / total)
    center = p_hat + (z * z / (2.0 * total))
    margin = z * math.sqrt((p_hat * (1.0 - p_hat) / total) + (z * z / (4.0 * total * total)))
    return max(0.0, (center - margin) / denominator)


def _mean(values: Sequence[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def _std(values: Sequence[float], mean_value: float) -> float:
    if not values:
        return 0.0
    variance = sum((v - mean_value) ** 2 for v in values) / len(values)
    return math.sqrt(variance)


def load_model(path: Path, threshold_override: float | None) -> WinnerModel:
    payload = json.loads(path.read_text(encoding="utf-8"))

    input_order = payload["input_order"]
    output_order = payload["output_order"]
    input_node_indices = payload["input_node_indices"]
    output_node_indices = payload["output_node_indices"]
    evaluation_order = payload["evaluation_order"]

    nodes = [
        ModelNode(
            idx=int(node["idx"]),
            bias=float(node.get("bias", 0.0)),
            response=float(node.get("response", 1.0)),
            activation=str(node.get("activation", "identity")),
        )
        for node in payload["nodes_compact"]
    ]

    edges = [
        ModelEdge(
            src_idx=int(edge["src_idx"]),
            dst_idx=int(edge["dst_idx"]),
            weight=float(edge.get("weight", 0.0)),
        )
        for edge in payload["edges_compact"]
    ]

    unsupported = {
        node.activation.strip().lower()
        for node in nodes
        if node.activation.strip().lower() not in SUPPORTED_ACTIVATIONS
    }
    if unsupported:
        raise ValueError(f"Unsupported activations: {sorted(unsupported)}")

    metadata = payload.get("metadata", {})
    metadata_threshold = metadata.get("flap_threshold")

    if threshold_override is not None:
        threshold = threshold_override
    elif isinstance(metadata_threshold, (int, float)):
        threshold = float(metadata_threshold)
    else:
        threshold = 0.5

    return WinnerModel(
        path=path,
        input_order=list(input_order),
        output_order=list(output_order),
        input_node_indices=[int(x) for x in input_node_indices],
        output_node_indices=[int(x) for x in output_node_indices],
        evaluation_order=[int(x) for x in evaluation_order],
        nodes=nodes,
        edges=edges,
        flap_threshold=max(0.0, min(1.0, threshold)),
    )


def run_episode(
    evaluator: FlattenedNetworkEvaluator,
    model: WinnerModel,
    difficulty: Difficulty,
    width: float,
    height: float,
    max_steps: int,
    seed: int,
) -> EpisodeResult:
    simulator = FlappySimulator(
        screen_width_px=width,
        screen_height_px=height,
        difficulty=difficulty,
        seed=seed,
    )

    for step in range(max_steps):
        obs = simulator.build_observation()
        named = {name: obs[idx] for idx, name in enumerate(OBSERVATION_ORDER)}
        ordered_inputs = [named.get(name, 0.0) for name in model.input_order]

        outputs = evaluator.evaluate(ordered_inputs)
        flap_score = _extract_flap_score(outputs, model.output_order)
        should_flap = flap_score > model.flap_threshold

        alive = simulator.step(flap=should_flap)
        if not alive:
            return EpisodeResult(
                score=simulator.state.score,
                passed_first_pipe=simulator.state.score >= 1,
                survived_steps=step + 1,
            )

    return EpisodeResult(
        score=simulator.state.score,
        passed_first_pipe=simulator.state.score >= 1,
        survived_steps=max_steps,
    )


def score_candidate(
    path: Path,
    episodes: int,
    base_seed: int,
    max_steps: int,
    difficulty: Difficulty,
    width: float,
    height: float,
    threshold_override: float | None,
) -> CandidateMetrics:
    model = load_model(path, threshold_override=threshold_override)
    evaluator = FlattenedNetworkEvaluator(model)

    results: List[EpisodeResult] = []
    for episode_idx in range(episodes):
        episode_seed = base_seed + (episode_idx * 7919)
        result = run_episode(
            evaluator=evaluator,
            model=model,
            difficulty=difficulty,
            width=width,
            height=height,
            max_steps=max_steps,
            seed=episode_seed,
        )
        results.append(result)

    pass_count = sum(1 for r in results if r.passed_first_pipe)
    pass_rate = pass_count / episodes if episodes > 0 else 0.0
    score_values = [float(r.score) for r in results]
    mean_score = _mean(score_values)
    std_score = _std(score_values, mean_score)
    mean_survived_steps = _mean([float(r.survived_steps) for r in results])

    return CandidateMetrics(
        path=path,
        episodes=episodes,
        pass_count=pass_count,
        first_pipe_pass_rate=pass_rate,
        first_pipe_pass_wilson_low95=_wilson_lower_bound(pass_count, episodes),
        mean_score=mean_score,
        std_score=std_score,
        mean_survived_steps=mean_survived_steps,
        threshold_used=model.flap_threshold,
    )


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    default_assets_path = script_dir.parent.parent / "app" / "src" / "main" / "assets" / "winner_network.json"

    parser = argparse.ArgumentParser(description="Evaluate multiple winner_network.json and rank by stability")
    parser.add_argument(
        "--search-root",
        type=Path,
        default=script_dir,
        help="Root folder to search for candidate JSON files",
    )
    parser.add_argument(
        "--glob",
        type=str,
        default="**/winner_network.json",
        help="Glob pattern relative to --search-root",
    )
    parser.add_argument(
        "--model",
        type=Path,
        nargs="*",
        default=[],
        help="Optional explicit model path(s), evaluated in addition to glob results",
    )
    parser.add_argument("--episodes", type=int, default=120, help="Episodes per model")
    parser.add_argument("--max-steps", type=int, default=2700)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--difficulty", choices=["easy", "normal", "hard"], default="normal")
    parser.add_argument("--width", type=float, default=1080.0)
    parser.add_argument("--height", type=float, default=1920.0)
    parser.add_argument("--threshold-override", type=float, default=None)
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument(
        "--report-json",
        type=Path,
        default=script_dir / "artifacts" / "model_selection_report.json",
    )
    parser.add_argument(
        "--report-csv",
        type=Path,
        default=script_dir / "artifacts" / "model_selection_report.csv",
    )
    parser.add_argument("--copy-best", action="store_true")
    parser.add_argument(
        "--copy-dest",
        type=Path,
        default=default_assets_path,
        help="Destination path when --copy-best is used",
    )
    return parser.parse_args()


def discover_candidates(search_root: Path, pattern: str, explicit: Iterable[Path]) -> List[Path]:
    candidates = {path.resolve() for path in search_root.glob(pattern) if path.is_file()}
    for path in explicit:
        resolved = path.resolve()
        if resolved.is_file():
            candidates.add(resolved)
    return sorted(candidates)


def write_reports(metrics: List[CandidateMetrics], json_path: Path, csv_path: Path) -> None:
    json_path.parent.mkdir(parents=True, exist_ok=True)
    csv_path.parent.mkdir(parents=True, exist_ok=True)

    rows = [
        {
            "path": str(m.path),
            "episodes": m.episodes,
            "pass_count": m.pass_count,
            "first_pipe_pass_rate": round(m.first_pipe_pass_rate, 6),
            "first_pipe_pass_wilson_low95": round(m.first_pipe_pass_wilson_low95, 6),
            "mean_score": round(m.mean_score, 6),
            "std_score": round(m.std_score, 6),
            "mean_survived_steps": round(m.mean_survived_steps, 6),
            "threshold_used": round(m.threshold_used, 6),
        }
        for m in metrics
    ]

    json_path.write_text(json.dumps(rows, indent=2), encoding="utf-8")

    fieldnames = [
        "path",
        "episodes",
        "pass_count",
        "first_pipe_pass_rate",
        "first_pipe_pass_wilson_low95",
        "mean_score",
        "std_score",
        "mean_survived_steps",
        "threshold_used",
    ]
    with csv_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def rank_metrics(metrics: List[CandidateMetrics]) -> List[CandidateMetrics]:
    return sorted(
        metrics,
        key=lambda m: (
            m.first_pipe_pass_wilson_low95,
            m.first_pipe_pass_rate,
            m.mean_score,
            -m.std_score,
            m.mean_survived_steps,
        ),
        reverse=True,
    )


def print_summary(ranked: List[CandidateMetrics], top_k: int) -> None:
    print("=== Model Ranking (stable first-pipe pass first) ===")
    for idx, m in enumerate(ranked[: max(1, top_k)], start=1):
        print(
            f"{idx:02d}. {m.path} | pass_rate={m.first_pipe_pass_rate:.3f} "
            f"wilson_low95={m.first_pipe_pass_wilson_low95:.3f} "
            f"mean_score={m.mean_score:.2f} std={m.std_score:.2f} "
            f"mean_steps={m.mean_survived_steps:.1f} threshold={m.threshold_used:.3f}"
        )


def main() -> None:
    args = parse_args()

    if args.episodes <= 0:
        raise ValueError("--episodes must be > 0")
    if args.max_steps <= 0:
        raise ValueError("--max-steps must be > 0")

    threshold_override = args.threshold_override
    if threshold_override is not None:
        threshold_override = max(0.0, min(1.0, float(threshold_override)))

    candidates = discover_candidates(args.search_root, args.glob, args.model)
    if not candidates:
        raise FileNotFoundError("No winner_network.json candidates found")

    difficulty = Difficulty.from_string(args.difficulty)

    metrics: List[CandidateMetrics] = []
    errors: List[str] = []
    for candidate in candidates:
        try:
            metric = score_candidate(
                path=candidate,
                episodes=args.episodes,
                base_seed=args.seed,
                max_steps=args.max_steps,
                difficulty=difficulty,
                width=float(args.width),
                height=float(args.height),
                threshold_override=threshold_override,
            )
            metrics.append(metric)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"{candidate}: {exc}")

    if not metrics:
        print("No valid candidate models. Errors:")
        for item in errors:
            print(f"- {item}")
        raise SystemExit(2)

    ranked = rank_metrics(metrics)
    write_reports(ranked, args.report_json, args.report_csv)
    print_summary(ranked, args.top_k)

    if errors:
        print("\nSkipped invalid candidates:")
        for item in errors:
            print(f"- {item}")

    best = ranked[0]
    print(f"\nBest model: {best.path}")
    print(f"Reports: {args.report_json} | {args.report_csv}")

    if args.copy_best:
        args.copy_dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(best.path, args.copy_dest)
        print(f"Copied best model -> {args.copy_dest}")


if __name__ == "__main__":
    main()

