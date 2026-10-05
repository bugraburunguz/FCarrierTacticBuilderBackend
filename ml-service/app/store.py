from __future__ import annotations

import json
import os
import threading
from collections import OrderedDict
from pathlib import Path
from typing import Optional

import numpy as np
from xgboost import XGBRegressor

from app import features
from app.training import MODEL_FILES

CACHE_SIZE = int(os.getenv("ML_CACHE_SIZE", "50000"))


class ModelStore:
    def __init__(self, model_dir: Path):
        self.model_dir = model_dir
        self.potential_model: Optional[XGBRegressor] = None
        self.value_model: Optional[XGBRegressor] = None
        self.meta: dict = {}
        self._cache: "OrderedDict[tuple, tuple[int, int]]" = OrderedDict()
        self._lock = threading.Lock()
        self.load()

    @property
    def loaded(self) -> bool:
        return self.potential_model is not None and self.value_model is not None

    @property
    def version(self) -> Optional[str]:
        return self.meta.get("model_version")

    def load(self) -> None:
        meta_path = self.model_dir / MODEL_FILES["meta"]
        pot_path = self.model_dir / MODEL_FILES["potential"]
        val_path = self.model_dir / MODEL_FILES["value"]
        if not (meta_path.exists() and pot_path.exists() and val_path.exists()):
            return
        potential, value = XGBRegressor(), XGBRegressor()
        potential.load_model(pot_path)
        value.load_model(val_path)
        self.potential_model, self.value_model = potential, value
        self.meta = json.loads(meta_path.read_text(encoding="utf-8"))
        with self._lock:
            self._cache.clear()

    def predict(self, players: list[dict]) -> list[tuple[int, int]]:
        keys = [self._key(p) for p in players]
        results: list[Optional[tuple[int, int]]] = [None] * len(players)
        missing: list[int] = []
        with self._lock:
            for index, key in enumerate(keys):
                hit = self._cache.get(key)
                if hit is None:
                    missing.append(index)
                else:
                    self._cache.move_to_end(key)
                    results[index] = hit
        if missing:
            batch = [players[i] for i in missing]
            potentials = np.clip(self.potential_model.predict(features.potential_matrix(batch)),
                                 [float(p["overall"]) for p in batch], 99)
            values = np.expm1(self.value_model.predict(features.value_matrix(batch, potentials)))
            with self._lock:
                for offset, index in enumerate(missing):
                    result = (int(round(potentials[offset])), max(int(round(values[offset])), 0))
                    results[index] = result
                    self._cache[keys[index]] = result
                    if len(self._cache) > CACHE_SIZE:
                        self._cache.popitem(last=False)
        return [r for r in results if r is not None]

    def _key(self, player: dict) -> tuple:
        row = features.potential_row(player) + features.value_row(player, 0.0)
        return (self.version, *[None if np.isnan(v) else v for v in row])
