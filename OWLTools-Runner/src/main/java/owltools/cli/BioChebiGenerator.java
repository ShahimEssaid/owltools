package owltools.cli;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;

import com.google.common.base.Optional;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

/**
 * Methods for creating the BioChEBI wrapper file for ChEBI. The wrapper creates
 * the GCIs equivalences from ChEBI in GO.
 */
public class BioChebiGenerator {

	private final Map<OWLObjectProperty, Set<OWLObjectProperty>> expansionMap;
	
	/**
	 * @param expansionMap
	 */
	public BioChebiGenerator(Map<OWLObjectProperty, Set<OWLObjectProperty>> expansionMap) {
		this.expansionMap = expansionMap;
	}
	
	/**
	 * Create the GCIs for BioChEBI. Add the axioms into the given ontology.
	 * 
	 * @param ontology
	 * @param ignoredClasses
	 */
	public void expand(OWLOntology ontology, Set<OWLClass> ignoredClasses) {
		final OWLOntologyManager manager = ontology.getOWLOntologyManager();
		final OWLDataFactory factory = manager.getOWLDataFactory();
		
		// scan axioms
		Set<OWLSubClassOfAxiom> axioms = ontology.getAxioms(AxiomType.SUBCLASS_OF, Imports.INCLUDED);
		for (OWLSubClassOfAxiom axiom : axioms) {
			OWLClassExpression superCE = axiom.getSuperClass();
			OWLClassExpression subCE = axiom.getSubClass();
			if (subCE.isAnonymous()) {
				// sub class needs to be an named OWLClass
				continue;
			}

			if (superCE instanceof OWLObjectSomeValuesFrom == false) {
				continue;
			}
			OWLObjectSomeValuesFrom some = (OWLObjectSomeValuesFrom) superCE;

			OWLObjectPropertyExpression expression = some.getProperty();
			if (expression.isAnonymous()) {
				// object property expression needs to be a named OWLObjectProperty 
				continue;
			}

			OWLObjectProperty p = (OWLObjectProperty) expression;
			
			Set<OWLObjectProperty> expansions = expansionMap.get(p);
			if (expansions == null) {
				continue;
			}

			// get content for GCI
			OWLClassExpression y = some.getFiller();
			OWLClass x = subCE.asOWLClass();
			if (ignoredClasses.contains(x)) {
				continue;
			}
			for (OWLObjectProperty createProperty : expansions) {
				OWLClassExpression ce1 = factory.getOWLObjectSomeValuesFrom(createProperty, x);
				OWLClassExpression ce2 = factory.getOWLObjectSomeValuesFrom(createProperty, y);
				OWLEquivalentClassesAxiom eq = factory.getOWLEquivalentClassesAxiom(ce1, ce2);
				manager.addAxiom(ontology, eq);
			}
		}
		
		Set<OWLOntology> imports = ontology.getImports();
		StringBuilder sb = new StringBuilder();
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		sb.append("Generated on ").append(dateFormat.format(new Date())).append(" using the following import chain:");
		for (OWLOntology owlOntology : imports) {
			OWLOntologyID ontologyID = owlOntology.getOntologyID();
			sb.append(" ");
			appendOntologyId(ontologyID, sb);
		}
		addComment(sb.toString(), ontology);
	}
	
	private void appendOntologyId(OWLOntologyID ontologyID, StringBuilder sb) {
		Optional<IRI> ontologyIRI = ontologyID.getOntologyIRI();
		if (ontologyIRI.isPresent()) {
			sb.append("Ontology(id=").append(ontologyIRI.get());
			Optional<IRI> versionIRI = ontologyID.getVersionIRI();
			if (versionIRI .isPresent()) {
				sb.append(", version=").append(versionIRI.get());
			}
			sb.append(")");
			
		}
		else {
			sb.append("Ontology with no ID");
		}
	}
	
	private void addComment(String comment, OWLOntology ontology) {
		final OWLOntologyManager manager = ontology.getOWLOntologyManager();
		final OWLDataFactory factory = manager.getOWLDataFactory();
		OWLAnnotation ontAnn = factory.getOWLAnnotation(factory.getRDFSComment(), factory.getOWLLiteral(comment));
		manager.applyChange(new AddOntologyAnnotation(ontology, ontAnn));
	}

