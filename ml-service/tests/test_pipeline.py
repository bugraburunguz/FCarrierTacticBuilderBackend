import json

import numpy as np
from fastapi.testclient import TestClient

from app import features
from app.store import ModelStore
from app.training import prepare_frame, train_models
from tests.synthetic import IDENTITY_MAPPING, make_dataset


def train_to(tmp_path):
    frame = prepare_frame(make_dataset(), IDENTITY_MAPPING)
    return train_models(frame, tmp_path)


def test_training_reports_u21_cohort_separately_and_writes_artifacts(tmp_path):
    result = train_to(tmp_path)

    assert result.metrics["potential_mae_u21"] is not None
    assert result.metrics["u21_rows_test"] > 0
    assert result.metrics["potential_mae"] < 3.0
    meta = json.loads((tmp_path / "meta.json").read_text(encoding="utf-8"))
    assert meta["fc27_pot_flag"] is True
    assert meta["model_version"] == result.model_version


def test_store_clips_potential_to_overall_and_99_and_caches(tmp_path):
    train_to(tmp_path)
    store = ModelStore(tmp_path)
    players = [{"player_id": 1, "age": 19, "overall": 70, "position_group": "ATT", "league_tier": 1},
               {"player_id": 2, "age": 34, "overall": 98, "position_group": "MID", "league_tier": 1}]

    first = store.predict(players)
    second = store.predict(players)

    assert first == second
    assert first[0][0] >= 70 and first[1][0] <= 99 and first[1][0] >= 98
    assert all(value > 0 for _, value in first)


def test_api_returns_503_until_model_exists_then_predicts(tmp_path, monkeypatch):
    import app.main as main
    monkeypatch.setattr(main, "store", ModelStore(tmp_path))
    client = TestClient(main.app)
    body = {"players": [{"player_id": 7, "age": 21, "overall": 75, "position_group": "ATT"}]}

    assert client.get("/health").json()["model_loaded"] is False
    assert client.post("/ml/predict", json=body).status_code == 503

    train_to(tmp_path)
    monkeypatch.setattr(main, "store", ModelStore(tmp_path))
    response = client.post("/ml/predict", json=body)

    assert response.status_code == 200
    prediction = response.json()["predictions"][0]
    assert prediction["player_id"] == 7 and prediction["potential"] >= 75
    assert response.json()["fc27_pot_flag"] is True


def test_api_validates_batch_bounds():
    import app.main as main
    client = TestClient(main.app)

    assert client.post("/ml/predict", json={"players": []}).status_code == 422
    assert client.post("/ml/predict", json={"players": [{"player_id": 1, "age": 20, "overall": 120}]}).status_code == 422


def test_attribute_groups_and_position_mapping():
    groups = features.compute_groups({"acceleration": 80, "sprint_speed": 90})

    assert groups["pace"] == 85.0
    assert np.isnan(groups["shooting"])
    assert features.position_group("LWB") == "DEF"
    assert features.position_group("XX") is None
