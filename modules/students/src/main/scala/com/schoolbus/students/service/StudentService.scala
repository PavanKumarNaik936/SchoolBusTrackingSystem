package com.schoolbus.students.service

import com.schoolbus.common.client.UserClient
import com.schoolbus.common.errors.AppError
import com.schoolbus.common.tenant.{Role, TenantContext}
import com.schoolbus.schools.repository.{RouteRepository, SchoolRepository}
import com.schoolbus.students.model.{Student, StudentDetail, StudentPage, StudentPatch}
import com.schoolbus.students.repository.{StudentParentRepository, StudentRepository}

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class StudentService(
    studentRepository: StudentRepository,
    studentParentRepository: StudentParentRepository,
    schoolRepository: SchoolRepository,
    routeRepository: RouteRepository,
    userClient: UserClient
)(implicit ec: ExecutionContext) {

  // A client asking for more than this gets silently capped, not rejected -
  // this is a system-protection policy (don't let one request table-scan
  // thousands of rows), not something wrong with the request itself. Page
  // and pageSize are otherwise trusted to already be >= 1 here - that's a
  // shape check, and shape checks are the routes layer's job (Phase 6),
  // same as the blank-name checks below.
  private val MaxPageSize = 100

  private val AllowedGrades: List[String] = "K" :: (1 to 12).map(_.toString).toList

  def createStudent(
      ctx: TenantContext,
      schoolId: UUID,
      firstName: String,
      lastName: String,
      grade: String,
      routeId: Option[UUID]
  ): Future[Either[AppError, Student]] =
    requireSchoolAdmin(ctx, schoolId) match {
      case Left(err) => Future.successful(Left(err))
      case Right(_) =>
        validateGrade(grade) match {
          case Left(err) => Future.successful(Left(err))
          case Right(_)  => createValidated(schoolId, firstName, lastName, grade, routeId)
        }
    }

  def listStudents(
      ctx: TenantContext,
      schoolId: UUID,
      routeId: Option[UUID],
      page: Int,
      pageSize: Int
  ): Future[Either[AppError, StudentPage]] =
    requireSchoolAdmin(ctx, schoolId) match {
      case Left(err) => Future.successful(Left(err))
      case Right(_) =>
        val boundedPageSize = math.min(pageSize, MaxPageSize)
        for {
          items <- studentRepository.listBySchool(schoolId, routeId, page, boundedPageSize)
          total <- studentRepository.countBySchool(schoolId, routeId)
        } yield Right(StudentPage(items, page, boundedPageSize, total))
    }

  def getStudent(ctx: TenantContext, studentId: UUID): Future[Either[AppError, StudentDetail]] =
    studentRepository.findById(studentId).flatMap {
      case None => Future.successful(Left(notFoundStudent(studentId)))
      case Some(student) =>
        authorizeView(ctx, student).flatMap {
          case Left(err) => Future.successful(Left(err))
          case Right(_) =>
            studentParentRepository.findParentUserIds(student.id).map(ids => Right(StudentDetail(student, ids)))
        }
    }

  def updateStudent(ctx: TenantContext, studentId: UUID, patch: StudentPatch): Future[Either[AppError, Student]] =
    studentRepository.findById(studentId).flatMap {
      case None => Future.successful(Left(notFoundStudent(studentId)))
      case Some(existing) =>
        requireSchoolAdmin(ctx, existing.schoolId) match {
          case Left(err) => Future.successful(Left(err))
          case Right(_)  => validateAndApplyPatch(existing, patch)
        }
    }

  def deactivateStudent(ctx: TenantContext, studentId: UUID): Future[Either[AppError, Unit]] =
    studentRepository.findById(studentId).flatMap {
      case None => Future.successful(Left(notFoundStudent(studentId)))
      case Some(existing) =>
        requireSchoolAdmin(ctx, existing.schoolId) match {
          case Left(err) => Future.successful(Left(err))
          // Idempotent by nature: deactivating an already-inactive student
          // isn't an error, it's just a no-op success - same reasoning as
          // AuthService.logout treating an already-gone token as success.
          case Right(_) => studentRepository.deactivate(studentId).map(_ => Right(()))
        }
    }

  def linkParent(ctx: TenantContext, studentId: UUID, parentUserId: UUID): Future[Either[AppError, Unit]] =
    studentRepository.findById(studentId).flatMap {
      case None => Future.successful(Left(notFoundStudent(studentId)))
      case Some(student) =>
        requireSchoolAdmin(ctx, student.schoolId) match {
          case Left(err) => Future.successful(Left(err))
          case Right(_)  => validateAndLinkParent(student, parentUserId)
        }
    }

  def unlinkParent(ctx: TenantContext, studentId: UUID, parentUserId: UUID): Future[Either[AppError, Unit]] =
    studentRepository.findById(studentId).flatMap {
      case None => Future.successful(Left(notFoundStudent(studentId)))
      case Some(student) =>
        requireSchoolAdmin(ctx, student.schoolId) match {
          case Left(err) => Future.successful(Left(err))
          // Idempotent, same reasoning as deactivateStudent: unlinking a
          // parent who was never linked isn't an error to surface.
          case Right(_) => studentParentRepository.unlink(studentId, parentUserId).map(_ => Right(()))
        }
    }

  // ---- shared checks ----

  /** Pure comparison against the caller's own token claims - no DB access
    * needed, so unlike authorizeView below, this doesn't need to be async.
    * Every mutating endpoint in this service uses this same check, so
    * "who's allowed to touch this school's students" can't drift between
    * Create/Update/Delete/Link/Unlink.
    */
  private def requireSchoolAdmin(ctx: TenantContext, targetSchoolId: UUID): Either[AppError, Unit] =
    if (ctx.isSuperAdmin) Right(())
    else if (ctx.role == Role.SchoolAdmin && ctx.schoolId.contains(targetSchoolId)) Right(())
    else Left(AppError.Forbidden("Only a school admin for this student's school may perform this action"))

  /** Unlike requireSchoolAdmin, this one *does* need the DB - a parent's
    * right to view a student depends on a link row that only the database
    * knows about, not anything present in the JWT itself.
    */
  private def authorizeView(ctx: TenantContext, student: Student): Future[Either[AppError, Unit]] =
    if (ctx.isSuperAdmin) Future.successful(Right(()))
    else if (ctx.role == Role.SchoolAdmin && ctx.schoolId.contains(student.schoolId)) Future.successful(Right(()))
    else if (ctx.role == Role.Parent)
      studentParentRepository.isLinked(student.id, ctx.userId).map { linked =>
        if (linked) Right(()) else Left(AppError.Forbidden("You are not linked to this student"))
      }
    else Future.successful(Left(AppError.Forbidden("Not authorized to view this student")))

  /** Grade is domain policy (which grades this system recognizes), not a
    * generic string-shape fact like "must not be blank" - that's why it's
    * validated here rather than in Routes alongside the blank checks.
    */
  private def validateGrade(grade: String): Either[AppError, Unit] =
    if (AllowedGrades.contains(grade)) Right(())
    else
      Left(
        AppError.ValidationFailed(
          List(com.schoolbus.common.errors.FieldError("grade", s"must be one of: ${AllowedGrades.mkString(", ")}"))
        )
      )

  /** Shared by create and update so the route-school-mismatch rule can't
    * drift between the two call sites that need it.
    */
  private def validateRoute(schoolId: UUID, routeId: UUID): Future[Either[AppError, Unit]] =
    routeRepository.findById(routeId).map {
      case None => Left(AppError.NotFound("route", routeId.toString))
      case Some(route) if route.schoolId != schoolId =>
        Left(AppError.UnprocessableEntity("ROUTE_SCHOOL_MISMATCH", s"Route $routeId does not belong to school $schoolId"))
      case Some(route) if !route.isActive =>
        Left(AppError.UnprocessableEntity("ROUTE_INACTIVE", s"Route $routeId is not active"))
      case Some(_) => Right(())
    }

  private def notFoundStudent(id: UUID): AppError = AppError.NotFound("student", id.toString)

  private def createValidated(
      schoolId: UUID,
      firstName: String,
      lastName: String,
      grade: String,
      routeId: Option[UUID]
  ): Future[Either[AppError, Student]] =
    schoolRepository.findById(schoolId).flatMap {
      case None => Future.successful(Left(AppError.NotFound("school", schoolId.toString)))
      case Some(_) =>
        routeId match {
          case None => insertStudent(schoolId, firstName, lastName, grade, None).map(Right(_))
          case Some(rid) =>
            validateRoute(schoolId, rid).flatMap {
              case Left(err) => Future.successful(Left(err))
              case Right(_)  => insertStudent(schoolId, firstName, lastName, grade, Some(rid)).map(Right(_))
            }
        }
    }

  private def insertStudent(
      schoolId: UUID,
      firstName: String,
      lastName: String,
      grade: String,
      routeId: Option[UUID]
  ): Future[Student] = {
    val now = Instant.now()
    val student = Student(
      id = UUID.randomUUID(),
      schoolId = schoolId,
      firstName = firstName,
      lastName = lastName,
      grade = grade,
      routeId = routeId,
      isActive = true,
      createdAt = now,
      updatedAt = now
    )
    studentRepository.create(student)
  }

  private def validateAndApplyPatch(existing: Student, patch: StudentPatch): Future[Either[AppError, Student]] = {
    val gradeCheck = patch.grade.fold[Either[AppError, Unit]](Right(()))(validateGrade)
    gradeCheck match {
      case Left(err) => Future.successful(Left(err))
      case Right(_) =>
        patch.routeId match {
          // Some(Some(rid)): caller is assigning/reassigning a route - the
          // only case that needs the same-school/active check. Some(None)
          // (explicit clear) and None (untouched) both skip straight to
          // applying the patch - clearing a route can never violate the
          // route-school rule.
          case Some(Some(rid)) =>
            validateRoute(existing.schoolId, rid).flatMap {
              case Left(err) => Future.successful(Left(err))
              case Right(_)  => applyPatch(existing.id, patch)
            }
          case _ => applyPatch(existing.id, patch)
        }
    }
  }

  private def applyPatch(id: UUID, patch: StudentPatch): Future[Either[AppError, Student]] =
    studentRepository.update(id, patch).map {
      case Some(updated) => Right(updated)
      // Only reachable if the student was deleted between findById and
      // this update - a genuine (if rare) race, not a normal 404 path.
      case None => Left(notFoundStudent(id))
    }

  private def validateAndLinkParent(student: Student, parentUserId: UUID): Future[Either[AppError, Unit]] =
    userClient.find(parentUserId).flatMap {
      case None =>
        Future.successful(Left(AppError.NotFound("user", parentUserId.toString)))
      case Some(user) if user.role != Role.Parent =>
        Future.successful(Left(AppError.UnprocessableEntity("INVALID_PARENT_ROLE", s"User $parentUserId is not a parent account")))
      case Some(user) if !user.schoolId.contains(student.schoolId) =>
        Future.successful(
          Left(AppError.UnprocessableEntity("PARENT_SCHOOL_MISMATCH", s"User $parentUserId does not belong to the same school as this student"))
        )
      case Some(_) =>
        // Both "newly linked" and "already linked" count as success here -
        // linking is idempotent from the caller's point of view, same
        // reasoning as deactivateStudent/unlinkParent above.
        studentParentRepository.link(student.id, parentUserId).map(_ => Right(()))
    }
}