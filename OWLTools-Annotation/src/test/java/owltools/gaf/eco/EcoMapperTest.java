package owltools.gaf.eco;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLClass;

import owltools.gaf.eco.EcoMapperFactory.OntologyMapperPair;
import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

/**
 * Basic tests for GO evidence code mappings.
 */
public class EcoMapperTest {

	@Test
	public void basicTest() throws Exception {
		ParserWrapper p = new ParserWrapper();
		OWLGraphWrapper graph = p.parseToOWLGraph(EcoMapper.ECO_PURL);
		EcoMapper mapper = EcoMapperFactory.createEcoMapper(graph);

		assertFalse(mapper.isGoEvidenceCode(null));
		assertFalse(mapper.isGoEvidenceCode("FOO"));

		assertTrue(mapper.isGoEvidenceCode("IEA"));
		assertTrue(mapper.isGoEvidenceCode("ND"));
		assertTrue(mapper.isGoEvidenceCode("IPI"));

		assertEquals(graph.getOWLClassByIdentifier("ECO:0000353"), mapper.getEcoClassForCode("IPI"));

		assertEquals(2, mapper.getAllEcoClassesForCode("IGC").size());
	}

	@Test
	@Ignore
	public void checkMapping() throws Exception {
		final OntologyMapperPair<TraversingEcoMapper> pair = EcoMapperFactory.createTraversingEcoMapper();
		TraversingEcoMapper mapper = pair.getMapper();
		OWLGraphWrapper g = pair.getGraph();

		single(mapper, g, "0000269", "EXP");
		single(mapper, g, "0000318", "IBA");
		single(mapper, g, "0000319", "IBD");
		single(mapper, g, "0000305", "IC");
		single(mapper, g, "0000314", "IDA");

		single(mapper, g, "0000270", "IEP");

		single(mapper, g, "0000316", "IGI");
		single(mapper, g, "0000320", "IKR"); // see also IMR
		single(mapper, g, "0000315", "IMP");
		single(mapper, g, "0000353", "IPI");
		single(mapper, g, "0000321", "IRD");
		single(mapper, g, "0000247", "ISA");
		single(mapper, g, "0000255", "ISM");
		single(mapper, g, "0000266", "ISO");
		single(mapper, g, "0000303", "NAS");
		single(mapper, g, "0000307", "ND");
		single(mapper, g, "0000245", "RCA");
		single(mapper, g, "0000304", "TAS");

		multiple(mapper, g,
				Arrays.asList("0000317", "0000354"),
				"IGC",
				Arrays.asList("GO_REF:0000025"));

		multiple(mapper, g,
			 Arrays.asList("0007669", "0000256", "0007669", "0007669",
				       "0007669", "0000265", "0007669", "0007669",
				       "0007669", "0007669", "0000249", "0000363"),
			 "IEA",
			 Arrays.asList("GO_REF:0000002", "GO_REF:0000003", "GO_REF:0000004", "GO_REF:0000020",
				       "GO_REF:0000035", "GO_REF:0000041", "GO_REF:0000043", "GO_REF:0000116",
				       "GO_REF:0000044", "GO_REF:0000107", "GO_REF:0000108"));

		multiple(mapper, g,
				Arrays.asList("0000250", "0000255", "0000031", "0000031"),
				"ISS",
				Arrays.asList("GO_REF:0000011", "GO_REF:0000012", "GO_REF:0000027"));
	}

	private void single(TraversingEcoMapper mapper, OWLGraphWrapper ecoGraph, String eco, String go) {
		final OWLClass cls = mapper.getEcoClassForCode(go);
		assertEquals("http://purl.obolibrary.org/obo/ECO_"+eco, cls.getIRI().toString());
		Set<OWLClass> codes = mapper.getAllEcoClassesForCode(go);
		assertEquals(1, codes.size());
		assertEquals(cls, codes.iterator().next());

		checkEcoBranch(mapper, ecoGraph, cls);

	}

	private void checkEcoBranch(TraversingEcoMapper mapper, OWLGraphWrapper g, final OWLClass cls) {
		// check that the cls is a descendant of ECO:0000000 ! evidence
		// but not of  ECO:0000217 ! assertion method
		OWLClass e = g.getOWLClassByIdentifier("ECO:0000000");
		assertNotNull(e);
		OWLClass a = g.getOWLClassByIdentifier("ECO:0000217");
		assertNotNull(a);

		Set<OWLClass> ancestors = mapper.getAncestors(cls, false);
		String id = g.getIdentifier(cls);
		assertTrue("The "+id+" class should be a descendant of ECO:0000000 ! evidence", ancestors.contains(e));
		assertFalse("The "+id+" class may not be a descendant of ECO:0000217 ! assertion method", ancestors.contains(a));
	}

	private void multiple(TraversingEcoMapper mapper, OWLGraphWrapper ecoGraph, List<String> ecos, String go, List<String> refs) {
		final OWLClass cls = mapper.getEcoClassForCode(go);
		final String first = ecos.get(0);
		assertEquals("http://purl.obolibrary.org/obo/ECO_"+first, cls.getIRI().toString());

		assertEquals(refs.size() + 1, ecos.size());
		checkEcoBranch(mapper, ecoGraph, cls);

		Set<OWLClass> all = new HashSet<OWLClass>();
		all.add(cls);

		for (int i = 0; i < refs.size(); i++) {
			String ref = refs.get(i);
			String currentEco = ecos.get(i+1);
			OWLClass refCls = mapper.getEcoClassForCode(go, ref);
			assertEquals("http://purl.obolibrary.org/obo/ECO_"+currentEco, refCls.getIRI().toString());
			all.add(refCls);
			checkEcoBranch(mapper, ecoGraph, refCls);
		}

		Set<OWLClass> codes = mapper.getAllEcoClassesForCode(go);
		assertEquals(all.size(), codes.size());
		assertTrue(all.containsAll(codes));
		assertTrue(codes.containsAll(all));
	}
}
