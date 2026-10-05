from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd
from sklearn.metrics import mean_absolute_error
from sklearn.model_selection import train_test_split
from xgboost import XGBRegressor

from app import features

AGE_BANDS = [(0, 20), (21, 25), (26, 30), (31, 99)]
MODEL_FILES = {"potential": "potential.json", "value": "value.json", "meta": "meta.json"}

POTENTIAL_PARAMS = dict(n_estimators=600, max_depth=6, learning_rate=0.05, subsample=0.9,
                        colsample_bytree=0.9, objective="reg:squarederror", tree_method="hist", random_state=7)
VALUE_PARAMS = dict(n_estimators=600, max_depth=6, learning_rate=0.05, subsample=0.9,
                    colsample_bytree=0.9, objective="reg:squarederror", tree_method="hist", random_state=7)


@dataclass
class TrainingResult:
    model_version: str
    metrics: dict


def age_band(age: float) -> int:
    for index, (low, high) in enumerate(AGE_BANDS):
        if low <= age <= high:
            return index
    return len(AGE_BANDS) - 1


def prepare_frame(df: pd.DataFrame, mapping: dict) -> pd.DataFrame:
    """Dataset kolonlarını feature adlarına eşler. Şema CONFIG: mapping.json ile verilir (uydurma yok)."""
    columns = mapping.get("columns", {})
    out = pd.DataFrame(index=df.index)
    for name in set(features.POTENTIAL_FEATURES) | set(features.VALUE_FEATURES) | {"position", "potential_target", "value_target"}:
        source = columns.get(name)
        out[name] = df[source] if source in df.columns else np.nan

    attr_columns = mapping.get("attr_columns", {})
    if attr_columns:
        raw = pd.DataFrame({k: df[v] for k, v in attr_columns.items() if v in df.columns})
        grouped = raw.apply(lambda r: features.compute_groups(r.to_dict()), axis=1, result_type="expand")
        for name in features.ATTRIBUTE_GROUPS:
            if out[name].isna().all() and name in grouped:
                out[name] = grouped[name]

    if out["position_group"].isna().all() and out["position"].notna().any():
        out["position_group"] = out["position"].map(
            lambda p: features.position_group(str(p).split(",")[0].strip()) if pd.notna(p) else None)
    out["is_gk"] = (out["position_group"] == "GK").astype(float)
    return out


def train_models(frame: pd.DataFrame, output_dir: Path, u21_max_age: int = 21, test_size: float = 0.2,
                 seed: int = 7, snapshot_date: Optional[str] = None) -> TrainingResult:
    has_value = bool("value_target" in frame and frame["value_target"].notna().any())
    required = ["age", "overall", "potential_target"] + (["value_target"] if has_value else [])
    data = frame.dropna(subset=required).copy()
    if has_value:
        data = data[data["value_target"] > 0]
    if len(data) < 200:
        raise ValueError(f"Not enough rows to train: {len(data)} (need >= 200)")

    data["band"] = data["age"].map(age_band)
    stratify = data["band"] if data["band"].value_counts().min() >= 2 else None
    train, test = train_test_split(data, test_size=test_size, random_state=seed, stratify=stratify)

    players_train = features.frame_to_players(train)
    players_test = features.frame_to_players(test)
    for rows in (players_train, players_test):
        for row in rows:
            row["position_group"] = row["position_group"] if isinstance(row["position_group"], str) else None

    x_train = features.potential_matrix(players_train)
    x_test = features.potential_matrix(players_test)
    potential_model = XGBRegressor(**POTENTIAL_PARAMS)
    potential_model.fit(x_train, train["potential_target"].to_numpy())
    pred_potential = np.clip(potential_model.predict(x_test), test["overall"].to_numpy(), 99)

    y_pot = test["potential_target"].to_numpy()
    u21 = (test["age"] <= u21_max_age).to_numpy()
    baseline = np.clip(test["overall"].to_numpy(), 0, 99)
    metrics = {
        "rows_train": int(len(train)),
        "rows_test": int(len(test)),
        "potential_mae": float(mean_absolute_error(y_pot, pred_potential)),
        "potential_mae_u21": float(mean_absolute_error(y_pot[u21], pred_potential[u21])) if u21.any() else None,
        "potential_mae_baseline_overall": float(mean_absolute_error(y_pot, baseline)),
        "potential_within_2": float((np.abs(y_pot - pred_potential) <= 2).mean()),
        "u21_rows_test": int(u21.sum()),
        "u21_max_age": u21_max_age,
        "value_mae_eur": None,
        "value_median_abs_pct_error": None,
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    potential_model.save_model(output_dir / MODEL_FILES["potential"])
    value_path = output_dir / MODEL_FILES["value"]
    value_path.unlink(missing_ok=True)

    if has_value:
        # Value modeli gerçek potential ile eğitilir; çıkarımda tahmin edilen potential kullanılır (dürüst sınır: hata birikir).
        v_train = features.value_matrix(players_train, train["potential_target"].to_numpy())
        value_model = XGBRegressor(**VALUE_PARAMS)
        value_model.fit(v_train, np.log1p(train["value_target"].to_numpy()))
        v_test = features.value_matrix(players_test, pred_potential)
        pred_value = np.expm1(value_model.predict(v_test))
        y_val = test["value_target"].to_numpy()
        metrics["value_mae_eur"] = float(mean_absolute_error(y_val, pred_value))
        metrics["value_median_abs_pct_error"] = float(np.median(np.abs(pred_value - y_val) / y_val))
        value_model.save_model(value_path)

    digest = hashlib.sha1(json.dumps(metrics, sort_keys=True).encode()).hexdigest()[:8]
    version = f"xgb-{datetime.now(timezone.utc):%Y%m%d}-{digest}"
    meta = {
        "model_version": version,
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "metrics": metrics,
        "potential_features": features.POTENTIAL_FEATURES,
        "value_features": features.VALUE_FEATURES,
        "value_model": has_value,
        # Yaş bu tarihe göre hesaplanmalı (backend: fcareer.ml.age-reference-date).
        "snapshot_date": snapshot_date,
        # FC27 verisiyle eğitildiğinde bayrak kapanır; FC26 vb. veriyle eğitildiyse açık kalır.
        "fc27_pot_flag": snapshot_date is None,
    }
    (output_dir / MODEL_FILES["meta"]).write_text(json.dumps(meta, indent=2), encoding="utf-8")
    return TrainingResult(model_version=version, metrics=metrics)
