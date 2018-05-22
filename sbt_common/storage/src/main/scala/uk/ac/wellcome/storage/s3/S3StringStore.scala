package uk.ac.wellcome.storage.s3

import com.amazonaws.services.s3.AmazonS3
import com.google.inject.Inject
import com.twitter.inject.Logging
import grizzled.slf4j.Logging
import uk.ac.wellcome.storage.GlobalExecutionContext.context

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

import uk.ac.wellcome.storage.type_classes.StorageStrategyGenerator._


class S3StringStore @Inject()(
  s3Client: AmazonS3,
)(implicit ec: ExecutionContext) extends Logging
    with S3ObjectStore[String]{

  override def put(bucket: String)(
    content: String,
    keyPrefix: String,
    keySuffix: String = ""
  ): Future[S3ObjectLocation] = {
    S3Storage.put(s3Client)(bucket)(content, keyPrefix, keySuffix)
  }

  override def get(s3ObjectLocation: S3ObjectLocation): Future[String] = {
    val futureInput = S3Storage.get(s3Client)(s3ObjectLocation)

    futureInput.map(input => Source.fromInputStream(input).mkString)
  }
}