# Read necessary output files, join trip stats for the three cases

import logging
import sys

import geopandas as gpd
import matsim
import pandas as pd


def get_emissions(path: str, scenario: str, nw: gpd.GeoDataFrame) -> tuple[
    pd.DataFrame, gpd.GeoDataFrame]:
    """
    bin_size in minutes
    returns: tuple of df (total values per pollutant, long format) and gdf (link centroids, pollutants columns)
    """

    scenario_to_runid = {"basecase": "withVehicleTypes", "decongestion": "withDecongestion",
                         "roadpricing": "withRoadpricing"}

    logging.info("Reading emissions...")
    link_emissions = pd.read_csv(
        path + "analysis/emissions/" +
        scenario_to_runid[
            scenario] + ".emissionsPerLink.csv",
        delimiter=";")

    logging.info("Aggregating emissions...")
    total_emissions: pd.DataFrame = (
        link_emissions
        .head(100)
        .drop(columns=["linkId"])
        .agg("sum")
        .reset_index(name=scenario)
        .rename(columns={"index": "pollutant"})
    )

    logging.info("Merging link centroids with emissions...")
    network_with_emissions = gpd.GeoDataFrame(nw.merge(
        link_emissions,
        on="linkId", how="left"
    ))

    return total_emissions, network_with_emissions


logging.basicConfig(format='%(asctime)s - %(message)s', level=logging.INFO)

path_network = sys.argv[1]
path_output_basecase = sys.argv[2]
if not path_output_basecase.endswith("/"): path_output_basecase += "/"
path_output_decongestion = sys.argv[3]
if not path_output_decongestion.endswith("/"): path_output_decongestion += "/"
path_output_roadpricing = sys.argv[4]
if not path_output_roadpricing.endswith("/"): path_output_roadpricing += "/"

all_paths = {"basecase": path_output_basecase,
             "decongestion": path_output_decongestion,
             "roadpricing": path_output_roadpricing}

logging.info("Reading network...")
network = matsim.read_network(path_network)
network_link_centroids = network.as_geo().copy()
network_link_centroids["geometry"] = network_link_centroids.centroid
network_link_centroids["linkId"] = network_link_centroids["link_id"].apply(str)

logging.info("Processing basecase...")
res = get_emissions(all_paths["basecase"], "basecase", network_link_centroids)
emissions = res[0]
nw_emissions = res[1]

for (name, path) in all_paths.items():
    if name != "basecase":
        logging.info(f"Processing {name}...")
        res = get_emissions(path, name, network_link_centroids)
        emissions = emissions.merge(
            res[0],
            how="left", on="pollutant"
        )
        nw_emissions = nw_emissions.merge(
            res[1],
            how="left", on=["linkId", "geometry"], suffixes=[None, "_" + name]
        )

nw_emissions = gpd.GeoDataFrame(nw_emissions)

logging.info("Writing output...")
emissions.to_csv("../output/total_emissions.csv")
emissions.query("pollutant == 'NOx [g]'").to_csv("../output/total_emissions_NOx.csv")
nw_emissions.to_file("../output/link_centroids_with_emissions.gpkg")
