package uk.ac.wellcome.platform.sierra_reader.modules

import java.time.Instant

import org.scalatest.compatible.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.exceptions.GracefulFailureException
import uk.ac.wellcome.platform.sierra_reader.models.{
  SierraConfig,
  SierraResourceTypes
}
import uk.ac.wellcome.sierra_adapter.models.SierraRecord
import uk.ac.wellcome.storage.s3.S3Config
import uk.ac.wellcome.storage.test.fixtures.S3
import uk.ac.wellcome.storage.test.fixtures.S3.Bucket
import uk.ac.wellcome.test.fixtures.TestWith
import uk.ac.wellcome.test.utils.ExtendedPatience
import uk.ac.wellcome.utils.JsonUtil._

class WindowManagerTest
    extends FunSpec
    with Matchers
    with S3
    with ScalaFutures
    with ExtendedPatience {

  private def withWindowManager(bucket: Bucket)(
    testWith: TestWith[WindowManager, Assertion]) = {
    val sierraConfig = SierraConfig(
      resourceType = SierraResourceTypes.bibs,
      apiUrl = "http://example.org",
      oauthKey = "0au1hk3y",
      oauthSec = "o4uth5ec",
      fields = "title"
    )

    val windowManager = new WindowManager(
      s3client = s3Client,
      s3Config = S3Config(bucket.name),
      sierraConfig = sierraConfig
    )

    testWith(windowManager)
  }

  it("returns an empty ID and offset 0 if there isn't a window in progress") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val result = windowManager.getCurrentStatus("[2013,2014]")

        whenReady(result) {
          _ shouldBe WindowStatus(id = None, offset = 0)
        }
      }
    }
  }

  it("finds the ID if there is a window in progress") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val prefix = windowManager.buildWindowShard("[2013,2014]")

        // We pre-populate S3 with files as if they'd come from a prior run of the reader.
        s3Client.putObject(bucket.name, s"$prefix/0000.json", "[]")

        val record =
          SierraRecord(
            id = "b1794165",
            data = "{}",
            modifiedDate = Instant.now())

        s3Client.putObject(
          bucket.name,
          s"$prefix/0001.json",
          toJson(List(record)).get
        )

        val result = windowManager.getCurrentStatus("[2013,2014]")

        whenReady(result) {
          _ shouldBe WindowStatus(id = Some("1794166"), offset = 2)
        }
      }
    }
  }

  it("throws an error if it finds invalid JSON in the bucket") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val prefix = windowManager.buildWindowShard("[2013,2014]")
        s3Client.putObject(bucket.name, s"$prefix/0000.json", "not valid")

        val result = windowManager.getCurrentStatus("[2013,2014]")

        whenReady(result.failed) {
          _ shouldBe a[GracefulFailureException]
        }
      }
    }
  }

  it("throws an error if it finds empty JSON in the bucket") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val prefix = windowManager.buildWindowShard("[2013,2014]")

        s3Client.putObject(bucket.name, s"$prefix/0000.json", "[]")

        val result = windowManager.getCurrentStatus("[2013,2014]")

        whenReady(result.failed) {
          _ shouldBe a[GracefulFailureException]
        }
      }
    }
  }

  it("throws an error if it finds a misnamed file in the bucket") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val prefix = windowManager.buildWindowShard("[2013,2014]")

        s3Client.putObject(bucket.name, s"$prefix/000x.json", "[]")

        val result = windowManager.getCurrentStatus("[2013,2014]")

        whenReady(result.failed) {
          _ shouldBe a[GracefulFailureException]
        }
      }
    }
  }
}
