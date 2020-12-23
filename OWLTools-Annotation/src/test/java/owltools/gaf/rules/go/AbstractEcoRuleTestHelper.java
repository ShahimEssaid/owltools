package owltools.gaf.rules.go;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import owltools.OWLToolsTestBasics;
import owltools.gaf.GafDocument;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.EcoMapperFactory.OntologyMapperPair;
import owltools.gaf.eco.TraversingEcoMapper;
import owltools.gaf.parser.GafObjectsBuilder;
import owltools.graph.OWLGraphWrapper;

public abstract class AbstractEcoRuleTestHelper extends OWLToolsTestBasics {

	protected static TraversingEcoMapper eco = null;
	protected static OWLGraphWrapper ecoGraph = null;
	private static Level logLevel = null;
	private static Logger logger = null;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		logger = Logger.getLogger("org.semanticweb.hermit");
		logLevel = logger.getLevel();
		logger.setLevel(Level.ERROR);
		final OntologyMapperPair<TraversingEcoMapper> pair = EcoMapperFactory.createTraversingEcoMapper();
		eco = pair.getMapper();
		ecoGraph = pair.getGraph();
		
	}
	
	protected GafDocument loadGaf(String gaf) throws Exception {
		GafObjectsBuilder builder = new GafObjectsBuilder();
		GafDocument gafdoc = builder.buildDocument(getResource(gaf));
		return gafdoc;
	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		if (ecoGraph != null) {
			ecoGraph = null;
		}
		if (eco != null) {
			eco.dispose();
			eco = null;
		}
		if (logLevel != null && logger != null) {
		    logger.setLevel(logLevel);
		    logger = null;
		    logLevel = null;
		}
	}
}
