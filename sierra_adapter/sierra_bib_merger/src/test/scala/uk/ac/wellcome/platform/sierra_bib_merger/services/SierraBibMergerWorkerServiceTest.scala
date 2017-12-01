package uk.ac.wellcome.platform.sierra_bib_merger.services

import akka.actor.ActorSystem
import com.gu.scanamo.Scanamo
import com.gu.scanamo.syntax._
import com.gu.scanamo.query.UniqueKey
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTestMixin
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.SierraRecord
import uk.ac.wellcome.models.aws.SQSMessage
import uk.ac.wellcome.platform.sierra_bib_merger.Server
import uk.ac.wellcome.platform.sierra_bib_merger.locals.DynamoDBLocal
import uk.ac.wellcome.models.MergedSierraObject
import com.gu.scanamo.DynamoFormat
import uk.ac.wellcome.test.utils.{AmazonCloudWatchFlag, SQSLocal}
import uk.ac.wellcome.utils.JsonUtil


class SierraBibMergerWorkerServiceTest
    extends FunSpec
    with FeatureTestMixin
    with AmazonCloudWatchFlag
    with Matchers
    with SQSLocal
    with DynamoDBLocal {

  implicit val system = ActorSystem()
  implicit val executionContext = system.dispatcher

  val queueUrl = createQueueAndReturnUrl("test_bib_merger")

  override protected def server = new EmbeddedHttpServer(
    new Server(),
    flags = Map(
      "aws.sqs.queue.url" -> queueUrl,
      "aws.sqs.waitTime" -> "1",
      "aws.dynamo.sierraBibMerger.tableName" -> tableName
    ) ++ sqsLocalFlags ++ cloudWatchLocalEndpointFlag ++ dynamoDbLocalEndpointFlags
  )

  def bibRecordString(id: String,
                      updatedDate: String,
                      title: String = "Lehrbuch und Atlas der Gastroskopie") =
    s"""
      |{
      |      "id": "$id",
      |      "updatedDate": "$updatedDate",
      |      "createdDate": "1999-11-01T16:36:51Z",
      |      "deleted": false,
      |      "suppressed": false,
      |      "lang": {
      |        "code": "ger",
      |        "name": "German"
      |      },
      |      "title": "$title",
      |      "author": "Schindler, Rudolf, 1888-",
      |      "materialType": {
      |        "code": "a",
      |        "value": "Books"
      |      },
      |      "bibLevel": {
      |        "code": "m",
      |        "value": "MONOGRAPH"
      |      },
      |      "publishYear": 1923,
      |      "catalogDate": "1999-01-01",
      |      "country": {
      |        "code": "gw ",
      |        "name": "Germany"
      |      }
      |    }
    """.stripMargin

  it("should put a bib from SQS into DynamoDB") {
    val id = "1000001"
    sendMessageForBibToSQS(id = id, updatedDate = "2001-01-01T01:01:01Z")
    val expectedMergedSierraObject = MergedSierraObject(id)

    dynamoQueryEqualsValue('id -> id)(expectedValue = expectedMergedSierraObject)
  }

  it("should put multiple bibs from SQS into DynamoDB") {
    val id1 = "1000001"
    sendMessageForBibToSQS(id = id1, updatedDate = "2001-01-01T01:01:01Z")
    val expectedMergedSierraObject1 = MergedSierraObject(id1)

    val id2 = "2000002"
    sendMessageForBibToSQS(id = id2, updatedDate = "2002-02-02T02:02:02Z")
    val expectedMergedSierraObject2 = MergedSierraObject(id2)

    dynamoQueryEqualsValue('id -> id1)(expectedValue = expectedMergedSierraObject1)
    dynamoQueryEqualsValue('id -> id2)(expectedValue = expectedMergedSierraObject2)
  }

  it("should update a bib in DynamoDB if a newer version is sent to SQS") { }

  it("should not update a bib in DynamoDB if an older version is sent to SQS") { }

  private def sendMessageForBibToSQS(id: String, updatedDate: String) = {
    val record = SierraRecord(
      id = id,
      data = bibRecordString(id, updatedDate),
      modifiedDate = updatedDate
    )
    val messageBody = JsonUtil.toJson(record).get

    val message = SQSMessage(
      subject = Some("Test message sent by SierraBibMergerWorkerServiceTest"),
      body = messageBody,
      topic = "topic",
      messageType = "messageType",
      timestamp = "2001-01-01T01:01:01Z"
    )
    sqsClient.sendMessage(queueUrl, JsonUtil.toJson(message).get)
  }

  private def dynamoQueryEqualsValue[T: DynamoFormat](key: UniqueKey[_])(expectedValue: T) = {
    eventually {
      val actualValue = Scanamo.get[T](dynamoDbClient)(tableName)(key).get
      actualValue shouldEqual Right(expectedValue)
    }
  }
}
