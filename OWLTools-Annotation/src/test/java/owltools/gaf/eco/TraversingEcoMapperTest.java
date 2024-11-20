package owltools.gaf.eco;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.OWLToolsTestBasics;
import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class TraversingEcoMapperTest extends OWLToolsTestBasics{

	private static OWLGraphWrapper g;
	private static OWLReasoner r;

	@BeforeClass
	public static void beforeClass() throws Exception {

		// Setup environment.
		ParserWrapper pw = new ParserWrapper();

		//NOTE: Yes, the GO here is unnecessary, but we're trying to also catch a certain behavior
		// where auxilery ontologies are not caught. The best wat to do that here is to load ECO
		// second and then do the merge.
		OWLOntology ont_main = pw.parse(getResourceIRIString("go_xp_predictor_test_subset.obo"));
		//OWLOntology ont_scnd = pw.parse(getResourceIRIString("eco.obo"));
		//OWLOntology ont_scnd = pw.parse(getResourceIRIString("eco-basic.20211012.obo"));
		OWLOntology ont_scnd = pw.parse(getResourceIRIString("eco.20211012.obo"));
		g = new OWLGraphWrapper(ont_main);
		g.addSupportOntology(ont_scnd);

		// NOTE: This step is necessary or things will get ignored!
		// (This cropped-up in the loader at one point.)
		for (OWLOntology ont : g.getSupportOntologySet())
			g.mergeOntology(ont);

		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
		r = reasonerFactory.createReasoner(g.getSourceOntology());
		g.setReasoner(r);
	}

	@AfterClass
	public static void afterClass() throws Exception {
		if (g != null) {
			OWLReasoner reasoner = g.getReasoner();
			if (reasoner != null) {
				reasoner.dispose();
			}
		}
	}

	@Test
	public void testSimpleEco() throws Exception{

	    // Create EcoTools instance.
	    TraversingEcoMapper eco = EcoMapperFactory.createTraversingEcoMapper(g, g.getReasoner(), true);

	    ///
	    /// IGI
	    ///
	    /// From: http://purl.obolibrary.org/obo/ECO_0000316
	    /// IGI should be: "experimental evidence used in manual assertion" in:
	    ///  + evidence
	    ///    + experimental evidence
	    ///        + experimental phenotypic evidence
	    ///            + genetic interaction evidence
	    ///                - genetic interaction evidence used in manual assertion
	    ///

	    OWLClass igi = eco.getEcoClassForCode("IGI");
	    assertNotNull("IGI must map to one OWLClass", igi);
	    assertEquals("http://purl.obolibrary.org/obo/ECO_0000316", igi.getIRI().toString());
	    String igiId = g.getIdentifier(igi);
	    assertEquals("ECO:0000316", igiId);

	    String igiLabel = g.getLabel(igi);
	    assertEquals("genetic interaction evidence used in manual assertion", igiLabel);

	    // Since we're reflexive, our six ancestors should be:
	    Set<String> foo = new HashSet<String>();
	    foo.add("evidence");
	    foo.add("experimental evidence");
	    foo.add("experimental phenotypic evidence");
	    foo.add("experimental phenotypic evidence used in manual assertion");
	    foo.add("experimental evidence used in manual assertion");
	    foo.add("genetic interaction evidence");
	    foo.add(igiLabel);

	    // inferred by reasoner using cross products
	    foo.add("evidence used in manual assertion");

	    Set<OWLClass> ecoSuperClasses = eco.getAncestors(igi, true);

	    for( OWLClass ec : ecoSuperClasses ){
		String ec_str_label = g.getLabel(ec);
		assertTrue("Actual ancestor should have been in hash, not: " + ec_str_label,
			   foo.contains(ec_str_label));
	    }

	    assertEquals(8, ecoSuperClasses.size());

	    ///
	    /// IEA
	    ///

	    OWLClass iea = eco.getEcoClassForCode("IEA");
	    assertNotNull("IEA must map to one OWLClass", iea);
	    assertEquals("http://purl.obolibrary.org/obo/ECO_0007669", iea.getIRI().toString());
	    String ieaId = g.getIdentifier(iea);
	    assertEquals("ECO:0007669", ieaId);

	    Set<OWLClass> ecoIeaSuperClasses = eco.getAncestors(iea, true);

	    String ieaLabel = g.getLabel(iea);
	    assertEquals("computational evidence used in automatic assertion", ieaLabel);

	    assertEquals(4, ecoIeaSuperClasses.size());

	}

}
