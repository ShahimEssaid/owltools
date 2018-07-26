package owltools.mooncat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.OWLEntityRenamer;

import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

/**
 * 
 * Copies axioms form a source ontology to a target ontology using equivalence axioms from
 * a mapping ontology.
 * 
 * Optionally also check to make sure that copying the axioms do not result in incoherencies.
 * NOTE: works well with disjoint-sibs, see: https://github.com/monarch-initiative/mondo-build/issues/52
 * 
 * https://github.com/owlcollab/owltools/issues/230
 * 
 * @author cjm
 *
 */
public class AxiomCopier {
    
    private static Logger LOG = Logger.getLogger(AxiomCopier.class);
    
    /**
     * If true, copy axiom over even if it exists in target.
     * We may want to do this if we want to annotate an axiom with all sources
     */
    public boolean isCopyDuplicates = false;

    public boolean isCopyDuplicatesOnly = false;

    /**
     * If true, exclude annotation axiom if we already have seen the literal
     * (regardless of axiom)
     */
    public boolean isFreshLiteralsOnly = false;

    /**
     * If true, check all incoming axioms to ensure they do not cause
     * incoherency
     */
    public boolean isTestForCoherency = false;
    
    /**
     * We have 2 strategies; see below
     */
    public boolean isUseConservative = true;
    
    public boolean isIncludeUnmapped = false;
    
    public OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
    
    public boolean isCopyLabelToExactSynonym = false;

