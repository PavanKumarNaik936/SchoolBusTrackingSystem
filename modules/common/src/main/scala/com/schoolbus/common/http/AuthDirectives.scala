package com.schoolbus.common.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._

import com.schoolbus.common.auth.TokenAuthenticator
import com.schoolbus.common.errors.AppError
import com.schoolbus.common.json.CommonCodecs._
import com.schoolbus.common.tenant.TenantContext

/** The one place that turns an "Authorization: Bearer <token>" header into
  * a TenantContext, for every module's protected routes - so a missing or
  * invalid header always produces the exact same 401 shape, no matter
  * which module's endpoint was hit. Deliberately only extracts *identity*
  * here - deciding what that identity is allowed to do is the service
  * layer's job (see StudentService.requireSchoolAdmin), not this
  * directive's.
  */
object AuthDirectives {
  def authenticated(authenticator: TokenAuthenticator): Directive1[TenantContext] =
    optionalHeaderValueByName("Authorization").flatMap {
      case Some(header) if header.startsWith("Bearer ") =>
        authenticator.authenticate(header.stripPrefix("Bearer ").trim) match {
          case Right(ctx)   => provide(ctx)
          case Left(reason) => complete(StatusCodes.Unauthorized -> (AppError.Unauthorized(reason): AppError).asJson)
        }
      case _ =>
        complete(StatusCodes.Unauthorized -> (AppError.Unauthorized("Missing or malformed Authorization header"): AppError).asJson)
    }
}