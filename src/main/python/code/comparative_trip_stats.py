# Read necessary output files, join trip stats for the three cases 

import sys
import matsim
import pandas as pd
import geopandas as gpd

def get_trip_info(path: str, metric: str, scenario: str) -> pd.DataFrame:
    
    trip_stats = pd.read_csv(
        path + "analysis/population/trip_stats.csv"
    )
    trip_stats = trip_stats[trip_stats["Info"] == metric]
    trip_stats = (trip_stats
    .drop(columns=["Info", "pt", "bike", "walk"])
    .melt(value_vars=["car", "ride"], var_name="mode", value_name=scenario))

    return trip_stats


# args: paths to [base case, decongestion, roadpricing]
path_output_basecase = sys.argv[1]
if not path_output_basecase.endswith("/"): path_output_basecase += "/"
path_output_decongestion = sys.argv[2]
if not path_output_decongestion.endswith("/"): path_output_decongestion += "/"
path_output_roadpricing = sys.argv[3]
if not path_output_roadpricing.endswith("/"): path_output_roadpricing += "/"

all_paths = {"basecase": path_output_basecase,
             "decongestion": path_output_decongestion,
             "roadpricing": path_output_roadpricing}

joined_duration = pd.DataFrame()
for (name, path) in all_paths.items():
    if joined_duration.empty:
        joined_duration = get_trip_info(path, "Total time traveled [h]", name)
    else:
        joined_duration = joined_duration.merge(
            get_trip_info(path, "Total time traveled [h]", name),
            how="outer",
            on="mode"
            )
        
joined_avg_dist = pd.DataFrame()
for (name, path) in all_paths.items():
    if joined_avg_dist.empty:
        joined_avg_dist = get_trip_info(path, "Avg. distance per trip [km]", name)
    else:
        joined_avg_dist = joined_avg_dist.merge(
            get_trip_info(path, "Avg. distance per trip [km]", name),
            how="outer",
            on="mode"
            )
    
joined_avg_speed = pd.DataFrame()
for (name, path) in all_paths.items():
    if (joined_avg_speed.empty):
        joined_avg_speed = get_trip_info(path, "Avg. speed [km/h]", name)
    else:
        joined_avg_speed = joined_avg_speed.merge(
            get_trip_info(path, "Avg. speed [km/h]", name),
            how="outer",
            on="mode"
            )

joined_duration.to_csv("../output/trips_duration.csv")
joined_avg_dist.to_csv("../output/trips_avg_dist.csv")
joined_avg_speed.to_csv("../output/trips_avg_speed.csv")



