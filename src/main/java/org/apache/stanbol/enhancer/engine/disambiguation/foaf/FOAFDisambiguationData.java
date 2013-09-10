package org.apache.stanbol.enhancer.engine.disambiguation.foaf;

import org.apache.clerezza.rdf.core.UriRef;
import org.apache.stanbol.enhancer.servicesapi.rdf.NamespaceEnum;

public class FOAFDisambiguationData {

	//have to use NamespacePrefixService from the engine..
    public static final UriRef FOAF_PERSON = new UriRef(NamespaceEnum.foaf
            + "Person");
    
    public static final UriRef FOAF_ORGANIZATION = new UriRef(NamespaceEnum.foaf
            + "Organization");



   public static final UriRef DBPEDIA_PERSON = new UriRef(NamespaceEnum.dbpedia_ont
           + "Person");
   
   public static final UriRef DBPEDIA_ORGANIZATION = new UriRef(NamespaceEnum.dbpedia_ont
           + "Organization");
   
   
  //foaf properties(literal)
   public static final UriRef FOAF_NAME = new UriRef(NamespaceEnum.foaf + "name");
   public static final UriRef FOAF_NICK = new UriRef(NamespaceEnum.foaf + "nick");;
   public static final UriRef FOAF_FAMILY_NAME = new UriRef(NamespaceEnum.foaf + "familyName");
   public static final UriRef FOAF_GIVEN_NAME = new UriRef(NamespaceEnum.foaf + "givenName");
   public static final UriRef FOAF_FIRST_NAME = new UriRef(NamespaceEnum.foaf + "firstName");
   public static final UriRef FOAF_SURNAME = new UriRef(NamespaceEnum.foaf + "surname");
   public static final UriRef FOAF_GENDER = new UriRef(NamespaceEnum.foaf + "gender");


}
