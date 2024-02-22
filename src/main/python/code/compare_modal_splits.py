# Read necessary output files, join trip stats for the three cases 

import sys
import pandas as pd


def get_modal_split(path: str, scenario: str) -> pd.DataFrame:
    scenario_to_runid = {"basecase": "withVehicleTypes", "decongestion": "withDecongestion", "roadpricing": "withRoadpricing"}

    trips = pd.read_csv(
        path + scenario_to_runid[scenario] + ".modestats.txt",
        delimiter="\t"
    )
    trips = (trips
             .iloc[-1:]  # only keep last iteration
             .drop(columns=["Iteration"])
             .assign(scenario=scenario)
             )
    return trips


def get_modal_split_long(path: str, scenario: str) -> pd.DataFrame:
    scenario_to_runid = {"basecase": "withVehicleTypes", "decongestion": "withDecongestion", "roadpricing": "withRoadpricing"}
    trips = pd.read_csv(
        path + scenario_to_runid[scenario] + ".modestats.txt",
        delimiter="\t"
    )
    trips = (trips
             .iloc[-1:]  # only keep last iteration
             .drop(columns=["Iteration"])
             .melt(value_vars=["bike", "car", "freight", "pt", "ride", "walk"], var_name="mode", value_name=scenario)
             )
    return trips


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

modal_splits = pd.DataFrame()
for (name, path) in all_paths.items():
    modal_splits = pd.concat([modal_splits, get_modal_split(path, name)])

modal_splits.to_csv("../output/modal_splits.csv")

modal_splits_relative = pd.DataFrame()
for (name, path) in all_paths.items():
    if modal_splits_relative.empty:
        modal_splits_relative = get_modal_split_long(path, name)
    else:
        modal_splits_relative = modal_splits_relative.merge(
            get_modal_split_long(path, name),
            how="outer",
            on="mode"
        )
    modal_splits_relative[name + "_rel"] = modal_splits_relative[name] - modal_splits_relative["basecase"]

modal_splits_relative.to_csv("../output/modal_splits_relative.csv")

