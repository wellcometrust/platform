package uk.ac.wellcome.platform.snapshot_generator.flow

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.exceptions.GracefulFailureException
import uk.ac.wellcome.models.{
  IdentifiedWork,
  IdentifierSchemes,
  SourceIdentifier
}
import uk.ac.wellcome.test.fixtures.Akka
import uk.ac.wellcome.test.utils.ExtendedPatience
import uk.ac.wellcome.utils.JsonUtil._

import scala.concurrent.{ExecutionContextExecutor, Future}

class ElasticsearchHitToIdentifiedWorkFlowTest
    extends FunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with ExtendedPatience {

  it("creates an IdentifiedWork from a single hit") {
    withActorSystem { actorSystem =>
      implicit val executionContext: ExecutionContextExecutor =
        actorSystem.dispatcher
      withMaterializer(actorSystem) { materializer =>
        val flow = ElasticsearchHitToIdentifiedWorkFlow()

        val work = IdentifiedWork(
          canonicalId = "t83tggem",
          title = Some("Tired of troubling tests"),
          sourceIdentifier = SourceIdentifier(
            identifierScheme = IdentifierSchemes.miroImageNumber,
            ontologyType = "work",
            value = "T0083000"
          ),
          version = 1
        )

        val elasticsearchHitJson = s"""{
          "_index": "zagbdjgf",
          "_type": "work",
          "_id": "${work.canonicalId}",
          "_score": 1,
          "_source": ${toJson(work).get}
        }"""

        val futureIdentifiedWork: Future[IdentifiedWork] = Source
          .single(elasticsearchHitJson)
          .via(flow)
          .runWith(Sink.head)(materializer)

        whenReady(futureIdentifiedWork) { identifiedWork =>
          identifiedWork shouldBe work
        }
      }
    }
  }

  it("returns a failed Future if it gets invalid JSON") {
    withActorSystem { actorSystem =>
      implicit val executionContext: ExecutionContextExecutor =
        actorSystem.dispatcher
      withMaterializer(actorSystem) { materializer =>
        val flow = ElasticsearchHitToIdentifiedWorkFlow()

        val elasticsearchHitJson = s"""MARC?XML RAARGH NOTJSON"""

        val future = Source
          .single(elasticsearchHitJson)
          .via(flow)
          .runWith(Sink.head)(materializer)

        whenReady(future.failed) { result =>
          result shouldBe a[GracefulFailureException]
        }
      }
    }
  }

  it("returns a failed Future if it gets valid JSON but _source isn't a Work") {
    withActorSystem { actorSystem =>
      implicit val executionContext: ExecutionContextExecutor =
        actorSystem.dispatcher
      withMaterializer(actorSystem) { materializer =>
        val flow = ElasticsearchHitToIdentifiedWorkFlow()

        val elasticsearchHitJson = s"""{
          "_index": "rd8a35zw",
          "_type": "work",
          "_id": "ndpwrqer",
          "_score": 1,
          "_source": {"foo": "bar", "baz": "bat"}
        }"""

        val future = Source
          .single(elasticsearchHitJson)
          .via(flow)
          .runWith(Sink.head)(materializer)

        whenReady(future.failed) { result =>
          result shouldBe a[GracefulFailureException]
        }
      }
    }
  }
}
