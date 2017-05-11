package uk.ac.wellcome.platform.api.services

import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import uk.ac.wellcome.models.{IdentifiedWork, SourceIdentifier, Work}
import uk.ac.wellcome.platform.api.models.DisplayWork
import uk.ac.wellcome.test.utils.IndexedElasticSearchLocal
import scala.concurrent.ExecutionContext.Implicits.global

class ElasticsearchServiceTest
    extends FunSpec
    with IndexedElasticSearchLocal
    with Matchers
    with ScalaFutures {

  val searchService =
    new ElasticSearchService(indexName, itemType, elasticClient)

  it("should return the correct number of results from Elasticsearch") {
    val work1 = identifiedWorkWith(
      canonicalId = "0001",
      label = "The first flounder"
    )
    val work2 = identifiedWorkWith(
      canonicalId = "0002",
      label = "The second salmon"
    )
    val work3 = identifiedWorkWith(
      canonicalId = "0003",
      label = "The third trout"
    )
    val work4 = identifiedWorkWith(
      canonicalId = "0004",
      label = "The fourth flagtail"
    )
    val work5 = identifiedWorkWith(
      canonicalId = "0005",
      label = "The fifth flagfin"
    )

    insertIntoElasticSearch(work1, work2, work3, work4, work5)

    val searchResultFuture = searchService.findResults(
      sortByField = "canonicalId",
      limit = 4
    )
    whenReady(searchResultFuture) { _.hits should have size 4 }

    val searchResultFutureWithLargeLimit = searchService.findResults(
      sortByField = "canonicalId",
      limit = 10
    )
    whenReady(searchResultFutureWithLargeLimit) { _.hits should have size 5 }
  }

  private def identifiedWorkWith(canonicalId: String, label: String) = {
    IdentifiedWork(canonicalId,
                   Work(identifiers = List(
                          SourceIdentifier(source = "Calm",
                                           sourceId = "AltRefNo",
                                           value = "calmid")),
                        label = label))
  }
}
