package org.matsim.run;

import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Coordinate;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.roadpricing.RoadPricing;
import org.matsim.contrib.roadpricing.RoadPricingConfigGroup;
import org.matsim.contrib.roadpricing.RoadPricingSchemeImpl;
import org.matsim.contrib.roadpricing.RoadPricingUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.geometry.Geometry;
import org.opengis.geometry.TransfiniteSet;
import picocli.CommandLine;

import java.util.Optional;

public class RunWithRoadpricing extends RunOpenBerlinScenario {


	// TODO: Actually provide a suitable shp file to test area tolling

	@CommandLine.Mixin
	private final ShpOptions shp = new ShpOptions();

	public static void main(String[] args) {
		MATSimApplication.run(RunWithRoadpricing.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		// change run id and output folder
		config.controler().setRunId("withRoadpricing");
		config.controler().setOutputDirectory("output/berlin-v" + VERSION + "-0pct-withRoadpricing");

		config = super.prepareConfig(config);

		// configure roadpricing module
		RoadPricingConfigGroup roadPricingConfig = ConfigUtils.addOrGetModule(config, RoadPricingConfigGroup.class);
		roadPricingConfig.setTollLinksFile("toll.xml");

		return config;
	}

	@Override
	protected void prepareControler(Controler controler) {
		// call prepareControler of basic RunOpenBerlinScenario
		super.prepareControler(controler);

		// add roadpricing module to controler
		RoadPricing.configure(controler);

		// toll links inside an area
		Optional<ShpOptions> shpOptions = Optional.ofNullable(shp);
		shpOptions.ifPresent(shp -> {
			addLinksToTollArea(controler.getScenario(), shp);
		});

	}

	private void addLinksToTollArea(Scenario sc, ShpOptions shp) {
		Geometry geom = (Geometry) shp.readFeatures().get(0).getDefaultGeometry();
		// identify links that are inside the area and add them to the tolled links

		// TODO: Track and log how many links were inside the area and are now tolled

		sc.getNetwork().getLinks().values().parallelStream()
			.filter(link -> geom.contains(
				(TransfiniteSet) MGC.coord2Point(link.getCoord())
			))
			.forEach(link ->
					RoadPricingUtils.addLink(
						(RoadPricingSchemeImpl)RoadPricingUtils.getRoadPricingScheme(sc), link.getId()
					)
			);
	}
}
