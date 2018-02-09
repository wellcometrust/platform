package uk.ac.wellcome.platform.sierra_items_to_dynamo

import com.google.inject.Stage
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import uk.ac.wellcome.test.utils.StartupLogbackOverride

class StartupTest extends FeatureTest with StartupLogbackOverride {

  val server = new EmbeddedHttpServer(
    twitterServer = new Server,
    flags = Map(
      "aws.dynamo.tableName" -> "startupTestTable"
    ),
    stage = Stage.PRODUCTION
  )

  test("server starts up correctly") {
    server.assertHealthy()
  }
}
