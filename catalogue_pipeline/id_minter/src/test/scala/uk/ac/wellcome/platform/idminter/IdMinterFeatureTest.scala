package uk.ac.wellcome.platform.idminter

import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTestMixin
import org.scalatest.{BeforeAndAfterEach, FunSpec}
import scalikejdbc._
import uk.ac.wellcome.circe.jsonUtil
import uk.ac.wellcome.circe.jsonUtil._
import uk.ac.wellcome.models.{IdentifierSchemes, _}
import uk.ac.wellcome.models.aws.SQSMessage
import uk.ac.wellcome.platform.idminter.utils.IdMinterTestUtils
import uk.ac.wellcome.test.utils.MessageInfo
import uk.ac.wellcome.utils.JsonUtil

class IdMinterFeatureTest
    extends FunSpec
    with FeatureTestMixin
    with IdMinterTestUtils
    with BeforeAndAfterEach {

  override val server: EmbeddedHttpServer = defineServer
  private val i = identifiersTable.i

  override def beforeEach(): Unit = {
    super.beforeEach()
    sql"TRUNCATE TABLE $identifiersDatabase.$identifiersTableName"
      .execute()
      .apply()
  }

  it(
    "mints the same ID for SourcedWorks that have matching source identifiers") {
    val miroID = "M0001234"
    val title = "A limerick about a lion"

    val identifier =
      SourceIdentifier(IdentifierSchemes.miroImageNumber, miroID)

    val work = Work(
      sourceIdentifier = identifier,
      identifiers = List(identifier),
      title = title
    )

    val value = jsonUtil.toJsonCirce(work).get

    val sqsMessage = SQSMessage(
      Some("subject"),
      value,
      "topic",
      "messageType",
      "timestamp"
    )

    def sendMessage = sqsClient.sendMessage(
      idMinterQueue,
      jsonUtil.toJsonCirce(sqsMessage).get
    )

    def getWorksFromMessages(messages: Seq[MessageInfo]) =
      messages.map(m => JsonUtil.fromJson[Work](m.message).get)

    sendMessage

    eventually {
      val messages = listMessagesReceivedFromSNS()
      messages should have size (1)

      val work = getWorksFromMessages(messages).head

      work.identifiers.head.value shouldBe miroID
      work.title shouldBe title
    }

    sendMessage

    eventually {
      val moreMessages = listMessagesReceivedFromSNS()
      moreMessages should have size (2)

      val works = getWorksFromMessages(moreMessages)

      works.head shouldBe works.tail.head
    }

  }

  it("continues if something fails processing a message") {
    sqsClient.sendMessage(idMinterQueue, "not a json string")

    val miroId = "1234"
    val sqsMessage = generateSqsMessage(miroId)

    sqsClient.sendMessage(idMinterQueue, jsonUtil.toJsonCirce(sqsMessage).get)

    eventually {
      val messages = listMessagesReceivedFromSNS()
      messages should have size (1)
    }

    assertMessageIsNotDeleted()
  }
}
