package uk.ac.wellcome.platform.sierra_item_merger.services

import akka.actor.ActorSystem
import com.google.inject.Inject
import grizzled.slf4j.Logging
import uk.ac.wellcome.circe.jsonUtil
import uk.ac.wellcome.circe.jsonUtil._
import uk.ac.wellcome.metrics.MetricsSender
import uk.ac.wellcome.models.aws.SQSMessage
import uk.ac.wellcome.sqs.{SQSReader, SQSReaderGracefulException, SQSWorker}
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class SierraItemMergerWorkerService @Inject()(
  reader: SQSReader,
  system: ActorSystem,
  metrics: MetricsSender,
  sierraItemMergerUpdaterService: SierraItemMergerUpdaterService
) extends SQSWorker(reader, system, metrics)
    with Logging {

  override def processMessage(message: SQSMessage): Future[Unit] =
    // Using Circe here because Jackson creates nulls for empty lists
    jsonUtil.fromJson[SierraItemRecord](message.body) match {
      case Success(record) => sierraItemMergerUpdaterService.update(record)
      case Failure(e) =>
        Future {
          logger.warn(s"Failed processing $message", e)
          throw SQSReaderGracefulException(e)
        }
    }
}
