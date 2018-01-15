package uk.ac.wellcome.platform.ingestor.models

import com.google.inject.Inject
import com.sksamuel.elastic4s.analyzers._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.mappings.dynamictemplate.DynamicMapping
import com.twitter.inject.Logging
import com.twitter.inject.annotations.Flag

class WorksIndex @Inject()(client: HttpClient,
                           @Flag("es.index") name: String,
                           @Flag("es.type") itemType: String)
    extends ElasticSearchIndex with Logging {

  val httpClient = client
  val indexName = name

  val license = objectField("license").fields(
    keywordField("type"),
    keywordField("licenseType"),
    textField("label"),
    textField("url")
  )

  val sourceIdentifier = objectField("sourceIdentifier")
    .fields(
      keywordField("type"),
      keywordField("identifierScheme"),
      keywordField("value")
    )

  val identifiers = objectField("identifiers")
    .fields(
      keywordField("type"),
      keywordField("identifierScheme"),
      keywordField("value")
    )

  def location(fieldName: String = "locations") =
    objectField(fieldName).fields(
      keywordField("type"),
      keywordField("locationType"),
      textField("url"),
      textField("credit"),
      license
    )

  def date(fieldName: String) = objectField(fieldName).fields(
    textField("label"),
    keywordField("type")
  )

  def labelledTextField(fieldName: String) = objectField(fieldName).fields(
    textField("label"),
    keywordField("type")
  )

  val items = objectField("items").fields(
    keywordField("canonicalId"),
    sourceIdentifier,
    identifiers,
    location(),
    booleanField("visible"),
    keywordField("type")
  )

  val mappingDefinition = putMapping(indexName / itemType)
    .dynamic(DynamicMapping.Strict)
    .as(
      keywordField("canonicalId"),
      booleanField("visible"),
      keywordField("type"),
      sourceIdentifier,
      identifiers,
      textField("title").fields(
        textField("english").analyzer(EnglishLanguageAnalyzer)),
      textField("description").fields(
        textField("english").analyzer(EnglishLanguageAnalyzer)),
      textField("lettering").fields(
        textField("english").analyzer(EnglishLanguageAnalyzer)),
      date("createdDate"),
      labelledTextField("creators"),
      labelledTextField("subjects"),
      labelledTextField("genres"),
      items,
      location("thumbnail")
    )
}