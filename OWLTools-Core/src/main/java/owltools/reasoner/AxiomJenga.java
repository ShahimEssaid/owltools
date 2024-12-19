package owltools.reasoner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.InferredAxiomGenerator;
import org.semanticweb.owlapi.util.InferredOntologyGenerator;
import org.semanticweb.owlapi.util.InferredSubClassAxiomGenerator;

/**
 * Utility for abductive reasoning
 * 
 * @author cjm
 *
 */
public class AxiomJenga {

    private static final Logger LOG = Logger.getLogger(AxiomJenga.class);

    public static JengaTower makeJengaTower(OWLOntology ontology, OWLReasoner reasoner) throws OWLOntologyCreationException {
        return makeJengaTower(ontology, reasoner, false);
    }
   
    public static JengaTower makeJengaTower(OWLOntology ontology, OWLReasoner reasoner,
            boolean isReplaceRedundant) throws OWLOntologyCreationException {
        
        Map<OWLAxiom, Integer> axiomJengaScoreMap = new HashMap<>();
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();
        List<InferredAxiomGenerator<? extends OWLAxiom>> gens =
                new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
        
        // TODO
        gens.add(new InferredSubClassAxiomGenerator());
        InferredOntologyGenerator generator =
                new InferredOntologyGenerator(reasoner, gens);
        LOG.info("Using these axiom generators:");
        for (InferredAxiomGenerator inf: generator.getAxiomGenerators()) {
            LOG.info("    " + inf);
        }
        OWLOntology newAxiomOntology;
        newAxiomOntology = manager.createOntology();
        generator.fillOntology(dataFactory, newAxiomOntology);
        
        Set<OWLAxiom> entailedAxioms = newAxiomOntology.getAxioms();
        Set<OWLAxiom> redundantAxioms = new HashSet<>();
        
        OWLAxiom axiomWithMaxJenga = null;
        int maxJengaScore = 0;
        int totJengaScore = 0;
        int totEquivJengaScore = 0;
        int n = 0;
        int nEquiv = 0;
        int tot = ontology.getLogicalAxioms().size();
        for (OWLAxiom axiom : ontology.getLogicalAxioms()) {
            n++;
            if (n % 100 == 1) {
                LOG.info("Axiom "+n+"/"+tot);
            }
            manager.removeAxiom(ontology, axiom);
            reasoner.flush();      
            newAxiomOntology = manager.createOntology();
            generator.fillOntology(dataFactory, newAxiomOntology);
            Set<OWLAxiom> entailedAxiomsX = newAxiomOntology.getAxioms();
            Set<OWLAxiom> unjustifiedAxioms = new HashSet<>();
            for (OWLAxiom ea : entailedAxioms) {
                if (!entailedAxiomsX.contains(ea)) {
                    unjustifiedAxioms.add(ea);
                }              
            }
            int s = unjustifiedAxioms.size();
            axiomJengaScoreMap.put(axiom, s);
            if (s > maxJengaScore) {
                maxJengaScore = s;
                axiomWithMaxJenga = axiom;
            }
            if (s == 0) {
                redundantAxioms.add(axiom);
            }
            totJengaScore += s;
            
            if (axiom instanceof OWLEquivalentClassesAxiom) {
                nEquiv ++;
                totEquivJengaScore += s;
            }
            
            // put it back, if it is either non-redundant or we choose to do this
            if (s > 0 || isReplaceRedundant) {
                manager.addAxiom(ontology, axiom);
            }
        }
        JengaTower jt = new JengaTower();
        jt.axiomJengaScoreMap = axiomJengaScoreMap;
        List<OWLAxiom> aa = new ArrayList<>(axiomJengaScoreMap.keySet());
        aa.sort( (OWLAxiom a1, OWLAxiom a2) -> 
            axiomJengaScoreMap.get(a1) - axiomJengaScoreMap.get(a2) );
        jt.sortedAxioms = aa;
        jt.maxJenga = maxJengaScore;
        jt.redundantAxioms = redundantAxioms;
        jt.axiomWithMaxJenga = axiomWithMaxJenga;
        jt.avgJenga = totJengaScore/((double)n);
        if (nEquiv > 0)
            jt.avgEquivJenga = totEquivJengaScore/((double)nEquiv);
        for (OWLAxiom a : aa) {
            int s = axiomJengaScoreMap.get(a);
            if (s > 1) {
                LOG.info(a+" SCORE: " + s);
            }
        }
        
        return jt;
    }
    
