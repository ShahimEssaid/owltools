package owltools.solrj;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import owltools.gaf.Bioentity;
import owltools.gaf.EcoTools;
import owltools.gaf.ExtensionExpression;
import owltools.gaf.GafDocument;
import owltools.gaf.GeneAnnotation;
import owltools.gaf.TaxonTools;
import owltools.graph.OWLGraphWrapper;
import owltools.graph.RelationSets;
import owltools.panther.PANTHERForest;
import owltools.panther.PANTHERTree;


/**
 * A very specific class for the specific use case of loading in a GAF-like document into a Solr index.
 * This method is very non-generic and GO-specific, and does not use the YAML configuration files to make
 * things easy for mirroring a BBOP-JS constrained SOlr index.
 */
@SuppressWarnings("deprecation")
public class GafSolrDocumentLoader extends AbstractSolrLoader {

	private static Logger LOG = Logger.getLogger(GafSolrDocumentLoader.class);

	EcoTools eco = null;
	TaxonTools taxo = null;
	PANTHERForest pset = null;
	String taxonSubsetName = "model_slim";
	String ecoSubsetName = null;


	GafDocument gafDocument;
	int doc_limit_trigger = 1000; // the number of documents to add before pushing out to solr
	//int doc_limit_trigger = 1; // the number of documents to add before pushing out to solr
	int current_doc_number = 0;

	public GafSolrDocumentLoader(String url) throws MalformedURLException {
		super(url);
	}

	/**
	 * Use for test purposes.
	 *
	 * @param server
	 * @param triggerLimit
	 */
	protected GafSolrDocumentLoader(SolrServer server, int triggerLimit) {
		super(server);
		this.doc_limit_trigger = triggerLimit;
	}

	public GafDocument getGafDocument() {
		return gafDocument;
	}

	public void setGafDocument(GafDocument gafDocument) {
		this.gafDocument = gafDocument;
	}

	public void setEcoTools(EcoTools inEco) {
		this.eco = inEco;
	}

	public void setTaxonTools(TaxonTools inTaxo) {
		this.taxo = inTaxo;
	}

	public void setPANTHERSet(PANTHERForest inPSet) {
		this.pset = inPSet;
	}

	public void setTaxonSubsetName(String taxonSubsetName) {
		this.taxonSubsetName = taxonSubsetName;
	}

	public void clearTaxonSubsetName() {
		this.taxonSubsetName = null;
	}

	public void setEcoSubsetName(String ecoSubsetName) {
		this.ecoSubsetName = ecoSubsetName;
	}

	public void clearEcoSubsetName() {
		this.ecoSubsetName = null;
	}

	@Override
	public void load() throws SolrServerException, IOException {
		gafDocument.index();
		LOG.info("Iteratively loading: " + gafDocument.getDocumentPath());
		Collection<Bioentity> bioentities = gafDocument.getBioentities();
		final int bioentityCount = bioentities.size();
		for (Bioentity e : bioentities) {
			add(e);
			current_doc_number++;
			if( current_doc_number % doc_limit_trigger == 0 ){
				LOG.info("Processed " + doc_limit_trigger + " bioentities at " + current_doc_number + " of " + bioentityCount + " and committing...");
				incrementalAddAndCommit();
			}
		}
		LOG.info("Doing cleanup commit.");
		incrementalAddAndCommit(); // pick up anything that we didn't catch
		LOG.info("Done.");
	}

	/**
	 * Helper class to hold taxon specific information.
	 */
	private class TaxonDetails {

		final String taxId;
		String taxLbl = null;

		List<String> taxIDClosure = new ArrayList<String>();
		List<String> taxLabelClosure = new ArrayList<String>();
		Map<String,String> taxonClosureMap = new HashMap<String,String>();

		// subset reflexive closure
		List<String> taxSubsetIDClosure = new ArrayList<String>();
		List<String> taxSubsetLabelClosure = new ArrayList<String>();
		Map<String,String> taxonSubsetClosureMap = new HashMap<String,String>();

		private TaxonDetails(String taxId) {
			this.taxId = taxId;
		}

