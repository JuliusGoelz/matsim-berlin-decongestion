package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.roadpricing.TollFactor;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

import java.util.List;

public class VehicleTypeSpecificTollFactor implements TollFactor {
	private static final Logger log = LogManager.getLogger(VehicleTypeSpecificTollFactor.class);
	private final Scenario scenario;

	VehicleTypeSpecificTollFactor(Scenario scenario){
		this.scenario = scenario;
	}

	@Override
	public double getTollFactor(Id<Person> personId, Id<Vehicle> vehicleId, Id<Link> linkId, double time) {

		final VehicleType petrolCar = scenario.getVehicles().getVehicleTypes().get(Id.get("petrol", VehicleType.class));
		final VehicleType dieselCar = scenario.getVehicles().getVehicleTypes().get(Id.get("diesel", VehicleType.class));
		final VehicleType cngCar = scenario.getVehicles().getVehicleTypes().get(Id.get("cng", VehicleType.class));
		final VehicleType lpgCar = scenario.getVehicles().getVehicleTypes().get(Id.get("lpg", VehicleType.class));
		final VehicleType hybridPetrolCar = scenario.getVehicles().getVehicleTypes().get(Id.get("hybridPetrol", VehicleType.class));
		final VehicleType hybridDieselCar = scenario.getVehicles().getVehicleTypes().get(Id.get("hybridDiesel", VehicleType.class));
		final VehicleType electricCar = scenario.getVehicles().getVehicleTypes().get(Id.get("electric", VehicleType.class));
		final VehicleType petrolFreight = scenario.getVehicles().getVehicleTypes().get(Id.get("petrolFreight", VehicleType.class));
		final VehicleType dieselFreight = scenario.getVehicles().getVehicleTypes().get(Id.get("dieselFreight", VehicleType.class));
		final VehicleType cngFreight = scenario.getVehicles().getVehicleTypes().get(Id.get("cngFreight", VehicleType.class));
		final VehicleType lpgFreight = scenario.getVehicles().getVehicleTypes().get(Id.get("lpgFreight", VehicleType.class));
		final VehicleType hybridPetrolFreight = scenario.getVehicles().getVehicleTypes().get(Id.get("hybridPetrolFreight", VehicleType.class));
		final VehicleType hybridDieselFreight = scenario.getVehicles().getVehicleTypes().get(Id.get("hybridDieselFreight", VehicleType.class));
		final VehicleType electricFreight = scenario.getVehicles().getVehicleTypes().get(Id.get("electricFreight", VehicleType.class));

		VehicleType type;
        try {
			type = scenario.getVehicles().getVehicles().get(vehicleId).getType();
		}
		// if the above fails, then it should be transit vehicle:
		catch (NullPointerException e) {
//			throw new RuntimeException("Failed to retrieve type for car " +
			type = scenario.getTransitVehicles().getVehicles().get(vehicleId).getType();
		}

		// check type and return appropriate tollFactor
		// TODO: Adjust to make sense
		if (List.of(petrolCar, hybridPetrolCar, petrolFreight, hybridPetrolFreight).contains(type)) {
//			log.info("-> " + 2);
			return 2;
		} else if (List.of(dieselCar, hybridDieselCar, dieselFreight, hybridDieselFreight).contains(type)) {
//			log.info("-> " + 2);
			return 2;
		} else if (List.of(cngCar, cngFreight).contains(type)) {
//			log.info("-> " + 1);
			return 1;
		} else if (List.of(lpgCar, lpgFreight).contains(type)) {
//			log.info("-> " + 1);
			return 1;
		} else if (List.of(electricCar, electricFreight).contains(type)) {
//			log.info("-> " + 0.5);
			return 0.5;
		} else {
			// This should not occur because pt is not routed
			// --> WRONG: It does happen! Is it routed???? (0 is correct tho)
//			log.info("-> " + 0);
			return 0;
		}
	}
}
