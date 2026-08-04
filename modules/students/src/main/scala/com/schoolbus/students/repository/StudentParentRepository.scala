package com.schoolbus.students.repository

import com.schoolbus.students.model.{StudentParent, Tables}
import slick.jdbc.PostgresProfile.api._
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait StudentParentRepository {
  def link(studentId: UUID, parentUserId: UUID): Future[Boolean]
  def unlink(studentId: UUID, parentUserId: UUID): Future[Boolean]
  def isLinked(studentId: UUID, parentUserId: UUID): Future[Boolean]
  def findParentUserIds(studentId: UUID): Future[List[UUID]]
}

class SlickStudentParentRepository(db: Database)(implicit ec: ExecutionContext) extends StudentParentRepository {
  import Tables._

  // 23505 is Postgres's SQLState for "unique/PK violation" - we only want
  // to swallow *that specific* failure into a clean `false` (link already
  // existed). Anything else (connection drop, etc.) should propagate as a
  // real error rather than being silently reported as "not linked."
  private val UniqueViolation = "23505"

  def link(studentId: UUID, parentUserId: UUID): Future[Boolean] =
    db.run(studentParents += StudentParent(studentId, parentUserId, Instant.now()))
      .map(_ => true)
      .recover { case e: SQLException if e.getSQLState == UniqueViolation => false }

  def unlink(studentId: UUID, parentUserId: UUID): Future[Boolean] =
    db.run(
      studentParents
        .filter(sp => sp.studentId === studentId && sp.parentUserId === parentUserId)
        .delete
    ).map(_ > 0)

  def isLinked(studentId: UUID, parentUserId: UUID): Future[Boolean] =
    db.run(
      studentParents
        .filter(sp => sp.studentId === studentId && sp.parentUserId === parentUserId)
        .exists
        .result
    )

  def findParentUserIds(studentId: UUID): Future[List[UUID]] =
    db.run(studentParents.filter(_.studentId === studentId).map(_.parentUserId).result).map(_.toList)
}