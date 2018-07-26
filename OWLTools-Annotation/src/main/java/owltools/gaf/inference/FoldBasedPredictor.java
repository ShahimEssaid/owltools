package owltools.gaf.inference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.geneontology.reasoner.ExpressionMaterializingReasoner;
import org.geneontology.reasoner.ExpressionMaterializingReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorAdapter;

import com.google.common.base.Optional;

import owltools.gaf.Bioentity;
import owltools.gaf.ExtensionExpression;
import owltools.gaf.GafDocument;
import owltools.gaf.GeneAnnotation;
import owltools.graph.OWLGraphWrapper;
import owltools.vocab.OBOUpperVocabulary;

/**
 * Use a reasoner to find more specific named classes for annotations with extension expressions.
 * 
 * see http://code.google.com/p/owltools/wiki/AnnotationExtensionFolding
 * @author cjm
 */
public class FoldBasedPredictor extends AbstractAnnotationPredictor implements AnnotationPredictor {

	private static final Logger LOG = Logger.getLogger(FoldBasedPredictor.class);
	
	private OWLReasoner reasoner = null;
	private Set<OWLClass> relevantClasses;
	
	private OWLObjectProperty partOf;
	private OWLObjectProperty occursIn;
	private Set<OWLObjectProperty> defaultProperties;

	private boolean isInitialized = false;
	private final boolean throwExceptions;
	
	public FoldBasedPredictor(GafDocument gafDocument, OWLGraphWrapper graph, boolean throwExceptions) {
		super(gafDocument, graph);
		this.throwExceptions = throwExceptions;
		isInitialized = init();
		Logger.getLogger("org.semanticweb.elk").setLevel(Level.ERROR);
	}

	@Override
	public boolean isInitialized() {
		return isInitialized;
	}

	public boolean init() {
		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
		reasoner = reasonerFactory.createReasoner(getGraph().getSourceOntology());
		boolean consistent = reasoner.isConsistent();
		if (!consistent) {
			LOG.error("The ontology is not consistent. Impossible to make proper predictions.");
			if (throwExceptions) {
				throw new RuntimeException("The ontology is not consistent. Impossible to make proper predictions.");	
			}
			return false;
		}
		relevantClasses = new HashSet<OWLClass>();
		// add GO
		// biological process
		OWLClass bp = getGraph().getOWLClassByIdentifier("GO:0008150");
		addRelevant(bp, reasoner, getGraph(), relevantClasses);
		
		// molecular function
		OWLClass mf = getGraph().getOWLClassByIdentifier("GO:0003674");
		addRelevant(mf, reasoner, getGraph(), relevantClasses);
		
		// cellular component
		OWLClass cc = getGraph().getOWLClassByIdentifier("GO:0005575");
		addRelevant(cc, reasoner, getGraph(), relevantClasses);
		
		// properties
		partOf = OBOUpperVocabulary.BFO_part_of.getObjectProperty(getGraph().getDataFactory());
		occursIn = OBOUpperVocabulary.BFO_occurs_in.getObjectProperty(getGraph().getDataFactory());
		defaultProperties = Collections.unmodifiableSet(new HashSet<OWLObjectProperty>(Arrays.asList(partOf, occursIn)));
		
		if (relevantClasses.isEmpty()) {
			LOG.error("No valid classes found for fold based prediction folding.");
			if (throwExceptions) {
				throw new RuntimeException("No valid classes found for fold based prediction folding.");
			}
			return false;
		}
		return true;
	}
	
	private static void addRelevant(OWLClass root, OWLReasoner r, OWLGraphWrapper g, Set<OWLClass> relevant) {
		if (root != null) {
			Set<OWLClass> candidates = r.getSubClasses(root, false).getFlattened();
			for (OWLClass candidate : candidates) {
				// hack to restric to GO ids!
				String identifier = g.getIdentifier(candidate.getIRI());
				if (identifier.startsWith("GO:")) {
					relevant.add(candidate);
				}
			}
		}
	}

