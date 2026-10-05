from __future__ import annotations

import math
from typing import Iterable, Mapping

import numpy as np
import pandas as pd

POTENTIAL_FEATURES = [
    "age", "overall", "position_group", "league_tier", "intl_reputation",
    "pace", "shooting", "passing", "dribbling", "defending", "physical",
    "weak_foot", "skill_moves", "is_gk",
]
VALUE_FEATURES = ["overall", "potential", "age", "league_tier", "intl_reputation", "contract_years"]

POSITION_GROUP_CODES = {"GK": 0, "DEF": 1, "MID": 2, "ATT": 3}
POSITION_TO_GROUP = {
    "GK": "GK",
    "CB": "DEF", "LB": "DEF", "RB": "DEF", "LWB": "DEF", "RWB": "DEF",
    "CDM": "MID", "CM": "MID", "CAM": "MID", "LM": "MID", "RM": "MID",
    "LW": "ATT", "RW": "ATT", "ST": "ATT", "CF": "ATT", "LF": "ATT", "RF": "ATT",
}

# Grup ortalamaları (CONFIG/calibrate): backend (MlFeatureBuilder) ile birebir aynı tanım olmalı.
ATTRIBUTE_GROUPS = {
    "pace": ["acceleration", "sprint_speed"],
    "shooting": ["att_position", "finishing", "shot_power", "long_shots", "volleys", "penalties"],
    "passing": ["vision", "crossing", "fk_accuracy", "short_passing", "long_passing", "curve"],
    "dribbling": ["agility", "balance", "reactions", "ball_control", "dribbling", "composure"],
    "defending": ["interceptions", "heading", "def_awareness", "standing_tackle", "sliding_tackle"],
    "physical": ["jumping", "stamina", "strength", "aggression"],
}


def position_group(position: str | None) -> str | None:
    if position is None:
        return None
    return POSITION_TO_GROUP.get(str(position).strip().upper())


def group_code(group: str | None) -> float:
    if group is None:
        return math.nan
    return float(POSITION_GROUP_CODES.get(group, math.nan))


def compute_groups(attrs: Mapping[str, float]) -> dict[str, float]:
    groups: dict[str, float] = {}
    for name, members in ATTRIBUTE_GROUPS.items():
        values = [attrs[m] for m in members if m in attrs and attrs[m] is not None]
        groups[name] = float(np.mean(values)) if values else math.nan
    return groups


def _num(value) -> float:
    return math.nan if value is None else float(value)


def potential_row(player: Mapping) -> list[float]:
    group = player.get("position_group")
    is_gk = player.get("is_gk")
    if is_gk is None:
        is_gk = group == "GK"
    row = {
        "age": _num(player.get("age")),
        "overall": _num(player.get("overall")),
        "position_group": group_code(group),
        "league_tier": _num(player.get("league_tier")),
        "intl_reputation": _num(player.get("intl_reputation")),
        "pace": _num(player.get("pace")),
        "shooting": _num(player.get("shooting")),
        "passing": _num(player.get("passing")),
        "dribbling": _num(player.get("dribbling")),
        "defending": _num(player.get("defending")),
        "physical": _num(player.get("physical")),
        "weak_foot": _num(player.get("weak_foot")),
        "skill_moves": _num(player.get("skill_moves")),
        "is_gk": 1.0 if is_gk else 0.0,
    }
    return [row[f] for f in POTENTIAL_FEATURES]


def value_row(player: Mapping, potential: float) -> list[float]:
    row = {
        "overall": _num(player.get("overall")),
        "potential": float(potential),
        "age": _num(player.get("age")),
        "league_tier": _num(player.get("league_tier")),
        "intl_reputation": _num(player.get("intl_reputation")),
        "contract_years": _num(player.get("contract_years")),
    }
    return [row[f] for f in VALUE_FEATURES]


def potential_matrix(players: Iterable[Mapping]) -> np.ndarray:
    return np.array([potential_row(p) for p in players], dtype=float)


def value_matrix(players: list[Mapping], potentials: np.ndarray) -> np.ndarray:
    return np.array([value_row(p, pot) for p, pot in zip(players, potentials)], dtype=float)


def frame_to_players(df: pd.DataFrame) -> list[dict]:
    return df.to_dict(orient="records")
