package uk.ac.wellcome.platform.sierra_reader.services

import akka.actor.ActorSystem
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSpec, Matchers}
import uk.ac.wellcome.metrics.MetricsSender
import uk.ac.wellcome.models.aws.{SQSConfig, SQSMessage}
import uk.ac.wellcome.sqs.{SQSReader, SQSReaderGracefulException}
import uk.ac.wellcome.test.utils.{ExtendedPatience, S3Local, SQSLocal}
import uk.ac.wellcome.utils.JsonUtil

import scala.collection.JavaConversions._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import uk.ac.wellcome.platform.sierra_reader.flow.{SierraRecord, SierraResourceTypes}
import uk.ac.wellcome.circe._

import scala.concurrent.duration._

class SierraReaderWorkerServiceTest
    extends FunSpec
    with MockitoSugar
    with S3Local
    with SQSLocal
    with Eventually
    with Matchers
    with ExtendedPatience
    with ScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  val queueUrl = createQueueAndReturnUrl("sierra-test-queue")
  val bucketName: String = createBucketAndReturnName("sierra-reader-test-bucket")

  val mockMetrics = mock[MetricsSender]
  var worker: Option[SierraReaderWorkerService] = None
  val actorSystem = ActorSystem()

  override def beforeEach(): Unit = {
    super.beforeEach()
    stopWorker(worker)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    actorSystem.terminate()
  }

  private def createSierraReaderWorkerService(
    fields: String,
    apiUrl: String = "http://localhost:8080",
    batchSize: Int = 50,
    resourceType: SierraResourceTypes.Value = SierraResourceTypes.bibs
  ) = {
    Some(
      new SierraReaderWorkerService(
        reader = new SQSReader(sqsClient, SQSConfig(queueUrl, 1.second, 1)),
        s3client = s3Client,
        batchSize = batchSize,
        resourceType = resourceType,
        bucketName = bucketName,
        system = actorSystem,
        metrics = mockMetrics,
        apiUrl = apiUrl,
        sierraOauthKey = "key",
        sierraOauthSecret = "secret",
        fields = fields
      ))
  }

  it(
    "reads a window message from SQS, retrieves the bibs from Sierra and writes them to S3") {
    worker = createSierraReaderWorkerService(
      fields = "updatedDate,deletedDate,deleted,suppressed,author,title",
      batchSize = 10,
      resourceType = SierraResourceTypes.bibs
    )
    worker.get.runSQSWorker()
    val message =
      """
        |{
        | "start": "2013-12-10T17:16:35Z",
        | "end": "2013-12-13T21:34:35Z"
        |}
      """.stripMargin

    val sqsMessage =
      SQSMessage(Some("subject"), message, "topic", "messageType", "timestamp")
    sqsClient.sendMessage(queueUrl, JsonUtil.toJson(sqsMessage).get)

    eventually {
      val objects = s3Client.listObjects(bucketName).getObjectSummaries

      // there are 29 bib updates in the sierra wiremock so we expect 3 files
      objects should have size 3
      objects.map { _.getKey() } shouldBe List("0000.json", "0001.json", "0002.json")

      getRecordsFromS3("0000.json") should have size 10
      getRecordsFromS3("0001.json") should have size 10
      getRecordsFromS3("0002.json") should have size 9
    }
  }

  it(
    "reads a window message from SQS, retrieves the items from Sierra and writes them to S3") {
    worker = createSierraReaderWorkerService(
      fields = "updatedDate,deleted,deletedDate,bibIds,fixedFields,varFields",
      batchSize = 50,
      resourceType = SierraResourceTypes.items
    )
    worker.get.runSQSWorker()
    val message =
      """
        |{
        | "start": "2013-12-10T17:16:35Z",
        | "end": "2013-12-13T21:34:35Z"
        |}
      """.stripMargin

    val sqsMessage =
      SQSMessage(Some("subject"), message, "topic", "messageType", "timestamp")
    sqsClient.sendMessage(queueUrl, JsonUtil.toJson(sqsMessage).get)

    eventually {
      val objects = s3Client.listObjects(bucketName).getObjectSummaries

      // there are 29 bib updates in the sierra wiremock so we expect 3 files
      objects should have size 4
      objects.map { _.getKey() } shouldBe List("0000.json", "0001.json", "0002.json", "0003.json")

      getRecordsFromS3("0000.json") should have size 50
      getRecordsFromS3("0001.json") should have size 50
      getRecordsFromS3("0002.json") should have size 50
      getRecordsFromS3("0003.json") should have size 7
    }
  }

  private def getRecordsFromS3(key: String): List[SierraRecord] =
    decode[List[SierraRecord]](getContentFromS3(bucketName, key)).right.get

  it(
    "returns a SQSReaderGracefulException if it receives a message that doesn't contain start or end values") {
    worker = createSierraReaderWorkerService(fields = "")

    val message =
      """
        |{
        | "start": "2013-12-10T17:16:35Z"
        |}
      """.stripMargin

    val sqsMessage =
      SQSMessage(Some("subject"), message, "topic", "messageType", "timestamp")
    whenReady(worker.get.processMessage(sqsMessage).failed) { ex =>
      ex shouldBe a[SQSReaderGracefulException]
    }

  }

  it(
    "does not return a SQSReaderGracefulException if it cannot reach the Sierra API") {
    worker = createSierraReaderWorkerService(
      fields = "",
      apiUrl = "http://localhost:5050"
    )

    val message =
      """
        |{
        | "start": "2013-12-10T17:16:35Z",
        | "end": "2013-12-13T21:34:35Z"
        |}
      """.stripMargin

    val sqsMessage =
      SQSMessage(Some("subject"), message, "topic", "messageType", "timestamp")

    whenReady(worker.get.processMessage(sqsMessage).failed) { ex =>
      ex shouldNot be(a[SQSReaderGracefulException])
    }
  }

  private def stopWorker(worker: Option[SierraReaderWorkerService]) = {
    eventually {
      worker.fold(true)(_.cancelRun()) shouldBe true
    }
  }
}
