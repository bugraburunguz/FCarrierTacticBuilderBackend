from __future__ import annotations

import os
from pathlib import Path
from typing import Optional

import hmac

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from app.store import ModelStore

MAX_BATCH = 500
MODEL_DIR = Path(os.getenv("ML_MODEL_DIR", "models"))

ML_SHARED_SECRET = os.getenv("ML_SHARED_SECRET", "")


def require_secret(x_ml_secret: Optional[str] = Header(default=None)) -> None:
    """Tahmin ve yeniden yükleme uçları yalnız backend'in paylaşılan anahtarıyla çağrılır (SEC-07). Anahtar tanımsızsa kapı açıktır (yalnız yerel geliştirme)."""
    if ML_SHARED_SECRET and not hmac.compare_digest(ML_SHARED_SECRET, x_ml_secret or ""):
        raise HTTPException(status_code=401, detail="invalid_secret")


app = FastAPI(title="fcareer-ml-service")
store = ModelStore(MODEL_DIR)


class PlayerFeatures(BaseModel):
    player_id: int
    age: float
    overall: float = Field(ge=1, le=99)
    position_group: Optional[str] = None
    league_tier: Optional[float] = None
    intl_reputation: Optional[float] = None
    pace: Optional[float] = None
    shooting: Optional[float] = None
    passing: Optional[float] = None
    dribbling: Optional[float] = None
    defending: Optional[float] = None
    physical: Optional[float] = None
    weak_foot: Optional[float] = None
    skill_moves: Optional[float] = None
    is_gk: Optional[bool] = None
    contract_years: Optional[float] = None


class PredictRequest(BaseModel):
    players: list[PlayerFeatures] = Field(min_length=1, max_length=MAX_BATCH)


class Prediction(BaseModel):
    player_id: int
    potential: int
    value: Optional[int] = None
    model_version: str


class PredictResponse(BaseModel):
    predictions: list[Prediction]
    fc27_pot_flag: bool


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "model_loaded": store.loaded, "model_version": store.version,
            "value_model": store.value_model is not None, "snapshot_date": store.meta.get("snapshot_date")}


@app.post("/ml/reload", dependencies=[Depends(require_secret)])
def reload() -> dict:
    store.load()
    return {"model_loaded": store.loaded, "model_version": store.version}


@app.post("/ml/predict", response_model=PredictResponse, dependencies=[Depends(require_secret)])
def predict(request: PredictRequest) -> PredictResponse:
    if not store.loaded:
        raise HTTPException(status_code=503, detail="model_not_loaded")
    players = [p.model_dump() for p in request.players]
    results = store.predict(players)
    return PredictResponse(
        predictions=[
            Prediction(player_id=p["player_id"], potential=pot, value=val, model_version=store.version)
            for p, (pot, val) in zip(players, results)
        ],
        fc27_pot_flag=bool(store.meta.get("fc27_pot_flag", True)),
    )
