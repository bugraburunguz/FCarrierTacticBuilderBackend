from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import pandas as pd

from app.training import prepare_frame, train_models

U21_MAE_TARGET = 3.0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Potential/value modellerini açık datasetle eğit.")
    parser.add_argument("--data", required=True, help="Eğitim CSV yolu (lisansı teyit edilmiş açık dataset).")
    parser.add_argument("--mapping", required=True, help="Kolon eşleme JSON (bkz. data/mapping.example.json).")
    parser.add_argument("--out", default="models", help="Model çıktı klasörü.")
    parser.add_argument("--u21-max-age", type=int, default=21)
    args = parser.parse_args(argv)

    mapping = json.loads(Path(args.mapping).read_text(encoding="utf-8"))
    frame = prepare_frame(pd.read_csv(args.data, low_memory=False), mapping)
    result = train_models(frame, Path(args.out), u21_max_age=args.u21_max_age)
    print(json.dumps({"model_version": result.model_version, "metrics": result.metrics}, indent=2))

    u21 = result.metrics.get("potential_mae_u21")
    if u21 is not None and u21 > U21_MAE_TARGET:
        print(f"UYARI: U21 potential MAE {u21:.2f} hedefin (<= {U21_MAE_TARGET}) üstünde.", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