		private void addToSolrDocument(SolrInputDocument bioentity_doc) {
			bioentity_doc.addField("taxon", taxId);

			if(taxLbl != null) {
				bioentity_doc.addField("taxon_label", taxLbl);
			}

			if (taxonClosureMap.isEmpty() == false) {
				bioentity_doc.addField("taxon_closure", taxIDClosure);
				bioentity_doc.addField("taxon_closure_label", taxLabelClosure);
				bioentity_doc.addField("taxon_closure_map", gson.toJson(taxonClosureMap));
			}

			if (taxonSubsetClosureMap.isEmpty() == false) {
				bioentity_doc.addField("taxon_subset_closure", taxSubsetIDClosure);
				bioentity_doc.addField("taxon_subset_closure_label", taxSubsetLabelClosure);
				bioentity_doc.addField("taxon_subset_closure_map", gson.toJson(taxonSubsetClosureMap));
			}
		}
	}

	/**
	 * This method will create one or two closures for any given taxon id:
	 * <ol>
	 * <li>Normal reflexive closure with all ancestors</li>
	 * <li>IF {@link #taxonSubsetName} not null: intersection of the closure
	 * with the subset, plus the taxon itself</li>
	 * </ol>
	 *
	 * @param taxonId
	 * @return details
	 */
	private TaxonDetails createTaxonDetails(final String taxonId) {
		final TaxonDetails details = new TaxonDetails(taxonId);

		// Add taxon_closure and taxon_closure_label.
		final OWLClass taxCls = graph.getOWLClassByIdentifier(taxonId);
		if (taxCls == null) {
			// do nothing for unknown ids
			if (LOG.isInfoEnabled()) {
				LOG.info("Skipping taxon closures for unknown id: "+taxonId);
			}
			return details;
		}
		String taxonLbl = graph.getLabel(taxCls);
		details.taxLbl = taxonLbl;
		Set<OWLClass> taxAncestors = taxo.getAncestors(taxCls, false); // make non-reflexive on purpose

		// Collect information: ids, labels, and mapping for full taxon and subset

		// handle self (aka reflexive)
		details.taxIDClosure.add(taxonId);
		details.taxLabelClosure.add(taxonLbl);
		details.taxonClosureMap.put(taxonId, taxonLbl);

		boolean taxonSubsetUsed = false;

		// handle ancestor closure
		for( OWLClass ts : taxAncestors ){
			String tid = graph.getIdentifier(ts);
			String tlbl = graph.getLabel(ts);
			details.taxIDClosure.add(tid);
			details.taxLabelClosure.add(tlbl);
			details.taxonClosureMap.put(tid, tlbl);

			List<String> subsets = graph.getSubsets(ts);
			if (taxonSubsetName != null && subsets.contains(taxonSubsetName)) {
				taxonSubsetUsed = true;
				details.taxSubsetIDClosure.add(tid);
				details.taxSubsetLabelClosure.add(tlbl);
				details.taxonSubsetClosureMap.put(tid, tlbl);
			}
		}

		// only add self, if taxon subset was ever used!
		if (taxonSubsetUsed) {
			details.taxSubsetIDClosure.add(taxonId);
			details.taxSubsetLabelClosure.add(taxonLbl);
			details.taxonSubsetClosureMap.put(taxonId, taxonLbl);
		}


		return details;
	}

