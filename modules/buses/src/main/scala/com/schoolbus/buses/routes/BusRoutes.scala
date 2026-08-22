package com.schoolbus.buses.routes

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._

import com.schoolbus.buses.dto._
import com.schoolbus.buses.model.BusStatus
import com.schoolbus.buses.service.BusService
import com.schoolbus.common.auth.TokenAuthenticator
import com.schoolbus.common.errors.{AppError, ErrorMapping, FieldError}
import com.schoolbus.common.http.AuthDirectives
import com.schoolbus.common.tenant.TenantContext
import com.schoolbus.common.json.CommonCodecs._

import java.util.UUID
import scala.concurrent.ExecutionContext

/** Deliberately thin, same rule as StudentRoutes/AuthRoutes: every branch
  * here either validates request *shape* or delegates to BusService and
  * maps its Either result to an HTTP response. No authorization decisions
  * and no business rules (positive-capacity, per-school plate uniqueness)
  * live here - those are BusService's job.
  *
  * One wrinkle unique to this module: BusService.create/list take a
  * TenantContext but no separate schoolId parameter - the caller's own
  * school (from their JWT) is the only school a SchoolAdmin can ever act
  * on, so there's nothing left for a second schoolId argument to mean.
  * The {schoolId} path segment below exists purely for URL consistency
  * with every other school-owned resource in this API; this layer checks
  * it against ctx.schoolId and rejects a mismatch rather than silently
  * ignoring it, so a caller can't be misled into thinking they queried a
  * school other than their own.
  */
class BusRoutes(busService: BusService, authenticator: TokenAuthenticator)(implicit ec: ExecutionContext) {

  private val authenticated = AuthDirectives.authenticated(authenticator)

  val routes: Route =
    pathPrefix("api" / "v1") {
      concat(
        pathPrefix("schools" / JavaUUID / "buses") { schoolId =>
          authenticated { ctx =>
            requireOwnSchool(ctx, schoolId) {
              concat(
                post {
                  entity(as[CreateBusRequest]) { req =>
                    validateCreate(req) match {
                      case Left(fieldErrors) => completeValidation(fieldErrors)
                      case Right(_) =>
                        onSuccess(busService.create(ctx, req.plateNumber.trim, req.capacity)) {
                          case Right(bus) => complete(StatusCodes.Created -> BusResponse.from(bus))
                          case Left(err)  => completeError(err)
                        }
                    }
                  }
                },
                get {
                  parameters("page".as[Int].?(1), "size".as[Int].?(20)) { (page, size) =>
                    validateListParams(page, size) match {
                      case Left(fieldErrors) => completeValidation(fieldErrors)
                      case Right(_) =>
                        onSuccess(busService.list(ctx, page, size)) {
                          case Right((items, total)) => complete(BusPageResponse.from(items, page, size, total))
                          case Left(err)              => completeError(err)
                        }
                    }
                  }
                }
              )
            }
          }
        },
        pathPrefix("buses" / JavaUUID) { busId =>
          authenticated { ctx =>
            concat(
              get {
                onSuccess(busService.get(ctx, busId)) {
                  case Right(bus) => complete(BusResponse.from(bus))
                  case Left(err)  => completeError(err)
                }
              },
              path("status") {
                patch {
                  entity(as[UpdateBusStatusRequest]) { req =>
                    BusStatus.fromString(req.status) match {
                      case Left(_) =>
                        completeValidation(List(FieldError("status", "must be one of: ACTIVE, INACTIVE, MAINTENANCE")))
                      case Right(status) =>
                        onSuccess(busService.updateStatus(ctx, busId, status)) {
                          case Right(bus) => complete(BusResponse.from(bus))
                          case Left(err)  => completeError(err)
                        }
                    }
                  }
                }
              },
              path("capacity") {
                patch {
                  entity(as[UpdateBusCapacityRequest]) { req =>
                    onSuccess(busService.updateCapacity(ctx, busId, req.capacity)) {
                      case Right(bus) => complete(BusResponse.from(bus))
                      case Left(err)  => completeError(err)
                    }
                  }
                }
              }
            )
          }
        }
      )
    }

  private def requireOwnSchool(ctx: TenantContext, pathSchoolId: UUID)(inner: Route): Route =
    if (ctx.schoolId.contains(pathSchoolId)) inner
    else completeError(AppError.Forbidden("Not authorized for this school"))

  private def completeError(err: AppError): Route =
    complete(ToResponseMarshallable(ErrorMapping.statusFor(err) -> err.asJson))

  private def completeValidation(fieldErrors: List[FieldError]): Route =
    complete(StatusCodes.UnprocessableContent -> (AppError.ValidationFailed(fieldErrors): AppError).asJson)

  private def validateCreate(req: CreateBusRequest): Either[List[FieldError], Unit] = {
    val errors = List(
      if (req.plateNumber.trim.isEmpty) Some(FieldError("plateNumber", "must not be blank")) else None
    ).flatten

    if (errors.isEmpty) Right(()) else Left(errors)
  }

  private def validateListParams(page: Int, size: Int): Either[List[FieldError], Unit] = {
    val errors = List(
      if (page < 1) Some(FieldError("page", "must be at least 1")) else None,
      if (size < 1) Some(FieldError("size", "must be at least 1")) else None
    ).flatten

    if (errors.isEmpty) Right(()) else Left(errors)
  }
}
