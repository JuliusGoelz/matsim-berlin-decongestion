package org.matsim.run;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.util.List;
import java.util.stream.Stream;

public class RunWithSpeedLimit extends RunOpenBerlinScenario{

	// TODO: Test

	@CommandLine.Mixin
	private final SpeedLimitOptions speedLimits = new SpeedLimitOptions();

	public static void main(String[] args) {
		MATSimApplication.run(RunWithSpeedLimit.class);
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		super.prepareScenario(scenario);

		// get the link types that should be limited according to the options supplied to SpeedLimitOptions
		List<String> limitedLinkTypes = speedLimits.getLimitedLinkTypes(scenario.getNetwork());

		// find links that have one of the types
		Stream<Link> limitedLinks = scenario.getNetwork().getLinks().values().parallelStream()
			.filter( l ->
				limitedLinkTypes
					.contains(l.getAttributes().getAttribute("type").toString())
				&& (
					l.getAllowedModes().contains("car")
					| l.getAllowedModes().contains("ride")
					| l.getAllowedModes().contains("freight")
				)
			)
			.map(l -> (Link) l); // no clue why this is necessary (why Network.getLinks() returns a Map on < Id<Link>, ? >)

		// find links that lie inside the limit area (if supplied)
		ShpOptions shp = speedLimits.getShpOptions();
		if (shp != null) {
			limitedLinks = limitedLinks
				.filter(l -> shp.getGeometry().contains(MGC.coord2Point(l.getCoord())));
		}
		// apply the speed limit (if current limit is higher)
		limitedLinks.forEach(
			l -> l.setFreespeed(Double.min(
				speedLimits.getLimit(),
				l.getFreespeed()
				))
		);
	}
}