	@Override
	public List<Prediction> predictForBioEntities(Map<Bioentity, ? extends Collection<GeneAnnotation>> annMap) {
		final List<Prediction> predictions = new ArrayList<Prediction>();
		final OWLGraphWrapper g = getGraph();
		final OWLDataFactory f = g.getDataFactory();
		final OWLOntologyManager m = g.getManager();
		
		// step 0: generate a throw away ontology which imports the source ontology
		final OWLOntology generatedContainer;
		try {
			generatedContainer = g.getManager().createOntology(IRI.generateDocumentIRI());
			// import source
			OWLOntology source = g.getSourceOntology();
			OWLOntologyID sourceId = source.getOntologyID();
			Optional<IRI> ontologyIRI = sourceId.getOntologyIRI();
			if (ontologyIRI.isPresent()) {
				OWLImportsDeclaration sourceImportDeclaration = f.getOWLImportsDeclaration(ontologyIRI.get());
				m.applyChange(new AddImport(generatedContainer, sourceImportDeclaration));
			}
			else {
				String msg = "Could not setup container ontology, missing ontology ID";
				LOG.error(msg);
				throw new RuntimeException(msg);
			}
		}
		catch(Exception e) {
			String msg = "Could not setup container ontology";
			LOG.error(msg, e);
			if (throwExceptions) {
				throw new RuntimeException(msg, e);
			}
			return predictions;
		}

		ExpressionMaterializingReasoner reasoner = null;
		try {
			// step 1: prepare ontology
			final Map<OWLClass, PredicationDataContainer> sourceData = new HashMap<OWLClass, PredicationDataContainer>();
			final Map<Bioentity, Set<OWLClass>> allExistingAnnotations = new HashMap<Bioentity, Set<OWLClass>>();
			final Map<Bioentity, Set<OWLClass>> allGeneratedClasses = generateAxioms(generatedContainer, annMap, allExistingAnnotations, sourceData);
			
			// step 2: reasoner
			reasoner = new ExpressionMaterializingReasonerFactory(new ElkReasonerFactory()).createReasoner(generatedContainer);
			reasoner.setIncludeImports(true);
			LOG.info("Materializing expressions for props: "+defaultProperties);
			reasoner.materializeExpressions(defaultProperties);
			
			
			// step 3: find folded classes
			BioentityLoop: for(Entry<Bioentity, Set<OWLClass>> entry : allGeneratedClasses.entrySet()) {
				final Set<OWLClass> used = new HashSet<OWLClass>();
				final Bioentity e = entry.getKey();
				final Set<OWLClass> existing = allExistingAnnotations.get(e);
				final Set<OWLClass> generatedClasses = entry.getValue();
				
				// step 3.1: check for direct equivalent classes
				final Set<OWLClass> checkForDirectSuper = new HashSet<OWLClass>(generatedClasses);
				for(OWLClass generated : generatedClasses) {
					Set<OWLClass> equivalents = reasoner.getEquivalentClasses(generated).getEntitiesMinus(generated);
					final PredicationDataContainer source = sourceData.get(generated);
					for (OWLClass equivalentCls : equivalents) {
						if (equivalentCls.isBottomEntity()) {
							// if the class is unsatisfiable skip the bioentity, no safe prediction possible
							LOG.warn("skipping folding prediction for '"+e.getId()+"' due unsatisfiable expression.");
							break BioentityLoop;
						}
						if (generatedClasses.contains(equivalentCls)) {
							continue;
						}
						if (relevantClasses.contains(equivalentCls) == false) {
							continue;
						}
						checkForDirectSuper.remove(generated);
						if (existing != null && existing.contains(equivalentCls)) {
							continue;
						}
						if (used.add(equivalentCls) && source != null) {
							Prediction prediction = getPrediction(source.ann, equivalentCls, e.getId(), source.ann.getCls());
							prediction.setReason(generateReason(equivalentCls, source.cls, source.extensionExpression, source.expressions, source.evidence, g));
							predictions.add(prediction);
						}
					}
				}
				
				// step 3.2: check for direct super classes (including default properties such as part_of and occurs_in), 
				// but only for classes which did not have a named relevant equivalent class
				for(final OWLClass generated : checkForDirectSuper) {
					Set<OWLClass> parents = reasoner.getSuperClasses(generated, true).getFlattened();
					final PredicationDataContainer source = sourceData.get(generated);
					for (final OWLClass parent : parents) {
						if (parent.isBuiltIn()) {
							continue;
						}
						if (generatedClasses.contains(parent)) {
							continue;
						}
						if (relevantClasses.contains(parent) == false) {
							continue;
						}
						if (existing != null && existing.contains(parent)) {
							continue;
						}
						if (used.add(parent) && source != null) {
							Prediction prediction = getPrediction(source.ann, parent, e.getId(), source.ann.getCls());
							prediction.setReason(generateReason(parent, source.cls, source.extensionExpression, source.expressions, source.evidence, g));
							predictions.add(prediction);
						}
					}
					// check also for occurs_in and part_of
					Set<OWLClassExpression> superClassesExpressions = reasoner.getSuperClassExpressions(generated, true);
					for (final OWLClassExpression ce : superClassesExpressions) {
						ce.accept(new OWLClassExpressionVisitorAdapter(){

							@Override
							public void visit(OWLObjectSomeValuesFrom svf) {
								OWLClassExpression superClsExpr = svf.getFiller();
								OWLObjectPropertyExpression p = svf.getProperty();
								if(defaultProperties.contains(p) && superClsExpr.isAnonymous() == false) {
									final OWLClass cls = superClsExpr.asOWLClass();
									if (cls.isBuiltIn()) {
										return;
									}
									if (relevantClasses.contains(cls) == false) {
										return;
									}
									if (existing != null && existing.contains(cls)) {
										return;
									}
									if (used.add(cls) && source != null) {
										Prediction prediction = getPrediction(source.ann, cls, e.getId(), source.ann.getCls());
										prediction.setReason(generateReason(cls, source.cls, source.extensionExpression, source.expressions, source.evidence, g));
										predictions.add(prediction);
									}
								}
							}
							
						});
					}
				}
			}
			return predictions;
		} finally {
			if (reasoner != null) {
				reasoner.dispose();
			}
			if (generatedContainer != null) {
				m.removeOntology(generatedContainer);
			}
		}
	}
	
