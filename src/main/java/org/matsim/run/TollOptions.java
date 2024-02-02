package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.emf.ecore.xmi.impl.SAXXMIHandler;
import org.matsim.application.options.ShpOptions;
import picocli.CommandLine.Option;

import java.nio.file.Path;

final class TollOptions {
	private static final Logger log = LogManager.getLogger(TollOptions.class);
	private static ShpOptions shp;

	@Option(
		names = {"--tollAreaShp"},
		description = {"Filepath to the shape file containing the toll area"},
		required = true
	)
	private Path shpPath;

	public double getTollAmount() {
		return tollAmount;
	}

	public void setTollAmount(double tollAmount) {
		this.tollAmount = tollAmount;
	}

	@Option(
		names = {"--tollAmount"},
		description = {"Amount to toll the links inside the toll area with"},
		required = true
	)
	private double tollAmount;

	public ShpOptions getShpOptions(){
		if (shp == null){
			shp = new ShpOptions(shpPath, null, null); // maybe this will lead to problems?
		}
		return shp;
	}
}
