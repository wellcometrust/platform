package uk.ac.wellcome.platform.archive.archivist.flow

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import com.amazonaws.services.s3.AmazonS3
import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.archive.archivist.models.{ArchiveJob, BagUploaderConfig}
import uk.ac.wellcome.platform.archive.common.models.ArchiveComplete

object ArchiveZipFileFlow extends Logging {
  def apply(config: BagUploaderConfig)(
    implicit s3Client: AmazonS3
  ): Flow[ZipFileDownloadComplete, ArchiveComplete, NotUsed] = {

    Flow[ZipFileDownloadComplete].flatMapConcat {
      case ZipFileDownloadComplete(zipFile, ingestRequest) =>
        Source
          .single(zipFile)
          .map(ArchiveJob.create(_, config).right.get)
          // TODO: Log error here
          .via(ArchiveJobFlow(config.bagItConfig.digestDelimiterRegexp))
          .collect {case Right(archiveJob) => archiveJob}
          .map(job =>
            ArchiveComplete(
              job.bagLocation,
              ingestRequest
            ))
    }
  }
}