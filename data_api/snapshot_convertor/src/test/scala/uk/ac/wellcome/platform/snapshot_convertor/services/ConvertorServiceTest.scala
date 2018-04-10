package uk.ac.wellcome.platform.snapshot_convertor.services

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.s3.scaladsl.S3Client
import com.amazonaws.services.s3.model.{AmazonS3Exception, GetObjectRequest}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.display.models.{AllWorksIncludes, DisplayWork}
import uk.ac.wellcome.exceptions.GracefulFailureException
import uk.ac.wellcome.models.{
  IdentifiedWork,
  IdentifierSchemes,
  Period,
  SourceIdentifier
}
import uk.ac.wellcome.platform.snapshot_convertor.fixtures.AkkaS3
import uk.ac.wellcome.platform.snapshot_convertor.models.{
  CompletedConversionJob,
  ConversionJob
}
import uk.ac.wellcome.platform.snapshot_convertor.test.utils.GzipUtils
import uk.ac.wellcome.test.fixtures.{Akka, S3, TestWith, _}
import uk.ac.wellcome.test.utils.ExtendedPatience
import uk.ac.wellcome.utils.JsonUtil._

import scala.util.Random

class ConvertorServiceTest
    extends FunSpec
    with ScalaFutures
    with Matchers
    with Akka
    with AkkaS3
    with S3
    with GzipUtils
    with ExtendedPatience {

  val mapper = new ObjectMapper with ScalaObjectMapper

  private def withConvertorService[R](
    actorSystem: ActorSystem,
    materializer: ActorMaterializer,
    s3AkkaClient: S3Client)(testWith: TestWith[ConvertorService, R]) = {
    val convertorService = new ConvertorService(
      actorSystem = actorSystem,
      s3Client = s3Client,
      akkaS3Client = s3AkkaClient,
      s3Endpoint = localS3EndpointUrl,
      objectMapper = mapper
    )

    testWith(convertorService)
  }

  def withFixtures[R] =
    withActorSystem[R] and
      withMaterializer[R] _ and
      withS3AkkaClient[R] _ and
      withConvertorService[R] _ and
      withLocalS3Bucket[R] and
      withLocalS3Bucket[R]

  it("completes a conversion successfully") {
    withFixtures {
      case (
          ((_, _, _, convertorService: ConvertorService), sourceBucketName),
          targetBucketName) =>
        // Create a collection of works.  These three differ by version,
        // if not anything more interesting!
        val visibleWorks = (1 to 3).map { version =>
          IdentifiedWork(
            canonicalId = "rbfhv6b4",
            title = Some("Rumblings from a rambunctious rodent"),
            sourceIdentifier = SourceIdentifier(
              identifierScheme = IdentifierSchemes.miroImageNumber,
              ontologyType = "work",
              value = "R0060400"
            ),
            version = version
          )
        }

        val notVisibleWork = IdentifiedWork(
          canonicalId = "rbfhv6b4",
          title = Some("Rumblings from a rambunctious rodent"),
          sourceIdentifier = SourceIdentifier(
            identifierScheme = IdentifierSchemes.miroImageNumber,
            ontologyType = "work",
            value = "R0060400"
          ),
          visible = false,
          version = 1
        )

        val works = visibleWorks :+ notVisibleWork

        val elasticsearchJsons = works.map { work =>
          s"""{"_index": "jett4fvw", "_type": "work", "_id": "${work.canonicalId}", "_score": 1, "_source": ${toJson(
            work).get}}"""
        }
        val content = elasticsearchJsons.mkString("\n")

        withGzipCompressedS3Key(sourceBucketName, content) { objectKey =>
          val targetObjectKey = "target.txt.gz"

          val conversionJob = ConversionJob(
            sourceBucketName = sourceBucketName,
            sourceObjectKey = objectKey,
            targetBucketName = targetBucketName,
            targetObjectKey = targetObjectKey
          )

          val future = convertorService.runConversion(conversionJob)

          whenReady(future) { result =>
            val downloadFile =
              File.createTempFile("convertorServiceTest", ".txt.gz")
            s3Client.getObject(
              new GetObjectRequest(targetBucketName, targetObjectKey),
              downloadFile)

            val contents = readGzipFile(downloadFile.getPath)
            val expectedContents = visibleWorks
              .map {
                DisplayWork(_, includes = AllWorksIncludes())
              }
              .map {
                mapper.writeValueAsString(_)
              }
              .mkString("\n") + "\n"

            contents shouldBe expectedContents

            result shouldBe CompletedConversionJob(
              conversionJob = conversionJob,
              targetLocation =
                s"http://localhost:33333/$targetBucketName/$targetObjectKey"
            )
          }
        }
    }
  }

  // This test is meant to catch an error we saw when we first turned on
  // the snapshot convertor:
  //
  //    akka.http.scaladsl.model.EntityStreamSizeException:
  //    EntityStreamSizeException: actual entity size (Some(19403836)) exceeded
  //    content length limit (8388608 bytes)! You can configure this by setting
  //    `akka.http.[server|client].parsing.max-content-length` or calling
  //    `HttpEntity.withSizeLimit` before materializing the dataBytes stream.
  //
  // With the original code, we were unable to read anything more than
  // an 8MB file from S3.  This test deliberately creates a very large file,
  // and tries to stream it back out.
  //
  it("completes a very large conversion successfully") {
    withFixtures {
      case (
          ((_, _, _, convertorService: ConvertorService), sourceBucketName),
          targetBucketName) =>
        // Create a collection of works.  The use of Random is meant
        // to increase the entropy of works, and thus the degree to
        // which they can be gzip-compressed -- so we can cross the
        // 8MB boundary with a shorter list!
        val works = (1 to 5000).map { version =>
          IdentifiedWork(
            canonicalId = Random.alphanumeric.take(7).mkString,
            title = Some(Random.alphanumeric.take(1500).mkString),
            sourceIdentifier = SourceIdentifier(
              identifierScheme = IdentifierSchemes.miroImageNumber,
              ontologyType = "work",
              value = Random.alphanumeric.take(10).mkString
            ),
            description = Some(Random.alphanumeric.take(2500).mkString),
            publicationDate = Some(Period(label = version.toString)),
            version = version
          )
        }

        val elasticsearchJsons = works.map { work =>
          s"""{"_index": "jett4fvw", "_type": "work", "_id": "${work.canonicalId}", "_score": 1, "_source": ${toJson(
            work).get}}"""
        }
        val content = elasticsearchJsons.mkString("\n")

        // We want to ensure the source snapshot is at least 8MB in size.
        val gzipFileSize = createGzipFile(content).length.toInt
        gzipFileSize shouldBe >=(8 * 1024 * 1024)

        withGzipCompressedS3Key(sourceBucketName, content) { objectKey =>
          val targetObjectKey = "target.txt.gz"
          val conversionJob = ConversionJob(
            sourceBucketName = sourceBucketName,
            sourceObjectKey = objectKey,
            targetBucketName = targetBucketName,
            targetObjectKey = targetObjectKey
          )

          val future = convertorService.runConversion(conversionJob)

          whenReady(future) { result =>
            val downloadFile =
              File.createTempFile("convertorServiceTest", ".txt.gz")
            s3Client.getObject(
              new GetObjectRequest(targetBucketName, targetObjectKey),
              downloadFile)

            val contents = readGzipFile(downloadFile.getPath)
            val expectedContents = works
              .map {
                DisplayWork(_, includes = AllWorksIncludes())
              }
              .map {
                mapper.writeValueAsString(_)
              }
              .mkString("\n") + "\n"

            contents shouldBe expectedContents

            result shouldBe CompletedConversionJob(
              conversionJob = conversionJob,
              targetLocation =
                s"http://localhost:33333/$targetBucketName/$targetObjectKey"
            )
          }
        }
    }
  }

  it("returns a failed future if asked to convert a non-existent snapshot") {
    withFixtures {
      case (
          ((_, _, _, convertorService: ConvertorService), sourceBucketName),
          targetBucketName) =>
        val conversionJob = ConversionJob(
          sourceBucketName = sourceBucketName,
          sourceObjectKey = "doesnotexist.txt.gz",
          targetBucketName = targetBucketName,
          targetObjectKey = "target.txt.gz"
        )

        val future = convertorService.runConversion(conversionJob)

        whenReady(future.failed) { result =>
          result shouldBe a[AmazonS3Exception]
        }
    }
  }

  it("returns a failed future if asked to convert a malformed snapshot") {
    withFixtures {
      case (
          ((_, _, _, convertorService: ConvertorService), sourceBucketName),
          targetBucketName) =>
        withGzipCompressedS3Key(
          sourceBucketName,
          content = "This is not what snapshots look like") { objectKey =>
          val conversionJob = ConversionJob(
            sourceBucketName = sourceBucketName,
            sourceObjectKey = objectKey,
            targetBucketName = targetBucketName,
            targetObjectKey = "target.txt.gz"
          )

          val future = convertorService.runConversion(conversionJob)

          whenReady(future.failed) { result =>
            result shouldBe a[GracefulFailureException]
          }
        }
    }
  }

  it("returns a failed future if the S3 upload fails") {
    withFixtures {
      case (
          ((_, _, _, convertorService: ConvertorService), sourceBucketName),
          targetBucketName) =>
        // Create a collection of works.  These three differ by version,
        // if not anything more interesting!
        val works = (1 to 3).map { version =>
          IdentifiedWork(
            canonicalId = "h4dh3esm",
            title = Some("Harrowing Henry is hardly heard from"),
            sourceIdentifier = SourceIdentifier(
              identifierScheme = IdentifierSchemes.miroImageNumber,
              ontologyType = "work",
              value = "r4f2t3bf"
            ),
            version = version
          )
        }

        val elasticsearchJsons = works.map { work =>
          s"""{"_index": "jett4fvw", "_type": "work", "_id": "${work.canonicalId}", "_score": 1, "_source": ${toJson(
            work).get}}"""
        }
        val content = elasticsearchJsons.mkString("\n")

        val bucketName = "wrongBukkit"
        val conversionJob = ConversionJob(
          sourceBucketName = bucketName,
          sourceObjectKey = "wrongKey",
          targetBucketName = bucketName,
          targetObjectKey = "target.json.gz"
        )

        val future = convertorService.runConversion(conversionJob)

        whenReady(future.failed) { result =>
          result shouldBe a[AmazonS3Exception]
        }

    }

  }
}