    public static JengaTower makeJengaTowerNEW(OWLOntology ontology, OWLReasoner reasoner,
            boolean isReplaceRedundant) throws OWLOntologyCreationException {
        
        Map<OWLAxiom, Integer> axiomJengaScoreMap = new HashMap<>();
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();
        List<InferredAxiomGenerator<? extends OWLAxiom>> gens =
                new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
        
        // TODO
        gens.add(new InferredSubClassAxiomGenerator());
        InferredOntologyGenerator generator =
                new InferredOntologyGenerator(reasoner, gens);
        LOG.info("Using these axiom generators:");
        for (InferredAxiomGenerator inf: generator.getAxiomGenerators()) {
            LOG.info("    " + inf);
        }
        OWLOntology newAxiomOntology;
        newAxiomOntology = manager.createOntology();
        generator.fillOntology(dataFactory, newAxiomOntology);
        
        Set<OWLAxiom> entailedAxioms = newAxiomOntology.getAxioms();
        Set<OWLAxiom> redundantAxioms = new HashSet<>();
        
        OWLAxiom axiomWithMaxJenga = null;
        int maxJengaScore = 0;
        int totJengaScore = 0;
        int totEquivJengaScore = 0;
        int n = 0;
        int nEquiv = 0;
        int tot = ontology.getLogicalAxioms().size();
        for (OWLAxiom axiom : ontology.getLogicalAxioms()) {
            n++;
            if (n % 100 == 1) {
                LOG.info("Axiom "+n+"/"+tot);
            }
            manager.removeAxiom(ontology, axiom);
            reasoner.flush();
            reasoner.isEntailed(axiom);
            if (axiom instanceof OWLSubClassOfAxiom) {
                OWLSubClassOfAxiom sca = (OWLSubClassOfAxiom)axiom;
                reasoner.getSuperClasses(sca.getSubClass(), false);
            }
            newAxiomOntology = manager.createOntology();
            generator.fillOntology(dataFactory, newAxiomOntology);
            Set<OWLAxiom> entailedAxiomsX = newAxiomOntology.getAxioms();
            Set<OWLAxiom> unjustifiedAxioms = new HashSet<>();
            for (OWLAxiom ea : entailedAxioms) {
                if (!entailedAxiomsX.contains(ea)) {
                    unjustifiedAxioms.add(ea);
                }              
            }
            int s = unjustifiedAxioms.size();
            axiomJengaScoreMap.put(axiom, s);
            if (s > maxJengaScore) {
                maxJengaScore = s;
                axiomWithMaxJenga = axiom;
            }
            if (s == 0) {
                redundantAxioms.add(axiom);
            }
            totJengaScore += s;
            
            if (axiom instanceof OWLEquivalentClassesAxiom) {
                nEquiv ++;
                totEquivJengaScore += s;
            }
            
            // put it back, if it is either non-redundant or we choose to do this
            if (s > 0 || isReplaceRedundant) {
                manager.addAxiom(ontology, axiom);
            }
        }
        JengaTower jt = new JengaTower();
        jt.axiomJengaScoreMap = axiomJengaScoreMap;
        List<OWLAxiom> aa = new ArrayList<>(axiomJengaScoreMap.keySet());
        aa.sort( (OWLAxiom a1, OWLAxiom a2) -> 
            axiomJengaScoreMap.get(a1) - axiomJengaScoreMap.get(a2) );
        jt.sortedAxioms = aa;
        jt.maxJenga = maxJengaScore;
        jt.redundantAxioms = redundantAxioms;
        jt.axiomWithMaxJenga = axiomWithMaxJenga;
        jt.avgJenga = totJengaScore/((double)n);
        if (nEquiv > 0)
            jt.avgEquivJenga = totEquivJengaScore/((double)nEquiv);
        for (OWLAxiom a : aa) {
            int s = axiomJengaScoreMap.get(a);
            if (s > 1) {
                LOG.info(a+" SCORE: " + s);
            }
        }
        
        return jt;
    }
    
    public boolean isEntailed(OWLAxiom axiom, OWLReasoner reasoner) {
        return false;
        
    }
}
