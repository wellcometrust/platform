package uk.ac.wellcome.finatra.elasticsearch

import com.google.inject.{Provides, Singleton}
import com.sksamuel.elastic4s.Index
import com.twitter.inject.TwitterModule
import uk.ac.wellcome.elasticsearch.DisplayElasticConfig

object ElasticConfigModule extends TwitterModule {
  private val documentType =
    flag[String]("es.type", "item", "document type in Elasticsearch")

  private val indexNameV1 = flag[String]("es.index.v1", "V1 ES index name")
  private val indexNameV2 = flag[String]("es.index.v2", "V2 ES index name")

  @Singleton
  @Provides
  def providesElasticConfig(): DisplayElasticConfig =
    DisplayElasticConfig(
      documentType = documentType(),
      indexV1 = Index(name = indexNameV1()),
      indexV2 = Index(name = indexNameV2())
    )
}