	// Main wrapping for adding non-ontology documents to GOlr.
	// Also see OntologySolrLoader.
	private void add(Bioentity e) {

		String eid = e.getId();
		String esym = e.getSymbol();
		String edb = e.getDb();
		String etype = e.getTypeCls();
		String ename = e.getFullName();
		String edbid = e.getDBID();
		//LOG.info("Adding: " + eid + " " + esym);

		SolrInputDocument bioentity_doc = new SolrInputDocument();

		// Bioentity document base.
		bioentity_doc.addField("document_category", "bioentity");
		bioentity_doc.addField("id", eid);
		bioentity_doc.addField("bioentity", eid);
		bioentity_doc.addField("bioentity_internal_id", edbid);
		bioentity_doc.addField("bioentity_label", esym);
		bioentity_doc.addField("bioentity_name", ename);
		bioentity_doc.addField("source", edb);
		bioentity_doc.addField("type", etype);

		// A little more work for the synonyms.
		List<String> esynonyms = e.getSynonyms();
		if( ! esynonyms.isEmpty() ){
			bioentity_doc.addField("synonym", esynonyms);
		}

		// Various taxon and taxon closure calculations, including map.
		String etaxid = e.getNcbiTaxonId();
		TaxonDetails taxonDetails = null;
		if (etaxid != null) {
			taxonDetails = createTaxonDetails(etaxid);
			taxonDetails.addToSolrDocument(bioentity_doc);
		}

		// Optionally, pull information from the PANTHER file set.
		List<String> pantherFamilyIDs = new ArrayList<String>();
		List<String> pantherFamilyLabels = new ArrayList<String>();
		List<String> pantherTreeGraphs = new ArrayList<String>();
		//List<String> pantherTreeAnnAncestors = new ArrayList<String>();
		//List<String> pantherTreeAnnDescendants = new ArrayList<String>();
		if( pset != null && pset.getNumberOfFilesInSet() > 0 ){
			Set<PANTHERTree> pTrees = pset.getAssociatedTrees(eid);
			if( pTrees != null ){
				Iterator<PANTHERTree> piter = pTrees.iterator();
				int pcnt = 0; // DEBUG
				while( piter.hasNext() ){
					pcnt++; // DEBUG
					PANTHERTree ptree = piter.next();
					pantherFamilyIDs.add(ptree.getPANTHERID());
					pantherFamilyLabels.add(StringUtils.lowerCase(ptree.getTreeLabel()));
					pantherTreeGraphs.add(ptree.getOWLShuntGraph().toJSON());
					//pantherTreeAnnAncestors = new ArrayList<String>(ptree.getAncestorAnnotations(eid));
					//pantherTreeAnnDescendants = new ArrayList<String>(ptree.getDescendantAnnotations(eid));
					if( pcnt > 1 ){ // DEBUG
						LOG.info("Belongs to multiple families (" + eid + "): " + StringUtils.join(pantherFamilyIDs, ", "));
					}

					// Store that we saw this for later use in the tree.
					ptree.addAssociatedGeneProduct(eid, esym);
				}
			}
		}
		// Optionally, actually /add/ the PANTHER family data to the document.
		if( ! pantherFamilyIDs.isEmpty() ){
			// BUG/TODO (but probably not ours): We only store the one tree for now as we're assuming that there is just one family.
			// Unfortunately, PANTHER still produces data that sez sometimes something belongs to more than one
			// family (eg something with fly in PTHR10919 PTHR10032), so we block it and just choose the first.
			bioentity_doc.addField("panther_family", pantherFamilyIDs.get(0));
			bioentity_doc.addField("panther_family_label", pantherFamilyLabels.get(0));
			// No longer load this field due to increased
			// size or use:
			// https://github.com/geneontology/amigo/issues/542
			//bioentity_doc.addField("phylo_graph_json", pantherTreeGraphs.get(0));
			//if( ! pantherTreeAnnAncestors.isEmpty() ){
			//	bioentity_doc.addField("phylo_ancestor_closure", pantherTreeAnnAncestors);
			//}
			//if( ! pantherTreeAnnDescendants.isEmpty() ){
			//	bioentity_doc.addField("phylo_descendant_closure", pantherTreeAnnDescendants);
			//}
		}

		// We're also going to want to make note of the direct annotations to this bioentity.
		// This will mean getting ready and then storing all of c5 when we pass through through
		// the annotation loop. We'll add to the document on the other side.
		// Collect information: ids and labels.
		Map<String,String> direct_list_map = new HashMap<String,String>();

		// Something that we'll need for the annotation evidence aggregate later.
		Map<String,SolrInputDocument> evAggDocMap = new HashMap<String,SolrInputDocument>();

		// Annotation doc.
		// We'll also need to be collecting some aggregate information, like for the GP term closures, which will be
		// added at the end of this section.
		Map<String, String> isap_map = new HashMap<String, String>();
		Map<String, String> reg_map = new HashMap<String, String>();
		for (GeneAnnotation a : gafDocument.getGeneAnnotations(e.getId())) {
			SolrInputDocument annotation_doc = new SolrInputDocument();

			String clsId = a.getCls();

			// Annotation document base from static and previous bioentity.
			annotation_doc.addField("document_category", "annotation"); // n/a
			annotation_doc.addField("source", edb); // Col. 1 (from bioentity above)
			annotation_doc.addField("bioentity", eid); // n/a, should be c1+c2.
			annotation_doc.addField("bioentity_internal_id", edbid); // Col. 2 (from bioentity above)
			annotation_doc.addField("bioentity_label", esym); // Col. 3 (from bioentity above)
			// NOTE: Col. 4 generation is below...
			annotation_doc.addField("annotation_class", clsId); // Col. 5
			addLabelField(annotation_doc, "annotation_class_label", clsId); // n/a
			// NOTE: Col. 6 generation is below...
			// NOTE: Col. 7 (evidence is in a separate method)
			// NOTE: Col. 8 generation is below...
			String a_aspect = a.getAspect();
			annotation_doc.addField("aspect", a_aspect); // Col. 9
			annotation_doc.addField("bioentity_name", ename); // Col. 10 (from bioentity above)
			annotation_doc.addField("synonym", esynonyms); // Col. 11 (from bioentity above)
			annotation_doc.addField("type", etype); // Col. 12 (from bioentity above)
			String adate = a.getLastUpdateDate();
			annotation_doc.addField("date", adate);  // Col. 14
			String assgnb = a.getAssignedBy();
			annotation_doc.addField("assigned_by", assgnb); // Col. 15
			// NOTE: Col. generation is 16 below...
			annotation_doc.addField("bioentity_isoform", a.getGeneProductForm()); // Col. 17

			// Optionally, if there is enough taxon for a map, add the collections to the document.
			if( taxonDetails != null ){
				taxonDetails.addToSolrDocument(annotation_doc);
			}

			// Optionally, actually /add/ the PANTHER family data to the document.
			if( ! pantherFamilyIDs.isEmpty() ){
				annotation_doc.addField("panther_family", pantherFamilyIDs.get(0));
				annotation_doc.addField("panther_family_label", pantherFamilyLabels.get(0));
			}

			// Evidence details.
			String a_ev_type = a.getShortEvidence();

			// legacy!!!!
			addLegacyEcoDetails(a_ev_type, annotation_doc);

			// make this GPAD safe, not all eco classes have a short evidence
			String evidenceId = a_ev_type != null ? a_ev_type : a.getEcoEvidenceCls();
			addEcoDetails(evidenceId, annotation_doc); // Col. 7

			// Col 4: qualifier generation.
			String comb_aqual = "";
			if( a.hasQualifiers() ){
				if (a.isNegated()) {
					comb_aqual = comb_aqual + "not";
					annotation_doc.addField("qualifier", "not");
				}
				if (a.isContributesTo()) {
					comb_aqual = comb_aqual + "contributes_to";
					annotation_doc.addField("qualifier", "contributes_to");
				}
				if (a.isIntegralTo()) {
					comb_aqual = comb_aqual + "integral_to";
					annotation_doc.addField("qualifier", "integral_to");
				}
				if (a.isColocatesWith()) {
					comb_aqual = comb_aqual + "colocalizes_with";
					annotation_doc.addField("qualifier", "colocalizes_with");
				}
				if (a.isCut()) {
					comb_aqual = comb_aqual + "cut";
					annotation_doc.addField("qualifier", "cut");
				}
			}

			// Drag in the reference (col 6)
			List<String> refIds = a.getReferenceIds();
			String refIdList = ""; // used to help make unique ID.
			for( String refId : refIds ){
				annotation_doc.addField("reference", refId);
				refIdList = refIdList + "_" + refId;
			}

			// Drag in "with" (col 8).
			//annotation_doc.addField("evidence_with", a.getWithExpression());
			String withList = ""; // used to help make unique ID.
			for (String wi : a.getWithInfos()) {
				annotation_doc.addField("evidence_with", wi);
				withList = withList + "_" + wi;
			}

			///
			/// isa_partof_closure
			///

			OWLObject cls = graph.getOWLObjectByIdentifier(clsId);
			// TODO: This may be a bug workaround, or it may be the way things are.
			// getOWLObjectByIdentifier returns null on alt_ids, so skip them for now.
			if( cls != null ){
				// Is-a part-of closures.
				ArrayList<String> isap = new ArrayList<String>();
				isap.add("BFO:0000050");
				Map<String, String> curr_isap_map = addClosureToAnnAndBio(isap, "isa_partof_closure", "isa_partof_closure_label", "isa_partof_closure_map",
									                                      cls, graph, annotation_doc, bioentity_doc, a.isNegated());
				isap_map.putAll(curr_isap_map); // add to aggregate map

				// Regulates closures.
				List<String> reg = RelationSets.getRelationSet(RelationSets.COMMON);
				Map<String, String> curr_reg_map = addClosureToAnnAndBio(reg, "regulates_closure", "regulates_closure_label", "regulates_closure_map",
						  			               cls, graph, annotation_doc, bioentity_doc, a.isNegated());
				reg_map.putAll(curr_reg_map); // add to aggregate map
			}

			// Let's piggyback on a little of the work above and cache the extra stuff that we'll be adding to the bioenity at the end
			// for the direct annotations. c5 and ???.
			if (a.isNegated() == false) {
				String dlbl = graph.getLabel(cls);
				direct_list_map.put(clsId, dlbl);
			}

			// Column 16.
			// We only want to climb the is_a/part_of parts here.
			ArrayList<String> aecc_rels = new ArrayList<String>();
			aecc_rels.add("BFO:0000050");
			// And capture the label and ID mappings for when we're done the loop.
			Map<String,String> ann_ext_map = new HashMap<String,String>(); // capture labels/ids
			for (List<ExtensionExpression> groups : a.getExtensionExpressions()) {
				// TODO handle extension expression groups
				for (ExtensionExpression ee : groups) {
					String eeid = ee.getCls();
					OWLObject eObj = graph.getOWLObjectByIdentifier(eeid);
					annotation_doc.addField("annotation_extension_class", eeid);
					String eLabel = addLabelField(annotation_doc, "annotation_extension_class_label", eeid);
					if( eLabel == null ) eLabel = eeid; // ensure the label

					///////////////
					// New
					///////////////

					// Get the closure maps.
					if (eObj != null) {
						Map<String, String> aecc_cmap = graph.getRelationClosureMap(eObj, aecc_rels);
						if( ! aecc_cmap.isEmpty() ){
							for( String aecc_id : aecc_cmap.keySet() ){
								String aecc_lbl = aecc_cmap.get(aecc_id);

								// Add all items to the document.
								annotation_doc.addField("annotation_extension_class_closure", aecc_id);
								annotation_doc.addField("annotation_extension_class_closure_label", aecc_lbl);

								// And make sure that both id and label are in the per-term map.
								ann_ext_map.put(aecc_lbl, aecc_id);
								ann_ext_map.put(aecc_id, aecc_lbl);
							}
						}
					}

					// Ugly. Hand roll out the data for the c16 special handler. Have mercy on me--I'm going
					// to just do this by hand since it's a limited case and I don't want to mess with Gson right now.
					String complicated_c16r = ee.getRelation();
					if( complicated_c16r != null ){
						List<OWLObjectProperty> relations = graph.getRelationOrChain(complicated_c16r);
						if( relations != null ){

							ArrayList<String> relChunk = new ArrayList<String>();
							for( OWLObjectProperty rel : relations ){
								// Use the IRI to get the BFO:0000050 as ID for the part_of OWLObjectProperty
								String rID = graph.getIdentifier(rel.getIRI());
								String rLabel = graph.getLabel(rel);
								if( rLabel == null ) rLabel = rID; // ensure the label
								relChunk.add("{\"id\": \"" + rID + "\", \"label\": \"" + rLabel + "\"}");
							}
							String finalSpan = StringUtils.join(relChunk, ", ");

							// Assemble final JSON blob.
							String aeJSON = "{\"relationship\": {\"relation\": [" +
									finalSpan +
									"], \"id\": \"" + eeid + "\", \"label\": \"" + eLabel + "\"}}";

							annotation_doc.addField("annotation_extension_json", aeJSON);
							//LOG.info("added complicated c16: (" + eeid + ", " + eLabel + ") " + aeJSON);
						}else{
							// The c16r is unknown to the ontology--render it as just a normal label, without the link.
							annotation_doc.addField("annotation_extension_json", complicated_c16r);
							LOG.info("added unknown c16: " + complicated_c16r);
						}
					}
				}
			}

			// Add annotation ext closure map to annotation doc (needs to be outside loop since there are multiple extensions).
			if( ! ann_ext_map.isEmpty() ){
				String jsonized_ann_ext_map = gson.toJson(ann_ext_map);
				annotation_doc.addField("annotation_extension_class_closure_map", jsonized_ann_ext_map);
			}

			// Final doc assembly; make the ID /really/ unique.
			// WARNING: We're relying on the current truth that the toString() method returns a nice concatenated version
			// of the GAF line, which is fundamentally unique (or should be). If this changes, revert to what we were
			// doing here pre-20150930, which was assembling out own unique line manually.
			String id = Hex.encodeHexString(a.toString().getBytes());
			annotation_doc.addField("id", id);

			// Finally add doc.
			add(annotation_doc);
		}

		// Add the necessary aggregates to the bio doc. These cannot be done incrementally like the multi-valued closures
		// sonce there can only be a single map.
		if( ! isap_map.isEmpty() ){
			String jsonized_cmap = gson.toJson(isap_map);
			bioentity_doc.addField("isa_partof_closure_map", jsonized_cmap);
		}
		if( ! reg_map.isEmpty() ){
			String jsonized_cmap = gson.toJson(reg_map);
			bioentity_doc.addField("regulates_closure_map", jsonized_cmap);
		}

		// Add c5 to bioentity.
		// Compile closure map to JSON and add to the document.
		String jsonized_direct_map = null;
		if( ! direct_list_map.isEmpty() ){
			jsonized_direct_map = gson.toJson(direct_list_map);
		}
		// Optionally, if there is enough direct annotations for a map, add the collections to the document.
		if( jsonized_direct_map != null ){
			List<String> directIDList = new ArrayList<String>(direct_list_map.keySet());
			List<String> directLabelList = new ArrayList<String>(direct_list_map.values());
			bioentity_doc.addField("annotation_class_list", directIDList);
			bioentity_doc.addField("annotation_class_list_label", directLabelList);
			bioentity_doc.addField("annotation_class_list_map", jsonized_direct_map);
		}

		add(bioentity_doc);

		for (SolrInputDocument ev_agg_doc : evAggDocMap.values()) {
			add(ev_agg_doc);
		}

		// Now repeat some of the same to help populate the "general" index for bioentities.
		SolrInputDocument general_doc = new SolrInputDocument();
		// Watch out for "id" collision!
		general_doc.addField("id", "general_bioentity_" + eid);
		general_doc.addField("entity", eid);
		general_doc.addField("entity_label", esym);
		general_doc.addField("document_category", "general");
		general_doc.addField("category", "bioentity");
		general_doc.addField("general_blob", ename + " " + edbid + " " + StringUtils.join(esynonyms, " "));
		add(general_doc);
	}

