package org.matsim.run;

import org.matsim.application.MATSimApplication;
import org.matsim.contrib.decongestion.DecongestionConfigGroup;
import org.matsim.contrib.decongestion.DecongestionModule;
import org.matsim.contrib.roadpricing.RoadPricing;
import org.matsim.contrib.roadpricing.RoadPricingConfigGroup;
import org.matsim.contrib.roadpricing.RoadPricingModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;

import java.util.regex.Pattern;

public class RunWithRoadpricing extends RunOpenBerlinScenario {

	public static void main(String[] args) {
		MATSimApplication.run(RunWithRoadpricing.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		config.controler().setRunId("withRoadpricing"); // this should adjust the output directory name

		// configure roadpricing module
		RoadPricingConfigGroup roadPricingConfig = ConfigUtils.addOrGetModule(config, RoadPricingConfigGroup.class);
		roadPricingConfig.setTollLinksFile("toll.xml");

		config = super.prepareConfig(config);
		return config;
	}

	@Override
	protected void prepareControler(Controler controler) {
		// call prepareControler of basic RunOpenBerlinScenario
		super.prepareControler(controler);

		// add roadpricing module to controler
		RoadPricing.configure(controler);
	}
}
