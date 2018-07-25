package uk.ac.wellcome.platform.api.fixtures

import org.scalatest.{Assertion, Suite}
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.platform.api.services.ElasticsearchService
import uk.ac.wellcome.test.fixtures.TestWith

trait ElasticsearchServiceFixture extends ElasticsearchFixtures {
  this: Suite =>
  def withElasticSearchService(indexName: String, itemType: String)(
    testWith: TestWith[ElasticsearchService, Assertion]) = {
    val searchService = new ElasticsearchService(
      elasticClient = elasticClient,
      elasticConfig = ElasticConfig(
        documentType = itemType,
        indexV1name = indexName,
        indexV2name = indexName
      )
    )
    testWith(searchService)
  }
}
