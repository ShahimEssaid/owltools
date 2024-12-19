package owltools.reasoner;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;

public class JengaTower {
    public Map<OWLAxiom, Integer> axiomJengaScoreMap;
    public List<OWLAxiom> sortedAxioms;
    public Integer maxJenga;
    public OWLAxiom axiomWithMaxJenga;
    public Double avgJenga = 0.0;
    public Double avgEquivJenga = 0.0;
    public Set<OWLAxiom> redundantAxioms;

    public JengaTower() {
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "JengaTower [axiomJengaScoreMap=" + axiomJengaScoreMap.size()
        + ", sortedAxioms=" + sortedAxioms.size() + ", maxJenga=" + maxJenga
        + ", redundantAxioms=" + redundantAxioms.size()
                + " " + axiomWithMaxJenga
                + ", avgJenga=" + avgJenga + ", avgEquivJenga=" + avgEquivJenga
                + "]";
    }
    
    
}