    public boolean isIncludeInferred = false;
        
    
    /**
     * Simple IRI-value pairs representing an Annotation minus property
     * 
     * @author cjm
     *
     */
    class AnnTuple {
        IRI iri;
        String val;
        
        
        public AnnTuple(IRI iri, String val) {
            super();
            this.iri = iri;
            this.val = val;
        }
        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((iri == null) ? 0 : iri.hashCode());
            result = prime * result + ((val == null) ? 0 : val.hashCode());
            return result;
        }
        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            AnnTuple other = (AnnTuple) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (iri == null) {
                if (other.iri != null)
                    return false;
            } else if (!iri.equals(other.iri))
                return false;
            if (val == null) {
                if (other.val != null)
                    return false;
            } else if (!val.equals(other.val))
                return false;
            return true;
        }
        private AxiomCopier getOuterType() {
            return AxiomCopier.this;
        }
        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "AnnTuple [iri=" + iri + ", val=" + val + "]";
        }
        
        
        
    }
    
    /**
     * Makes a copy of axioms in source ontology, ready to be placed in targetOnt.
     * 
     * Note: does not actually place the axioms in targetOnt, but the target ontology
     * is used to avoid creating duplicates. Also used to build an exist of classes
     * to be used in mapping.
     * 
     * Note for non-logical axioms, when copying annotations we ignore the property
     * for the purposes of duplicate checking. Rationale: we may want to block
     * copying of an EXACT syn if we have already have the same string with the same class
     * for a RELATED syn.
     * 
     * If isTestForCoherency is set, reasoner-based checks are used
     * 
     * @param sourceOnt - ontology to copy axioms from
     * @param targetOnt - ontology to copy to (used to check for dupes and build index of classes)
     * @param mapOnt - ontology with equivalence axioms
     * @return copied and rewritten axioms
     * @throws OWLOntologyCreationException 
     */
    public Set<OWLAxiom> copyAxioms(OWLOntology sourceOnt, OWLOntology targetOnt, OWLOntology mapOnt) throws OWLOntologyCreationException {
        LOG.info("Copying axioms...");
        
        // axioms in targetOnt (minus annotations)
        Set<OWLAxiom> axiomsExisting  = new HashSet<>();
        
        // all simple annotations in T
        Set<AnnTuple> annTuples = new HashSet();
        
        // all classes in T
        Set<OWLClass> classesInTarget = new HashSet<>();
        
        // reflexive mappings of classes S->T
        Map<OWLClass, OWLClass> eqMap = new HashMap<>();
        
        // all literals encountered
        Set<String> literals = new HashSet<String>();
        
        
        
        OWLDataFactory df = targetOnt.getOWLOntologyManager().getOWLDataFactory();
        
        Set<OWLAxiom> targetAxioms = targetOnt.getAxioms(Imports.EXCLUDED);
        
        // build index of what we have already, to avoid dupes
        for (OWLAxiom ax : targetAxioms) {
            
            // add non-annotation part
            axiomsExisting.add(ax.getAxiomWithoutAnnotations());
            
            if (ax.isLogicalAxiom()) {
                
                // add subclasses to index of classes to be mapped to
                if (ax instanceof OWLSubClassOfAxiom &&
                       !((OWLSubClassOfAxiom)ax).getSubClass().isAnonymous()) {
                    classesInTarget.add( ((OWLSubClassOfAxiom)ax).getSubClass().asOWLClass() );
                }
            }
            else if (ax instanceof OWLAnnotationAssertionAxiom) {
                
                // keep simple IRI->value index.
                // motivation: avoid copying for example X relatedSyn Y if we have X exactSyn Y
                OWLAnnotationAssertionAxiom aax = (OWLAnnotationAssertionAxiom)ax;
                AnnTuple tup = getAnnTuple(aax);
                annTuples.add(tup);
                literals.add(tup.val);
            }
        }
        LOG.info("AnnTuples = "+annTuples.size());
        
        if (isIncludeUnmapped) {
            // seed with reflexive mappings
            for (OWLClass c : sourceOnt.getClassesInSignature(Imports.EXCLUDED)) {
                eqMap.put(c, c);
            }
        }
        
        // build equivalence map S->T, pointing source classes to target classes
        // this map is reflexive
        for (OWLAxiom ax : mapOnt.getAxioms(Imports.INCLUDED)) {
            if (ax instanceof OWLEquivalentClassesAxiom) {
                OWLEquivalentClassesAxiom eca = (OWLEquivalentClassesAxiom)ax;
                for (OWLClass c : eca.getNamedClasses()) {
                    if (classesInTarget.contains(c)) {
                        for (OWLClass xc : eca.getNamedClasses()) {
                            eqMap.put(xc, c);
                        }
                        continue;
                    }
                }
            }
        }        
        LOG.info("eqMap = "+eqMap.size());
        
        Set<OWLAxiom> axiomsToAdd  = new HashSet<>();
        
        Set<OWLAxiom> srcAxioms = sourceOnt.getAxioms(Imports.EXCLUDED);
        
        Set<OWLAxiom> inferredSrcAxioms = new HashSet<>();
        if (isIncludeInferred) {
            
            LOG.info("computing inferred source axioms; orig = "+srcAxioms.size());
            OWLReasoner reasoner = reasonerFactory.createReasoner(sourceOnt);
            for (OWLClass c : sourceOnt.getClassesInSignature()) {
                
                Set<OWLClass> ancs = new HashSet<>(reasoner.getSuperClasses(c, false).getFlattened());
                ancs.removeAll(reasoner.getSuperClasses(c, true).getFlattened());
                LOG.info("C= "+c +" ancs = "+ancs.size());
                for (OWLClass a : ancs) {
                    String id = c.getIRI().toString();
                    id = id.replaceAll(".*/", "");
                    id = id.replaceAll("_", ":");

                    OWLAnnotationProperty p = 
                            df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#source"));
                    OWLAnnotation ann = df.getOWLAnnotation(p, df.getOWLLiteral(id+"/inferred"));
                    OWLSubClassOfAxiom ax = df.getOWLSubClassOfAxiom(c, a, Collections.singleton(ann));
                    srcAxioms.add(ax);
                    inferredSrcAxioms.add(ax);
                }
                
            }
            LOG.info("computed inferred source axioms; total = "+srcAxioms.size());
        }
        
        if (isTestForCoherency) {
            LOG.info("testing for coherency");
            
            Set<OWLAxiom> badAxioms = findIncoherentAxioms(reasonerFactory, sourceOnt, targetOnt, mapOnt);
            System.out.println("ORIG:"+srcAxioms.size());
            System.out.println("INCOHERENCY-CAUSING AXIOMS:"+badAxioms.size());
            srcAxioms.removeAll(badAxioms);
            System.out.println("NEW:"+srcAxioms.size());
        }
      
        // get copied axioms
        for (OWLAxiom ax : srcAxioms) {
            
            OWLAxiom coreAxiom = ax.getAxiomWithoutAnnotations();
            OWLClass srcClass = null;
            if (ax.isLogicalAxiom()) {
                // LOGICAL
                
                Set<OWLClass> sig = ax.getClassesInSignature();
                
                if (sig.size() < 2) {
                    continue;
                }
                
                // of logical axioms, currently only subClassOf axioms (NC->NC) are copied
               if (ax instanceof OWLSubClassOfAxiom) {
                    OWLSubClassOfAxiom sca = (OWLSubClassOfAxiom)ax;
                    if (!sca.getSubClass().isAnonymous() && !sca.getSuperClass().isAnonymous()) {
                        srcClass = sca.getSubClass().asOWLClass();
                        OWLClass supc = sca.getSuperClass().asOWLClass();
                        if (eqMap.containsKey(supc) && eqMap.containsKey(srcClass)) {
                            
                            // make a new axiom, including annotation
                            OWLSubClassOfAxiom newAxiom = df.getOWLSubClassOfAxiom(eqMap.get(srcClass), 
                                    eqMap.get(supc), 
                                    anns(df, srcClass, false));
                            
                            // preserve original annotations for inferred
                            // (we previously stamp these)
                            if (inferredSrcAxioms.contains(ax)) {
                                newAxiom = df.getOWLSubClassOfAxiom(eqMap.get(srcClass), 
                                        eqMap.get(supc), 
                                        ax.getAnnotations());
                            }
                            
                            boolean isAdd = false;
                            // add copy to list
                            if (isCopyDuplicates ||
                                    !axiomsExisting.contains(newAxiom.getAxiomWithoutAnnotations())) {
                                isAdd = true;
                            }
                            if (isCopyDuplicatesOnly) {
                                isAdd = axiomsExisting.contains(newAxiom.getAxiomWithoutAnnotations());
                            }
                            if (isAdd) {
                                axiomsToAdd.add(newAxiom);
                            }
                        }
                    }
                    else if (!sca.getSubClass().isAnonymous() && sca.getSuperClass().isAnonymous()) {
                        // e.g. A SubClassOf R some B
                        srcClass = sca.getSubClass().asOWLClass();
                        OWLClassExpression supx = sca.getSuperClass();
                        if (supx instanceof OWLObjectSomeValuesFrom) {
                            OWLObjectSomeValuesFrom x = (OWLObjectSomeValuesFrom)supx;
                            if (!x.isAnonymous()) {
                                OWLClass supc = x.getFiller().asOWLClass();
                                if (eqMap.containsKey(supc) && eqMap.containsKey(srcClass)) {

                                    // make a new axiom, including annotation
                                    OWLSubClassOfAxiom newAxiom = df.getOWLSubClassOfAxiom(
                                            eqMap.get(srcClass), 
                                            df.getOWLObjectSomeValuesFrom(x.getProperty(), eqMap.get(supc)),
                                            anns(df, srcClass, false));

                                    boolean isAdd = false;
                                    // add copy to list
                                    if (isCopyDuplicates ||
                                            !axiomsExisting.contains(newAxiom.getAxiomWithoutAnnotations())) {
                                        isAdd = true;
                                    }
                                    if (isCopyDuplicatesOnly) {
                                        isAdd = axiomsExisting.contains(newAxiom.getAxiomWithoutAnnotations());
                                    }
                                    if (isAdd) {
                                        axiomsToAdd.add(newAxiom);
                                    }

                                }
                            }
                        }
                    }
                }
            }
            else if (ax instanceof OWLAnnotationAssertionAxiom){
                // NON-LOGICAL/ANNOTATION

                OWLAnnotationAssertionAxiom aax = (OWLAnnotationAssertionAxiom)ax;
                OWLAnnotationSubject subj = aax.getSubject();
                
                // do not copy annotations on blank nodes
                if (!(subj instanceof IRI)) {
                    continue;
                }
 
                srcClass = df.getOWLClass((IRI)subj);
                
                // only copy if (reflexive) equiv mapping has entry
                if (eqMap.containsKey(srcClass)) {
                    
                    // OBO convention: xref annotations are treated differently for
                    // axiom annotations (as are defs)
                    boolean isXrefAnn = 
                            aax.getProperty().getIRI().toString().contains("Synonym") || 
                            aax.getProperty().getIRI().toString().equals("http://purl.obolibrary.org/obo/IAO_0000115");
                    OWLAnnotationProperty ap1 = aax.getProperty();
                    Set<OWLAnnotationProperty> aps = new HashSet<>();
                    aps.add(ap1);
                    if (ap1.isLabel()) {
                        if (isCopyLabelToExactSynonym) {
                            OWLAnnotationProperty sp = 
                                    df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"));

                            aps.add(sp);
                        }
                    }
                    for (OWLAnnotationProperty ap: aps) {
                        OWLAnnotationAssertionAxiom newAxiom = df.getOWLAnnotationAssertionAxiom(ap, 
                                eqMap.get(srcClass).getIRI(),
                                aax.getValue(),
                                anns(df, srcClass, isXrefAnn));
                        AnnTuple tup = getAnnTuple(newAxiom);

                        boolean isDupe = annTuples.contains(tup) || 
                                axiomsExisting.contains(newAxiom.getAxiomWithoutAnnotations());
                        if (isFreshLiteralsOnly && literals.contains(tup.val))
                            isDupe = true;
                        if (isCopyDuplicates || !isDupe) {
                            axiomsToAdd.add(newAxiom);
                        }
                    }
                    axiomsToAdd.add(df.getOWLDeclarationAxiom(eqMap.get(srcClass)));
                }
            }
            else {
                //System.out.println("SKIP:"+ax);

            }
        }
        LOG.info("Axioms to add = "+axiomsToAdd.size());
        return axiomsToAdd;
    }

    /**
     * make annotations to be placed on axiom
     * 
     * @param df
     * @param srcClass
     * @param isXrefAnn if true use hasDbXref not source
     * @return annotations
     */
    private Set<? extends OWLAnnotation> anns(OWLDataFactory df, OWLClass srcClass, boolean isXrefAnn) {
        String iri = srcClass.getIRI().toString();
        iri = iri.replaceAll(".*/", "");
        iri = iri.replaceAll("_", ":");
        String pn = "source";
        if (isXrefAnn) {
            pn = "hasDbXref";
        }
        OWLAnnotationProperty p = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#"+pn));
        OWLAnnotation ann = df.getOWLAnnotation(p, df.getOWLLiteral(iri));
        return Collections.singleton(ann);
    }

    private AnnTuple getAnnTuple(OWLAnnotationAssertionAxiom aax) {
        String v;
        OWLAnnotationValue av = aax.getValue();
        if (av instanceof OWLLiteral) {
            v = ((OWLLiteral)av).getLiteral();
        }
        else {
            v = av.toString();
        }
        v = v.toLowerCase();
        v = v.replaceAll("^ +", "").replaceAll(" +$", "");
        return new AnnTuple((IRI)aax.getSubject(), v);
    }

    /**
     * Finds all incoherency-causing axioms in sourceOnt
     * 
     * One of two strategies is used:
     * 
     * strict (default):
     * 
     * combine all axioms, find unsat classes, create a module using SLME BOT from these,
     * and treat all axioms in this module as potentially suspect.
     * 
     * conservative:
     * 
     * greedy approach. Keep adding axioms from sourceOntology to a combo ontology, until
     * an unsat is found. If this happens, reject the axioms and carry on
     * 
     * note on greedy approach: this is generally less desirable as with a greedy apprpach we may accept a problem
     * axiom early and reject ok axioms (based on compatibility with already accepted bad axioms).
     * with a strict approach all potentially problematic axioms are treated equally.
     * 
     * 
     * @param rf
     * @param sourceOnt
     * @param targetOnt
     * @param mapOnt
     * @return
     * @throws OWLOntologyCreationException
     */
    public Set<OWLAxiom> findIncoherentAxioms(OWLReasonerFactory rf, 
            OWLOntology sourceOnt, OWLOntology targetOnt, OWLOntology mapOnt) throws OWLOntologyCreationException { 
        
        Set<OWLAxiom> badAxioms  = new HashSet<>();
        
        OWLOntologyManager mgr = targetOnt.getOWLOntologyManager();
        OWLDataFactory df = mgr.getOWLDataFactory();
        
        OWLOntology combo = mgr.createOntology(targetOnt.getAxioms(Imports.INCLUDED));
        mgr.addAxioms(combo, mapOnt.getAxioms(Imports.INCLUDED));
        
        // TODO: Order these
        Set<OWLAxiom> testAxioms = sourceOnt.getAxioms(Imports.INCLUDED);
        
        OWLReasoner reasoner = rf.createReasoner(combo);
        
        if (isUseConservative) {
            mgr.addAxioms(combo, testAxioms);
            reasoner.flush();
            Set<OWLClass> unsatisfiableClasses = reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom();
            ModuleType mtype = ModuleType.BOT;
            SyntacticLocalityModuleExtractor sme = new SyntacticLocalityModuleExtractor(mgr, combo, mtype);
            Set<OWLEntity> seeds = new HashSet<OWLEntity>(unsatisfiableClasses);
            Set<OWLAxiom> axioms = sme.extract(seeds);
            System.out.println("Axiom in unsat module: "+axioms.size());
            for (OWLAxiom ax : testAxioms) {
                if (axioms.contains(ax) || 
                        axioms.contains(ax.getAxiomWithoutAnnotations())) {
                    badAxioms.add(ax);
                }
            }
            System.out.println("Axioms to be filtered: "+axioms.size());

        }
        else {
        
            for (OWLAxiom ax : testAxioms) {
                System.out.println("TESTING: "+ax);
                mgr.addAxiom(combo, ax);
                reasoner.flush();
                Set<OWLClass> unsats = reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom();
                if (unsats.size() > 0) {
                    badAxioms.add(ax);
                    mgr.removeAxiom(combo, ax);
                }
            }
        }
        
        return badAxioms;
        
    }
    
    public class IdAssigner {
        public OWLOntology ontology;
        public String prefix;
        Integer lastId = 0;
        Set<String> taken = null;
    }
    
    public IRI getNextId(IdAssigner ida) {
        //int id = ida.lastId;
        Set<String> taken = ida.taken;
        
        if (taken == null) {
            taken = new HashSet<>();
            for (OWLClass cls : ida.ontology.getClassesInSignature()) {
                String frag = cls.getIRI().getFragment();
                String[] parts = frag.split("_");
                if (parts.length == 2) {
                    String id = parts[0] + ":" + parts[1];
                    taken.add(id);
                    String prefix = parts[0];
                    if (ida.prefix == null)
                        ida.prefix = prefix;
                    if (ida.prefix.equals(prefix)) {

                    }
                    else {
                        //
                    }
                }
                else {
                    // warn
                }
            }
        }
        String id = null;
        while (id == null) {
            ida.lastId ++;
            String nextid = ida.prefix + String.format(":%07d", ida.lastId);
            if (!taken.contains(nextid)) {
                id = nextid;
            }
        }
        
        return IRI.create(id);
        
    }
    
    public void remintOntologyIds(OWLOntology ontology, String prefix, boolean isCreateLabels) {
        IdAssigner ida = new IdAssigner();
        ida.ontology = ontology;
        ida.prefix = prefix;
        remintOntologyIds(ontology, ida, isCreateLabels);
    }
 
    /**
     * Mint new IDs for all classes in an ontology
     * 
     * @param ontology
     * @param ida
     */
    public void remintOntologyIds(OWLOntology ontology, IdAssigner ida, boolean isCreateLabels) {
        if (ida == null) {
            ida = new IdAssigner();
            ida.ontology = ontology;
        }
        OWLOntologyManager m = ontology.getOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        Map<IRI, IRI> iriMap = new HashMap<>();
        OWLEntityRenamer renamer = new OWLEntityRenamer(m, 
                ontology.getImportsClosure());
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange> ();
        for (OWLClass c : ontology.getClassesInSignature()) {
            IRI id = getNextId(ida);
            iriMap.put(c.getIRI(), id);
            List<OWLOntologyChange> ch = 
                    renamer.changeIRI(c.getIRI(), id);
            changes.addAll(ch);
            if (isCreateLabels) {
                String label = c.getIRI().getFragment();
                label = label.replaceAll("_", " ");
                OWLAnnotationValue value = df.getOWLLiteral(label);
                OWLAnnotationAssertionAxiom ax = 
                        df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), id, value);
                m.addAxiom(ontology, ax);
            }
        }
        m.applyChanges(changes);
    }
}
