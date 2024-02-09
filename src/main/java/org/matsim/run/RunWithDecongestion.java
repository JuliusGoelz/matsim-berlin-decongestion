package org.matsim.run;

import org.matsim.application.MATSimApplication;
import org.matsim.contrib.decongestion.DecongestionConfigGroup;
import org.matsim.contrib.decongestion.DecongestionModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;

public class RunWithDecongestion extends RunWithVehicleTypes {

	public static void main(String[] args) {
		MATSimApplication.run(RunWithDecongestion.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		// change run id and output folder
		config.controler().setRunId("withDecongestion");
		config.controler().setOutputDirectory("output/berlin-v" + VERSION + "-0pct-withDecongestion");
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
//		decongestionSettings.setKp(0.0123);
		decongestionSettings.setKp(0.123);
		decongestionSettings.setKd(0.0);
		decongestionSettings.setKi(0.0);
		decongestionSettings.setMsa(false);
		decongestionSettings.setTollBlendFactor(1.0);
		decongestionSettings.setFractionOfIterationsToEndPriceAdjustment(1.0);
		decongestionSettings.setFractionOfIterationsToStartPriceAdjustment(0.0);

		controler.addOverridingModule(new DecongestionModule(controler.getScenario()) );
	}
}
