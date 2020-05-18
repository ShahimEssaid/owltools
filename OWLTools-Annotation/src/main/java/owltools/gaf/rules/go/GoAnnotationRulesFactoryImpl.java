package owltools.gaf.rules.go;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.OWLOntology;

import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.EcoMapperFactory.OntologyMapperPair;
import owltools.gaf.eco.TraversingEcoMapper;
import owltools.gaf.rules.AnnotationRule;
import owltools.gaf.rules.AnnotationRulesFactoryImpl;
import owltools.gaf.rules.GenericReasonerValidationCheck;
import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class GoAnnotationRulesFactoryImpl extends AnnotationRulesFactoryImpl {
	
	private static final Logger logger = Logger.getLogger(GoAnnotationRulesFactoryImpl.class);

	private final Map<String, AnnotationRule> namedRules;
	
	public GoAnnotationRulesFactoryImpl(ParserWrapper parserWrapper, String taxonModule) {
		this("http://www.geneontology.org/quality_control/annotation_checks/annotation_qc.xml",
				"http://www.geneontology.org/doc/GO.xrf_abbs",
				parserWrapper,
				Arrays.asList(
					"http://purl.obolibrary.org/obo/go/extensions/go-plus.owl",
					"http://purl.obolibrary.org/obo/go/extensions/gorel.owl"
				),
				"http://purl.obolibrary.org/obo/eco.owl",
				taxonModule);
	}
	
	@Deprecated
	public GoAnnotationRulesFactoryImpl(String qcfile, String xrfabbslocation, ParserWrapper p, String go, String gorel, String eco) {
		this(qcfile, xrfabbslocation, getGO(p, Arrays.asList(go, gorel)), getEco(p, eco).getMapper(), null);
	}
	
	public GoAnnotationRulesFactoryImpl(String qcfile, String xrfabbslocation, ParserWrapper p, List<String> ont, String eco, String taxonModule) {
		this(qcfile, xrfabbslocation, getGO(p, ont), getEco(p, eco).getMapper(), taxonModule);
	}
	
	public GoAnnotationRulesFactoryImpl(OWLGraphWrapper graph, TraversingEcoMapper eco, String taxonModule) {
		this("https://raw.githubusercontent.com/owlcollab/owltools/master/docs/legacy/annotation_qc.xml",
				"http://current.geneontology.org/metadata/GO.xrf_abbs", graph, eco, taxonModule);
	}

	@Override
	protected void handleAdditionalRules(List<AnnotationRule> annotationRules, List<AnnotationRule> documentRules,
			List<AnnotationRule> owlRules, List<AnnotationRule> inferenceRules, List<AnnotationRule> experimentalInferenceRules) {
		final AnnotationRule predictionRule = namedRules.get(GoAnnotationPredictionRule.PERMANENT_JAVA_ID);
		if (predictionRule != null) {
			inferenceRules.add(predictionRule);
		}
		final AnnotationRule experimentalRule = namedRules.get(GoAnnotationExperimentalPredictionRule.PERMANENT_JAVA_ID);
		if (experimentalRule != null) {
			experimentalInferenceRules.add(experimentalRule);
		}
		
	}

	public GoAnnotationRulesFactoryImpl(String qcfile, String xrfabbslocation, OWLGraphWrapper graph, TraversingEcoMapper eco, String taxonModule) {
		super(qcfile, graph);
		logger.info("Start preparing ontology checks");
		namedRules = new HashMap<String, AnnotationRule>();
		namedRules.put(BasicChecksRule.PERMANENT_JAVA_ID,  new BasicChecksRule(xrfabbslocation, eco));
//		String taxonModuleFile = null;
//		if (createOntologyModules) {
//			taxonModuleFile = "go-taxon-rule-unsatisfiable-module.owl";
//		}
		namedRules.put(GoAnnotationTaxonRule.PERMANENT_JAVA_ID, new GoAnnotationTaxonRule(graph, taxonModule));
		namedRules.put(GoClassReferenceAnnotationRule.PERMANENT_JAVA_ID, new GoClassReferenceAnnotationRule(graph, "GO:","CL:"));
		namedRules.put(GenericReasonerValidationCheck.PERMANENT_JAVA_ID, new GenericReasonerValidationCheck());
		namedRules.put(GoNoISSProteinBindingRule.PERMANENT_JAVA_ID, new GoNoISSProteinBindingRule(eco));
		namedRules.put(GoBindingCheckWithFieldRule.PERMANENT_JAVA_ID, new GoBindingCheckWithFieldRule(eco));
		namedRules.put(GoIEPRestrictionsRule.PERMANENT_JAVA_ID, new GoIEPRestrictionsRule(graph, eco));
		namedRules.put(GoIPICatalyticActivityRestrictionsRule.PERMANENT_JAVA_ID, new GoIPICatalyticActivityRestrictionsRule(graph, eco));
		namedRules.put(GoICAnnotationRule.PERMANENT_JAVA_ID, new GoICAnnotationRule(eco));
		namedRules.put(GoIDAAnnotationRule.PERMANENT_JAVA_ID, new GoIDAAnnotationRule(eco));
		namedRules.put(GoIPIAnnotationRule.PERMANENT_JAVA_ID, new GoIPIAnnotationRule(eco));
		namedRules.put(GoNDAnnotationRule.PERMANENT_JAVA_ID, new GoNDAnnotationRule(eco));
		namedRules.put(GOReciprocalAnnotationRule.PERMANENT_JAVA_ID, new GOReciprocalAnnotationRule(graph, eco));
		namedRules.put(GoMultipleTaxonRule.PERMANENT_JAVA_ID, new GoMultipleTaxonRule(graph));
		namedRules.put(GoNoHighLevelTermAnnotationRule.PERMANENT_JAVA_ID, new GoNoHighLevelTermAnnotationRule(graph, eco));
		namedRules.put(GoAnnotationPredictionRule.PERMANENT_JAVA_ID, new GoAnnotationPredictionRule(graph));
		namedRules.put(GoAnnotationExperimentalPredictionRule.PERMANENT_JAVA_ID, new GoAnnotationExperimentalPredictionRule());
		logger.info("Finished preparing ontology checks");
	}

	private static OWLGraphWrapper getGO(ParserWrapper p, List<String> ont) {
		try {
			OWLGraphWrapper graph = null;
			for (String location : ont) {
				if (graph == null) {
					graph = p.parseToOWLGraph(location);
				}
				else {
					OWLOntology gorelOwl = p.parseOWL(location);
					graph.addSupportOntology(gorelOwl);
				}
			}
			return graph;
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static OntologyMapperPair<TraversingEcoMapper> getEco(ParserWrapper p, String location) {
		try {
			OntologyMapperPair<TraversingEcoMapper> pair = EcoMapperFactory.createTraversingEcoMapper(p, location);
			return pair;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	protected AnnotationRule getClassForName(String className) throws Exception {
		AnnotationRule rule = namedRules.get(className);
		if (rule != null) {
			return rule;
		}
		return super.getClassForName(className);
	}
	
}
