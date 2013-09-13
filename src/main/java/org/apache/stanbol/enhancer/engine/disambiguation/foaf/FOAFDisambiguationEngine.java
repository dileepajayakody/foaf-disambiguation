package org.apache.stanbol.enhancer.engine.disambiguation.foaf;

import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.ENHANCER_CONFIDENCE;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.RDF_TYPE;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.DC_RELATION;
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
	
	//private Map<UriRef, Set<EntityAnnotation>> suggestionMap = new HashMap<UriRef, Set<EntityAnnotation>>();
	// to detect multiple TextAnnotations mapped to the same EntityAnnotation
	// key:EntityAnnotationUri, values:TextAnnotations.
	//private Map<UriRef, Set<UriRef>> entityAnnotationMap = new HashMap<UriRef, Set<UriRef>>();

	// for disambiguation..
	// key: reference value: Set<EntityAnnotation>
	private Map<org.apache.stanbol.entityhub.servicesapi.model.Reference, Set<UriRef>> urisReferencedByEntities = new HashMap<org.apache.stanbol.entityhub.servicesapi.model.Reference, Set<UriRef>>();
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
		// TODO Auto-generated method stub

		MGraph graph = ci.getMetadata();
		// AnalysedText at = NlpEngineHelper.getAnalysedText(this, ci, true);

		Iterator<Triple> it = graph.filter(null, RDF_TYPE,
				TechnicalClasses.ENHANCER_TEXTANNOTATION);
		while (it.hasNext()) {
			UriRef textAnnotation = (UriRef) it.next().getSubject();
			//Set<EntityAnnotation> suggestionSet = new TreeSet<EntityAnnotation>();

			// NOTE: this iterator will also include dc:relation between
			// fise:TextAnnotation's
			Iterator<Triple> relatedLinks = graph.filter(null, DC_RELATION,
					textAnnotation);

			while (relatedLinks.hasNext()) {
				UriRef link = (UriRef) relatedLinks.next().getSubject();
				EntityAnnotation suggestion = EntityAnnotation.createFromUri(
						graph, link);
				// if returned suggestion is an entity-annotation proceed with
				// disambiguation process
				if (suggestion != null) {
					// process entityAnnotation for disambiguation
					try {
						// TO DO: need to add disambiguation logic for literal
						// matching with foaf:name and related properties
						// processFOAFNames(suggestion);
						processEntityReferences(suggestion);
						// adding new entity annotation to the global map
						allEnitityAnnotations.put(suggestion.getEntityUri(),
								suggestion);
						//suggestionSet.add(suggestion);
					} catch (SiteException e) {
						log.error(e.getMessage());
						e.printStackTrace();
					}

					// maintaining the set of multiple textAnnotations for a
					// single entityAnnotation <entityAnnotation:
					// Set<textAnnotation>>
					/*if (entityAnnotationMap.get(link) != null) {
						entityAnnotationMap.get(link).add(textAnnotation);
					} else {
						Set<UriRef> textAnnotations = new TreeSet<UriRef>();
						textAnnotations.add(textAnnotation);
						entityAnnotationMap.put(link, textAnnotations);
					}*/
				}
			}

			/*if (suggestionSet.isEmpty()) {
				log.warn("TextAnnotation" + textAnnotation
						+ "has no suggestions.");
				// return null; // nothing to disambiguate
			} else {
				// putting suggestions for a textAnnotation
				// textAnnotation:suggestions
				suggestionMap.put(textAnnotation, suggestionSet);
			}*/
		}
		// calculate link matches
		caculateLinkMatchesForEntities();
		performLinksDisambiguation();
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

	public void processFOAFNames(EntityAnnotation ea) throws SiteException {
		Entity entity = this.getEntityFromEntityHub(ea);

		String entityLabel = ea.getEntityLabel();
		if (entityLabel != null) {
			// substring entityLabel from @language index eg: Bob Marley@en
			if (entityLabel.indexOf('@') != -1) {
				entityLabel = ea.getEntityLabel().substring(0,
						ea.getEntityLabel().indexOf('@'));
			}
		}
		Representation entityRep = entity.getRepresentation();
		Iterator<Text> name = entityRep
				.getText(FOAFDisambiguationData.FOAF_NAME.toString());
		Iterator<Text> fName = entityRep
				.getText(FOAFDisambiguationData.FOAF_FIRST_NAME.toString());
		Iterator<Text> gName = entityRep
				.getText(FOAFDisambiguationData.FOAF_GIVEN_NAME.toString());
		Iterator<Text> surName = entityRep
				.getText(FOAFDisambiguationData.FOAF_SURNAME.toString());

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
				System.out.println("processing uriReference : "+ uriReference.getReference() + " from entity: " + entityAnnotation.getEntityUri().getUnicodeString());
				if (urisReferencedByEntities.containsKey(uriReference)) {
					Set<UriRef> eas = urisReferencedByEntities
							.get(uriReference);
					eas.add(entityAnnotation.getEntityUri());
					urisReferencedByEntities.put(uriReference, eas);
				} else {
					Set<UriRef> eas = new HashSet<UriRef>();
					eas.add(entityAnnotation.getEntityUri());
					// key:link, value:entityAnnotation set referencing link
					urisReferencedByEntities.put(uriReference, eas);
				}
			}
		}
		entityAnnotation.setLinksFromEntity(linksFromEntity);
	}

	public void caculateLinkMatchesForEntities() {
		for (org.apache.stanbol.entityhub.servicesapi.model.Reference uriReference : urisReferencedByEntities
				.keySet()) {
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

	public void performLinksDisambiguation() {
		int allUriLinks = urisReferencedByEntities.keySet().size();
		System.out.println("All URI links : " + allUriLinks);

		for (EntityAnnotation ea : allEnitityAnnotations.values()) {
			double linkMatchesByEntity = ea.getLinkMatches();
			double linksFromEntity = ea.getLinksFromEntity();
			double disambiguationScore = (linkMatchesByEntity / allUriLinks)
					* (linksFromEntity / allUriLinks);
			System.out.println("ea :" + ea.getEntityLabel() + " site: "
					+ ea.getSite() + " link matches: " + ea.getLinkMatches()
					+ " links from entitty: " + ea.getLinksFromEntity()
					+ "dis-score: " + disambiguationScore);
			ea.setDisambiguationScore(disambiguationScore);
			//update the confidence
			ea.calculateDisambiguatedConfidence(allUriLinks);
		}
	}

	public void applyDisambiguationResults(MGraph graph) {
		for (EntityAnnotation ea : allEnitityAnnotations.values()) {
			System.out.println("ea : " + ea.getEntityLabel()
					+ " originalconf: " + ea.getOriginalConfidnece().toString()
					+ "no of links from entity: " + ea.getLinksFromEntity()
					+ " no of matches : " + ea.getLinkMatches()
					+ " dis-score :" + ea.getDisambiguationScore()
					+ " disamb-conf: "
					+ ea.getDisambiguatedConfidence().toString());
			EnhancementEngineHelper.set(graph, ea.getUriLink(),
					ENHANCER_CONFIDENCE, ea.getDisambiguatedConfidence(),
					literalFactory);
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
