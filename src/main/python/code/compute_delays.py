# Read necessary output files, compute delays per (car/ride/freight) agent and trip and output the results

import sys
import matsim
import pandas as pd
import geopandas as gpd

def get_link_total_delays(
    link_delays: pd.DataFrame, network: pd.DataFrame
) -> pd.DataFrame:
    return (
        link_delays.merge(network, how="left", on="link_id")
        .assign(
            total_delays=lambda row: (
                (
                    row["lane_km"] * 1000 / row["freespeed"]
                    - row["lane_km"] * 1000 / row["avg_speed"]
                )
                * row["simulated_traffic_volume"] / 3600
            )
        )
        [
            [
                "link_id",
                "total_delays",
                "avg_speed",
                "congestion_index",
                "speed_performance_index",
                "simulated_traffic_volume",
                "vol_freight",
                "vol_car",
            ]
        ]
    )


# args: paths to [base case, decongestion, roadpricing]
path_network = sys.argv[1]
path_output_basecase = sys.argv[2]
if not path_output_basecase.endswith("/"): path_output_basecase += "/"
path_output_decongestion = sys.argv[3]
if not path_output_decongestion.endswith("/"): path_output_decongestion += "/"
path_output_roadpricing = sys.argv[4]
if not path_output_roadpricing.endswith("/"): path_output_roadpricing += "/"

# path_output_basecase = "C:/Users/jdgoe/Documents/Uni/MATSimAdv Wise2324/matsim-berlin-decongestion/output/berlin-v6.0-1pct/"

all_paths = {"basecase": path_output_basecase,
             "withDecongestion": path_output_decongestion,
             "withRoadpricing": path_output_roadpricing}

# read general files

network = matsim.read_network(path_network)
network_links = network.as_geo().copy()
network_links["link_id"] = network_links["link_id"].apply(str)

# prepare scenario comparison

network_with_delays: gpd.GeoDataFrame = network_links.copy()

# generate comparison data

for (name, path) in all_paths.items():
    link_delays = pd.read_csv(
        path + "analysis/traffic/traffic_stats_by_link_daily.csv"
    )
    link_delays["link_id"] = link_delays["link_id"].apply(str)
    delays = get_link_total_delays(link_delays, network_links)
    network_with_delays = (network_with_delays
                           .merge(delays, how="left", on="link_id", suffixes=[None, f"_{name}"]))

# save the network as gpkg and as csv

network_with_delays.to_file("../output/network_with_delays.gpkg")
network_with_delays.drop(columns=["geometry"]).to_csv("../output/link_delays.csv")


# calculate total delays
network_with_delays: pd.DataFrame


total_delays: pd.DataFrame = (
    network_with_delays
    .rename(columns={"total_delays": "Basecase", "total_delays_withDecongestion": "Decongestion", "total_delays_withRoadpricing": "Roadpricing"})
    .agg(
        {"Basecase": "sum",
         "Decongestion": "sum",
         "Roadpricing": "sum"}
        ).reset_index(name="delays")
    .rename(columns={"index": "scenario"})
    # new:
    .pivot_table(values="delays", columns="scenario")
    .reset_index().rename(columns={"index": "x"})
)
print(total_delays)

delays_relative = total_delays.copy()
delays_relative["Decongestion"] = delays_relative["Decongestion"]/delays_relative["Basecase"] - 1
delays_relative["Roadpricing"] = delays_relative["Roadpricing"]/delays_relative["Basecase"] - 1
delays_relative["Basecase"] = 0.

# save total delays

total_delays.to_csv("../output/total_delays.csv")
delays_relative.to_csv("../output/relative_delays.csv")
