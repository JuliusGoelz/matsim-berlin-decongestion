package org.matsim.run;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.VspHbefaRoadTypeMapping;
import org.matsim.contrib.roadpricing.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import picocli.CommandLine;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class RunWithRoadpricing extends RunOpenBerlinScenario {

	// command line options to set toll area and toll amount
	// TODO: this does not make the times of day in which the toll is effective configurable (yet)
	// for this, cf. enableTolling()
	@CommandLine.Mixin
	private final TollOptions toll = new TollOptions();

	public static void main(String[] args) {
		MATSimApplication.run(RunWithRoadpricing.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		// change run id and output folder
		config.controler().setRunId("withRoadpricing");
		config.controler().setOutputDirectory("output/berlin-v" + VERSION + "-0pct-withRoadpricing");

		config = super.prepareConfig(config);

		// configure roadpricing module -- not needed (?)
//		RoadPricingConfigGroup roadPricingConfig = ConfigUtils.addOrGetModule(config, RoadPricingConfigGroup.class);
//		roadPricingConfig.setTollLinksFile("toll.xml");

		

		return config;
	}

	@Override
	protected void prepareControler(Controler controler) {
		// call prepareControler of basic RunOpenBerlinScenario
		super.prepareControler(controler);

		// add roadpricing module to controler
		RoadPricing.configure(controler);

		// toll links inside an area
		Optional<ShpOptions> shpOptions = Optional.of(toll.getShpOptions());
		shpOptions.ifPresent(shp -> {
			enableTolling(controler.getScenario());
		});

	}

	private void enableTolling(Scenario sc) {

		/* Configure roadpricing scheme. */
		// base version without vehicle-specific toll factors:
//		RoadPricingSchemeImpl scheme = RoadPricingUtils.addOrGetMutableRoadPricingScheme(sc);
		RoadPricingSchemeImpl scheme = new RoadPricingSchemeImpl(sc); // is not public
		// TODO: so how do I get a scheme without instantly adding it to the scenario?
		RoadPricingUtils.setName(scheme, "area");
		RoadPricingUtils.setType(scheme, RoadPricingScheme.TOLL_TYPE_AREA);
		RoadPricingUtils.setDescription(scheme, "Scheme where links inside the area are tolled once per agent");

		// read the provided shape and extract the first feature (should be a polygon)
		Geometry geom = toll.getShpOptions().getGeometry();

		AtomicInteger linksTolled = new AtomicInteger(); // track how many links are being tolled

		// identify links that are inside the area and add them to the tolled links
		sc.getNetwork().getLinks().values().parallelStream()
			.filter(link -> geom.contains(
				MGC.coord2Point(link.getCoord())
			))
			.forEach(link -> {
					RoadPricingUtils.addLink(scheme, link.getId());
					linksTolled.incrementAndGet();
				}
			);
		log.info("Tolling " + linksTolled.get() +
			" links that are inside the tolling area polygon with a toll of " + toll.getTollAmount());
		RoadPricingUtils.createAndAddGeneralCost(scheme,
			Time.parseTime("7:00:00"),
			Time.parseTime("10:00:00"),
			toll.getTollAmount());

		// Last step: Pass the configured scheme on and put toll factors on top:
		TollFactor tollFactor = new VehicleTypeSpecificTollFactor(sc);
		RoadPricingConfigGroup rpConfig = ConfigUtils.addOrGetModule(sc.getConfig(), RoadPricingConfigGroup.class);
//		URL roadpricingUrl = IOUtils.extendUrl(sc.getConfig().getContext(), rpConfig.getTollLinksFile());
//		RoadPricingScheme scheme = RoadPricingSchemeUsingTollFactor.createAndRegisterRoadPricingSchemeUsingTollFactor(
//			roadpricingUrl, tollFactor, sc
//		);
		RoadPricingScheme schemeUsingTollFactor = new RoadPricingSchemeUsingTollFactor(scheme, tollFactor);
		RoadPricingUtils.addRoadPricingScheme(sc, schemeUsingTollFactor);
	}

	private void changeVehicleAndRoadTypes(Scenario sc) {

		// TODO: Refactor to make configurable in an easy way
		// Source: https://www.kba.de/DE/Statistik/Fahrzeuge/Bestand/Jahrebilanz_Bestand/2023/2023_b_jahresbilanz_tabellen.html?nn=3532350&fromStatistic=3532350&yearFilter=2023&fromStatistic=3532350&yearFilter=2023
		final double petrolShare = 0.626769717;
		final double dieselShare = 0.296138944;
		final double lpgShare = 0.006704345;
		final double cngShare = 0.001653867;
		// "Plug-in Hybrid": 0.02071945
		// "Hybrid": 0.046918964 --> Mild-Hybrid, sowohl Benzin als auch Diesel
		final double hybridPetrolShare = 0.005743607878685; // FIXME: OLD
		final double hybridDieselShare = 0.00014232617104426; // FIXME: OLD
		final double electricShare = 0.020067541;

		// network
		new VspHbefaRoadTypeMapping().addHbefaMappings(sc.getNetwork());

		// car vehicles

		VehicleType petrolCarVehicleType = addAndRegisterVehicleType(sc, "petrolCar", "petrol (4S)");
		VehicleType dieselCarVehicleType = addAndRegisterVehicleType(sc, "dieselCar", "diesel");
		VehicleType cngVehicleType = addAndRegisterVehicleType(sc, "cngCar", "bifuel CNG/petrol");
		VehicleType lpgVehicleType = addAndRegisterVehicleType(sc, "lpgCar", "bifuel LPG/petrol");
		VehicleType pluginHybridPetrolVehicleType = addAndRegisterVehicleType(sc, "pluginHybridPetrol", "Plug-in Hybrid petrol/electric");
		VehicleType pluginHybridDieselVehicleType = addAndRegisterVehicleType(sc, "pluginHybridDiesel", "Plug-in Hybrid diesel/electric");
		VehicleType electricVehicleType = addAndRegisterVehicleType(sc, "electricCar", "electricity");

		// ignore default car vehicles

		VehicleType defaultCarVehicleType = sc.getVehicles().getVehicleTypes().get(Id.create("car", VehicleType.class));
		EngineInformation carEngineInformation = defaultCarVehicleType.getEngineInformation();
		VehicleUtils.setHbefaVehicleCategory(carEngineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());

		// ignore freight vehicles

		VehicleType freightVehicleType = sc.getVehicles().getVehicleTypes().get(Id.create("freight", VehicleType.class));
		EngineInformation freightEngineInformation = freightVehicleType.getEngineInformation();
		VehicleUtils.setHbefaVehicleCategory(freightEngineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());

		// ignore public transit vehicles

		for (VehicleType type : sc.getTransitVehicles().getVehicleTypes().values()) {
			EngineInformation engineInformation = type.getEngineInformation();
			VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
		}

		List<Id<Vehicle>> carVehiclesToChangeToSpecificType = sc.getVehicles().getVehicles().keySet().stream().toList(); // change all vehicles
		final Random rnd = MatsimRandom.getLocalInstance();

		/*int totalVehiclesCounter = 0;
		// randomly change some vehicle types
		List<Id<Vehicle>> vehiclesToChangeToElectric = sc.getVehicles().getVehicles().values().stream()
			.filter(vehicle -> vehicle.getType().getId().equals(defaultCarVehicleType.getId()))
			.filter(vehicle -> !vehicle.getId().toString().contains("freight")) // some freight vehicles have the type "car", skip them...
			.filter(vehicle -> rnd.nextDouble() < electricShare)
			.map(Identifiable::getId)
			.collect(Collectors.toList());*/

		// assume: freight vehicle types have the same distribution
		// TODO: Consider splitting it into private cars and freight vehicles
		for (Id<Vehicle> id : carVehiclesToChangeToSpecificType) {
			sc.getVehicles().removeVehicle(id);

			VehicleType vehicleType;
			double rndNumber = rnd.nextDouble();
			if (rndNumber < petrolShare) {
				vehicleType = petrolCarVehicleType;
			} else if (rndNumber < petrolShare + dieselShare) {
				vehicleType = dieselCarVehicleType;
			} else if (rndNumber < petrolShare + dieselShare + lpgShare) {
				vehicleType = lpgVehicleType;
			} else if (rndNumber < petrolShare + dieselShare + lpgShare + cngShare) {
				vehicleType = cngVehicleType;
			} else if (rndNumber < petrolShare + dieselShare + lpgShare + cngShare + hybridPetrolShare) {
				vehicleType = pluginHybridPetrolVehicleType;
			} else if (rndNumber < petrolShare + dieselShare + lpgShare + cngShare + hybridPetrolShare + hybridDieselShare) {
				vehicleType = pluginHybridDieselVehicleType;
			} else if (rndNumber < petrolShare + dieselShare + lpgShare + cngShare + hybridPetrolShare + hybridDieselShare + electricShare) {
				vehicleType = electricVehicleType;
			} else { // handle the other 0.03% of cars that use hydrogen, hot air turbines, nuclear energy etc.
				// No clue if this will work/makes sense:
				vehicleType = sc.getVehicles().getVehicleTypes().get(Id.get("car", VehicleType.class));
			}

			Vehicle vehicleNew = sc.getVehicles().getFactory().createVehicle(id, vehicleType);
			sc.getVehicles().addVehicle(vehicleNew);
			log.info("Type for vehicle " + id + " changed to: " + vehicleType.getId().toString());
		}
	}

	private VehicleType addAndRegisterVehicleType(Scenario sc, String vehicleTypeId, String emissionsConcept){
		VehicleType vehicleType = sc.getVehicles().getFactory().createVehicleType(Id.create(vehicleTypeId, VehicleType.class));
		sc.getVehicles().addVehicleType(vehicleType);
		EngineInformation engineInformation = vehicleType.getEngineInformation();
		VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
		VehicleUtils.setHbefaTechnology(engineInformation, "average");
		VehicleUtils.setHbefaSizeClass(engineInformation, "average");
		VehicleUtils.setHbefaEmissionsConcept(engineInformation, emissionsConcept);
		return vehicleType;
	}
}
