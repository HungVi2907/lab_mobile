"""Headless Flappy Bird simulator mapped from Android GameViewModel logic."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import random
from typing import List, Sequence, Tuple

OBSERVATION_ORDER: List[str] = [
    "bird_y_norm",
    "bird_velocity_norm",
    "next_pipe_dx_norm",
    "gap_top_delta_norm",
    "gap_bottom_delta_norm",
]

OUTPUT_ORDER: List[str] = ["flap_score"]


class Difficulty(str, Enum):
    EASY = "easy"
    NORMAL = "normal"
    HARD = "hard"

    @classmethod
    def from_string(cls, value: str) -> "Difficulty":
        normalized = value.strip().lower()
        if normalized == "easy":
            return cls.EASY
        if normalized == "hard":
            return cls.HARD
        return cls.NORMAL


@dataclass(frozen=True)
class DifficultyProfile:
    gravity_multiplier: float
    flap_velocity_multiplier: float
    pipe_speed_multiplier: float
    pipe_gap_multiplier: float
    pipe_spacing_multiplier: float


@dataclass(frozen=True)
class DimensionsPx:
    bird_width_px: float = 64.0
    bird_height_px: float = 48.0
    pipe_width_px: float = 92.0
    pipe_gap_height_px: float = 190.0
    pipe_spacing_px: float = 250.0
    ground_height_px: float = 96.0
    pipe_vertical_margin_px: float = 44.0


@dataclass(frozen=True)
class RuntimeTuning:
    gravity_px_per_sec_squared: float
    flap_velocity_px_per_sec: float
    pipe_speed_px_per_sec: float
    pipe_gap_height_px: float
    pipe_spacing_px: float


@dataclass
class Bird:
    x: float
    y: float
    velocity_y: float


@dataclass
class Pipe:
    x: float
    gap_top_y: float
    has_scored: bool = False


@dataclass
class SimulationState:
    bird: Bird
    pipes: List[Pipe]
    score: int
    alive: bool
    elapsed_seconds: float


def _difficulty_profile(difficulty: Difficulty) -> DifficultyProfile:
    if difficulty == Difficulty.EASY:
        return DifficultyProfile(
            gravity_multiplier=0.9,
            flap_velocity_multiplier=1.06,
            pipe_speed_multiplier=0.86,
            pipe_gap_multiplier=1.2,
            pipe_spacing_multiplier=1.1,
        )
    if difficulty == Difficulty.HARD:
        return DifficultyProfile(
            gravity_multiplier=1.14,
            flap_velocity_multiplier=0.95,
            pipe_speed_multiplier=1.18,
            pipe_gap_multiplier=0.82,
            pipe_spacing_multiplier=0.9,
        )
    return DifficultyProfile(
        gravity_multiplier=1.0,
        flap_velocity_multiplier=1.0,
        pipe_speed_multiplier=1.0,
        pipe_gap_multiplier=1.0,
        pipe_spacing_multiplier=1.0,
    )


class FlappySimulator:
    # Mirrors GameTuning constants in app.
    GRAVITY_PX_PER_SEC_SQUARED = 1850.0
    FLAP_VELOCITY_PX_PER_SEC = -670.0
    PIPE_SPEED_PX_PER_SEC = 210.0

    BIRD_X_RATIO = 0.28
    BIRD_Y_RATIO = 0.42
    PIPE_COUNT = 3
    PIPE_SPAWN_OFFSET_RATIO = 0.28
    PIPE_SPACING_JITTER_RATIO = 0.18

    def __init__(
        self,
        screen_width_px: float = 1080.0,
        screen_height_px: float = 1920.0,
        difficulty: Difficulty = Difficulty.NORMAL,
        seed: int | None = None,
        dimensions: DimensionsPx | None = None,
    ) -> None:
        self.screen_width_px = float(screen_width_px)
        self.screen_height_px = float(screen_height_px)
        self.dimensions = dimensions or DimensionsPx()
        self.difficulty = difficulty
        self._random = random.Random(seed)

        self.runtime_tuning = self._runtime_tuning(difficulty)
        self.state = self._build_initial_state()

    def reset(self, seed: int | None = None) -> SimulationState:
        if seed is not None:
            self._random.seed(seed)
        self.runtime_tuning = self._runtime_tuning(self.difficulty)
        self.state = self._build_initial_state()
        return self.state

    @property
    def ground_top(self) -> float:
        return self.screen_height_px - self.dimensions.ground_height_px

    def build_observation(self) -> Tuple[float, ...]:
        pipe = self._next_pipe()
        bird = self.state.bird

        bird_center_y = bird.y + (self.dimensions.bird_height_px * 0.5)
        gap_top = pipe.gap_top_y
        gap_bottom = pipe.gap_top_y + self.runtime_tuning.pipe_gap_height_px

        # Keep normalization bounded so NEAT is less sensitive to outliers.
        bird_y_norm = self._clip01(bird.y / self.screen_height_px)
        velocity_norm = self._clip(
            bird.velocity_y / 1000.0,
            min_value=-3.0,
            max_value=3.0,
        )
        next_pipe_dx_norm = self._clip(
            (pipe.x + self.dimensions.pipe_width_px - bird.x) / self.screen_width_px,
            min_value=-2.0,
            max_value=2.0,
        )
        gap_top_delta_norm = self._clip(
            (bird_center_y - gap_top) / self.screen_height_px,
            min_value=-2.0,
            max_value=2.0,
        )
        gap_bottom_delta_norm = self._clip(
            (bird_center_y - gap_bottom) / self.screen_height_px,
            min_value=-2.0,
            max_value=2.0,
        )

        return (
            bird_y_norm,
            velocity_norm,
            next_pipe_dx_norm,
            gap_top_delta_norm,
            gap_bottom_delta_norm,
        )

    def step(self, flap: bool, delta_seconds: float = 1.0 / 60.0) -> bool:
        if not self.state.alive:
            return False

        dt = self._clip(delta_seconds, 0.0, 0.05)
        bird = self.state.bird

        if flap:
            bird.velocity_y = self.runtime_tuning.flap_velocity_px_per_sec

        bird.velocity_y += self.runtime_tuning.gravity_px_per_sec_squared * dt
        bird.y += bird.velocity_y * dt

        moved_pipes = [
            Pipe(
                x=pipe.x - (self.runtime_tuning.pipe_speed_px_per_sec * dt),
                gap_top_y=pipe.gap_top_y,
                has_scored=pipe.has_scored,
            )
            for pipe in self.state.pipes
        ]

        recycled_pipes = self._recycle_pipes(moved_pipes)

        next_score = self.state.score
        for pipe in recycled_pipes:
            if (not pipe.has_scored) and (bird.x > pipe.x + self.dimensions.pipe_width_px):
                next_score += 1
                pipe.has_scored = True

        has_collision = self._detect_collision(bird, recycled_pipes)
        if has_collision:
            bird.velocity_y = 0.0

        self.state = SimulationState(
            bird=bird,
            pipes=recycled_pipes,
            score=next_score,
            alive=not has_collision,
            elapsed_seconds=self.state.elapsed_seconds + dt,
        )
        return self.state.alive

    def _build_initial_state(self) -> SimulationState:
        bird = Bird(
            x=self.screen_width_px * self.BIRD_X_RATIO,
            y=self.screen_height_px * self.BIRD_Y_RATIO,
            velocity_y=0.0,
        )

        first_pipe_x = self.screen_width_px * (1.0 + self.PIPE_SPAWN_OFFSET_RATIO)
        pipes = [
            Pipe(
                x=first_pipe_x + (idx * self.runtime_tuning.pipe_spacing_px),
                gap_top_y=self._random_gap_top(),
                has_scored=False,
            )
            for idx in range(self.PIPE_COUNT)
        ]

        return SimulationState(
            bird=bird,
            pipes=pipes,
            score=0,
            alive=True,
            elapsed_seconds=0.0,
        )

    def _runtime_tuning(self, difficulty: Difficulty) -> RuntimeTuning:
        profile = _difficulty_profile(difficulty)

        gap_height_px = max(
            self.dimensions.pipe_gap_height_px * profile.pipe_gap_multiplier,
            self.dimensions.bird_height_px * 2.3,
        )
        spacing_px = max(
            self.dimensions.pipe_spacing_px * profile.pipe_spacing_multiplier,
            self.dimensions.pipe_width_px * 1.8,
        )

        return RuntimeTuning(
            gravity_px_per_sec_squared=self.GRAVITY_PX_PER_SEC_SQUARED * profile.gravity_multiplier,
            flap_velocity_px_per_sec=self.FLAP_VELOCITY_PX_PER_SEC * profile.flap_velocity_multiplier,
            pipe_speed_px_per_sec=self.PIPE_SPEED_PX_PER_SEC * profile.pipe_speed_multiplier,
            pipe_gap_height_px=gap_height_px,
            pipe_spacing_px=spacing_px,
        )

    def _next_pipe(self) -> Pipe:
        candidates = [
            pipe
            for pipe in self.state.pipes
            if (pipe.x + self.dimensions.pipe_width_px) >= self.state.bird.x
        ]
        if candidates:
            return min(candidates, key=lambda item: item.x)
        return min(self.state.pipes, key=lambda item: item.x)

    def _recycle_pipes(self, pipes: Sequence[Pipe]) -> List[Pipe]:
        right_most_x = max((pipe.x for pipe in pipes), default=self.screen_width_px)
        spawn_cursor = right_most_x
        recycled: List[Pipe] = []

        for pipe in pipes:
            if pipe.x + self.dimensions.pipe_width_px >= 0.0:
                recycled.append(pipe)
                continue

            spawn_cursor += self.runtime_tuning.pipe_spacing_px + self._random_spacing_jitter(
                self.runtime_tuning.pipe_spacing_px,
            )
            recycled.append(
                Pipe(
                    x=spawn_cursor,
                    gap_top_y=self._random_gap_top(),
                    has_scored=False,
                ),
            )

        return recycled

    def _detect_collision(self, bird: Bird, pipes: Sequence[Pipe]) -> bool:
        bird_inset_x = self.dimensions.bird_width_px * 0.18
        bird_inset_y = self.dimensions.bird_height_px * 0.14
        pipe_inset_x = self.dimensions.pipe_width_px * 0.08

        bird_left = bird.x + bird_inset_x
        bird_right = bird.x + self.dimensions.bird_width_px - bird_inset_x
        bird_top = bird.y + bird_inset_y
        bird_bottom = bird.y + self.dimensions.bird_height_px - bird_inset_y

        if bird_top <= 0.0 or bird_bottom >= self.ground_top:
            return True

        for pipe in pipes:
            pipe_left = pipe.x + pipe_inset_x
            pipe_right = pipe.x + self.dimensions.pipe_width_px - pipe_inset_x
            overlaps_x = bird_right > pipe_left and bird_left < pipe_right
            if not overlaps_x:
                continue

            upper_pipe_bottom = pipe.gap_top_y
            lower_pipe_top = pipe.gap_top_y + self.runtime_tuning.pipe_gap_height_px
            if bird_top < upper_pipe_bottom or bird_bottom > lower_pipe_top:
                return True

        return False

    def _random_gap_top(self) -> float:
        min_gap_top = self.dimensions.pipe_vertical_margin_px
        max_gap_top = (
            self.ground_top
            - self.runtime_tuning.pipe_gap_height_px
            - self.dimensions.pipe_vertical_margin_px
        )
        if max_gap_top <= min_gap_top:
            return min_gap_top
        return self._random.random() * (max_gap_top - min_gap_top) + min_gap_top

    def _random_spacing_jitter(self, spacing_px: float) -> float:
        jitter = spacing_px * self.PIPE_SPACING_JITTER_RATIO
        return self._random.random() * jitter

    @staticmethod
    def _clip(value: float, min_value: float, max_value: float) -> float:
        if value < min_value:
            return min_value
        if value > max_value:
            return max_value
        return value

    @classmethod
    def _clip01(cls, value: float) -> float:
        return cls._clip(value, 0.0, 1.0)
