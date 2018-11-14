package uk.ac.wellcome.platform.archive.progress_http

import java.net.URL

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import grizzled.slf4j.Logging
import uk.ac.wellcome.messaging.sns.SNSWriter
import uk.ac.wellcome.platform.archive.common.config.models.HTTPServerConfig
import uk.ac.wellcome.platform.archive.common.progress.monitor.ProgressTracker
import uk.ac.wellcome.platform.archive.progress_http.services.{
  ProgressService,
  SwaggerDocService
}
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import akka.http.scaladsl.server.Directives._

import scala.concurrent.{ExecutionContext, Future}

class ProgressHTTP(
  dynamoClient: AmazonDynamoDB,
  dynamoConfig: DynamoConfig,
  snsWriter: SNSWriter,
  httpServerConfig: HTTPServerConfig,
  contextURL: URL
)(implicit val actorSystem: ActorSystem,
  materializer: ActorMaterializer,
  executionContext: ExecutionContext)
    extends Logging {
  val progressTracker = new ProgressTracker(
    dynamoClient = dynamoClient,
    dynamoConfig = dynamoConfig
  )

  val progressService = new ProgressService(
    monitor = progressTracker,
    progressStarter = new ProgressStarter(
      progressTracker = progressTracker,
      snsWriter = snsWriter
    ),
    httpServerConfig = httpServerConfig,
    contextURL = contextURL
  )
  val routes = progressService.routes ~ SwaggerDocService.routes

  val bindingFuture: Future[Http.ServerBinding] = Http()
    .bindAndHandle(routes, httpServerConfig.host, httpServerConfig.port)

  def run(): Future[Http.HttpTerminated] =
    bindingFuture
      .map(b => {
        info(s"Listening on ${httpServerConfig.host}:${httpServerConfig.port}")
        b
      })
      .flatMap { _.whenTerminated }
}
