package org.matsim.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.roadpricing.TollFactor;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

public class VehicleTypeSpecificTollFactor implements TollFactor {
	private final Scenario scenario;

	VehicleTypeSpecificTollFactor(Scenario scenario){
		this.scenario = scenario;
	}

	@Override
	public double getTollFactor(Id<Person> personId, Id<Vehicle> vehicleId, Id<Link> linkId, double time) {
		final Id<VehicleType> petrolCar = Id.get("petrolCar", VehicleType.class);
		final Id<VehicleType> dieselCar = Id.get("dieselCar", VehicleType.class);
		final Id<VehicleType> cngCar = Id.get("cngCar", VehicleType.class);
		final Id<VehicleType> lpgCar = Id.get("lpgCar", VehicleType.class);
		final Id<VehicleType> electricCar = Id.get("electricCar", VehicleType.class);

        VehicleType type = scenario.getVehicles().getVehicles().get(vehicleId).getType();
        if (type.equals(petrolCar)) {
            return 1;
        } else if (type.equals(dieselCar)) {
            return 1;
        } else if (type.equals(cngCar)) {
            return 1;
        } else if (type.equals(lpgCar)) {
            return 1;
        } else if (type.equals(electricCar)) {
            return 0.5;
        } else {
			return 0; // This should not occur because pt is not routed
		}
	}
}
