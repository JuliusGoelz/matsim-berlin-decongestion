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
import java.util.Map;

/**
 * Class to analyze emissions from simulation output based on the vehicle types from the simulation.
 * For this to work, there have to be hbefa vehicle types in the simulation.
 * @author Julius Gölz
 */
public class RunOfflineAirPollutionAnalysisByVehicleInformation {

	// !!!!! NOT YET TESTED !!!!!


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
			String rootDirectory = args[0];
			if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";

			final String runDirectory = "output/berlin-v6.0-1pct/";
			final String runId = "withRoadpricing";

			// TODO: Figure out how to get the files when on the cluster (probably can't upload them)
			final String hbefaFileCold = "D:/01_DOKUMENTE/Uni/WiSe2324/MATSim_advanced/hbefa-files_v4.1/v4.1/EFA_ColdStart_Concept_2020_detailed_perTechAverage_Bln_carOnly.csv";
			final String hbefaFileWarm = "D:/01_DOKUMENTE/Uni/WiSe2324/MATSim_advanced/hbefa-files_v4.1/v4.1/EFA_HOT_Concept_2020_detailed_perTechAverage_Bln_carOnly.csv";

			RunOfflineAirPollutionAnalysisByVehicleInformation analysis = new RunOfflineAirPollutionAnalysisByVehicleInformation(
				runDirectory, //rootDirectory + runDirectory,
				runId,
				hbefaFileWarm, //rootDirectory + hbefaFileWarm,
				hbefaFileCold, //rootDirectory + hbefaFileCold,
				rootDirectory + runDirectory + "emission-analysis-hbefa-v4.1");
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
		eConfig.setDetailedVsAverageLookupBehavior(EmissionsConfigGroup.DetailedVsAverageLookupBehavior.tryDetailedThenTechnologyAverageElseAbort);
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


