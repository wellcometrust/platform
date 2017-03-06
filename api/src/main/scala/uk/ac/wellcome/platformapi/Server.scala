package uk.ac.wellcome.platform.api

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{CommonFilters, LoggingMDCFilter, TraceIdMDCFilter}
import com.twitter.finatra.http.routing.HttpRouter

import uk.ac.wellcome.finatra.exceptions._
import uk.ac.wellcome.finatra.modules._
import uk.ac.wellcome.platform.api.controllers._

import io.swagger.models.{Info, Swagger}


object ServerMain extends Server

object ApiSwagger extends Swagger

class Server extends HttpServer {
  override val name = "uk.ac.wellcome.platform.api Platformapi"
  override val modules = Seq(ElasticClientModule)

  ApiSwagger.info(
    new Info()
      .description("An API")
      .version("0.0.1")
      .title("The API"))

  val swaggerController =
    new SwaggerController(swagger = ApiSwagger)

  override def configureHttp(router: HttpRouter) {
    router
      .filter[CommonFilters]
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .add[ManagementController]
      .add[MainController]
      .add(swaggerController)
      .exceptionMapper[ElasticsearchExceptionMapper]
  }
}
