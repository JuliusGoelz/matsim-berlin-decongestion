package org.matsim.run;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.roadpricing.*;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.misc.Time;
import picocli.CommandLine;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class to run the OpenBerlinScenario with area tolling based on vehicle types.
 * @author Julius Gölz
 */
public class RunWithRoadpricing extends RunWithVehicleTypes {

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

		return config;
	}

	@Override
	protected void prepareControler(Controler controler) {
		// call prepareControler of basic RunOpenBerlinScenario (it's not overridden in RunWithVehicleTypes)
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
			" links that are inside the tolling area polygon with a base toll of " + toll.getTollAmount());
		RoadPricingUtils.createAndAddGeneralCost(scheme,
			Time.parseTime("7:00:00"),
			Time.parseTime("10:00:00"),
			toll.getTollAmount());

		// Pass the configured scheme on and put toll factors on top
		VehicleTypeSpecificTollFactor tollFactor = new VehicleTypeSpecificTollFactor(sc);

		return new RoadPricingSchemeUsingTollFactor(scheme, tollFactor); // FIXME:  this does not work for now
//		return scheme; // regular, non-vehicle-specific toll!!!
	}

}
