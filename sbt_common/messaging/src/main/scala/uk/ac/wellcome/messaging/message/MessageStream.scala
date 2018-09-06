package uk.ac.wellcome.messaging.message

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.Message
import com.google.inject.Inject
import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.json.JsonUtil._

import scala.concurrent.{ExecutionContext, Future}

class MessageStream[T] @Inject()(actorSystem: ActorSystem,
                                 sqsClient: AmazonSQSAsync,
                                 s3Client: AmazonS3,
                                 messageReaderConfig: MessageReaderConfig,
                                 metricsSender: MetricsSender)(
  implicit objectStore: ObjectStore[T],
  ec: ExecutionContext) {

  private val sqsStream = new SQSStream[NotificationMessage](
    actorSystem = actorSystem,
    sqsClient = sqsClient,
    sqsConfig = messageReaderConfig.sqsConfig,
    metricsSender = metricsSender
  )

  def runStream(
    streamName: String,
    modifySource: Source[(Message, T), NotUsed] => Source[Message, NotUsed])(
    implicit decoder: Decoder[MessageNotification[T]]): Future[Done] =
    sqsStream.runStream(
      streamName,
      source => modifySource(messageFromS3Source(source)))

  def foreach(streamName: String, process: T => Future[Unit])(
    implicit decoder: Decoder[MessageNotification[T]]): Future[Done] =
    sqsStream.foreach(
      streamName = streamName,
      process = (notification: NotificationMessage) =>
        for {
          obj <- getBody(notification.Message)
          result <- process(obj)
        } yield result
    )

  private def messageFromS3Source(
    source: Source[(Message, NotificationMessage), NotUsed])(
    implicit decoder: Decoder[MessageNotification[T]]) = {
    source.mapAsyncUnordered(messageReaderConfig.sqsConfig.parallelism) {
      case (message, notification) =>
        for {
          deserialisedObject <- getBody(notification.Message)
        } yield (message, deserialisedObject)
    }
  }

  private def getBody(messageString: String)(
    implicit decoder: Decoder[MessageNotification[T]]): Future[T] =
    for {
      notification <- Future.fromTry(
        fromJson[MessageNotification[T]](messageString))
      body <- notification match {
        case inlineNotification: InlineNotification[T] =>
          Future.successful(inlineNotification.t)
        case remoteNotification: RemoteNotification[T] =>
          objectStore.get(remoteNotification.location)
      }
    } yield body
}
