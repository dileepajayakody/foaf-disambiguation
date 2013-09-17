package org.apache.stanbol.enhancer.engine.disambiguation.foaf;

import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.clerezza.rdf.core.Literal;
import org.apache.clerezza.rdf.core.LiteralFactory;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.namespaceprefix.NamespacePrefixService;
import org.apache.stanbol.enhancer.nlp.model.AnalysedText;
import org.apache.stanbol.enhancer.nlp.utils.NlpEngineHelper;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.EngineException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementEngine;
import org.apache.stanbol.enhancer.servicesapi.InvalidContentException;
import org.apache.stanbol.enhancer.servicesapi.ServiceProperties;
import org.apache.stanbol.enhancer.servicesapi.helper.ContentItemHelper;
import org.apache.stanbol.enhancer.servicesapi.helper.EnhancementEngineHelper;
import org.apache.stanbol.enhancer.servicesapi.impl.AbstractEnhancementEngine;
import org.apache.stanbol.enhancer.servicesapi.rdf.TechnicalClasses;
import org.apache.stanbol.entityhub.servicesapi.model.Entity;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.apache.stanbol.entityhub.servicesapi.model.Text;
import org.apache.stanbol.entityhub.servicesapi.site.SiteException;
import org.apache.stanbol.entityhub.servicesapi.site.SiteManager;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, metatype = true)
@Service
@Properties(value = { @Property(name = EnhancementEngine.PROPERTY_NAME, value = "disambiguation-foaf") })
public class FOAFDisambiguationEngine extends
		AbstractEnhancementEngine<IOException, RuntimeException> implements
		EnhancementEngine, ServiceProperties {

	private static Logger log = LoggerFactory
			.getLogger(FOAFDisambiguationEngine.class);

	/**
	 * The default value for the execution of this Engine. Currently set to
	 * {@link ServiceProperties#ORDERING_POST_PROCESSING} + 90.
	 * <p>
	 * This should ensure that this engines runs as one of the first engines of
	 * the post-processing phase
	 */
	public static final Integer defaultOrder = ServiceProperties.ORDERING_POST_PROCESSING - 90;

	/**
	 * The {@link LiteralFactory} used to create typed RDF literals
	 */
	private final LiteralFactory literalFactory = LiteralFactory.getInstance();

	@Reference
	protected SiteManager siteManager;

	@Reference
	protected NamespacePrefixService namespacePrefixService;

	// private Map<UriRef, Set<EntityAnnotation>> suggestionMap = new
	// HashMap<UriRef, Set<EntityAnnotation>>();
	// to detect multiple TextAnnotations mapped to the same EntityAnnotation
	// key:EntityAnnotationUri, values:TextAnnotations.
	// private Map<UriRef, Set<UriRef>> entityAnnotationMap = new
	// HashMap<UriRef, Set<UriRef>>();

	// for disambiguation..
	// key: reference value: Set<EntityAnnotation>
	private Map<String, Set<UriRef>> urisReferencedByEntities = new HashMap<String, Set<UriRef>>();
	private Map<UriRef, EntityAnnotation> allEnitityAnnotations = new HashMap<UriRef, EntityAnnotation>();

	@Override
	public Map<String, Object> getServiceProperties() {
		return Collections.unmodifiableMap(Collections.singletonMap(
				ENHANCEMENT_ENGINE_ORDERING, (Object) defaultOrder));
	}

	@Override
	public int canEnhance(ContentItem ci) throws EngineException {
		// check if content is present
		try {
			if ((ContentItemHelper.getText(ci.getBlob()) == null)
					|| (ContentItemHelper.getText(ci.getBlob()).trim()
							.isEmpty())) {
				return CANNOT_ENHANCE;
			}
		} catch (IOException e) {
			log.error("Failed to get the text for "
					+ "enhancement of content: " + ci.getUri(), e);
			throw new InvalidContentException(this, ci, e);
		}
		// default enhancement is synchronous enhancement
		return ENHANCE_SYNCHRONOUS;
	}

	@Override
	public void computeEnhancements(ContentItem ci) throws EngineException {
		MGraph graph = ci.getMetadata();
		// AnalysedText at = NlpEngineHelper.getAnalysedText(this, ci, true);

		Iterator<Triple> it = graph.filter(null, RDF_TYPE,
				TechnicalClasses.ENHANCER_TEXTANNOTATION);
		while (it.hasNext()) {
			UriRef textAnnotation = (UriRef) it.next().getSubject();
			// Set<EntityAnnotation> suggestionSet = new
			// TreeSet<EntityAnnotation>();

			// NOTE: this iterator will also include dc:relation between
			// fise:TextAnnotation's
			Iterator<Triple> relatedLinks = graph.filter(null, DC_RELATION,
					textAnnotation);
			// extracting selected text for foaf-name comparison
			Iterator<Triple> selectedTextsItr = graph.filter(textAnnotation,
					ENHANCER_SELECTED_TEXT, null);
			while (relatedLinks.hasNext()) {
				UriRef link = (UriRef) relatedLinks.next().getSubject();
				EntityAnnotation suggestion = EntityAnnotation.createFromUri(
						graph, link);
				// if returned suggestion is an entity-annotation proceed with
				// disambiguation process
				if (suggestion != null) {
					// process entityAnnotation for disambiguation
					try {
						// process co-referenced entity-references
						processEntityReferences(suggestion);
						// matching with foaf:name and related properties
						processFOAFNameDisambiguation(suggestion,
								selectedTextsItr);
						// adding new entity annotation to the global map
						allEnitityAnnotations.put(suggestion.getEntityUri(),
								suggestion);
						// suggestionSet.add(suggestion);
					} catch (SiteException e) {
						log.error(e.getMessage());
						e.printStackTrace();
					}

					// maintaining the set of multiple textAnnotations for a
					// single entityAnnotation <entityAnnotation:
					// Set<textAnnotation>>
					/*
					 * if (entityAnnotationMap.get(link) != null) {
					 * entityAnnotationMap.get(link).add(textAnnotation); } else
					 * { Set<UriRef> textAnnotations = new TreeSet<UriRef>();
					 * textAnnotations.add(textAnnotation);
					 * entityAnnotationMap.put(link, textAnnotations); }
					 */
				}
			}

			/*
			 * if (suggestionSet.isEmpty()) { log.warn("TextAnnotation" +
			 * textAnnotation + "has no suggestions."); // return null; //
			 * nothing to disambiguate } else { // putting suggestions for a
			 * textAnnotation // textAnnotation:suggestions
			 * suggestionMap.put(textAnnotation, suggestionSet); }
			 */
		}
		// calculate link matches
		caculateLinkMatchesForEntities();
		disambiguateEntityReferences();
		// writing back to graph
		ci.getLock().writeLock().lock();
		try {
			applyDisambiguationResults(graph);
		} finally {
			ci.getLock().writeLock().unlock();
		}
		clearEhancementData();
	}

	public void clearEhancementData() {
		urisReferencedByEntities.clear();
		allEnitityAnnotations.clear();
	}

	public Entity getEntityFromEntityHub(EntityAnnotation sug)
			throws SiteException {
		UriRef entityUri = sug.getEntityUri();
		String entityhubSite = sug.getSite();
		Entity entity = null;
		// dereferencing the entity from the entityhub
		if (entityhubSite != null && entityUri != null) {
			entity = siteManager.getSite(entityhubSite).getEntity(
					entityUri.getUnicodeString());
		}
		return entity;
	}

	public void processFOAFNameDisambiguation(EntityAnnotation ea,
			Iterator<Triple> selectedTextsTriples) throws SiteException {
		Entity entity = this.getEntityFromEntityHub(ea);
		String entityLabel = ea.getEntityLabel();
		if (entityLabel != null) {
			// substring entityLabel from @language index eg: Bob Marley@en
			if (entityLabel.indexOf('@') != -1) {
				entityLabel = ea.getEntityLabel().substring(0,
						ea.getEntityLabel().indexOf('@'));
			}
		}
		// need to match fise:TextAnnotation with the entity's foaf:name
		Representation entityRep = entity.getRepresentation();
		Text foafNameText = ((Text) entityRep
				.getFirst(FOAFDisambiguationConstants.FOAF_NAME
						.getUnicodeString()));
		if (foafNameText != null) {
			String foafName = foafNameText.getText();
			System.out.println("The foaf name value is : " + foafName);
			// if the selected-text matches exactly with the foaf-name then
			// increase
			// the ds by 1
			Double foafNameScore = 0.0;
			while (selectedTextsTriples.hasNext()) {
				String selectedText = ((Literal) selectedTextsTriples.next()
						.getObject()).getLexicalForm();
				if (foafName != null) {
					if (selectedText.equalsIgnoreCase(foafName)) {
						foafNameScore++;
						System.out
								.println("the foaf name matches with selectedText..increasing foafNamesScore:"
										+ foafNameScore);
					}
				}

			}
			ea.setFoafNameDisambiguationScore(foafNameScore);
			ea.calculateFoafNameDisambiguatedConfidence();
		}

		// use these in a disambiguation score order
		Iterator<Text> fName = entityRep
				.getText(FOAFDisambiguationConstants.FOAF_FIRST_NAME
						.getUnicodeString());
		Iterator<Text> gName = entityRep
				.getText(FOAFDisambiguationConstants.FOAF_GIVEN_NAME
						.getUnicodeString());
		Iterator<Text> surName = entityRep
				.getText(FOAFDisambiguationConstants.FOAF_SURNAME
						.getUnicodeString());

	}

	public void processEntityReferences(EntityAnnotation entityAnnotation)
			throws SiteException {
		Entity entity = this.getEntityFromEntityHub(entityAnnotation);
		Representation entityRep = entity.getRepresentation();

		Iterator<String> fields = entityRep.getFieldNames();
		int linksFromEntity = 0;
		while (fields.hasNext()) {
			String field = fields.next();
			Iterator<org.apache.stanbol.entityhub.servicesapi.model.Reference> urisReferenced = entityRep
					.getReferences(field);
			while (urisReferenced.hasNext()) {
				org.apache.stanbol.entityhub.servicesapi.model.Reference uriReference = urisReferenced
						.next();
				linksFromEntity++;
				System.out.println("processing uriReference : "
						+ uriReference.getReference() + "\n from entity: "
						+ entityAnnotation.getEntityUri().getUnicodeString()
						+ "\n for field : " + field + "\n");
				String referenceString = uriReference.getReference();
				if (urisReferencedByEntities.containsKey(referenceString)) {
					Set<UriRef> eas = urisReferencedByEntities
							.get(referenceString);
					eas.add(entityAnnotation.getEntityUri());
					urisReferencedByEntities.put(referenceString, eas);
				} else {
					Set<UriRef> eas = new HashSet<UriRef>();
					eas.add(entityAnnotation.getEntityUri());
					// key:link, value:entityAnnotation set referencing link
					urisReferencedByEntities.put(referenceString, eas);
				}
			}
		}
		entityAnnotation.setLinksFromEntity(linksFromEntity);
	}

	public void caculateLinkMatchesForEntities() {
		for (String uriReference : urisReferencedByEntities.keySet()) {
			Set<UriRef> entityAnnotationsLinked = urisReferencedByEntities
					.get(uriReference);
			for (UriRef ea : entityAnnotationsLinked) {
				if (allEnitityAnnotations.get(ea) != null) {
					System.out
							.println("increasing link match for entityAnnot: "
									+ ea.toString());
					allEnitityAnnotations.get(ea).increaseLinkMatches();
				}
			}
		}
	}

	public void disambiguateEntityReferences() {
		int allUriLinks = urisReferencedByEntities.keySet().size();
		System.out.println("All URI links : " + allUriLinks);
		for (EntityAnnotation ea : allEnitityAnnotations.values()) {
			this.performEntityReferenceDisambiguation(ea, allUriLinks);
		}
	}

	public void performEntityReferenceDisambiguation(EntityAnnotation ea,
			int allUriLinks) {
		double linkMatchesByEntity = ea.getLinkMatches();
		double linksFromEntity = ea.getLinksFromEntity();
		double disambiguationScore = (linkMatchesByEntity / allUriLinks)
				* (linksFromEntity / allUriLinks);
		System.out.println("ea :" + ea.getEntityLabel() + " site: "
				+ ea.getSite() + " link matches: " + ea.getLinkMatches()
				+ " links from entitty: " + ea.getLinksFromEntity()
				+ "dis-score: " + disambiguationScore);
		ea.setEntityReferenceDisambiguationScore(disambiguationScore);
		// update the confidence
		ea.calculateEntityReferenceDisambiguatedConfidence();
	}

	public void applyDisambiguationResults(MGraph graph) {
		System.out
				.println("In applyDisResults the size of allEntityAnnotations : "
						+ allEnitityAnnotations.size());
		for (EntityAnnotation ea : allEnitityAnnotations.values()) {
			// calculate total dc
			ea.calculateDisambiguatedConfidence();
			System.out.println("ea : " + ea.getEntityLabel()
					+ " originalconf: " + ea.getOriginalConfidnece().toString()
					+ "no of links from entity: " + ea.getLinksFromEntity()
					+ " no of matches : " + ea.getLinkMatches()
					+ " dis-score :"
					+ ea.getEntityReferenceDisambiguationScore()
					+ " foaf name disamb-conf: "
					+ ea.getEntityReferenceDisambiguatedConfidence().toString()
					+ " entity reference disamb-conf: "
					+ ea.getDisambiguatedConfidence().toString()
					+ " disamb-conf: "
					+ ea.getDisambiguatedConfidence().toString());
			EnhancementEngineHelper.set(graph, ea.getUriLink(),
					ENHANCER_CONFIDENCE, ea.getDisambiguatedConfidence(),
					literalFactory);
			// adding this engine as a contributor
			EnhancementEngineHelper.addContributingEngine(graph,
					ea.getUriLink(), this);
		}
	}

	/**
	 * Activate and read the properties
	 * 
	 * @param ce
	 *            the {@link ComponentContext}
	 */
	@Activate
	protected void activate(ComponentContext ce) throws ConfigurationException {
		try {
			super.activate(ce);

		} catch (IOException e) {
			// log
			log.error("Error in activation method.", e);
		}
	}

	/**
	 * Deactivate
	 * 
	 * @param ce
	 *            the {@link ComponentContext}
	 */
	@Deactivate
	protected void deactivate(ComponentContext ce) {
		super.deactivate(ce);
	}
}
