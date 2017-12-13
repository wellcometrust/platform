package uk.ac.wellcome.platform.sierra_item_merger.services

import com.gu.scanamo.Scanamo
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.metrics.MetricsSender
import uk.ac.wellcome.models.MergedSierraRecord
import uk.ac.wellcome.models.aws.DynamoConfig
import uk.ac.wellcome.platform.sierra_item_merger.utils.SierraItemMergerTestUtil
import uk.ac.wellcome.dynamo._
import uk.ac.wellcome.test.utils.ExtendedPatience

class SierraItemMergerUpdaterServiceTest
    extends FunSpec
    with Matchers
    with SierraItemMergerTestUtil
    with MockitoSugar
    with ScalaFutures with ExtendedPatience{
  val sierraUpdateRService = new SierraItemMergerUpdaterService(
    dynamoDbClient,
    mock[MetricsSender],
    DynamoConfig(tableName))

  it(
    "should update an item in DynamoDB if it receives an update with a newer date") {
    val id = "i3000003"
    val bibId = "b3000003"
    val oldRecord = MergedSierraRecord(
      id = bibId,
      itemData = Map(
        id -> sierraItemRecord(
          id = id,
          updatedDate = "2003-03-03T03:03:03Z",
          bibIds = List(bibId)
        )),
      version = 1
    )
    Scanamo.put(dynamoDbClient)(tableName)(oldRecord)

    val newItemRecord = sierraItemRecord(
      id = id,
      updatedDate = "2014-04-04T04:04:04Z",
      bibIds = List(bibId)
    )

    whenReady(sierraUpdateRService.update(newItemRecord)) { _ =>
      val expectedSierraRecord = oldRecord.copy(itemData = Map(id -> newItemRecord), version = 2)
      dynamoQueryEqualsValue(id = bibId)(expectedValue = expectedSierraRecord)
    }
  }

  it(
    "should not update an item in DynamoDB if it receives an update with an older date") {
    val id = "i6000006"
    val bibId = "b6000006"
    val newRecord = MergedSierraRecord(
      id = bibId,
      itemData = Map(
        id -> sierraItemRecord(
          id = id,
          updatedDate = "2006-06-06T06:06:06Z",
          bibIds = List(bibId)
        )),
      version = 1
    )
    Scanamo.put(dynamoDbClient)(tableName)(newRecord)

    val oldItemRecord = sierraItemRecord(
      id = id,
      updatedDate = "2001-01-01T01:01:01Z",
      bibIds = List(bibId)
    )

    whenReady(sierraUpdateRService.update(oldItemRecord)) { _ =>
      dynamoQueryEqualsValue(id = bibId)(expectedValue = newRecord)
    }
  }

  it(
    "should add an item to the MergedSierraRecord if the bibId exists in DuynamoDB but no itemData") {
    val bibId = "b7000007"
    val newRecord = MergedSierraRecord(id = bibId, version = 1)
    Scanamo.put(dynamoDbClient)(tableName)(newRecord)

    val itemRecord = sierraItemRecord(
      id = "i7000007",
      updatedDate = "2007-07-07T07:07:07Z",
      bibIds = List(bibId)
    )

    whenReady(sierraUpdateRService.update(itemRecord)) { _ =>
      val expectedSierraRecord = MergedSierraRecord(
        id = bibId,
        itemData = Map(itemRecord.id -> itemRecord),
        version = 2
      )
      dynamoQueryEqualsValue(id = bibId)(expectedValue = expectedSierraRecord)
    }
  }

  // shouldn't unlink if older version
  // read unlinked IDs
  // put multiple items with same bibId
}
