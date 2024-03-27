package org.matsim.legacy.analysis.emissions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Injector;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class to analyze emissions from simulation output based on the vehicle types from the simulation.
 * For this to work, there have to be hbefa vehicle types in the simulation.
 * @author Julius Gölz
 */
public class RunOfflineAirPollutionAnalysisByVehicleInformation {

	// For me this produces a lot of warnings like:
	//  "WARN WarmEmissionHandler:126 At time 69707.0, vehicle commercialPersonTraffic_service_Berlin_re_vkz.1033_3_47_car
	//  enters and leaves traffic without having entered link 121481. Thus, no emissions are calculated for travel along this link."

	private static final Logger log = LogManager.getLogger(RunOfflineAirPollutionAnalysisByEngineInformation.class);

	private final String runDirectory;
	private final String runId;
	private final String hbefaWarmFile;
	private final String hbefaColdFile;
	private final String analysisOutputDirectory;

	public RunOfflineAirPollutionAnalysisByVehicleInformation(String runDirectory, String runId, String hbefaFileWarm, String hbefaFileCold, String analysisOutputDirectory) {
		this.runDirectory = runDirectory;
		this.runId = runId;
		this.hbefaWarmFile = hbefaFileWarm;
		this.hbefaColdFile = hbefaFileCold;

		if (!analysisOutputDirectory.endsWith("/")) analysisOutputDirectory = analysisOutputDirectory + "/";
		this.analysisOutputDirectory = analysisOutputDirectory;
	}

	public static void main(String[] args) throws IOException {

		if (args.length == 1) {
			String rootDirectory = args[0]; // just put a dot '.' as argument to use the current directory
			if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";

//			final String runDirectory = "output/berlin-v6.0-1pct-withVehicleTypes/";
//			final String runDirectory = "output/berlin-v6.0-1pct-withDecongestion/";
			final String runDirectory = "output/berlin-v6.0-1pct-withRoadpricing/";
//			final String runId = "withVehicleTypes";
//			final String runId = "withDecongestion";
			final String runId = "withRoadpricing";

//			final String hbefaFileCold = "D:/01_DOKUMENTE/Uni/WiSe2324/MATSim_advanced/hbefa-files_v4.1/v4.1/EFA_ColdStart_Concept_2020_detailed_perTechAverage_Bln_carOnly.csv";
			final String hbefaFileCold = "D:/01_DOKUMENTE/Uni/WiSe2324/MATSim_advanced/hbefa-files_v4.1/v4.1/EFA_ColdStart_Concept_2020_detailed_perTechAverage_withHGVetc.csv";
//			final String hbefaFileCold = "D:/01_DOKUMENTE/Uni/WiSe2324/MATSim_advanced/hbefa-files_v4.1/v4.1/EFA_ColdStart_Concept_2020_detailed_perTechAverage.csv";

//			final String hbefaFileWarm = "D:/01_DOKUMENTE/Uni/WiSe2324/MATSim_advanced/hbefa-files_v4.1/v4.1/EFA_HOT_Concept_2020_detailed_perTechAverage_Bln_carOnly.csv";
			final String hbefaFileWarm = "D:/01_DOKUMENTE/Uni/WiSe2324/MATSim_advanced/hbefa-files_v4.1/v4.1/EFA_HOT_Concept_2020_detailed_perTechAverage.csv";

			RunOfflineAirPollutionAnalysisByVehicleInformation analysis = new RunOfflineAirPollutionAnalysisByVehicleInformation(
				runDirectory, //rootDirectory + runDirectory,
				runId,
				hbefaFileWarm, //rootDirectory + hbefaFileWarm,
				hbefaFileCold, //rootDirectory + hbefaFileCold,
				rootDirectory + runDirectory + "analysis/emissions");
			analysis.run();

		} else {
			throw new RuntimeException("Please set the root directory. Aborting...");
		}
	}

