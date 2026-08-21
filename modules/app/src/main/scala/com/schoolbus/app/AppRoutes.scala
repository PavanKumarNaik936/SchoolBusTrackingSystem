package com.schoolbus.app

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/** Composes every module's route tree under one `Route`, plus a `/health`
  * endpoint that belongs to none of them - it's this process's own
  * operational signal, not a auth/schools/students concern.
  *
  * Deliberately unauthenticated and deliberately not just "return 200
  * always": a health check that can't tell a broken DB connection from a
  * healthy one is worse than no health check, since it actively hides the
  * first thing likely to go wrong in production (this is also the reason
  * it's worth having before the integration test even runs - a bad
  * connection string should surface here, not three layers deep in a
  * failing repository call).
  */
object AppRoutes {
  def apply(db: Database, moduleRoutes: Route)(implicit ec: ExecutionContext): Route =
    concat(healthRoute(db), moduleRoutes)

  private def healthRoute(db: Database)(implicit ec: ExecutionContext): Route =
    path("health") {
      get {
        onComplete(db.run(sql"select 1".as[Int])) {
          case Success(_) =>
            complete(StatusCodes.OK -> HttpEntity(ContentTypes.`application/json`, """{"status":"ok"}"""))
          case Failure(_) =>
            complete(StatusCodes.ServiceUnavailable -> HttpEntity(ContentTypes.`application/json`, """{"status":"down"}"""))
        }
      }
    }
}
