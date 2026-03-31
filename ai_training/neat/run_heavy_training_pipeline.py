"""Run multiple heavy NEAT trainings, export candidates, then rank and optionally promote best model.

Example:
    python run_heavy_training_pipeline.py --runs 8 --gens 150 --pop 120 --episodes 3 --eval-episodes 160 --copy-best
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent

    parser = argparse.ArgumentParser(description="Heavy multi-run NEAT pipeline")
    parser.add_argument("--runs", type=int, default=8)
    parser.add_argument("--gens", type=int, default=150)
    parser.add_argument("--pop", type=int, default=120)
    parser.add_argument("--episodes", type=int, default=3, help="Episodes per genome during training")
    parser.add_argument("--max-steps", type=int, default=3200)
    parser.add_argument("--difficulty", choices=["easy", "normal", "hard"], default="normal")
    parser.add_argument("--width", type=float, default=1080.0)
    parser.add_argument("--height", type=float, default=1920.0)
    parser.add_argument("--flap-threshold", type=float, default=0.5)
    parser.add_argument("--seed-base", type=int, default=1000)
    parser.add_argument("--seed-step", type=int, default=1009)
    parser.add_argument("--eval-episodes", type=int, default=160)
    parser.add_argument("--eval-max-steps", type=int, default=3200)
    parser.add_argument("--eval-seed", type=int, default=42)
    parser.add_argument("--top-k", type=int, default=8)
    parser.add_argument("--threshold-override", type=float, default=None)
    parser.add_argument("--copy-best", action="store_true")
    parser.add_argument(
        "--candidates-dir",
        type=Path,
        default=script_dir / "artifacts" / "candidates",
    )
    parser.add_argument(
        "--copy-dest",
        type=Path,
        default=script_dir.parent.parent / "app" / "src" / "main" / "assets" / "winner_network.json",
    )
    parser.add_argument("--continue-on-error", action="store_true")
    return parser.parse_args()


def run_command(command: list[str]) -> None:
    print("[cmd]", " ".join(str(part) for part in command))
    subprocess.run(command, check=True)


def main() -> None:
    args = parse_args()
    if args.runs <= 0:
        raise ValueError("--runs must be > 0")

    script_dir = Path(__file__).resolve().parent
    train_script = script_dir / "train_neat.py"
    export_script = script_dir / "export_winner_json.py"
    eval_script = script_dir / "evaluate_winner_networks.py"

    args.candidates_dir.mkdir(parents=True, exist_ok=True)

    completed_runs = 0
    for run_idx in range(1, args.runs + 1):
        run_dir = args.candidates_dir / f"run_{run_idx:02d}"
        run_dir.mkdir(parents=True, exist_ok=True)

        seed = args.seed_base + ((run_idx - 1) * args.seed_step)
        winner_path = run_dir / "winner_genome.pkl"
        metadata_path = run_dir / "training_run.json"
        model_path = run_dir / "winner_network.json"

        train_cmd = [
            sys.executable,
            str(train_script),
            "--winner",
            str(winner_path),
            "--metadata",
            str(metadata_path),
            "--difficulty",
            args.difficulty,
            "--width",
            str(args.width),
            "--height",
            str(args.height),
            "--gens",
            str(args.gens),
            "--pop",
            str(args.pop),
            "--episodes",
            str(args.episodes),
            "--max-steps",
            str(args.max_steps),
            "--flap-threshold",
            str(args.flap_threshold),
            "--seed",
            str(seed),
        ]

        export_cmd = [
            sys.executable,
            str(export_script),
            "--winner",
            str(winner_path),
            "--out",
            str(model_path),
            "--difficulty",
            args.difficulty,
            "--width",
            str(args.width),
            "--height",
            str(args.height),
            "--flap-threshold",
            str(args.flap_threshold),
        ]

        try:
            print(f"\n=== Run {run_idx}/{args.runs} | seed={seed} ===")
            run_command(train_cmd)
            run_command(export_cmd)
            completed_runs += 1
        except subprocess.CalledProcessError as exc:
            print(f"Run {run_idx} failed with exit code {exc.returncode}")
            if not args.continue_on_error:
                raise

    if completed_runs == 0:
        raise RuntimeError("No training runs completed successfully")

    eval_cmd = [
        sys.executable,
        str(eval_script),
        "--search-root",
        str(args.candidates_dir),
        "--glob",
        "**/winner_network.json",
        "--episodes",
        str(args.eval_episodes),
        "--max-steps",
        str(args.eval_max_steps),
        "--seed",
        str(args.eval_seed),
        "--difficulty",
        args.difficulty,
        "--width",
        str(args.width),
        "--height",
        str(args.height),
        "--top-k",
        str(args.top_k),
    ]

    if args.threshold_override is not None:
        eval_cmd += ["--threshold-override", str(args.threshold_override)]

    if args.copy_best:
        eval_cmd += ["--copy-best", "--copy-dest", str(args.copy_dest)]

    print(f"\nCompleted runs: {completed_runs}/{args.runs}")
    run_command(eval_cmd)


if __name__ == "__main__":
    main()

