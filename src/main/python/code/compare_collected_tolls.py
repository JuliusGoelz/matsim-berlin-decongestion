# Read necessary output files, join trip stats for the three cases

import sys
import matsim
import logging
import pandas as pd


def get_collected_toll_per_timebin(path: str, scenario: str, bin_size: int) -> pd.DataFrame:
    """bin_size in minutes"""
    scenario_to_runid = {"decongestion": "withDecongestion", "roadpricing": "withRoadpricing"}

    events = matsim.event_reader(path + scenario_to_runid[scenario] + ".output_events.xml.gz")
    dict_tolls_collected = {str(time_bin): 0 for time_bin in range(0, 3360, bin_size)}
    for i, event in enumerate(events):
        if i % 1_000_000 == 0:
            logging.info(f"Processing event {i}")

        # if i > 1_000_000: break # FIXME ONLY DEBUG

        if event.get("type") == "personMoney":
            time_bin = str(int(float(event.get("time")) / 60 // bin_size * bin_size))  # convert to the bin value
            dict_tolls_collected[time_bin] -= float(event.get("amount"))

    dict_tolls_collected = {k: [v] for k, v in dict_tolls_collected.items()}
    tolls_collected = (pd.DataFrame(data=dict_tolls_collected)
                       .melt(value_vars=list(dict_tolls_collected.keys()),
                             value_name=scenario,
                             var_name="minute")
                       )

    return tolls_collected

logging.basicConfig(format='%(asctime)s - %(message)s', level=logging.INFO)

# args: paths to [decongestion, roadpricing]
path_output_decongestion = sys.argv[1]
if not path_output_decongestion.endswith("/"): path_output_decongestion += "/"
path_output_roadpricing = sys.argv[2]
if not path_output_roadpricing.endswith("/"): path_output_roadpricing += "/"

all_paths = {"decongestion": path_output_decongestion,
             "roadpricing": path_output_roadpricing}

collected_tolls = get_collected_toll_per_timebin(all_paths["decongestion"], "decongestion", 60)
collected_tolls = collected_tolls.merge(
    get_collected_toll_per_timebin(all_paths["roadpricing"], "roadpricing", 60),
    how="outer", on="minute"
)
collected_tolls["minute"] = collected_tolls["minute"].apply(int)
collected_tolls["hour"] = collected_tolls["minute"]/60
collected_tolls = collected_tolls.sort_values(by=["minute"])

total_collected_tolls = (
    collected_tolls[["decongestion", "roadpricing"]]
    .agg("sum")
    .reset_index(name="amount")
    .rename(columns={"index": "scenario"})
    .pivot_table(values="amount", columns="scenario")
    .reset_index().rename(columns={"index": "x"})
)

collected_tolls.to_csv("../output/collected_tolls.csv")
total_collected_tolls.to_csv("../output/collected_tolls_total.csv")