	/*
	 * Add specified closure of OWLObject to annotation and bioentity docs.
	 * Not the map for bio.
	 */
	private Map<String, String> addClosureToAnnAndBio(List<String> relations, String closureName, String closureNameLabel, String closureMap,
			OWLObject cls, OWLGraphWrapper graph, SolrInputDocument ann_doc, SolrInputDocument bio_doc, boolean isNegated){

		// Add closures to doc; label and id.
		graph.addPropertyIdsForMaterialization(relations);
		final Map<String, String> cmap = graph.getRelationClosureMap(cls, relations);
		List<String> idClosure = new ArrayList<String>(cmap.keySet());
		List<String> labelClosure = new ArrayList<String>(cmap.values());

		ann_doc.addField(closureName, idClosure);
		ann_doc.addField(closureNameLabel, labelClosure);

		// WARNING this is a side effect for the bio-entity
		// only add the class and closure, if it is a non-negated annotation
		if (isNegated == false) {
			for( String tid : idClosure){
				addFieldUnique(bio_doc, closureName, tid);
			}
			for( String tlabel : labelClosure){
				addFieldUnique(bio_doc, closureNameLabel, tlabel);
			}
		}

		// Compile closure maps to JSON.
		if( ! cmap.isEmpty() ){
			String jsonized_cmap = gson.toJson(cmap);
			ann_doc.addField(closureMap, jsonized_cmap);
			// NOTE: This is harder since we'd be adding multiple, so the is done on a collector variable elsewhere.
			//bio_doc.addField(closureMap, jsonized_cmap);
		}

		if (isNegated) {
			// WARNING this is a side effect for the bio-entity
			// only add the class and closure, if it is a non-negated annotation
			return Collections.emptyMap();
		}
		return cmap;
	}

