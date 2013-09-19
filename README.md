Stanbol Entity Disambiguation using FOAF Co-reference
======================================================

This is the Stanbol enhacement engine developed as part of the GSoC 2013 project [1]. <br/>

The engine's main functionality is to increase the confidence of Entity-Annotations identified from previous engines, by using 2 fundamental techniques; <br/>
1. Processing co-referencing URI references in the Entities to detect connected-ness<br/>
2. Processing foaf:name comparison with fise:selected-text

The main objective is to identify correlated URIs between entities and increase the confidence of the most 'connected' entity from the suggested entities. 
All URI/Reference type fields of the entities are extracted and processed to find correlations with other entities suggested. The most connected entity will have the most number of URI correlations, and the disambiguated-confidence will be increased accordingly.

The second technique used is literal matching of foaf:name field of the entity with the fise:selected-texts in the content. With an exact match, the disambiguated-confidence will be increased. Finally the cumulative disambiguated-confidence is calculated and adjusted.

This engine requires Entity-Annotations extracted from previous engines, and entityhub pre-configured with FOAF entities. 
The entityhub-site: <code>foaf-site</code> created by indexing the btc2012 dataset including substantial amount of FOAF data can be found at [2]. <br/>
Please go through the steps in the project's README to configure the 'foaf-site' in Stanbol entityhub and use it in the foaf-site-chain enhancement-chain. The new disambiguation-foaf engine will be used to extend the functionality of this enhancement-chain in this project. 

How to execute the engine:
--------------------------
1. Build the maven project using command : <code>mvn clean install</code> 
2. Start the Stanbol engine and install the bundle: <code>org.apache.stanbol.enhancer.engines.disambiguation.foaf-1.0-SNAPSHOT.jar</code> 
3. Configure the foaf-site-chain with the new disambiguation engine

The new engine is identified by : <code>disambiguation-foaf</code>
Please note that in addition to the foaf-site I have also used entitylinking with dbpedia in the foaf-site-chain to increase the amount of entitiies for disambiguation.
Therefore after configuring the enhancement-chain successfully the foaf-site-chain should look like below; <br/>
<pre>
Engines: langdetect, opennlp-sentence, opennlp-token, opennlp-pos, foaf-site-linking, opennlp-ner, dbpediaLinking, disambiguation-foaf
</pre>

[1] http://www.google-melange.com/gsoc/proposal/review/google/gsoc2013/dileepaj/1 <br/>
[2] https://github.com/dileepajayakody/FOAFSite
