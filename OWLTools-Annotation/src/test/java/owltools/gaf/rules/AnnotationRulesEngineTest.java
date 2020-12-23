package owltools.gaf.rules;

import static org.junit.Assert.*;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;

import owltools.OWLToolsTestBasics;
import owltools.gaf.GafDocument;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.TraversingEcoMapper;
import owltools.gaf.parser.GafObjectsBuilder;
import owltools.gaf.rules.AnnotationRuleViolation.ViolationType;
import owltools.gaf.rules.AnnotationRulesEngine.AnnotationRulesEngineResult;
import owltools.gaf.rules.go.GoAnnotationRulesFactoryImpl;
import owltools.graph.OWLGraphWrapper;
import owltools.io.CatalogXmlIRIMapper;
import owltools.io.ParserWrapper;

/**
 * Tests for {@link AnnotationRulesEngine}.
 *
 * TODO currently this is excluded from the test suite due to incomplete check for taxon violations.
 */
public class AnnotationRulesEngineTest extends OWLToolsTestBasics {

	private static boolean renderViolations = false;
	private static final String LOCATION = "src/test/resources/rules/";
	private static TraversingEcoMapper eco = null;
	private static AnnotationRulesEngine engine = null;
	
	private static Level logLevel = null;
	private static Logger logger = null;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		logger = Logger.getLogger("org.semanticweb.hermit");
		logLevel = logger.getLevel();
		logger.setLevel(Level.ERROR);
		
		String qcfile = LOCATION + "annotation_qc.xml";
		String xrfabbslocation = LOCATION + "GO.xrf_abbs";
		ParserWrapper p = new ParserWrapper();
		
		OWLOntologyIRIMapper mapper = new CatalogXmlIRIMapper(getResource("rules/ontology/extensions/catalog-v001.xml"));
		p.addIRIMapper(mapper);
		
		OWLOntology goTaxon = p.parse("http://purl.obolibrary.org/obo/go/extensions/go-plus.owl");
		OWLOntology gorel = p.parse("http://purl.obolibrary.org/obo/go/extensions/gorel.owl");
		OWLGraphWrapper graph = new OWLGraphWrapper(goTaxon);
		graph.addImport(gorel);
		eco = EcoMapperFactory.createTraversingEcoMapper(p, getResourceIRIString("eco.obo")).getMapper();
		
		AnnotationRulesFactory rulesFactory = new GoAnnotationRulesFactoryImpl(
				qcfile, xrfabbslocation, graph, eco, null);
		engine = new AnnotationRulesEngine(rulesFactory, true, false);
	}

	@Test
	public void testValidateAnnotations() throws Exception {
		GafObjectsBuilder builder = new GafObjectsBuilder();
		GafDocument gafdoc = builder.buildDocument(getResource("test_gene_association_mgi.gaf"));			
		AnnotationRulesEngineResult result = engine.validateAnnotations(gafdoc);
		
		if (renderViolations) {
			renderViolations(result);
		}
		// warning
		assertTrue(result.hasWarnings());
		Map<String, List<AnnotationRuleViolation>> warnings = result.getViolations(ViolationType.Warning);
		assertEquals(4, warnings.size()); // 4 rules with Warnings
		assertEquals(2, warnings.get("GO_AR:0000008").size()); // do not annotate to high level terms
		assertEquals(3, warnings.get("GO_AR:0000013").size()); // Taxon check warning
		assertEquals(1, warnings.get("GO_AR:0000018").size()); // IPI annotations require a With/From entry
		assertEquals(2, warnings.get("GO_AR:0000019").size()); // generic reasoner check
		
		// recommendation
		assertTrue(result.hasRecommendations());
		Map<String, List<AnnotationRuleViolation>> recommendations = result.getViolations(ViolationType.Recommendation);
		assertEquals(5, recommendations.get("GO_AR:0000004").size());
		
		// error
		assertTrue(result.hasErrors());
		Map<String, List<AnnotationRuleViolation>> errors = result.getViolations(ViolationType.Error);
		assertEquals(4, errors.size()); // 4 rules with Errors
		assertEquals(154, errors.get("GO_AR:0000001").size()); // general errors + out-dated IEAs
		assertEquals(1, errors.get("GO_AR:0000011").size()); // ND for root nodes only
		assertEquals(2, errors.get("GO_AR:0000013").size()); // taxon check
		assertEquals(1, errors.get("GO_AR:0000014").size()); // Dangling reference
	}

	private static void renderViolations(AnnotationRulesEngineResult result) {
		AnnotationRulesReportWriter.renderViolations(result, engine, new PrintWriter(System.out));
	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		if (engine != null) {
			engine = null;
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
