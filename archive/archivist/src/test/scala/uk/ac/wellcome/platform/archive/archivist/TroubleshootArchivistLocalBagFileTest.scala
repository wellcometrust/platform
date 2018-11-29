package uk.ac.wellcome.platform.archive.archivist

import java.util.zip.ZipFile

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import uk.ac.wellcome.platform.archive.archivist.fixtures.ArchivistFixtures

// Useful test to troubleshoot running the archivist using a local bagfile
class TroubleshootArchivistLocalBagFileTest
    extends FunSpec
    with Matchers
    with ArchivistFixtures
    with MetricsSenderFixture {

  ignore("downloads, uploads and verifies a known BagIt bag") {
    withArchivist { case (ingestBucket, storageBucket, queuePair, _, _) =>
      sendBag(
        new ZipFile(
          List(
            System.getProperty("user.home"),
            "git/platform",
            "b22454408.zip"
          ).mkString("/")),
        ingestBucket,
        queuePair
      ) { _ =>
        while (true) {
          Thread.sleep(10000)
          println(s"Uploaded: ${listKeysInBucket(storageBucket).size}")
        }
      }
    }
  }
}
