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
                * row["simulated_traffic_volume"]
            )
        )
        [
            [
                "link_id",
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
# path_output_basecase = sys.argv[0]
# if not path_output_basecase.endswith("/"): path_output_basecase += "/"
# path_output_decongestion = sys.argv[1]
# if not path_output_decongestion.endswith("/"): path_output_decongestion += "/"
# path_output_roadpricing = sys.argv[2]
# if not path_output_roadpricing.endswith("/"): path_output_roadpricing += "/"

path_output_basecase = "C:/Users/jdgoe/Documents/Uni/MATSimAdv Wise2324/matsim-berlin-decongestion/output/berlin-v6.0-1pct/"

all_paths = {"basecase": path_output_basecase}

# read general files

network = matsim.read_network(
    path_output_basecase + "berlin-v6.0." + "output_network.xml.gz"
)
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

print(network_with_delays)
print(type(network_with_delays))
print(isinstance(network_with_delays, gpd.GeoDataFrame))

