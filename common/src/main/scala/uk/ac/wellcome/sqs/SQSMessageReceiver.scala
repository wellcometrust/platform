package uk.ac.wellcome.sqs

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import com.twitter.inject.Logging

import uk.ac.wellcome.metrics.MetricsSender
import uk.ac.wellcome.models.aws.SQSMessage
import uk.ac.wellcome.models.Work
import uk.ac.wellcome.sns.{PublishAttempt, SNSWriter}
import uk.ac.wellcome.utils.JsonUtil

class SQSMessageReceiver(snsWriter: SNSWriter,
                         messageProcessor: (SQSMessage) => Try[Any],
                         metricsSender: MetricsSender)
    extends Logging {

  def receiveMessage(message: SQSMessage): Future[PublishAttempt] = {
    info(s"Starting to process message $message")
    metricsSender.timeAndCount(
      "ingest-time",
      () => {
        val processAttempt = messageProcessor(message)
        processAttempt match {
          case Success(s) =>
            publishMessage(s)
          case Failure(SQSReaderGracefulException(e)) =>
            info("Recoverable failure while processing message $message", e)
            Future.successful(PublishAttempt(Left(e)))
          case Failure(e) =>
            info("Unrecoverable failure while processing message $message", e)
            Future.failed(e)
        }
      }
    )
  }

  // TODO: Make the subject configurable?
  def publishMessage(message: Any): Future[PublishAttempt] =
    snsWriter.writeMessage(JsonUtil.toJson(message).get, Some("Foo"))
}
