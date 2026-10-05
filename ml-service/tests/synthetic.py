"""SADECE test için sentetik veri — gerçek model eğitiminde ASLA kullanılmaz."""
from __future__ import annotations

import numpy as np
import pandas as pd


def make_dataset(rows: int = 3000, seed: int = 11) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    age = rng.integers(16, 38, rows)
    position = rng.choice(["GK", "CB", "CM", "CAM", "RW", "ST"], rows)
    base = rng.normal(66, 8, rows).clip(45, 90)
    growth = np.clip(27 - age, 0, None) * rng.uniform(0.6, 1.4, rows)
    potential = np.clip(base + growth, base, 99).round()
    overall = base.round()
    tier = rng.integers(1, 5, rows)
    value = np.exp(8 + 0.18 * (overall - 50) + 0.04 * (potential - overall) - 0.02 * np.clip(age - 28, 0, None)
                   - 0.15 * (tier - 1) + rng.normal(0, 0.08, rows))
    frame = pd.DataFrame({
        "age": age, "overall": overall, "position": position, "league_tier": tier,
        "intl_reputation": rng.integers(1, 6, rows),
        "pace": rng.normal(65, 10, rows), "shooting": rng.normal(60, 12, rows),
        "passing": rng.normal(62, 10, rows), "dribbling": rng.normal(63, 10, rows),
        "defending": rng.normal(55, 15, rows), "physical": rng.normal(64, 9, rows),
        "weak_foot": rng.integers(1, 6, rows), "skill_moves": rng.integers(1, 6, rows),
        "contract_years": rng.integers(1, 6, rows),
        "potential": potential, "value": value.round(),
    })
    return frame


IDENTITY_MAPPING = {
    "columns": {name: name for name in [
        "age", "overall", "position", "league_tier", "intl_reputation", "pace", "shooting", "passing",
        "dribbling", "defending", "physical", "weak_foot", "skill_moves", "contract_years"]}
    | {"potential_target": "potential", "value_target": "value"}
}
