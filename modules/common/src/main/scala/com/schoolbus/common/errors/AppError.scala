package com.schoolbus.common.errors

/** Base type for every domain error in the system.
  *
  * Module-specific errors don't invent their own hierarchy - they construct
  * one of these cases (e.g. AppError.NotFound("student", id)). That means
  * the HTTP layer (ErrorMapping, below) can translate any error to a status
  * code in one place, instead of every module's routes reinventing
  * "what status code does a missing student return."
  */
sealed abstract class AppError(val code: String, val message: String)
    extends Throwable(message)

object AppError {
  final case class NotFound(entity: String, id: String)
      extends AppError(
        s"${entity.toUpperCase}_NOT_FOUND",
        s"$entity with id $id not found"
      )

  final case class Forbidden(reason: String)
      extends AppError("FORBIDDEN", reason)

  final case class Unauthorized(reason: String)
      extends AppError("UNAUTHORIZED", reason)

  /** Generic 400 for a malformed/unusable request that isn't a field-level
    * validation failure, e.g. an invalid or expired one-time token. Kept
    * generic like Conflict, since new codes will keep showing up.
    */
  final case class BadRequest(errorCode: String, reason: String)
      extends AppError(errorCode, reason)

  /** For business-rule conflicts with a specific machine-readable code,
    * e.g. AppError.Conflict("DUPLICATE_SEQUENCE", "...") from Phase 4.
    * Kept generic rather than one case class per conflict type, since new
    * conflict codes will keep showing up as modules get built and forcing
    * a new case class each time would be more ceremony than value.
    */
  final case class Conflict(conflictCode: String, reason: String)
      extends AppError(conflictCode, reason)

  final case class ValidationFailed(fieldErrors: List[FieldError])
      extends AppError("VALIDATION_FAILED", "One or more fields are invalid")

  /** Also a 422, like ValidationFailed, but for a single named business
    * rule (e.g. ROUTE_SCHOOL_MISMATCH) rather than a list of per-field
    * errors. Kept generic like Conflict/BadRequest, since new rule codes
    * will keep showing up as modules get built.
    */
  final case class UnprocessableEntity(errorCode: String, reason: String)
      extends AppError(errorCode, reason)

  final case class Internal(reason: String)
      extends AppError("INTERNAL_ERROR", reason)
}

final case class FieldError(field: String, message: String)
