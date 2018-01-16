package uk.ac.wellcome.platform.ingestor.services

import javax.inject.{Inject, Singleton}

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.http.index.IndexResponse
import com.twitter.inject.Logging
import com.twitter.inject.annotations.Flag
import uk.ac.wellcome.metrics.MetricsSender
import uk.ac.wellcome.models.Work
import uk.ac.wellcome.utils.GlobalExecutionContext.context

import scala.concurrent.Future

import scala.util.{Failure, Success, Try}

@Singleton
class WorkIndexer @Inject()(
  @Flag("es.index") esIndex: String,
  @Flag("es.type") esType: String,
  elasticClient: HttpClient,
  metricsSender: MetricsSender
) extends Logging {

  def indexWork(work: Work): Future[IndexResponse] = {
    implicit val jsonMapper = Work
    metricsSender.timeAndCount[IndexResponse](
      "ingestor-index-work",
      () => {
        info("Indexing work $work")
        elasticClient.execute {
          indexInto(esIndex / esType).id(work.id).doc(work)
        }.recover {
          case e: Throwable =>
            error(s"Error indexing work $work into Elasticsearch", e)
            throw e
        }
      }
    )
  }
}
