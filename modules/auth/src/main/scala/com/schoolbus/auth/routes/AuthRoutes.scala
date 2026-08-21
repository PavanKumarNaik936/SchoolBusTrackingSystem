package com.schoolbus.auth.routes

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._

import com.schoolbus.auth.dto.{LoginRequest, MessageResponse, PasswordResetConfirmRequest, PasswordResetRequest, RefreshRequest, TokenResponse}
import com.schoolbus.auth.service.AuthService
import com.schoolbus.common.errors.{AppError, ErrorMapping, FieldError}
import com.schoolbus.common.json.CommonCodecs._

import scala.concurrent.ExecutionContext

/** Deliberately thin: every branch here either validates shape (is the
  * email non-blank) or delegates to AuthService and maps its Either result
  * to an HTTP response. No password checking, no token logic, no DB
  * calls happen in this file - that's the whole point of keeping Routes
  * as a separate layer from Service.
  */
class AuthRoutes(authService: AuthService)(implicit ec: ExecutionContext) {

  val routes: Route =
    pathPrefix("api" / "v1" / "auth") {
      concat(
        path("login") {
          post {
            entity(as[LoginRequest]) { req =>
              validateLogin(req) match {
                case Left(fieldErrors) =>
                  complete(StatusCodes.UnprocessableContent -> (AppError.ValidationFailed(fieldErrors): AppError).asJson)
                case Right(_) =>
                  onSuccess(authService.login(req.email, req.password)) {
                    respond(_)
                  }
              }
            }
          }
        },
        path("refresh") {
          post {
            entity(as[RefreshRequest]) { req =>
              onSuccess(authService.refresh(req.refreshToken)) {
                respond(_)
              }
            }
          }
        },
        path("logout") {
          post {
            entity(as[RefreshRequest]) { req =>
              onSuccess(authService.logout(req.refreshToken)) {
                case Right(_)  => complete(StatusCodes.NoContent)
                case Left(err) => completeError(err)
              }
            }
          }
        },
        path("password-reset" / "request") {
          post {
            entity(as[PasswordResetRequest]) { req =>
              validatePasswordResetRequest(req) match {
                case Left(fieldErrors) =>
                  complete(StatusCodes.UnprocessableContent -> (AppError.ValidationFailed(fieldErrors): AppError).asJson)
                case Right(_) =>
                  // Same response no matter what happened server-side - that's
                  // the whole point, see AuthService.requestPasswordReset.
                  // (mapped to a non-Unit response rather than passed through
                  // onSuccess, since a Directive1[Unit] confuses akka-http's
                  // HList-based overload resolution)
                  complete(authService.requestPasswordReset(req.email).map { _ =>
                    MessageResponse("If the account exists, password reset instructions have been sent.")
                  })
              }
            }
          }
        },
        path("password-reset" / "confirm") {
          post {
            entity(as[PasswordResetConfirmRequest]) { req =>
              validatePasswordResetConfirm(req) match {
                case Left(fieldErrors) =>
                  complete(StatusCodes.UnprocessableContent -> (AppError.ValidationFailed(fieldErrors): AppError).asJson)
                case Right(_) =>
                  onSuccess(authService.confirmPasswordReset(req.token, req.newPassword)) {
                    case Right(_)  => complete(StatusCodes.OK)
                    case Left(err) => completeError(err)
                  }
              }
            }
          }
        }
      )
    }

  private def respond(result: Either[AppError, com.schoolbus.auth.service.TokenPair]): Route =
    result match {
      case Right(pair) => complete(TokenResponse.from(pair))
      case Left(err)   => completeError(err)
    }

  private def completeError(err: AppError): Route =
    complete(ToResponseMarshallable(ErrorMapping.statusFor(err) -> err.asJson))

  private def validateLogin(req: LoginRequest): Either[List[FieldError], Unit] = {
    val errors = List(
      if (req.email.trim.isEmpty) Some(FieldError("email", "must not be blank")) else None,
      if (!req.email.contains("@")) Some(FieldError("email", "must be a valid email address")) else None,
      if (req.password.isEmpty) Some(FieldError("password", "must not be blank")) else None
    ).flatten

    if (errors.isEmpty) Right(()) else Left(errors)
  }

  private def validatePasswordResetRequest(req: PasswordResetRequest): Either[List[FieldError], Unit] = {
    val errors = List(
      if (req.email.trim.isEmpty) Some(FieldError("email", "must not be blank")) else None,
      if (!req.email.contains("@")) Some(FieldError("email", "must be a valid email address")) else None
    ).flatten

    if (errors.isEmpty) Right(()) else Left(errors)
  }

  private def validatePasswordResetConfirm(req: PasswordResetConfirmRequest): Either[List[FieldError], Unit] = {
    val errors = List(
      if (req.token.trim.isEmpty) Some(FieldError("token", "must not be blank")) else None,
      if (req.newPassword.isEmpty) Some(FieldError("newPassword", "must not be blank")) else None
    ).flatten

    if (errors.isEmpty) Right(()) else Left(errors)
  }
}