	private void addLegacyEcoDetails(String shortEvidence, SolrInputDocument annotation_doc) {
		// handle legacy fields
		Set<OWLClass> ecoClasses = eco.getClassesForGoCode(shortEvidence);
		Set<OWLClass> ecoSuper = eco.getAncestors(ecoClasses, true);
		List<String> ecoIDClosure = new ArrayList<String>();
		for( OWLClass es : ecoSuper ){
			String itemID = es.toStringID();
			ecoIDClosure.add(itemID);
		}
		annotation_doc.addField("evidence_type", shortEvidence);
		addLabelFields(annotation_doc, "evidence_type_closure", ecoIDClosure);
	}

	/**
	 * @param evidence
	 * @param annotation_doc
	 */
	private void addEcoDetails(String evidence, SolrInputDocument annotation_doc) {
		if (evidence == null) {
			return; // do nothing
		}
		// translate evidence to an OWLClass
		final OWLClass ecoCls;
		final Set<OWLClass> ecoClasses;
		if (evidence.startsWith("ECO:")) {
			// handle as ECO id
			ecoCls = graph.getOWLClassByIdentifier(evidence);
			ecoClasses = Collections.singleton(ecoCls);
		}
		else {
			// try to parse as GO-Code
			ecoClasses = eco.getClassesForGoCode(evidence);
			if (ecoClasses.isEmpty()) {
				return;
			}
			ecoCls = ecoClasses.iterator().next();
		}

		if (ecoCls == null) {
			LOG.error("Could not find class for evidence: "+evidence);
			return;
		}

		// prepare data
		final String ecoId = graph.getIdentifier(ecoCls.getIRI());
		final String ecoLbl = graph.getLabel(ecoCls);

		// Evidence type closure.
		final List<String> ecoIDClosure = new ArrayList<String>();
		final List<String> ecoLabelClosure = new ArrayList<String>();
		final Map<String,String> ecoClosureMap = new HashMap<String,String>();

		// subset reflexive closure
		final List<String> ecoSubsetIDClosure = new ArrayList<String>();
		final List<String> ecoSubsetLabelClosure = new ArrayList<String>();
		final Map<String,String> ecoSubsetClosureMap = new HashMap<String,String>();

		Set<OWLClass> ecoSuper = eco.getAncestors(ecoClasses, true);
		for( OWLClass cls : ecoSuper ){
			String currentId = graph.getIdentifier(cls.getIRI());
			String currentLbl = graph.getLabel(cls);
			ecoIDClosure.add(currentId);
			ecoLabelClosure.add(currentLbl);
			ecoClosureMap.put(currentId, currentLbl);

			List<String> subsets = graph.getSubsets(cls);
			// TODO: make this more resilient. For rationale see:
			// https://github.com/geneontology/amigo/issues/433
			if (ecoSubsetName != null && (subsets.contains(ecoSubsetName) ||
						      subsets.contains(ecoSubsetName.replace('_',':'))
						     ) {
				ecoSubsetIDClosure.add(currentId);
				ecoSubsetLabelClosure.add(currentLbl);
				ecoSubsetClosureMap.put(currentId, currentLbl);
			}
		}

		if (ecoSubsetName != null) {
			ecoSubsetIDClosure.add(ecoId);
			ecoSubsetLabelClosure.add(ecoLbl);
			ecoSubsetClosureMap.put(ecoId, ecoLbl);
		}

		// add to doc
		annotation_doc.addField("evidence", ecoId);

		if(ecoLbl != null) {
			annotation_doc.addField("evidence_label", ecoLbl);
		}

		if (ecoClosureMap.isEmpty() == false) {
			annotation_doc.addField("evidence_closure", ecoIDClosure);
			annotation_doc.addField("evidence_closure_label", ecoLabelClosure);
			annotation_doc.addField("evidence_closure_map", gson.toJson(ecoClosureMap));
		}

		if (ecoSubsetClosureMap.isEmpty() == false) {
			annotation_doc.addField("evidence_subset_closure", ecoSubsetIDClosure);
			annotation_doc.addField("evidence_subset_closure_label", ecoSubsetLabelClosure);
			annotation_doc.addField("evidence_subset_closure_map", gson.toJson(ecoSubsetClosureMap));
		}

	}

}
