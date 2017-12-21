package uk.ac.wellcome.models

import java.time.Instant
import java.time.Instant._

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.utils.JsonUtil

class MergedSierraRecordTest extends FunSpec with Matchers {

  describe("creation") {
    it("should allow creation of MergedSierraRecord with no data") {
      MergedSierraRecord(id = "111")
    }

    it("should allow creation from only a SierraBibRecord") {
      val bibRecord = sierraBibRecord(id = "101")
      val mergedRecord = MergedSierraRecord(bibRecord = bibRecord)
      mergedRecord.id shouldEqual bibRecord.id
      mergedRecord.maybeBibData.get shouldEqual bibRecord
    }

    it("should allow creation from a SierraBibRecord and a version") {
      val bibRecord = sierraBibRecord(id = "202")
      val version = 10
      val mergedRecord = MergedSierraRecord(
        bibRecord = bibRecord,
        version = version
      )
      mergedRecord.version shouldEqual version
    }

    it("should be at version 0 when first created") {
      val record = MergedSierraRecord(id = "555")
      record.version shouldEqual 0
    }
  }

  describe("merging with a SierraItemRecord") {
    describe("when the merged record is linked to the item") {
      it("should add the item if it doesn't exist already") {
        val record = sierraItemRecord(
          id = "i888",
          title = "Illustrious imps are ingenious",
          modifiedDate = "2008-08-08T08:08:08Z",
          bibIds = List("b888")
        )

        val mergedSierraRecord = MergedSierraRecord(id = "b888")
        val result = mergedSierraRecord.mergeItemRecord(record)

        result.itemData(record.id) shouldBe record
      }

      it("should update itemData when merging item records with newer data") {
        val itemId = "i999"
        val mergedSierraRecord = MergedSierraRecord(
          id = "b999",
          itemData = Map(
            itemId -> sierraItemRecord(
              id = itemId,
              title = "No, new narwhals are never naughty",
              modifiedDate = "2009-09-09T09:09:09Z",
              bibIds = List("b999")
            ))
        )

        val newerRecord = sierraItemRecord(
          id = itemId,
          title = "Nobody noticed the naughty narwhals",
          modifiedDate = "2010-10-10T10:10:10Z",
          bibIds = List("b999")
        )
        val result = mergedSierraRecord.mergeItemRecord(newerRecord)

        result shouldBe mergedSierraRecord.copy(
          itemData = Map(itemId -> newerRecord))
      }

      it("should return itself when merging item records with stale data") {
        val itemId = "i111"
        val mergedSierraRecord = MergedSierraRecord(
          id = "b111",
          itemData = Map(
            itemId -> sierraItemRecord(
              id = itemId,
              title = "Only otters occupy the orange oval",
              modifiedDate = "2001-01-01T01:01:01Z",
              bibIds = List("b111")
            ))
        )

        val oldRecord = sierraItemRecord(
          id = itemId,
          title = "Old otters outside the oblong",
          modifiedDate = "2000-01-01T01:01:01Z",
          bibIds = List("b111")
        )
        val result = mergedSierraRecord.mergeItemRecord(oldRecord)
        result shouldBe mergedSierraRecord
      }

      it("should support adding multiple items to a merged record") {
        val record1 = sierraItemRecord(
          id = "i111",
          title = "Outside the orangutan opens an orange",
          modifiedDate = "2001-01-01T01:01:01Z",
          bibIds = List("b121")
        )
        val record2 = sierraItemRecord(
          id = "i222",
          title = "Twice the turtles took the turn",
          modifiedDate = "2002-02-02T02:02:02Z",
          bibIds = List("b121")
        )

        val mergedSierraRecord = MergedSierraRecord(id = "b121")
        val result1 = mergedSierraRecord.mergeItemRecord(record1)
        val result2 = result1.mergeItemRecord(record2)

        result1.itemData(record1.id) shouldBe record1
        result2.itemData(record2.id) shouldBe record2
      }
    }

    describe("when the merged record is unlinked from the item") {
      it("removes the item if it already exists") {
        val bibId = "222"

        val record = sierraItemRecord(
          id = "i111",
          title = "Only otters occupy the orange oval",
          modifiedDate = "2001-01-01T01:01:01Z",
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val unlinkedItemRecord = record.copy(
          bibIds = Nil,
          modifiedDate = record.modifiedDate.plusSeconds(1),
          unlinkedBibIds = List(bibId)
        )

        val mergedSierraRecord = MergedSierraRecord(
          id = bibId,
          itemData = Map(record.id -> record)
        )

        val expectedMergedSierraRecord = mergedSierraRecord.copy(
          itemData = Map.empty
        )

        mergedSierraRecord.unlinkItemRecord(unlinkedItemRecord) shouldBe expectedMergedSierraRecord
      }

      it("returns the original record when merging an unlinked record which is already absent") {
        val bibId = "333"

        val record = sierraItemRecord(
          id = "i111",
          title = "Only otters occupy the orange oval",
          modifiedDate = "2001-01-01T01:01:01Z",
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val previouslyUnlinkedRecord = sierraItemRecord(
          id = "i222",
          title = "Only otters occupy the orange oval",
          modifiedDate = "2001-01-01T01:01:01Z",
          bibIds = List(),
          unlinkedBibIds = List(bibId)
        )

        val mergedSierraRecord = MergedSierraRecord(
          id = bibId,
          itemData = Map(record.id -> record)
        )

        val expectedMergedSierraRecord = mergedSierraRecord

        mergedSierraRecord.unlinkItemRecord(previouslyUnlinkedRecord) shouldBe expectedMergedSierraRecord
      }

      it("returns the original record when merging an unlinked record which has linked more recently") {
        val bibId = "444"
        val itemId = "i111"

        val record = sierraItemRecord(
          id = itemId,
          title = "Only otters occupy the orange oval",
          modifiedDate = "2001-01-01T01:01:01Z",
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val outOfDateUnlinkedRecord = sierraItemRecord(
          id = record.id,
          title = "Curious clams caught in caul",
          modifiedDate = "2000-01-01T01:01:01Z",
          bibIds = List(),
          unlinkedBibIds = List(bibId)
        )

        val mergedSierraRecord = MergedSierraRecord(
          id = bibId,
          itemData = Map(record.id -> record)
        )

        val expectedMergedSierraRecord = mergedSierraRecord

        mergedSierraRecord.unlinkItemRecord(outOfDateUnlinkedRecord) shouldBe expectedMergedSierraRecord
      }
    }

    describe("when the merged record is unrelated to the item") {
      describe("mergeSierraItemRecord") {
        it("should only merge item records with matching bib IDs") {
          val bibId = "444"
          val unrelatedBibId = "666"

          val record = sierraItemRecord(
            id = "i999",
            title = "Only otters occupy the orange oval",
            modifiedDate = "2001-01-01T01:01:01Z",
            bibIds = List(unrelatedBibId),
            unlinkedBibIds = List()
          )

          val mergedSierraRecord = MergedSierraRecord(id = bibId)

          val caught = intercept[RuntimeException] {
            mergedSierraRecord.mergeItemRecord(record)
          }

          caught.getMessage shouldEqual "Non-matching bib id 444 in item bib List(666)"
        }
      }
      describe("unlinkSierraItemRecord") {
        it("should only unlink item records with matching bib IDs") {
          val bibId = "222"
          val unrelatedBibId = "846"

          val record = sierraItemRecord(
            id = "i111",
            title = "Only otters occupy the orange oval",
            modifiedDate = "2001-01-01T01:01:01Z",
            bibIds = List(bibId),
            unlinkedBibIds = List()
          )

          val unrelatedItemRecord = sierraItemRecord(
            id = "i999",
            title = "Only otters occupy the orange oval",
            modifiedDate = "2001-01-01T01:01:01Z",
            bibIds = List(),
            unlinkedBibIds = List(unrelatedBibId)
          )

          val mergedSierraRecord = MergedSierraRecord(
            id = bibId,
            itemData = Map(record.id -> record)
          )

          val caught = intercept[RuntimeException] {
            mergedSierraRecord.unlinkItemRecord(unrelatedItemRecord)
          }

          caught.getMessage shouldEqual "Non-matching bib id 222 in item unlink bibs List(846)"
        }
      }
    }
  }

  def sierraBibRecord(
    id: String = "111",
    title: String = "Two toucans touching a towel",
    modifiedDate: String = "2001-01-01T01:01:01Z"
  ) = SierraBibRecord(
    id = id,
    data = sierraRecordString(
      id = id,
      updatedDate = modifiedDate,
      title = title
    ),
    modifiedDate = modifiedDate
  )

  def sierraItemRecord(
    id: String = "i111",
    title: String = "Ingenious imps invent invasive implements",
    modifiedDate: String = "2001-01-01T01:01:01Z",
    bibIds: List[String] = List(),
    unlinkedBibIds: List[String] = List()
  ) = SierraItemRecord(
    id = id,
    data = sierraRecordString(
      id = id,
      updatedDate = modifiedDate,
      title = title
    ),
    modifiedDate = modifiedDate,
    bibIds = bibIds,
    unlinkedBibIds = unlinkedBibIds
  )

  private def sierraRecordString(
    id: String,
    updatedDate: String,
    title: String
  ) =
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

}