	private static class PredicationDataContainer {
		final GeneAnnotation ann;
		final OWLClass cls;
		final List<ExtensionExpression> expressions;
		final OWLClassExpression extensionExpression;
		final String evidence;
		
		PredicationDataContainer(GeneAnnotation source, OWLClass annotatedCls, String evidence,
				OWLClassExpression extensionExpression, List<ExtensionExpression> expressions) {
			super();
			this.ann = source;
			this.cls = annotatedCls;
			this.extensionExpression = extensionExpression;
			this.expressions = expressions;
			this.evidence = evidence;
		}
	}
	
	private Map<Bioentity, Set<OWLClass>> generateAxioms(OWLOntology generatedContainer, Map<Bioentity, ? extends Collection<GeneAnnotation>> annMap, Map<Bioentity, Set<OWLClass>> allExistingAnnotations, Map<OWLClass, PredicationDataContainer> sourceData) {
		final OWLGraphWrapper g = getGraph();
		final OWLDataFactory f = g.getDataFactory();
		final OWLOntologyManager m = g.getManager();
		
		Map<Bioentity, Set<OWLClass>> allGeneratedClasses = new HashMap<Bioentity, Set<OWLClass>>();
		for(Entry<Bioentity, ? extends Collection<GeneAnnotation>> entry : annMap.entrySet()) {
			Set<OWLClass> generatedClasses = new HashSet<OWLClass>();
			Set<OWLClass> existingAnnotations = new HashSet<OWLClass>();
			Bioentity e = entry.getKey();
			for (GeneAnnotation ann : entry.getValue()) {
				// skip ND evidence annotations
				String evidenceString = ann.getShortEvidence();
				if ("ND".equals(evidenceString)) {
					continue;
				}
				// parse annotation cls
				String annotatedToClassString = ann.getCls();
				OWLClass annotatedToClass = g.getOWLClassByIdentifierNoAltIds(annotatedToClassString);
				if (annotatedToClass == null) {
					LOG.warn("Skipping annotation for prediction. Could not find cls for id: "+annotatedToClassString);
					continue;
				}
				// add annotation class (and its super classes as known annotation) 
				existingAnnotations.add(annotatedToClass);
				existingAnnotations.addAll(reasoner.getSuperClasses(annotatedToClass, false).getFlattened());
				
				// parse c16 expressions
				List<List<ExtensionExpression>> extensionExpressionGroups = ann.getExtensionExpressions();
				if (extensionExpressionGroups != null && !extensionExpressionGroups.isEmpty()) {
					for (List<ExtensionExpression> group : extensionExpressionGroups) {
						Set<OWLClassExpression> units = new HashSet<OWLClassExpression>();
						for (ExtensionExpression ext : group) {
							String extClsString = ext.getCls();
							String extRelString = ext.getRelation();
							OWLClass extCls = f.getOWLClass(g.getIRIByIdentifier(extClsString));
							OWLObjectProperty extRel = g.getOWLObjectPropertyByIdentifier(extRelString);
							if (extRel == null) {
								continue;
							}
							units.add(f.getOWLObjectSomeValuesFrom(extRel, extCls));
						}
						if (units.isEmpty()) {
							continue;
						}
						units.add(annotatedToClass);
						final OWLClassExpression groupExpression = f.getOWLObjectIntersectionOf(units);
						OWLClass generatedClass = f.getOWLClass(IRI.generateDocumentIRI());
						OWLAxiom axiom = f.getOWLEquivalentClassesAxiom(generatedClass, groupExpression);
						m.addAxiom(generatedContainer, axiom);
						generatedClasses.add(generatedClass);
						sourceData.put(generatedClass, new PredicationDataContainer(ann, annotatedToClass, evidenceString, groupExpression, group));
					}
				}
			}
			if (generatedClasses.isEmpty() == false) {
				allGeneratedClasses.put(e, generatedClasses);
				allExistingAnnotations.put(e, existingAnnotations);
			}
		}
		return allGeneratedClasses;
	}