	void run() throws IOException {

		Config config = ConfigUtils.createConfig();
		config.vehicles().setVehiclesFile(runDirectory + runId + ".output_vehicles.xml.gz");
		config.network().setInputFile(runDirectory + runId + ".output_network.xml.gz");
		config.transit().setTransitScheduleFile(runDirectory + runId + ".output_transitSchedule.xml.gz");
		config.transit().setVehiclesFile(runDirectory + runId + ".output_transitVehicles.xml.gz");
		config.global().setCoordinateSystem("EPSG:5678"); // had to replace "GK4" - consider changing it again if errors/faulty results
		config.plans().setInputFile(null);
		config.parallelEventHandling().setNumberOfThreads(null);
		config.parallelEventHandling().setEstimatedNumberOfEvents(null);
		config.global().setNumberOfThreads(1);

		EmissionsConfigGroup eConfig = ConfigUtils.addOrGetModule(config, EmissionsConfigGroup.class);
		eConfig.setDetailedVsAverageLookupBehavior(EmissionsConfigGroup.DetailedVsAverageLookupBehavior.onlyTryDetailedElseAbort); // .tryDetailedThenTechnologyAverageElseAbort);
		eConfig.setHbefaTableConsistencyCheckingLevel(EmissionsConfigGroup.HbefaTableConsistencyCheckingLevel.consistent); // This is absolutely necessary
		eConfig.setDetailedColdEmissionFactorsFile(hbefaColdFile);
		eConfig.setDetailedWarmEmissionFactorsFile(hbefaWarmFile);
		eConfig.setNonScenarioVehicles(EmissionsConfigGroup.NonScenarioVehicles.ignore); // No clue what this actually does
		eConfig.setWritingEmissionsEvents(true);

		File folder = new File(analysisOutputDirectory);
		folder.mkdirs();

		final String eventsFile = runDirectory + runId + ".output_events.xml.gz";

		final String emissionEventOutputFile = analysisOutputDirectory + runId + ".emission.events.offline.xml.gz";
		final String linkEmissionAnalysisFile = analysisOutputDirectory + runId + ".emissionsPerLink.csv";
		final String linkEmissionPerMAnalysisFile = analysisOutputDirectory + runId + ".emissionsPerLinkPerM.csv";
		final String vehicleTypeFile = analysisOutputDirectory + runId + ".emissionVehicleInformation.csv";

		Scenario scenario = ScenarioUtils.loadScenario(config);

		// network
//		new VspHbefaRoadTypeMapping().addHbefaMappings(scenario.getNetwork()); // this should not be necessary because road types are already being changed in the simulation setup

		// ignore public transit vehicles

		for (VehicleType type : scenario.getTransitVehicles().getVehicleTypes().values()) {
			EngineInformation engineInformation = type.getEngineInformation();
			VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
		}

//		scenario.getVehicles().getVehicleTypes().values().forEach(t -> log.info(VehicleUtils.getHbefaEmissionsConcept(t.getEngineInformation())));

		// before changing freight vehicle types: Need to create proper CNG and LPG vehicle types
		VehicleType cngFreightType = scenario.getVehicles().getFactory().createVehicleType(Id.create("cngFreightNew", VehicleType.class));
		EngineInformation cngEngineInformation = cngFreightType.getEngineInformation();
		VehicleUtils.setHbefaTechnology(cngEngineInformation, "average");
		VehicleUtils.setHbefaSizeClass(cngEngineInformation, "average");
		VehicleUtils.setHbefaEmissionsConcept(cngEngineInformation, "CNG");
		VehicleUtils.setHbefaVehicleCategory(cngEngineInformation, HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
		cngFreightType.setPcuEquivalents(3.5);
		cngFreightType.setLength(15.);
		scenario.getVehicles().addVehicleType(cngFreightType);

		VehicleType lngFreightType = scenario.getVehicles().getFactory().createVehicleType(Id.create("lngFreightNew", VehicleType.class));
		EngineInformation lpgEngineInformation = lngFreightType.getEngineInformation();
		VehicleUtils.setHbefaTechnology(lpgEngineInformation, "average");
		VehicleUtils.setHbefaSizeClass(lpgEngineInformation, "average");
		VehicleUtils.setHbefaEmissionsConcept(lpgEngineInformation, "LNG");
		VehicleUtils.setHbefaVehicleCategory(lpgEngineInformation, HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
		lngFreightType.setPcuEquivalents(3.5);
		lngFreightType.setLength(15.);
		scenario.getVehicles().addVehicleType(lngFreightType);

		// replace hybrid and petrol vehicles with diesel vehicles for HEAVY_GOODS_VEHICLEs
		var debug = scenario.getVehicles().getVehicleTypes().values().stream().map(VehicleType::getEngineInformation).toList();


		scenario.getVehicles().getVehicles().entrySet().parallelStream()
			.filter(e -> VehicleUtils.getHbefaVehicleCategory(
				e.getValue().getType().getEngineInformation()
				).equals(HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString())
			)
			.forEach(e -> {
				// replace non-existent vehicle types with diesel
				if (
					List.of("petrol (4S)", "Plug-in Hybrid petrol/electric", "Plug-in Hybrid diesel/electric")
						.contains(VehicleUtils.getHbefaEmissionsConcept(e.getValue().getType().getEngineInformation()))
				){
					scenario.getVehicles().removeVehicle(e.getKey()); // first remove car to add back
					VehicleType type = scenario.getVehicles().getVehicleTypes().get(Id.get("dieselFreight", VehicleType.class));
					Vehicle vehicleNew = scenario.getVehicles().getFactory().createVehicle(e.getKey(), type);
					scenario.getVehicles().addVehicle(vehicleNew);
				}
				else if (VehicleUtils.getHbefaEmissionsConcept(e.getValue().getType().getEngineInformation()).equals("bifuel CNG/petrol")) {
					scenario.getVehicles().removeVehicle(e.getKey()); // first remove car to add back
					Vehicle vehicleNew = scenario.getVehicles().getFactory().createVehicle(e.getKey(), cngFreightType);
					scenario.getVehicles().addVehicle(vehicleNew);
				}
				else if (VehicleUtils.getHbefaEmissionsConcept(e.getValue().getType().getEngineInformation()).equals("bifuel LPG/petrol")) {
					scenario.getVehicles().removeVehicle(e.getKey()); // first remove car to add back
					Vehicle vehicleNew = scenario.getVehicles().getFactory().createVehicle(e.getKey(), lngFreightType);
					scenario.getVehicles().addVehicle(vehicleNew);
				}
			});

		EventsManager eventsManager = EventsUtils.createEventsManager();

		AbstractModule module = new AbstractModule() {
			@Override
			public void install() {
				bind(Scenario.class).toInstance(scenario);
				bind(EventsManager.class).toInstance(eventsManager);
				bind(EmissionModule.class);
			}
		};

		com.google.inject.Injector injector = Injector.createInjector(config, module);

		EmissionModule emissionModule = injector.getInstance(EmissionModule.class);

		EventWriterXML emissionEventWriter = new EventWriterXML(emissionEventOutputFile);
		emissionModule.getEmissionEventsManager().addHandler(emissionEventWriter);

		EmissionsOnLinkHandler emissionsEventHandler = new EmissionsOnLinkHandler();
		eventsManager.addHandler(emissionsEventHandler);

		eventsManager.initProcessing();

		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
		matsimEventsReader.readFile(eventsFile);

		log.info("Done reading the events file.");
		log.info("Finish processing...");
		eventsManager.finishProcessing();

		log.info("Closing events file...");
		emissionEventWriter.closeFile();

		log.info("Emission analysis completed.");

		log.info("Writing output...");

		{
			File file1 = new File(linkEmissionAnalysisFile);

			BufferedWriter bw1 = new BufferedWriter(new FileWriter(file1));

			bw1.write("linkId");

			for (Pollutant pollutant : Pollutant.values()) {
				bw1.write(";" + pollutant + " [g]");
			}
			bw1.newLine();

			Map<Id<Link>, Map<Pollutant, Double>> link2pollutants = emissionsEventHandler.getLink2pollutants();

			for (Id<Link> linkId : link2pollutants.keySet()) {
				bw1.write(linkId.toString());

				for (Pollutant pollutant : Pollutant.values()) {
					double value = 0.;
					if (link2pollutants.get(linkId).get(pollutant) != null) {
						value = link2pollutants.get(linkId).get(pollutant);
					}
					bw1.write(";" + value);
				}
				bw1.newLine();
			}

			bw1.close();
			log.info("Output written to " + linkEmissionAnalysisFile);
		}

		{
			File file1 = new File(linkEmissionPerMAnalysisFile);

			BufferedWriter bw1 = new BufferedWriter(new FileWriter(file1));

			bw1.write("linkId");

			for (Pollutant pollutant : Pollutant.values()) {
				bw1.write(";" + pollutant + " [g/m]");
			}
			bw1.newLine();

			Map<Id<Link>, Map<Pollutant, Double>> link2pollutants = emissionsEventHandler.getLink2pollutants();

			for (Id<Link> linkId : link2pollutants.keySet()) {
				bw1.write(linkId.toString());

				for (Pollutant pollutant : Pollutant.values()) {
					double emission = 0.;
					if (link2pollutants.get(linkId).get(pollutant) != null) {
						emission = link2pollutants.get(linkId).get(pollutant);
					}

					double emissionPerM = Double.NaN;
					Link link = scenario.getNetwork().getLinks().get(linkId);
					if (link != null) {
						emissionPerM = emission / link.getLength();
					}

					bw1.write(";" + emissionPerM);
				}
				bw1.newLine();
			}

			bw1.close();
			log.info("Output written to " + linkEmissionPerMAnalysisFile);
		}

		{
			File file2 = new File(vehicleTypeFile);

			BufferedWriter bw2 = new BufferedWriter(new FileWriter(file2));

			bw2.write("vehicleId;vehicleType;emissionsConcept");
			bw2.newLine();

			for (Vehicle vehicle : scenario.getVehicles().getVehicles().values()) {
				String emissionsConcept = "null";
				if (vehicle.getType().getEngineInformation() != null && VehicleUtils.getHbefaEmissionsConcept(vehicle.getType().getEngineInformation()) != null) {
					emissionsConcept = VehicleUtils.getHbefaEmissionsConcept(vehicle.getType().getEngineInformation()).toString();
				}

				bw2.write(vehicle.getId() + ";" + vehicle.getType().getId().toString() + ";" + emissionsConcept);
				bw2.newLine();
			}

			bw2.close();
			log.info("Output written to " + vehicleTypeFile);
		}

	}

}


