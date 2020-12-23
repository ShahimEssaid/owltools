package owltools.cli.sim2;

import org.junit.Ignore;
import org.junit.Test;

import owltools.cli.AbstractCommandRunnerTest;
import owltools.cli.CommandRunner;
import owltools.cli.Sim2CommandRunner;

/**
 * Tests for {@link CommandRunner}.
 * 
 * these are somewhat ad-hoc at the moment - output is written to stdout;
 * no attempt made to check results
 * 
 */
public class MapAboxTest extends AbstractCommandRunnerTest {
	
	protected void init() {
		runner = new Sim2CommandRunner();
	}

	@Ignore
	@Test
	public void testSimRunnerSubset() throws Exception {
		init();
		load("mgi-test-ns.owl");
		//run("--reasoner hermit");
		run("--init-reasoner -r hermit");
		System.out.println("reasonerX = "+runner.reasoner);
		//run("--reasoner-query -r hermit MP_0002064");
		run("--reasoner-query MP_0002064");
		System.out.println("reasonerY = "+runner.reasoner);
		//run("-a MP:0002064");
		//run("--map-abox-to-namespace http://purl.obolibrary.org/obo/HP_");
		run("--reasoner-dispose");
		
	}
	
	
}
