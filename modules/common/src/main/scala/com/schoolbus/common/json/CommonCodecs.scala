package com.schoolbus.common.json

import io.circe.{Encoder, Json}
import io.circe.generic.semiauto.deriveEncoder
import com.schoolbus.common.errors.{AppError, FieldError}

/** JSON codecs shared across every module.
  *
  * Module-specific DTOs (LoginRequest, StudentDto, etc.) define their own
  * circe codecs next to the DTO itself - that's normal and fine. What lives
  * here is specifically the stuff that must look identical everywhere, so a
  * frontend client only ever parses one error shape, no matter which
  * module's endpoint produced it.
  */
object CommonCodecs {
  implicit val fieldErrorEncoder: Encoder[FieldError] = deriveEncoder

  implicit val appErrorEncoder: Encoder[AppError] = Encoder.instance { err =>
    val details = err match {
      case AppError.ValidationFailed(fieldErrors) =>
        Encoder[List[FieldError]].apply(fieldErrors)
      case _ =>
        Json.arr()
    }

    Json.obj(
      "error" -> Json.obj(
        "code"    -> Json.fromString(err.code),
        "message" -> Json.fromString(err.message),
        "details" -> details
      )
    )
  }
}
