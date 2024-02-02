package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.options.ShpOptions;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class SpeedLimitOptions {

	private static final Logger log = LogManager.getLogger(TollOptions.class);
	private static ShpOptions shp;

	@Option(
		names = {"--speedLimit"},
		description = {"Speed limit in [length unit]/[s] to apply to links for car modes"}, // or is it always meters?
		required = true
	)
	private double limit;

	@Option(
		names = {"--speedLimitArea"},
		description = {"Optional filepath to the shape file containing the area in which the speed limit should be effective"},
		required = false
	)
	private Path shpPath;

	// TODO: those two probably are too restrictive for practical use
	//  -> what if I want to exclude/include two types? Not possible this way
	@Option(
		names = {"--onlyOnType"},
		description = {"Apply speed limit only on links of a specified type"},
		required = false
	)
	private String onlyOnType;

	@Option(
		names = {"--notOnType"},
		description = {"Apply speed limit on all links NOT of a specified type"},
		required = false
	)
	private String notOnType;

	public double getLimit() {
		return limit;
	}

	public void setLimit(double limit) {
		this.limit = limit;
	}

	public List<String> getLimitedLinkTypes(Network nw) {
		Stream<String> linkTypes = nw.getLinks().values().parallelStream()
			.unordered()
			.map(link -> link.getAttributes().getAttribute("type").toString())
			.distinct();

		if (onlyOnType != null){
			linkTypes = linkTypes.filter(lt -> lt.contains(onlyOnType));
		}
		if (notOnType != null){
			linkTypes = linkTypes.filter(lt -> !lt.contains(notOnType));
		}
		return linkTypes.toList();
	}

	public ShpOptions getShpOptions() {
		if (shpPath != null) {
			if (shp == null) {
				shp = new ShpOptions(shpPath, null, null);
			}
			return shp;
		}
		else {
			return null;
		}
	}
}

