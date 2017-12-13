package uk.ac.wellcome.platform.sierra_item_merger.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.gu.scanamo.{Scanamo, Table}
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.syntax._
import com.twitter.inject.Logging
import uk.ac.wellcome.models.MergedSierraRecord
import uk.ac.wellcome.dynamo._
import uk.ac.wellcome.models.aws.DynamoConfig

import scala.concurrent.Future

import uk.ac.wellcome.utils.GlobalExecutionContext.context

class MergedSierraRecordDao(dynamoDbClient: AmazonDynamoDB,
                            dynamoConfig: DynamoConfig)
    extends Logging {
  private val table = Table[MergedSierraRecord](dynamoConfig.table)

  private def scanamoExec[T](op: ScanamoOps[T]) =
    Scanamo.exec(dynamoDbClient)(op)

  private def putRecord(record: MergedSierraRecord) =
    table
      .given(
        not(attributeExists('id)) or
          (attributeExists('id) and 'version < record.version)
      )
      .put(record)

  def updateRecord(record: MergedSierraRecord): Future[Unit] = Future {
    scanamoExec(putRecord(record))
  }

  def getRecord(id: String): Future[Option[MergedSierraRecord]] = Future {
    scanamoExec(table.get('id -> id)) match {
      case Some(Right(record)) => Some(record)
      case Some(Left(scanamoError)) =>
        val exception = new RuntimeException(scanamoError.toString)
        error(s"An error occurred while retrieving $id from DynamoDB",
              exception)
        throw exception
      case None => None
    }
  }
}
