package uk.ac.wellcome.platform.merger

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.WellcomeApp
import uk.ac.wellcome.config.core.builders.AkkaBuilder
import uk.ac.wellcome.config.messaging.builders.{MessagingBuilder, SQSBuilder}
import uk.ac.wellcome.config.storage.builders.VHSBuilder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.{BaseWork, TransformedBaseWork}
import uk.ac.wellcome.platform.merger.services.{
  Merger,
  MergerManager,
  MergerWorkerService,
  RecorderPlaybackService
}
import uk.ac.wellcome.storage.vhs.EmptyMetadata

import scala.concurrent.ExecutionContext

object Main extends WellcomeApp {
  override def buildWorkerService(config: Config): MergerWorkerService = {
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val playbackService = new RecorderPlaybackService(
      versionedHybridStore =
        VHSBuilder.buildVHS[TransformedBaseWork, EmptyMetadata](config)
    )

    val mergerManager = new MergerManager(
      mergerRules = new Merger()
    )

    new MergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      playbackService = playbackService,
      mergerManager = mergerManager,
      messageWriter = MessagingBuilder.buildMessageWriter[BaseWork](config)
    )
  }

  run()
}
