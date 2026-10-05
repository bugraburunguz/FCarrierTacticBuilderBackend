"""Live Editor dokumunu (players.csv) EA sitesinden cekilen katalogla karsilastirir. Tek seferlik inceleme."""
import sys

import numpy as np
import pandas as pd

base = sys.argv[1] if len(sys.argv) > 1 else "data/fc27-dump"
players = pd.read_csv(f"{base}/players.csv", low_memory=False)
catalog = pd.read_csv(f"{base}/catalog.csv")

print("players rows", len(players), "unique ids", players.playerid.nunique())
print("catalog rows", len(catalog))

joined = catalog.merge(players, left_on="ea_resource_id", right_on="playerid", how="left", indicator=True)
print("catalog matched in dump:", (joined._merge == "both").sum(), "/", len(catalog))
only_dump = set(players.playerid) - set(catalog.ea_resource_id)
print("in dump but not in site catalog:", len(only_dump))

m = joined[joined._merge == "both"].copy()
print("\n-- overall agreement (site vs game DB)")
diff = m.overallrating - m.overall
print("equal:", (diff == 0).mean().round(4), "abs diff mean:", diff.abs().mean().round(3), "max:", diff.abs().max())
print(diff.value_counts().head(6).to_dict())

print("\n-- potential")
print(m.potential.describe().round(1).to_dict())
print("potential >= overall:", (m.potential >= m.overallrating).mean().round(4))
print("potential == overall:", (m.potential == m.overallrating).mean().round(4))
print("potential==0 or null:", ((m.potential == 0) | m.potential.isna()).sum())

print("\n-- birthdate / age")
print(m.birthdate.describe().round(0).to_dict())
site_dob = pd.to_datetime(m.dob, errors="coerce")
epoch = pd.Timestamp("1582-10-14")
try:
    game_dob = epoch + pd.to_timedelta(m.birthdate, unit="D")
    same = (game_dob.dt.date == site_dob.dt.date)
    print("birthdate decoded as days since 1582-10-14 matches site dob:", same.mean().round(4))
except Exception as e:
    print("birthdate decode failed", e)

print("\n-- money / contract fields")
for col in ["wage", "releaseclause", "contractvaliduntil", "internationalrep", "growthprofile", "gender", "isretiring"]:
    s = m[col]
    print(col, s.describe().round(1).to_dict() if s.dtype != object else s.value_counts().head(3).to_dict())

print("\n-- sample Mbappé (231747)")
row = players[players.playerid == 231747].iloc[0]
print(row[["overallrating", "potential", "birthdate", "height", "weight", "preferredfoot", "skillmoves",
           "weakfootabilitytypecode", "trait1", "icontrait1", "preferredposition1", "internationalrep", "wage",
           "releaseclause", "contractvaliduntil", "finishing", "sprintspeed"]].to_dict())
