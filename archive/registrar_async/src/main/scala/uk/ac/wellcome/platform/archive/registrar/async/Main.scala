package uk.ac.wellcome.platform.archive.registrar.async

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.WellcomeApp
import uk.ac.wellcome.config.core.builders.AkkaBuilder
import uk.ac.wellcome.config.messaging.builders.SNSBuilder
import uk.ac.wellcome.config.storage.builders.{S3Builder, VHSBuilder}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.archive.common.config.builders._
import uk.ac.wellcome.platform.archive.common.models.NotificationMessage
import uk.ac.wellcome.platform.archive.registrar.common.models.StorageManifest
import uk.ac.wellcome.storage.vhs.EmptyMetadata

import scala.concurrent.ExecutionContext

object Main extends WellcomeApp {
  def buildWorkerService(config: Config): Registrar = {
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher

    val messageStream =
      MessagingBuilder.buildMessageStream[NotificationMessage, Unit](config)

    val dataStore = VHSBuilder.buildVHS[StorageManifest, EmptyMetadata](config)

    new Registrar(
      snsClient = SNSBuilder.buildSNSClient(config),
      progressSnsConfig = SNSBuilder.buildSNSConfig(config),
      s3Client = S3Builder.buildS3Client(config),
      messageStream = messageStream,
      dataStore = dataStore,
      actorSystem = actorSystem
    )
  }

  run()
}
