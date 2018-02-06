package owltools.mooncat;

import static org.junit.Assert.*;

import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.obolibrary.obo2owl.OWLAPIOwl2Obo;
import org.obolibrary.oboformat.model.OBODoc;
import org.obolibrary.oboformat.parser.OBOFormatParser;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import com.google.common.base.Optional;

import owltools.OWLToolsTestBasics;
import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class QuerySubsetGeneratorTest extends OWLToolsTestBasics {

	static {
		Logger.getLogger(OBOFormatParser.class).setLevel(Level.ERROR); // ignore parser warnings
		Logger.getLogger("org.semanticweb.elk.reasoner").setLevel(Level.ERROR); // silent reasoning
	}
	
	private static boolean renderObo = false;
	private static boolean useElk = false;
	
	@Test
	public void testCreateSubOntologyFromDLQuery() throws Exception {
		QuerySubsetGenerator generator = new QuerySubsetGenerator();
		
		OWLGraphWrapper sourceGraph = getSourceGraph();
		OWLGraphWrapper targetGraph = getTargetGraph();
		
		String dlQueryString = "FBbt_00005106 and 'part_of' some FBbt_00005095";
		OWLReasonerFactory reasonerFactory;
		if (useElk) {
			reasonerFactory = new ElkReasonerFactory();
		}
		else {
			reasonerFactory = new org.semanticweb.HermiT.ReasonerFactory();
		}
		
		String query1 = "FBbt_00005106"; // neuron
		String query2 = "FBbt_00005106 and 'part_of' some FBbt_00005095"; // Brain
		int neuronClassCount = DLQueryTool.executeDLQuery(query1, sourceGraph, reasonerFactory).size();
		int neuronAndBrainClassCount = DLQueryTool.executeDLQuery(query2, sourceGraph, reasonerFactory).size();
		assertTrue(neuronClassCount > 1);
		assertTrue(neuronAndBrainClassCount > 1 && neuronAndBrainClassCount < neuronClassCount);
		
		Set<OWLOntology> toMerge = sourceGraph.getAllOntologies();
		generator.createSubOntologyFromDLQuery(dlQueryString, sourceGraph, targetGraph, reasonerFactory, toMerge);

		OWLAPIOwl2Obo owl2Obo = new  OWLAPIOwl2Obo(OWLManager.createOWLOntologyManager() );
		OBODoc oboDoc = owl2Obo.convert(targetGraph.getSourceOntology());
		if (renderObo) {
			renderOBO(oboDoc);
		}
		assertTrue(oboDoc.getTermFrames().size() > neuronAndBrainClassCount); // includes all the parents
	}
	
	private synchronized OWLGraphWrapper getSourceGraph() throws Exception {
		String resourceIRI = getResourceIRIString("mooncat/fly_anatomy.obo");
		ParserWrapper wrapper = new ParserWrapper();
		return new OWLGraphWrapper(wrapper.parseOBO(resourceIRI));
	}

	private OWLGraphWrapper getTargetGraph() throws Exception {
		OWLOntologyManager targetManager = OWLManager.createOWLOntologyManager();
		OWLOntologyID ontologyID  = new OWLOntologyID(Optional.of(IRI.create("http://test.owltools.org/dynamic")), Optional.absent());
		OWLOntology targetOntology = targetManager.createOntology(ontologyID);
		OWLGraphWrapper targetGraph = new OWLGraphWrapper(targetOntology);
		return targetGraph;
	}

}
