package org.matsim.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.emissions.VspHbefaRoadTypeMapping;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class extends RunOpenBerlinScenario and only adds fixed vehicles based on a pre-defined distribution.
 * It is used by both decongestion and roadpricing setups.
 *
 * @author Julius Gölz
 */
public class RunWithVehicleTypes extends RunOpenBerlinScenario {

	protected String runId = "withVehicleTypes";

	public static void main(String[] args) {
		MATSimApplication.run(RunWithVehicleTypes.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		int lastIteration = 500;
		config.controler().setLastIteration(lastIteration);

		config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);
		config.qsim().setUsePersonIdForMissingVehicleId(false);

//		ConfigUtils.addOrGetModule(config, VehiclesConfigGroup.class); // probably not needed?
		config.controler().setRunId(runId);
		config.controler().setOutputDirectory("output/berlin-v" + VERSION + "-0pct-" + runId);
		config.controler().setWriteEventsInterval(50);
		config.controler().setWritePlansInterval(50);

		// This needs to happen after setting output directory because 0pct is being replaced with the appropriate value in RunOpenBerlinScenario
		config = super.prepareConfig(config);
		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		super.prepareScenario(scenario);

		// network HBEFA types
		new VspHbefaRoadTypeMapping().addHbefaMappings(scenario.getNetwork());

		// this also creates the vehicle types initially:
		RandomVehicleTypeProvider randomVehicleTypeProvider = new RandomVehicleTypeProvider(scenario);

		// Link persons to a vehicle

		// ??? Is this deterministic? Theoretically not, because it's not a guaranteed TreeMap
		//  -> need the same vehicles to have the same types across the scenarios/runs!
		//  => Solution: sort persons
		LinkedHashMap<Id<Person>, ? extends Person> sortedPersonMap = scenario.getPopulation().getPersons().entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldVal, newVal) -> oldVal, LinkedHashMap::new));
		for (Person person : sortedPersonMap.values()) {
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
}