	/**
	 * Create the GCIs for BioChEBI. Use the given ontology file as template.
	 * 
	 * @param bioChebiTemplateFile
	 * @param ignoredSubset optional subset for ignored classes
	 * @return ontology
	 * @throws OWLOntologyCreationException
	 */
	public static OWLOntology createBioChebi(File bioChebiTemplateFile, String ignoredSubset) throws OWLOntologyCreationException {
		// load
		ParserWrapper pw = new ParserWrapper();
		OWLOntology ontology = pw.parseOWL(IRI.create(bioChebiTemplateFile));
		
		createBioChebi(new OWLGraphWrapper(ontology), ignoredSubset);
		return ontology;
	}
	
	/**
	 * Create the GCIs for BioChEBI. Add the axioms into the given ontology graph.
	 * 
	 * @param graph
	 * @param ignoredSubset optional subset for ignored classes
	 * @throws OWLOntologyCreationException
	 */
	public static void createBioChebi(OWLGraphWrapper graph, String ignoredSubset) throws OWLOntologyCreationException {
		Set<OWLClass> ignoredClasses = null;
		if (ignoredSubset != null) {
			ignoredClasses = new HashSet<OWLClass>();
			for(OWLClass cls : graph.getAllOWLClasses()) {
				List<String> subsets = graph.getSubsets(cls);
				if (subsets.contains(ignoredSubset)) {
					ignoredClasses.add(cls);
				}
			}
		}
		createBioChebi(graph, ignoredClasses);
	}
	
	/**
	 * Create the GCIs for BioChEBI. Add the axioms into the given ontology graph.
	 * 
	 * @param graph
	 * @param ignoredClasses options set of ignored classes
	 */
	public static void createBioChebi(OWLGraphWrapper graph, Set<OWLClass> ignoredClasses) {
		// find properties
		Set<OWLObjectProperty> searchProperties = getPropertiesByLabels(graph, 
				"is conjugate acid of", 
				"is conjugate base of");
		Set<OWLObjectProperty> createProperties = getPropertiesByIRI(graph,
				"http://purl.obolibrary.org/obo/RO_0004007", // has primary input or output
				"http://purl.obolibrary.org/obo/RO_0004009", // has primary input
				"http://purl.obolibrary.org/obo/RO_0004008", // has primary output
				"http://purl.obolibrary.org/obo/RO_0000057",    // has participant
				"http://purl.obolibrary.org/obo/RO_0002313",     // transports or maintains localization of
				"http://purl.obolibrary.org/obo/RO_0002233",     // has input
				"http://purl.obolibrary.org/obo/RO_0002234",     // has output
				"http://purl.obolibrary.org/obo/RO_0002340",     // imports
				"http://purl.obolibrary.org/obo/RO_0002345",     // exports
				"http://purl.obolibrary.org/obo/RO_0002332"      // regulates level of
				);

		// create GCI expansion map
		Map<OWLObjectProperty, Set<OWLObjectProperty>> expansionMap = new HashMap<OWLObjectProperty, Set<OWLObjectProperty>>();
		for (OWLObjectProperty p : searchProperties) {
			expansionMap.put(p, createProperties);
		}
		BioChebiGenerator generator = new BioChebiGenerator(expansionMap);
		OWLOntology ontology = graph.getSourceOntology();
		
		generator.expand(ontology, ignoredClasses);
	}
	
	/**
	 * Main method. For testing purposes ONLY!
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		OWLOntology ontology = createBioChebi(new File("src/main/resources/bio-chebi-input.owl"), "no_conj_equiv");
		
		// save
		File outFile = new File("bio-chebi.owl");
		ontology.getOWLOntologyManager().saveOntology(ontology, IRI.create(outFile));
	}

	private static Set<OWLObjectProperty> getPropertiesByIRI(OWLGraphWrapper graph, String...iris) {
		Set<OWLObjectProperty> set = new HashSet<OWLObjectProperty>();
		for (String iri : iris) {
			OWLObjectProperty property = graph.getOWLObjectProperty(iri);
			if (property == null) {
				throw new RuntimeException("No property found for IRI: "+iri);
			}
			set.add(property);
		}
		return set;
	}

	private static Set<OWLObjectProperty> getPropertiesByLabels(OWLGraphWrapper graph, String...labels) {
		Set<OWLObjectProperty> set = new HashSet<OWLObjectProperty>();
		for (String label : labels) {
			OWLObject obj = graph.getOWLObjectByLabel(label);
			if (obj == null) {
				throw new RuntimeException("No property found for label: "+label);
			}
			if (obj instanceof OWLObjectProperty) {
				set.add((OWLObjectProperty) obj);
			}
			else {
				throw new RuntimeException("No obj is wrong type: '"+obj.getClass().getName()+"' + for label: "+label);
			}
		}
		return set;
	}
}
