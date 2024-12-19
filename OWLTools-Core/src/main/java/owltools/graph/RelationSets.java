package owltools.graph;

import java.util.ArrayList;
import java.util.List;

public class RelationSets {

    public static final String ISA_PARTOF = "is_a/part_of";
    public static final String REGULATES = "regulates/is_a/part_of";
    public static final String REGULATES_ONLY = "regulates/part_of";
    /*
     * Should be using this in most cases.
     */
    public static final String COMMON = "regulates/is_a/part_of/occurs_in";

	/*
	 * 
	 */
	public static List<String> getRelationSet(String relset) {

		List<String> rel_ids = new ArrayList<String>();
		if( relset != null ){
			if( relset.equals(ISA_PARTOF) ){
				rel_ids.add("BFO:0000050");
			}else if( relset.equals(REGULATES) ){
				rel_ids.add("BFO:0000050");
				rel_ids.add("RO:0002211");
				rel_ids.add("RO:0002212");
				rel_ids.add("RO:0002213");
			}else if( relset.equals(REGULATES_ONLY) ){
				rel_ids.add("RO:0002211");
				rel_ids.add("RO:0002212");
				rel_ids.add("RO:0002213");
			}else if( relset.equals(COMMON) ){
				rel_ids.add("BFO:0000050");
				rel_ids.add("BFO:0000066");
				rel_ids.add("RO:0002211");
				rel_ids.add("RO:0002212");
				rel_ids.add("RO:0002213");
			}
		}
		return rel_ids;		
	}
}
