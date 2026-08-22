package com.schoolbus.buses.model

import slick.jdbc.PostgresProfile.api._
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** Slick table definitions live here, and only here - same rule as
  * auth.model.Tables/schools.model.Tables. Nothing outside this module
  * should reach into these tables directly.
  */
object Tables {

  // Duplicated from auth.model.Tables/schools.model.Tables rather than
  // shared, since `buses` doesn't depend on either of them (it only
  // depends on `common` - see build.sbt). Worth promoting into `common`
  // once enough modules need this same Instant mapping.
  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      Timestamp.from,
      _.toInstant
    )

  implicit val busStatusColumnType: BaseColumnType[BusStatus] =
    MappedColumnType.base[BusStatus, String](
      status => status.wireName,
      str => BusStatus.fromString(str).getOrElse(
        throw new IllegalStateException(s"Corrupt bus status value in DB: $str")
      )
    )

  class BusesTable(tag: Tag) extends Table[Bus](tag, "buses") {
    def id          = column[UUID]("id", O.PrimaryKey)
    def schoolId    = column[UUID]("school_id")
    def plateNumber = column[String]("plate_number")
    def capacity    = column[Int]("capacity")
    def status      = column[BusStatus]("status")
    def createdAt   = column[Instant]("created_at")
    def updatedAt   = column[Instant]("updated_at")

    def * = (id, schoolId, plateNumber, capacity, status, createdAt, updatedAt) <> (Bus.tupled, Bus.unapply)

    // No foreignKey() to schools.model.Tables.schools here - `buses`
    // doesn't depend on `schools` at the Scala level (same reasoning as
    // students.model.Tables.StudentParentsTable not depending on `auth`).
    // The real FK constraint still exists at the database level - see the
    // Flyway migration.
    def schoolIdx = index("buses_school_id_idx", schoolId)
  }
  val buses = TableQuery[BusesTable]
}
