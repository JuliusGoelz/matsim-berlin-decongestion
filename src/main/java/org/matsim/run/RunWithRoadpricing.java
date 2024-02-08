package org.matsim.run;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.VspHbefaRoadTypeMapping;
import org.matsim.contrib.roadpricing.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.VehiclesConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.misc.Time;
import org.matsim.vehicles.*;
import picocli.CommandLine;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

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
		// configure qsim to use vehicles from vehicles definitions
		config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);
		config.qsim().setUsePersonIdForMissingVehicleId(false);

		ConfigUtils.addOrGetModule(config, VehiclesConfigGroup.class); // probably not needed

		config = super.prepareConfig(config);

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		super.prepareScenario(scenario);

		// network HBEFA types
		new VspHbefaRoadTypeMapping().addHbefaMappings(scenario.getNetwork());

		RandomVehicleTypeProvider randomVehicleTypeProvider = new RandomVehicleTypeProvider(scenario);

		// Link persons to a vehicle
		for (Person person : scenario.getPopulation().getPersons().values()) {
			Id<Vehicle> vehIdCar = VehicleUtils.createVehicleId(person, TransportMode.car);
			Id<Vehicle> vehIdRide = VehicleUtils.createVehicleId(person, TransportMode.ride);
			Id<Vehicle> vehIdFreight = VehicleUtils.createVehicleId(person, "freight");
			VehicleType vehTypeCar = randomVehicleTypeProvider.getVehicleTypeBasedOnDistribution(TransportMode.car); // get a random vehicle type based on pre-defined distribution (currently hardcoded)
			VehicleType vehTypeFreight = randomVehicleTypeProvider.getVehicleTypeBasedOnDistribution("freight");
			// Create and add cars
			Vehicle carVeh = VehicleUtils.createVehicle(
				vehIdCar,
				vehTypeCar
			);
			scenario.getVehicles().addVehicle(carVeh);
			Vehicle rideVeh = VehicleUtils.createVehicle(
				vehIdRide,
				vehTypeCar
			);
			scenario.getVehicles().addVehicle(rideVeh);
			Vehicle freightVeh = VehicleUtils.createVehicle(
				vehIdFreight,
				vehTypeFreight
			);
			scenario.getVehicles().addVehicle(freightVeh);

			// map that maps from transport mode to a vehicle id for each person
			Map<String, Id<Vehicle>> vehicleMap = new HashMap<>();
			vehicleMap.put(TransportMode.car, vehIdCar);
			vehicleMap.put(TransportMode.ride, vehIdRide);
			vehicleMap.put("freight", vehIdFreight);
			VehicleUtils.insertVehicleIdsIntoAttributes(person, vehicleMap); // assign vehicle ids to person
		}
		log.info("Vehicles have been created.");
	}

	@Override
	protected void prepareControler(Controler controler) {
		// call prepareControler of basic RunOpenBerlinScenario
		super.prepareControler(controler);

		// add roadpricing module to controler
		RoadPricing.configure(controler);

		// toll links inside an area
		RoadPricingScheme scheme = getRoadPricingScheme(controler.getScenario());
		controler.addOverridingModule(new RoadPricingModule(scheme));
	}

	private RoadPricingScheme getRoadPricingScheme(Scenario sc) {

		// first create an area pricing scheme
		RoadPricingSchemeImpl scheme = RoadPricingUtils.addOrGetMutableRoadPricingScheme(sc);
		RoadPricingUtils.setName(scheme, "area");
		RoadPricingUtils.setType(scheme, RoadPricingScheme.TOLL_TYPE_AREA);
		RoadPricingUtils.setDescription(scheme, "Scheme where links inside the area are tolled once per agent");

		// read the provided shape and extract the first feature (must be a polygon)
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

		// Pass the configured scheme on and put toll factors on top
		VehicleTypeSpecificTollFactor tollFactor = new VehicleTypeSpecificTollFactor(sc);

//		return new RoadPricingSchemeUsingTollFactor(scheme, tollFactor); // FIXME:  this does not work for now
		return scheme; // regular, non-vehicle-specific toll!!!
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

	// FIXME: Remove this method, not needed anymore
	private void changeVehicleAndRoadTypes(Scenario sc) {

		// TODO: Refactor to make configurable in an easy way
		// Source: https://www.kba.de/DE/Statistik/Fahrzeuge/Bestand/Jahrebilanz_Bestand/2023/2023_b_jahresbilanz_tabellen.html?nn=3532350&fromStatistic=3532350&yearFilter=2023&fromStatistic=3532350&yearFilter=2023
		final double petrolShare = 0.626769717;
		final double dieselShare = 0.296138944;
		final double lpgShare = 0.006704345;
		final double cngShare = 0.001653867;
		// "Plug-in Hybrid": 0.02071945
		// "Hybrid": 0.046918964 --> Mild-Hybrid, sowohl Benzin als auch Diesel
		final double hybridPetrolShare = 0.005743607878685; // plug-in hybrid FIXME: OLD
		final double hybridDieselShare = 0.00014232617104426; // plug-in hybrid FIXME: OLD
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

//		List<Id<Vehicle>> carVehiclesToChangeToSpecificType = sc.getVehicles().getVehicles().keySet().stream().toList(); // change all vehicles
		final Random rnd = MatsimRandom.getLocalInstance();

		log.info(sc.getVehicles().getVehicles());

		// assume: freight vehicle types have the same distribution
		// TODO: Consider splitting it into private cars and freight vehicles
		for (Id<Vehicle> id : sc.getVehicles().getVehicles().keySet()) {
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
		log.info("Done changing vehicle types.");
	}
}
