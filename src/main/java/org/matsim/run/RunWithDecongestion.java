package org.matsim.run;

import org.matsim.application.MATSimApplication;
import org.matsim.contrib.decongestion.DecongestionConfigGroup;
import org.matsim.contrib.decongestion.DecongestionModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;

/**
 * Class to run the OpenBerlinScenario with decongestion pricing.
 * The toll is not dependent on vehicle types, but it extends RunWithVehicleTypes
 *  to have the same basis as roadpricing to do emission analysis on
 * @author Julius Gölz
 */
public final class RunWithDecongestion extends RunWithVehicleTypes {

	public static void main(String[] args) {
		MATSimApplication.run(RunWithDecongestion.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		// No. of iterations is set in RunWithVehicleTypes

		// change run id and output folder
		runId = "withDecongestion";
		// This needs to happen after setting output directory because 0pct is being replaced with the appropriate value in RunOpenBerlinScenario
		config = super.prepareConfig(config);
		return config;
	}

	@Override
	protected void prepareControler(Controler controler) {
		// call prepareControler of basic RunOpenBerlinScenario
		super.prepareControler(controler);

		// configure and add decongestion module to controler
		final DecongestionConfigGroup decongestionSettings = ConfigUtils.addOrGetModule( controler.getConfig(), DecongestionConfigGroup.class );

		decongestionSettings.setWriteOutputIteration(1);
		decongestionSettings.setKp(0.003); // cf. Ihab's doctoral thesis pg. 53
		decongestionSettings.setKd(0.0);
		decongestionSettings.setKi(0.0);
		decongestionSettings.setMsa(false);
		decongestionSettings.setTollBlendFactor(1.0);
		decongestionSettings.setFractionOfIterationsToEndPriceAdjustment(1.0);
		decongestionSettings.setFractionOfIterationsToStartPriceAdjustment(0.0);

		controler.addOverridingModule(new DecongestionModule(controler.getScenario()) );
	}
}