	private String generateReason(OWLClass foldedClass, OWLClass annotatedToClass, OWLClassExpression groupExpression, List<ExtensionExpression> expressions, String evidence, OWLGraphWrapper g) {
		StringBuilder sb = new StringBuilder();
		sb.append(g.getIdentifier(foldedClass));
		sb.append('\t');
		String foldedClassLabel = g.getLabel(foldedClass);
		if (foldedClassLabel != null) {
			sb.append(foldedClassLabel);
		}
		sb.append('\t');
		sb.append(FoldBasedPredictor.class.getSimpleName());
		sb.append('\t');
		sb.append(g.getIdentifier(annotatedToClass));
		sb.append('\t');
		String annotatedToClassLabel = g.getLabel(annotatedToClass);
		if (annotatedToClassLabel != null) {
			sb.append(annotatedToClassLabel);
		}
		for(ExtensionExpression ext : expressions) {
			sb.append('\t');
			sb.append(ext.getRelation());
			sb.append('\t');
			sb.append(ext.getCls());
			sb.append('\t');
			OWLClass extCls = g.getOWLClassByIdentifierNoAltIds(ext.getCls());
			if (extCls != null) {
				String extClsLabel = g.getLabel(extCls);
				if (extClsLabel != null) {
					sb.append(extClsLabel);
				}
			}
		}
		sb.append('\t');
		sb.append(evidence);
		return sb.toString();
	}
	
	protected Prediction getPrediction(GeneAnnotation ann, OWLClass c, String bioentity, String with) {
		GeneAnnotation annP = new GeneAnnotation(ann);
		annP.setBioentity(bioentity);
		annP.setCls(getGraph().getIdentifier(c));
		annP.setEvidence("IC", null);
		annP.setWithInfos(Collections.singleton(with));
		annP.setAssignedBy("GOC-OWL");
		Prediction prediction = new Prediction(annP);
		return prediction;
	}

	@Override
	public void dispose() {
		if (reasoner != null) {
			reasoner.dispose();
		}
	}

}
