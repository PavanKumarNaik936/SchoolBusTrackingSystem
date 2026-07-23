package com.schoolbus.common.errors

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import akka.http.scaladsl.model.StatusCodes._

class ErrorMappingSpec extends AnyWordSpec with Matchers {

  "ErrorMapping.statusFor" should {
    "map NotFound to 404" in {
      ErrorMapping.statusFor(AppError.NotFound("student", "abc-123")) shouldBe NotFound
    }

    "map Forbidden to 403" in {
      ErrorMapping.statusFor(AppError.Forbidden("not your student")) shouldBe Forbidden
    }

    "map Unauthorized to 401" in {
      ErrorMapping.statusFor(AppError.Unauthorized("token expired")) shouldBe Unauthorized
    }

    "map Conflict to 409" in {
      ErrorMapping.statusFor(AppError.Conflict("DUPLICATE_SEQUENCE", "already used")) shouldBe Conflict
    }

    "map ValidationFailed to 422" in {
      ErrorMapping.statusFor(AppError.ValidationFailed(Nil)) shouldBe UnprocessableEntity
    }

    "map Internal to 500" in {
      ErrorMapping.statusFor(AppError.Internal("boom")) shouldBe InternalServerError
    }
  }
}
