package com.schoolbus.students.model

import com.schoolbus.schools.model.{Tables => SchoolsTables}
import slick.jdbc.PostgresProfile.api._
import java.time.Instant
import java.util.UUID

/** Slick table definitions for this module - same rule as everywhere
  * else: nothing outside `students` reaches into these directly.
  *
  * Reuses SchoolsTables.instantColumnType instead of redefining it a
  * third time (like schools.model.Tables had to for auth) - this module
  * legitimately depends on `schools`, so there's no duplication needed
  * here. That's the payoff of keeping the dependency direction one-way.
  */
object Tables {
  import SchoolsTables.instantColumnType

  class StudentsTable(tag: Tag) extends Table[Student](tag, "students") {
    def id        = column[UUID]("id", O.PrimaryKey)
    def schoolId  = column[UUID]("school_id")
    def firstName = column[String]("first_name")
    def lastName  = column[String]("last_name")
    def grade     = column[String]("grade")
    def routeId   = column[Option[UUID]]("route_id")
    def isActive  = column[Boolean]("is_active")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")

    def * = (id, schoolId, firstName, lastName, grade, routeId, isActive, createdAt, updatedAt) <>
      (Student.tupled, Student.unapply)

    def schoolFk  = foreignKey("students_school_fk", schoolId, SchoolsTables.schools)(_.id)
    def routeFk   = foreignKey("students_route_fk", routeId, SchoolsTables.routes)(_.id.?)
    def schoolIdx = index("students_school_id_idx", schoolId)
    def routeIdx  = index("students_route_id_idx", routeId)
  }
  val students = TableQuery[StudentsTable]

  class StudentParentsTable(tag: Tag) extends Table[StudentParent](tag, "student_parents") {
    def studentId    = column[UUID]("student_id")
    def parentUserId = column[UUID]("parent_user_id")
    def createdAt    = column[Instant]("created_at")

    // Composite PK instead of a surrogate id - see StudentParent's doc
    // comment. This constraint is what makes "link" idempotent-detectable
    // rather than something the service has to check-then-insert for.
    def pk = primaryKey("student_parents_pk", (studentId, parentUserId))

    def * = (studentId, parentUserId, createdAt) <> (StudentParent.tupled, StudentParent.unapply)

    // No foreignKey() to auth's UsersTable here - students doesn't depend
    // on auth (same reasoning as schools not depending on auth). The
    // column is a plain UUID; whether a real DB-level FK exists depends on
    // whatever eventually applies the schema (this project has no
    // migration tool yet - see the schools module's notes).
    def studentFk = foreignKey("student_parents_student_fk", studentId, students)(_.id)
    def parentIdx = index("student_parents_parent_user_id_idx", parentUserId)
  }
  val studentParents = TableQuery[StudentParentsTable]
}