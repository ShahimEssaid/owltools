package owltools.sim2;

import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Test;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import owltools.graph.OWLGraphWrapper;
import owltools.io.OWLPrettyPrinter;
import owltools.io.ParserWrapper;
import owltools.sim2.AbstractOWLSimTest;
import owltools.sim2.scores.ElementPairScores;

/**
 * This is the main test class for PropertyViewOntologyBuilder
 * 
 * @author cjm
 *
 */
public class FindMatchesSimTest extends AbstractOWLSimTest {

	private Logger LOG = Logger.getLogger(FindMatchesSimTest.class);

	@Test
	public void testBasicSim() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		sourceOntol = pw.parseOWL(getResourceIRIString("sim/mp-subset-1.obo"));
		g =  new OWLGraphWrapper(sourceOntol);
		parseAssociations(getResource("sim/mgi-gene2mp-subset-1.tbl"), g);
		setOutput("target/find-matches-test.out");

		owlpp = new OWLPrettyPrinter(g);

		// assume buffering
		OWLReasoner reasoner = new ReasonerFactory().createReasoner(sourceOntol);
		try {

			this.createOwlSim();
			owlsim.createElementAttributeMapFromOntology();

			//sos.saveOntology("/tmp/z.owl");

			reasoner.flush();
			for (OWLNamedIndividual i : sourceOntol.getIndividualsInSignature()) {

				renderer.getResultOutStream().println("\nI = "+i);
				
				List<ElementPairScores> scoreSets = owlsim.findMatches(i, "MGI");
				int rank = 1;
				for (ElementPairScores s : scoreSets) {
					renderer.getResultOutStream().println("\n  RANK = "+rank);
					renderer.printPairScores(s);
					rank++;
				}
			}
		}
		finally {
			reasoner.dispose();
		}
	}

	


}
