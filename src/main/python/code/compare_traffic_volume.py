# Read necessary output files, compute delays per (car/ride/freight) agent and trip and output the results

import sys
import matsim
import pandas as pd
import geopandas as gpd

def read_link_stats(
    name: str
) -> pd.DataFrame:
    df_link_stats = pd.read_csv(
        all_paths[name] + "analysis/traffic/traffic_stats_by_link_daily.csv"
    )
    df_link_stats["link_id"] = df_link_stats["link_id"].apply(str)
    return df_link_stats
    

# args: paths to [base case, decongestion, roadpricing]
path_output_basecase = sys.argv[1]
if not path_output_basecase.endswith("/"): path_output_basecase += "/"
path_output_decongestion = sys.argv[2]
if not path_output_decongestion.endswith("/"): path_output_decongestion += "/"
path_output_roadpricing = sys.argv[3]
if not path_output_roadpricing.endswith("/"): path_output_roadpricing += "/"

all_paths = {"basecase": path_output_basecase,
             "withDecongestion": path_output_decongestion,
             "withRoadpricing": path_output_roadpricing}

# generate comparison data

df_link_stats_bc_full = read_link_stats("basecase")
df_link_stats_bc = df_link_stats_bc_full[["link_id", "simulated_traffic_volume"]].rename(columns={"simulated_traffic_volume": "simulated_traffic_volume_bc"})
df_link_stats_dc = read_link_stats("withDecongestion")
df_link_stats_rp = read_link_stats("withRoadpricing")

df_link_stats_dc = df_link_stats_dc.merge(df_link_stats_bc, how="left", on="link_id")
df_link_stats_dc["simulated_traffic_volume_diff"] = df_link_stats_dc["simulated_traffic_volume"] - df_link_stats_dc["simulated_traffic_volume_bc"]
df_link_stats_dc["simulated_traffic_volume_diff_abs"] = df_link_stats_dc["simulated_traffic_volume_diff"].abs()

df_link_stats_rp = df_link_stats_rp.merge(df_link_stats_bc, how="left", on="link_id")
df_link_stats_rp["simulated_traffic_volume_diff"] = df_link_stats_rp["simulated_traffic_volume"] - df_link_stats_rp["simulated_traffic_volume_bc"]
df_link_stats_rp["simulated_traffic_volume_diff_abs"] = df_link_stats_rp["simulated_traffic_volume_diff"].abs()

df_link_stats_bc_full.to_csv("../output/traffic_stats_by_link_daily_bc.csv")
df_link_stats_dc.to_csv("../output/traffic_stats_by_link_daily_dc.csv")
df_link_stats_rp.to_csv("../output/traffic_stats_by_link_daily_rp.csv")