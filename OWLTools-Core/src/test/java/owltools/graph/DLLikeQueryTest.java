package owltools.graph;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import owltools.OWLToolsTestBasics;
import owltools.graph.OWLGraphWrapper;

public class DLLikeQueryTest extends OWLToolsTestBasics {

	@Test
	@Ignore("Disabling test due to lack of resources to debug")
	public void testQuery() throws Exception {
		OWLGraphWrapper g = getOntologyWrapper();
		OWLClass obj = g.getOWLClass("http://example.org#probe_3");
		boolean ok = false;
		for (OWLObject e : g.queryDescendants(obj)) {
			System.out.println(e);
				ok = true;
		}
		assertEquals(3, g.queryDescendants(obj).size());
	}
	
	@Test
	@Ignore("Disabling test due to lack of resources to debug")
	public void testIntersectionsReturnedInClosure() throws Exception {
		OWLGraphWrapper g = getOntologyWrapper();
		OWLClass obj = g.getOWLClass("http://example.org#probe_4");
		boolean ok = false;
		for (OWLObject e : g.queryDescendants(obj)) {
			System.out.println(e);
				ok = true;
		}
		assertEquals(3, g.queryDescendants(obj).size());
	}
	
	@Test
	@Ignore("Disabling test due to lack of resources to debug")
	public void testRestrictionQuery() throws Exception {
		OWLGraphWrapper g = getOntologyWrapper();
		OWLClass c = g.getOWLClass("http://example.org#degenerated");
		OWLObjectProperty p = g.getOWLObjectProperty("http://example.org#has_quality");
		boolean ok = false;
		OWLObjectSomeValuesFrom obj = g.getDataFactory().getOWLObjectSomeValuesFrom(p, c);
		for (OWLObject x : g.queryDescendants(obj)) {
			System.out.println("R:"+x);
			ok = true;
		}
		assertEquals(5, g.queryDescendants(obj).size());
		assertTrue(ok);
	}

	@Test
	@Ignore("Disabling test due to lack of resources to debug")
	public void testRestrictionQuery2() throws Exception {
		OWLGraphWrapper g = getOntologyWrapper();
		OWLClass c = g.getOWLClass("http://example.org#axon_terminals_degenerated");
		OWLObjectProperty p = g.getOWLObjectProperty("http://example.org#has_part");
		boolean ok = false;
		OWLObjectSomeValuesFrom obj = g.getDataFactory().getOWLObjectSomeValuesFrom(p, c);

		for (OWLObject x : g.queryDescendants(obj)) {
			System.out.println("R2:"+x);
			ok = true;
		}
		assertTrue(ok);
		assertEquals(10, g.queryDescendants(obj).size());
	}


	private OWLGraphWrapper getOntologyWrapper() throws OWLOntologyCreationException{
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(getResource("lcstest2.ofn"));
		return new OWLGraphWrapper(ontology);
	}
	
}
