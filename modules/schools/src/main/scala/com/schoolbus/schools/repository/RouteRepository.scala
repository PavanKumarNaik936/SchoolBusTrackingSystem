package com.schoolbus.schools.repository

import com.schoolbus.schools.model.{Route, Tables}
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

/** Deliberately does NOT expose a "belongs to this school" query - that
  * comparison is the ROUTE_SCHOOL_MISMATCH business rule, and per this
  * project's layering, business rules live in the service that calls this
  * repository, not baked into a query here. findById gives the caller
  * (StudentService) everything it needs to make that comparison itself.
  */
trait RouteRepository {
  def create(route: Route): Future[Route]
  def findById(id: UUID): Future[Option[Route]]
}

class SlickRouteRepository(db: Database)(implicit ec: ExecutionContext) extends RouteRepository {
  import Tables._

  def create(route: Route): Future[Route] =
    db.run(routes += route).map(_ => route)

  def findById(id: UUID): Future[Option[Route]] =
    db.run(routes.filter(_.id === id).result.headOption)
}