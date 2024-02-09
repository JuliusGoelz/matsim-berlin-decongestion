package org.matsim.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.VspHbefaRoadTypeMapping;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
* Class to provide a vehicle type based on a pre-specified distribution
* @author Julius Gölz
**/
final class RandomVehicleTypeProvider {
	private final Map<String, Double> weights = new HashMap<>();
	private final Map<String, Double> freightWeights = new HashMap<>();
	private final Map<String, VehicleType> types = new HashMap<>();
	private final Map<String, VehicleType> freightTypes = new HashMap<>();
	private final Random random;
	private final Scenario scenario;

	public RandomVehicleTypeProvider(Scenario scenario) {
		this.random = MatsimRandom.getLocalInstance();
		this.scenario = scenario;
		createTypes();

		// TODO: Refactor to make configurable in an easy way
		// Source: https://www.kba.de/DE/Statistik/Fahrzeuge/Bestand/Jahrebilanz_Bestand/2023/2023_b_jahresbilanz_tabellen.html?nn=3532350&fromStatistic=3532350&yearFilter=2023&fromStatistic=3532350&yearFilter=2023
		weights.put("petrol", 0.626769717);
		weights.put("diesel", 0.296138944);
		weights.put("lpg", 0.006704345);
		weights.put("cng", 0.001653867);
		// "Plug-in Hybrid": 0.02071945
		// "Hybrid": 0.046918964 --> Mild-Hybrid, sowohl Benzin als auch Diesel
		weights.put("hybridPetrol", 0.005743607878685); // plug-in hybrid FIXME: OLD VALUE, NOT CORRESPONDING WITH SOURCE
		weights.put("hybridDiesel", 0.00014232617104426); // plug-in hybrid FIXME: OLD VALUE, NOT CORRESPONDING WITH SOURCE
		weights.put("electric", 0.020067541);

		for (Map.Entry entry : weights.entrySet()) { // This can be changed to respect a freight-specific distribution
			freightWeights.put(entry.getKey() + "_freight", (double) entry.getValue());
		}

	}

	public VehicleType getVehicleTypeBasedOnDistribution(String mode) {
		Map<String, Double> modeSpecificWeights;
		Map<String, VehicleType> modeSpecificTypes;
		double totalWeight;
		if (List.of(TransportMode.car, TransportMode.ride).contains(mode)) {
			totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
			modeSpecificWeights = weights;
			modeSpecificTypes = types;
		}
		else if (mode.equals("freight")) {
			totalWeight = freightWeights.values().stream().mapToDouble(Double::doubleValue).sum();
			modeSpecificWeights = freightWeights;
			modeSpecificTypes = freightTypes;
		}
		else {
			throw new IllegalArgumentException("The mode `" + mode + "` provided does not have vehicle types");
		}

		double randomValue = random.nextDouble() * totalWeight;

		double cumulativeWeight = 0.0;
		for (Map.Entry<String, Double> entry : modeSpecificWeights.entrySet()) {
			cumulativeWeight += entry.getValue();
			if (randomValue < cumulativeWeight) {
				return modeSpecificTypes.get(entry.getKey());
			}
		}
		throw new RuntimeException("Random value for choosing vehicleType exceeded maximum value");
	}

	private void createTypes() {
		if (types.isEmpty()) {
			// car/ride vehicles
			types.put("petrol", addAndRegisterVehicleType("petrol", "petrol (4S)", TransportMode.car));
			types.put("diesel", addAndRegisterVehicleType("diesel", "diesel", TransportMode.car));
			types.put("lpg", addAndRegisterVehicleType("lpg", "bifuel LPG/petrol", TransportMode.car));
			types.put("cng", addAndRegisterVehicleType("cng", "bifuel CNG/petrol", TransportMode.car));
			types.put("hybridPetrol", addAndRegisterVehicleType("hybridPetrol", "Plug-in Hybrid petrol/electric", TransportMode.car));
			types.put("hybridDiesel", addAndRegisterVehicleType("hybridDiesel", "Plug-in Hybrid diesel/electric", TransportMode.car));
			types.put("electric", addAndRegisterVehicleType("electric", "electricity", TransportMode.car));

			// freight vehicles (longer, higher pce) // TODO: probably the emissionsConcepts don't make sense for freight
			freightTypes.put("petrol_freight", addAndRegisterVehicleType("petrolFreight", "petrol (4S)", "freight"));
			freightTypes.put("diesel_freight", addAndRegisterVehicleType("dieselFreight", "diesel", "freight"));
			freightTypes.put("lpg_freight", addAndRegisterVehicleType("lpgFreight", "bifuel LPG/petrol", "freight"));
			freightTypes.put("cng_freight", addAndRegisterVehicleType("cngFreight", "bifuel CNG/petrol", "freight"));
			freightTypes.put("hybridPetrol_freight", addAndRegisterVehicleType("hybridPetrolFreight", "Plug-in Hybrid petrol/electric", "freight"));
			freightTypes.put("hybridDiesel_freight", addAndRegisterVehicleType("hybridDieselFreight", "Plug-in Hybrid diesel/electric", "freight"));
			freightTypes.put("electric_freight", addAndRegisterVehicleType("electricFreight", "electricity", "freight"));
		}
		else {
			throw new RuntimeException("Vehicle types have already been created! This error should not be able to occur!");
		}
	}
	private VehicleType addAndRegisterVehicleType(String vehicleTypeId, String emissionsConcept, String mode){
		if (! List.of(TransportMode.car, TransportMode.ride, "freight").contains(mode)) {
			throw new IllegalArgumentException("The mode `" + mode + "` provided does not have vehicle types");
		}
		VehicleType vehicleType = scenario.getVehicles().getFactory().createVehicleType(Id.create(vehicleTypeId, VehicleType.class));
		EngineInformation engineInformation = vehicleType.getEngineInformation();
		VehicleUtils.setHbefaTechnology(engineInformation, "average");
		VehicleUtils.setHbefaSizeClass(engineInformation, "average");
		VehicleUtils.setHbefaEmissionsConcept(engineInformation, emissionsConcept);

		if (mode.equals("freight")) {
			VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
			vehicleType.setPcuEquivalents(3.5);
			vehicleType.setLength(15.);
		}
		else {
			VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
		}
		scenario.getVehicles().addVehicleType(vehicleType);
		return vehicleType;
	}
}
