package uk.ac.wellcome.platform.sierra_to_dynamo.sink

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset}

import akka.Done
import akka.stream.scaladsl.Sink
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.gu.scanamo.syntax._
import com.gu.scanamo.{Scanamo, Table}
import com.twitter.inject.Logging
import io.circe.Json
import io.circe.optics.JsonPath.root
import uk.ac.wellcome.platform.sierra_to_dynamo.models.SierraRecord
import uk.ac.wellcome.platform.sierra_to_dynamo.models.SierraRecord._

import scala.concurrent.Future

object SierraDynamoSink extends Logging {
  def apply(client: AmazonDynamoDB,
            tableName: String): Sink[Json, Future[Done]] =
    Sink.foreach(json => {
      logger.debug(s"Inserting ${json.spaces4} in dynamo Db")
      val maybeUpdatedDate = root.updatedDate.string.getOption(json)
      val record = maybeUpdatedDate match {
        case Some(updatedDate) =>
          SierraRecord(
            id = getId(json),
            data = json.noSpaces,
            modifiedDate = updatedDate
          )
        case None =>
          SierraRecord(
            id = getId(json),
            data = json.noSpaces,
            modifiedDate = getDeletedDateTimeAtStartOfDay(json)
          )
      }

      val table = Table[SierraRecord](tableName)
      val ops = table
        .given(
          not(attributeExists('id)) or
            (attributeExists('id) and 'updatedDate < record.modifiedDate.getEpochSecond)
        )
        .put(record)
      Scanamo.exec(client)(ops) match {
        case Right(_) => logger.info(s"${json.spaces4} saved successfully to dynamo Db")
        case Left(error) => logger.warn(s"Failed saving ${json.spaces4} into DynamoDB", error)
      }
    })

  private def getDeletedDateTimeAtStartOfDay(json: Json) = {
    val formatter = DateTimeFormatter.ISO_DATE
    LocalDate.parse(root.deletedDate.string.getOption(json).get, formatter).atStartOfDay().toInstant(ZoneOffset.UTC)
  }

  private def getId(json: Json) = {
    root.id.string.getOption(json).get
  }
}
