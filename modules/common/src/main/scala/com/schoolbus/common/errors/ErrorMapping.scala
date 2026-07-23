package com.schoolbus.common.errors

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._

/** The one place that decides "what HTTP status does this error map to."
  *
  * Every module's routes layer calls this instead of matching on error
  * types itself. If we ever decide ValidationFailed should be 400 instead
  * of 422, it's a one-line change here rather than a hunt through every
  * module's routes file.
  */
object ErrorMapping {
  def statusFor(error: AppError): StatusCode = error match {
    case _: AppError.NotFound         => NotFound
    case _: AppError.Forbidden        => Forbidden
    case _: AppError.Unauthorized     => Unauthorized
    case _: AppError.BadRequest       => BadRequest
    case _: AppError.Conflict         => Conflict
    case _: AppError.ValidationFailed => UnprocessableEntity
    case _: AppError.Internal         => InternalServerError
  }
}
