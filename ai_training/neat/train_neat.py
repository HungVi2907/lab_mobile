"""Train NEAT on the headless simulator mapped from Android game logic."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import pickle
from typing import Iterable, Tuple

import neat

from simulator import Difficulty, FlappySimulator, OBSERVATION_ORDER, OUTPUT_ORDER


class GenomeEvaluator:
    def __init__(
        self,
        difficulty: Difficulty,
        screen_width_px: float,
        screen_height_px: float,
        max_steps: int,
        episodes: int,
        flap_threshold: float,
        seed: int,
    ) -> None:
        self.difficulty = difficulty
        self.screen_width_px = screen_width_px
        self.screen_height_px = screen_height_px
        self.max_steps = max_steps
        self.episodes = episodes
        self.flap_threshold = flap_threshold
        self.seed = seed

    def eval_genomes(self, genomes: Iterable[Tuple[int, neat.DefaultGenome]], config: neat.Config) -> None:
        for genome_id, genome in genomes:
            net = neat.nn.FeedForwardNetwork.create(genome, config)
            scores = [
                self._run_episode(net, episode_seed=self.seed + (genome_id * 1009) + (episode * 7919))
                for episode in range(self.episodes)
            ]
            genome.fitness = sum(scores) / len(scores)

    def _run_episode(self, net: neat.nn.FeedForwardNetwork, episode_seed: int) -> float:
        simulator = FlappySimulator(
            screen_width_px=self.screen_width_px,
            screen_height_px=self.screen_height_px,
            difficulty=self.difficulty,
            seed=episode_seed,
        )

        fitness = 0.0
        for _ in range(self.max_steps):
            observation = simulator.build_observation()
            output = net.activate(observation)[0]
            should_flap = output > self.flap_threshold

            previous_score = simulator.state.score
            alive = simulator.step(flap=should_flap)

            # Reward survival and passing pipes, penalize spam flaps and death.
            fitness += 0.1
            if should_flap:
                fitness -= 0.01
            if simulator.state.score > previous_score:
                fitness += 5.0 * (simulator.state.score - previous_score)
            if not alive:
                fitness -= 1.0
                break

        fitness += simulator.state.score * 0.5
        return fitness


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent

    parser = argparse.ArgumentParser(description="Train NEAT for Flappy Bird simulator")
    parser.add_argument("--config", type=Path, default=script_dir / "config-feedforward.txt")
    parser.add_argument("--winner", type=Path, default=script_dir / "artifacts" / "winner_genome.pkl")
    parser.add_argument("--metadata", type=Path, default=script_dir / "artifacts" / "training_run.json")
    parser.add_argument("--difficulty", type=str, default="normal", choices=["easy", "normal", "hard"])
    parser.add_argument("--width", type=float, default=1080.0)
    parser.add_argument("--height", type=float, default=1920.0)
    parser.add_argument("--gens", type=int, default=50)
    parser.add_argument("--pop", type=int, default=50)
    parser.add_argument("--episodes", type=int, default=1)
    parser.add_argument("--max-steps", type=int, default=2700)
    parser.add_argument("--flap-threshold", type=float, default=0.5)
    parser.add_argument("--seed", type=int, default=42)
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
    config.pop_size = max(2, int(args.pop))

    population = neat.Population(config)
    population.add_reporter(neat.StdOutReporter(True))
    population.add_reporter(neat.StatisticsReporter())

    evaluator = GenomeEvaluator(
        difficulty=Difficulty.from_string(args.difficulty),
        screen_width_px=float(args.width),
        screen_height_px=float(args.height),
        max_steps=max(1, int(args.max_steps)),
        episodes=max(1, int(args.episodes)),
        flap_threshold=float(args.flap_threshold),
        seed=int(args.seed),
    )

    winner = population.run(evaluator.eval_genomes, max(1, int(args.gens)))

    args.winner.parent.mkdir(parents=True, exist_ok=True)
    with args.winner.open("wb") as handle:
        pickle.dump(winner, handle)

    run_metadata = {
        "difficulty": args.difficulty,
        "screen_width_px": args.width,
        "screen_height_px": args.height,
        "gens": args.gens,
        "pop": args.pop,
        "episodes": args.episodes,
        "max_steps": args.max_steps,
        "flap_threshold": args.flap_threshold,
        "seed": args.seed,
        "observation_order": OBSERVATION_ORDER,
        "output_order": OUTPUT_ORDER,
        "winner_path": str(args.winner),
        "winner_fitness": winner.fitness,
    }

    args.metadata.parent.mkdir(parents=True, exist_ok=True)
    args.metadata.write_text(json.dumps(run_metadata, indent=2), encoding="utf-8")

    print("Training done.")
    print(f"Winner fitness: {winner.fitness}")
    print(f"Winner saved to: {args.winner}")
    print(f"Run metadata: {args.metadata}")


if __name__ == "__main__":
    main()
