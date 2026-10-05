"""EA sitesinden cekilen katalog ozellikleri (catalog_features.csv) + Live Editor dokumundeki gercek potential
(players.csv) -> egitim seti. Ozellikler KATALOGDAN gelir; boylece egitim ve cikarim ayni dagilimi gorur.
Gercek potential yalnizca egitim etiketidir, hicbir yere yayinlanmaz."""
import argparse
import sys
from pathlib import Path

import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app import features  # noqa: E402
from app.training import train_models  # noqa: E402

parser = argparse.ArgumentParser()
parser.add_argument("--dir", default="data/fc27-dump")
parser.add_argument("--snapshot-date", required=True, help="Yasin hesaplandigi referans tarih (YYYY-MM-DD).")
parser.add_argument("--out", default="models")
args = parser.parse_args()

base = Path(args.dir)
catalog = pd.read_csv(base / "catalog_features.csv", parse_dates=["dob"])
dump = pd.read_csv(base / "players.csv", usecols=["playerid", "potential"], low_memory=False)

data = catalog.merge(dump, left_on="ea_resource_id", right_on="playerid", how="inner")
ref = pd.Timestamp(args.snapshot_date)
dob = data["dob"]
data["age"] = ref.year - dob.dt.year - ((ref.month < dob.dt.month) | ((ref.month == dob.dt.month) & (ref.day < dob.dt.day))).astype(int)

attr_columns = {name: name for members in features.ATTRIBUTE_GROUPS.values() for name in members}
groups = data.apply(lambda r: features.compute_groups({k: r[k] for k in attr_columns}), axis=1, result_type="expand")

frame = pd.DataFrame({
    "age": data["age"], "overall": data["overall"], "position": data["position"],
    "weak_foot": data["weak_foot"], "skill_moves": data["skill_moves"],
    "potential_target": data["potential"],
    **{name: groups[name] for name in features.ATTRIBUTE_GROUPS},
})
frame["position_group"] = frame["position"].map(features.position_group)
frame["is_gk"] = (frame["position_group"] == "GK").astype(float)
# league_tier / intl_reputation / contract_years katalogda yok -> NaN (egitimde ve cikarimda ayni)

print("training rows:", frame.dropna(subset=["age", "overall", "potential_target"]).shape[0])
result = train_models(frame, Path(args.out), snapshot_date=args.snapshot_date)
for key, value in result.metrics.items():
    print(f"{key}: {value}")
print("model_version:", result.model_version)
