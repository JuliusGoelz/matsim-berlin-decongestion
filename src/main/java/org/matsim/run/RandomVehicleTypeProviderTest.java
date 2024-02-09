package org.matsim.run;


import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;

import java.util.HashMap;
import java.util.Map;

public class RandomVehicleTypeProviderTest {
	@Test
	public void testDistribution() {
		Config config = new Config();
		ConfigUtils.addOrGetModule(config, NetworkConfigGroup.class);
		ConfigUtils.addOrGetModule(config, PlansConfigGroup.class);
//		Network nw = NetworkUtils.createNetwork();
		ScenarioUtils.ScenarioBuilder scBuilder = new ScenarioUtils.ScenarioBuilder(config);
		Scenario testScenario = scBuilder.build();

		RandomVehicleTypeProvider prov = new RandomVehicleTypeProvider(testScenario);
		Map<VehicleType, Integer> counts = new HashMap<>();
		double totalCars = 1000000.;
		for (int i = 0; i < totalCars; i++) {
			VehicleType type = prov.getVehicleTypeBasedOnDistribution(TransportMode.car);
			if (counts.containsKey(type)) {
				counts.put(type, counts.get(type) + 1);
			}
			else {
				counts.put(type, 1);
			}
		}
		for (Map.Entry<VehicleType, Integer> entry : counts.entrySet()) {
			System.out.println(entry.getKey().getId().toString() + ": " + entry.getValue() + " (" + (entry.getValue()/totalCars)*100 + "%)");
		}
	}

}
