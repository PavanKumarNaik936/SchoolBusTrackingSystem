package com.schoolbus.schools.repository

import com.schoolbus.schools.model.{School, Tables}
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

/** Trait, not a concrete class, so callers (e.g. StudentService) can depend
  * on this interface and be tested with a fake in-memory implementation.
  *
  * Only two methods for now: create (so tests and future seeding code have
  * a way to get a school into the DB at all, since there's no HTTP layer
  * yet) and findById (what StudentService needs to check "does this school
  * exist"). Add more only when something outside this module needs them.
  */
trait SchoolRepository {
  def create(school: School): Future[School]
  def findById(id: UUID): Future[Option[School]]
}

class SlickSchoolRepository(db: Database)(implicit ec: ExecutionContext) extends SchoolRepository {
  import Tables._

  def create(school: School): Future[School] =
    db.run(schools += school).map(_ => school)

  def findById(id: UUID): Future[Option[School]] =
    db.run(schools.filter(_.id === id).result.headOption)
}