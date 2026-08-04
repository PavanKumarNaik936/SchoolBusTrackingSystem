package com.schoolbus.common.json

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.deriveEncoder
import com.schoolbus.common.errors.{AppError, FieldError}

import java.time.Instant
import java.util.UUID
import scala.util.Try

/** JSON codecs shared across every module.
  *
  * Module-specific DTOs (LoginRequest, StudentDto, etc.) define their own
  * circe codecs next to the DTO itself - that's normal and fine. What lives
  * here is specifically the stuff that must look identical everywhere, so a
  * frontend client only ever parses one error shape, no matter which
  * module's endpoint produced it.
  *
  * UUID/Instant belong here for the same reason: every module's DTOs will
  * eventually have id/timestamp fields, and they should all serialize the
  * same way (plain strings) rather than each module inventing its own.
  */
object CommonCodecs {
  implicit val uuidEncoder: Encoder[UUID] = Encoder.encodeString.contramap(_.toString)
  implicit val uuidDecoder: Decoder[UUID] = Decoder.decodeString.emapTry(s => Try(UUID.fromString(s)))

  implicit val instantEncoder: Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeString.emapTry(s => Try(Instant.parse(s)))

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
