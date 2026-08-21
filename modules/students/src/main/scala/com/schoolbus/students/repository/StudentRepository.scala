package com.schoolbus.students.repository

import com.schoolbus.students.model.{Student, StudentPatch, Tables}
import slick.jdbc.PostgresProfile.api._
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait StudentRepository {
  def create(student: Student): Future[Student]
  def findById(id: UUID): Future[Option[Student]]
  def listBySchool(schoolId: UUID, routeId: Option[UUID], page: Int, pageSize: Int): Future[List[Student]]
  def countBySchool(schoolId: UUID, routeId: Option[UUID]): Future[Int]
  def update(id: UUID, patch: StudentPatch): Future[Option[Student]]
  def deactivate(id: UUID): Future[Boolean]
}

class SlickStudentRepository(db: Database)(implicit ec: ExecutionContext) extends StudentRepository {
  import Tables._

  def create(student: Student): Future[Student] =
    db.run(students += student).map(_ => student)

  def findById(id: UUID): Future[Option[Student]] =
    db.run(students.filter(_.id === id).result.headOption)

  // Shared by listBySchool/countBySchool so the two queries can never
  // silently drift apart on what "matching" means (e.g. one of them
  // forgetting the isActive filter). Only active students are visible
  // through either method - a caller who somehow needs inactive ones too
  // is a new method, not a hidden flag on this one.
  private def scoped(schoolId: UUID, routeId: Option[UUID]) = {
    val bySchool = students.filter(s => s.schoolId === schoolId && s.isActive)
    routeId.fold(bySchool)(rid => bySchool.filter(_.routeId === rid))
  }

  // Assumes the caller (service/routes layer) already validated page >= 1
  // and pageSize > 0 - this method trusts its inputs rather than
  // re-validating, per the "routes validate shape, service validates
  // business rules" split; a repository re-checking shape would just be
  // the same check done twice in two layers.
  def listBySchool(schoolId: UUID, routeId: Option[UUID], page: Int, pageSize: Int): Future[List[Student]] =
    db.run(
      scoped(schoolId, routeId)
        .sortBy(s => (s.lastName, s.firstName))
        .drop((page - 1) * pageSize)
        .take(pageSize)
        .result
    ).map(_.toList)

  def countBySchool(schoolId: UUID, routeId: Option[UUID]): Future[Int] =
    db.run(scoped(schoolId, routeId).length.result)

  // Read-modify-write inside one transaction, not two separate db.run
  // calls - otherwise two concurrent updates to the same student could
  // both read the old row, and the second write would silently discard
  // the first one's change (a lost update).
  def update(id: UUID, patch: StudentPatch): Future[Option[Student]] = {
    val action = students.filter(_.id === id).result.headOption.flatMap {
      case None => DBIO.successful(None)
      case Some(existing) =>
        val updated = existing.copy(
          firstName = patch.firstName.getOrElse(existing.firstName),
          lastName  = patch.lastName.getOrElse(existing.lastName),
          grade     = patch.grade.getOrElse(existing.grade),
          // patch.routeId is Option[Option[UUID]]: None keeps the existing
          // value, Some(x) - whatever x is, including None - replaces it.
          // That single getOrElse is the entire "absent vs null" handling.
          routeId   = patch.routeId.getOrElse(existing.routeId),
          updatedAt = Instant.now()
        )
        students.filter(_.id === id).update(updated).map(_ => Some(updated))
    }.transactionally

    db.run(action)
  }

  def deactivate(id: UUID): Future[Boolean] =
    db.run(students.filter(_.id === id).map(_.isActive).update(false)).map(_ > 0)
}