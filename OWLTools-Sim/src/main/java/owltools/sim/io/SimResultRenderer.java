package owltools.sim.io;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;

import owltools.graph.OWLGraphWrapper;
import owltools.io.OWLPrettyPrinter;
import owltools.sim2.SimpleOwlSim.ScoreAttributePair;
import owltools.sim2.SimpleOwlSim.ScoreAttributesPair;
import owltools.util.ClassExpressionPair;

public interface SimResultRenderer {

	public void printComment(CharSequence comment);

	public void printAttributeSim(AttributesSimScores simScores,
			OWLGraphWrapper graph);
	
	public void printAttributeSim(AttributesSimScores simScores, OWLGraphWrapper graph,
			OWLPrettyPrinter owlpp);
	
	public void printAttributeSimWithIndividuals(AttributesSimScores simScores,
			OWLPrettyPrinter owlpp, OWLGraphWrapper g, OWLNamedIndividual i,
			OWLNamedIndividual j);

	public abstract void printIndividualPairSim(IndividualSimScores scores, OWLPrettyPrinter owlpp,
			OWLGraphWrapper graph);

	
	public static class AttributesSimScores {
		
		public final OWLClass a;
		public final OWLClass b;
		public boolean isFiltered = false;
		public boolean isBestMatch = false;
		
		/**
		 * @param a
		 * @param b
		 */
		public AttributesSimScores(OWLClass a, OWLClass b) {
			this.a = a;
			this.b = b;
		}
		
		public Double simJScore = null;
		public String simjScoreLabel = null;
		
		public Double AsymSimJScore = null;
		public String AsymSimJScoreLabel = null;
		
		public ScoreAttributePair lcsScore = null;
		public String lcsScorePrefix = null;
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((a == null) ? 0 : a.hashCode());
			result = prime * result + ((b == null) ? 0 : b.hashCode());
			return result;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AttributesSimScores other = (AttributesSimScores) obj;
			if (a == null) {
				if (other.a != null)
					return false;
			} else if (!a.equals(other.a))
				return false;
			if (b == null) {
				if (other.b != null)
					return false;
			} else if (!b.equals(other.b))
				return false;
			return true;
		}
		
	}
	
	public static class IndividualSimScores {
		
		public final OWLNamedIndividual i;
		public final OWLNamedIndividual j;
		
		/**
		 * @param i
		 * @param j
		 */
		public IndividualSimScores(OWLNamedIndividual i, OWLNamedIndividual j) {
			this.i = i;
			this.j = j;
		}
		
		public int numberOfElementsI;
		public int numberOfElementsJ;
		
		public ScoreAttributesPair maxIC = null;
		public String maxICLabel = null;
		
		public Double simjScore = null;
		public String simjScoreLabel = null;
		
		public ScoreAttributesPair bmaAsymIC = null;
		public String bmaAsymICLabel = null;
		
		public ScoreAttributesPair bmaSymIC = null;
		public String bmaSymICLabel = null;
		
		public ScoreAttributesPair bmaAsymJ = null;
		public String bmaAsymJLabel = null;
		
		public ScoreAttributesPair bmaSymJ = null;
		public String bmaSymJLabel = null;
		
		public Double simGIC = null;
		public String simGICLabel = null;
	}

	public void dispose();

}
