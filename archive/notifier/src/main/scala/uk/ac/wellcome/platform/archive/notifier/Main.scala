package uk.ac.wellcome.platform.archive.notifier

import java.net.URL

import com.amazonaws.services.sns.model.PublishResult
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.archive.common.config.builders.EnrichConfig._
import uk.ac.wellcome.platform.archive.common.config.builders._
import uk.ac.wellcome.platform.archive.common.models.NotificationMessage

import scala.concurrent.duration.Duration
import scala.concurrent.Await

object Main extends App with Logging {
  val config = ConfigFactory.load()

  implicit val actorSystem = AkkaBuilder.buildActorSystem()
  implicit val materializer = AkkaBuilder.buildActorMaterializer()

  val messageStream =
    MessagingBuilder.buildMessageStream[NotificationMessage, PublishResult](
      config)

  val notifier = new Notifier(
    messageStream = messageStream,
    snsClient = SNSBuilder.buildSNSClient(config),
    snsConfig = SNSBuilder.buildSNSConfig(config),
    contextUrl = buildContextURL(config)
  )

  try {
    info("Starting worker.")

    val result = notifier.run()

    Await.result(result, Duration.Inf)
  } catch {
    case e: Throwable =>
      error("Fatal error:", e)
  } finally {
    info("Terminating worker.")
  }

  private def buildContextURL(config: Config): URL =
    new URL(config.required[String]("notifier.context-url"))
}
