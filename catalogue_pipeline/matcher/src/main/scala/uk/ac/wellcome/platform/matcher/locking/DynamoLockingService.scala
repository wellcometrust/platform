package uk.ac.wellcome.platform.matcher.lockable
import java.util.UUID.randomUUID

import grizzled.slf4j.Logging
import javax.inject.Inject
import uk.ac.wellcome.monitoring.MetricsSender

import scala.concurrent.{ExecutionContext, Future}

class DynamoLockingService @Inject()(
  dynamoRowLockDao: DynamoRowLockDao,
  metricsSender: MetricsSender)(implicit context: ExecutionContext)
    extends Logging {

  val failedLockMetricName: String = "WorkMatcher_FailedLock"

  def withLocks[T](ids: Set[String])(f: => Future[T]): Future[T] = {
    val contextGuid = randomUUID.toString
    val identifiers: Set[Identifier] = ids.map(Identifier)
    debug(s"Locking identifiers $identifiers in context $contextGuid")
    val eventuallyExecutedWithLock = for {
      _ <- Future.sequence(
        identifiers.map(dynamoRowLockDao.lockRow(_, contextGuid)))
      result <- f
    } yield {
      result
    }
    eventuallyExecutedWithLock
      .transformWith { triedResult =>
        dynamoRowLockDao
          .unlockRows(contextGuid)
          .flatMap(_ => {
            debug(s"Released locked identifiers $identifiers in $contextGuid")
            Future.fromTry(triedResult)
          })
      }
      .recover {
        case failedLockException: FailedLockException =>
          info(
            s"Failed to obtain a lock ${failedLockException.getClass.getSimpleName} ${failedLockException.getMessage}")
          metricsSender.incrementCount(failedLockMetricName)
          throw failedLockException
      }
  }
}
