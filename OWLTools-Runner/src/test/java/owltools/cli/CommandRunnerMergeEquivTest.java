package owltools.cli;

import org.junit.Test;

/**
 * Tests for {@link CommandRunner}.
 * 
 * these are somewhat ad-hoc at the moment - output is written to stdout;
 * no attempt made to check results
 * 
 */
public class CommandRunnerMergeEquivTest extends AbstractCommandRunnerTest {
	
	@Test
	public void testMergeEquivalent() throws Exception {
		String obo = "http://purl.obolibrary.org/obo";
		load("merge-equiv-test.obo");
		//run("--list-class-axioms NCBITaxon:7954");
		
		//run("--reasoner hermit --merge-equivalent-classes -f MA -f FMA -t U");
		run("--merge-equivalent-classes -f MA -f FMA -t U");
		run("--reasoner hermit");
		run("--merge-equivalent-classes -f HP -t MP");
		run("-o -f obo --no-check target/equiv-merged.obo");

	}
	
	@Test
	public void testMMergeEquivalentReverseAnnotations() throws Exception {
		String obo = "http://purl.obolibrary.org/obo";
		load("merge-equiv-test.obo");
		//run("--list-class-axioms NCBITaxon:7954");
		
		//run("--reasoner hermit --merge-equivalent-classes -f MA -f FMA -t U");
		run("--merge-equivalent-classes -f MA -f FMA -t U -sa");
		run("--reasoner hermit");
		run("--merge-equivalent-classes -f HP -t MP -sa");
		run("-o -f obo --no-check target/equiv-merged2.obo");

	}
	
}
