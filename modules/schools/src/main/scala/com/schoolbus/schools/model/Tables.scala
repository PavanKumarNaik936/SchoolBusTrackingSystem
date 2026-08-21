package com.schoolbus.schools.model

import slick.jdbc.PostgresProfile.api._
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** Slick table definitions live here, and only here - same rule as
  * auth.model.Tables. Nothing outside this module should reach into these
  * tables directly.
  */
object Tables {

  // Duplicated from auth.model.Tables rather than shared, since schools
  // doesn't depend on auth (dependencies should flow students -> schools,
  // not schools -> auth). Worth promoting into `common` once a third
  // module needs this same Instant mapping.
  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      Timestamp.from,
      _.toInstant
    )

  class SchoolsTable(tag: Tag) extends Table[School](tag, "schools") {
    def id        = column[UUID]("id", O.PrimaryKey)
    def name      = column[String]("name")
    def isActive  = column[Boolean]("is_active")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")

    def * = (id, name, isActive, createdAt, updatedAt) <> (School.tupled, School.unapply)
  }
  val schools = TableQuery[SchoolsTable]

  class RoutesTable(tag: Tag) extends Table[Route](tag, "routes") {
    def id        = column[UUID]("id", O.PrimaryKey)
    def schoolId  = column[UUID]("school_id")
    def name      = column[String]("name")
    def isActive  = column[Boolean]("is_active")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")

    def * = (id, schoolId, name, isActive, createdAt, updatedAt) <> (Route.tupled, Route.unapply)

    def schoolFk  = foreignKey("routes_school_fk", schoolId, schools)(_.id)
    def schoolIdx = index("routes_school_id_idx", schoolId)
  }
  val routes = TableQuery[RoutesTable]
}