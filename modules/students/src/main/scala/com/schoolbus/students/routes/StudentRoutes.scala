package com.schoolbus.students.routes

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._

import com.schoolbus.common.auth.TokenAuthenticator
import com.schoolbus.common.errors.{AppError, ErrorMapping, FieldError}
import com.schoolbus.common.http.AuthDirectives
import com.schoolbus.common.json.CommonCodecs._
import com.schoolbus.students.dto._
import com.schoolbus.students.model.StudentPatch
import com.schoolbus.students.service.StudentService

import java.util.UUID
import scala.concurrent.ExecutionContext

/** Deliberately thin, same rule as AuthRoutes: every branch here either
  * validates request *shape* (blank names, page/pageSize being sane
  * positive integers) or delegates to StudentService and maps its Either
  * result to an HTTP response. No authorization decisions and no business
  * rules (grade lists, route/school matching, parent-role checks) live
  * here - those are StudentService's job, and are exercised through
  * `authenticated`, which only extracts *who's asking*, not what they're
  * allowed to do.
  */
class StudentRoutes(studentService: StudentService, authenticator: TokenAuthenticator)(implicit ec: ExecutionContext) {

  // Not promoted to `common` - only this module currently needs to parse a
  // UUID out of a query string. Promote it if/when a second module does.
  private implicit val uuidUnmarshaller: Unmarshaller[String, UUID] =
    Unmarshaller.strict(UUID.fromString)

  private val authenticated = AuthDirectives.authenticated(authenticator)

  val routes: Route =
    pathPrefix("api" / "v1") {
      concat(
        pathPrefix("schools" / JavaUUID / "students") { schoolId =>
          authenticated { ctx =>
            concat(
              post {
                entity(as[CreateStudentRequest]) { req =>
                  validateCreate(req) match {
                    case Left(fieldErrors) => completeValidation(fieldErrors)
                    case Right(_) =>
                      onSuccess(
                        studentService.createStudent(ctx, schoolId, req.firstName.trim, req.lastName.trim, req.grade, req.routeId)
                      ) {
                        case Right(student) => complete(StatusCodes.Created -> StudentResponse.from(student))
                        case Left(err)       => completeError(err)
                      }
                  }
                }
              },
              get {
                parameters("routeId".as[UUID].?, "page".as[Int].?(1), "pageSize".as[Int].?(20)) {
                  (routeId, page, pageSize) =>
                    validateListParams(page, pageSize) match {
                      case Left(fieldErrors) => completeValidation(fieldErrors)
                      case Right(_) =>
                        onSuccess(studentService.listStudents(ctx, schoolId, routeId, page, pageSize)) {
                          case Right(result) => complete(StudentListResponse.from(result))
                          case Left(err)     => completeError(err)
                        }
                    }
                }
              }
            )
          }
        },
        pathPrefix("students" / JavaUUID) { studentId =>
          authenticated { ctx =>
            concat(
              pathEnd {
                concat(
                  get {
                    onSuccess(studentService.getStudent(ctx, studentId)) {
                      case Right(detail) => complete(StudentDetailResponse.from(detail))
                      case Left(err)     => completeError(err)
                    }
                  },
                  patch {
                    entity(as[UpdateStudentRequest]) { req =>
                      validateUpdate(req) match {
                        case Left(fieldErrors) => completeValidation(fieldErrors)
                        case Right(_) =>
                          val patch = StudentPatch(
                            firstName = req.firstName.map(_.trim),
                            lastName = req.lastName.map(_.trim),
                            grade = req.grade,
                            routeId = req.routeId
                          )
                          onSuccess(studentService.updateStudent(ctx, studentId, patch)) {
                            case Right(student) => complete(StudentResponse.from(student))
                            case Left(err)      => completeError(err)
                          }
                      }
                    }
                  },
                  delete {
                    onSuccess(studentService.deactivateStudent(ctx, studentId)) {
                      case Right(_)  => complete(StatusCodes.NoContent)
                      case Left(err) => completeError(err)
                    }
                  }
                )
              },
              path("parents") {
                post {
                  entity(as[LinkParentRequest]) { req =>
                    onSuccess(studentService.linkParent(ctx, studentId, req.parentUserId)) {
                      case Right(_)  => complete(StatusCodes.NoContent)
                      case Left(err) => completeError(err)
                    }
                  }
                }
              },
              path("parents" / JavaUUID) { parentUserId =>
                delete {
                  onSuccess(studentService.unlinkParent(ctx, studentId, parentUserId)) {
                    case Right(_)  => complete(StatusCodes.NoContent)
                    case Left(err) => completeError(err)
                  }
                }
              }
            )
          }
        }
      )
    }

  private def completeError(err: AppError): Route =
    complete(ToResponseMarshallable(ErrorMapping.statusFor(err) -> err.asJson))

  private def completeValidation(fieldErrors: List[FieldError]): Route =
    complete(StatusCodes.UnprocessableContent -> (AppError.ValidationFailed(fieldErrors): AppError).asJson)

  private def validateCreate(req: CreateStudentRequest): Either[List[FieldError], Unit] = {
    val errors = List(
      if (req.firstName.trim.isEmpty) Some(FieldError("firstName", "must not be blank")) else None,
      if (req.lastName.trim.isEmpty) Some(FieldError("lastName", "must not be blank")) else None
    ).flatten

    if (errors.isEmpty) Right(()) else Left(errors)
  }

  private def validateUpdate(req: UpdateStudentRequest): Either[List[FieldError], Unit] = {
    val errors = List(
      if (req.firstName.exists(_.trim.isEmpty)) Some(FieldError("firstName", "must not be blank")) else None,
      if (req.lastName.exists(_.trim.isEmpty)) Some(FieldError("lastName", "must not be blank")) else None
    ).flatten

    if (errors.isEmpty) Right(()) else Left(errors)
  }

  private def validateListParams(page: Int, pageSize: Int): Either[List[FieldError], Unit] = {
    val errors = List(
      if (page < 1) Some(FieldError("page", "must be at least 1")) else None,
      if (pageSize < 1) Some(FieldError("pageSize", "must be at least 1")) else None
    ).flatten

    if (errors.isEmpty) Right(()) else Left(errors)
  }
}