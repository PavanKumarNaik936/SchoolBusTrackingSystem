package com.schoolbus.auth.service

import com.schoolbus.common.tenant.Role
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import java.util.UUID

class JwtServiceSpec extends AnyWordSpec with Matchers {
  val jwtService = new JwtService(secretKey = "test-secret-do-not-use-in-prod")

  "issueAccessToken + decode" should {
    "round-trip a school-scoped user correctly" in {
      val userId   = UUID.randomUUID()
      val schoolId = UUID.randomUUID()

      val (token, expiresIn) = jwtService.issueAccessToken(userId, Role.SchoolAdmin, Some(schoolId))
      expiresIn shouldBe 3600L

      val decoded = jwtService.decode(token)
      decoded shouldBe Right(JwtClaimsData(userId, Role.SchoolAdmin, Some(schoolId)))
    }

    "round-trip a super-admin user with no schoolId" in {
      val userId = UUID.randomUUID()
      val (token, _) = jwtService.issueAccessToken(userId, Role.SuperAdmin, None)

      jwtService.decode(token) shouldBe Right(JwtClaimsData(userId, Role.SuperAdmin, None))
    }

    "reject a token signed with a different secret" in {
      val userId = UUID.randomUUID()
      val (token, _) = jwtService.issueAccessToken(userId, Role.Driver, Some(UUID.randomUUID()))

      val otherService = new JwtService(secretKey = "a-different-secret")
      otherService.decode(token).isLeft shouldBe true
    }

    "reject a malformed token" in {
      jwtService.decode("not.a.real.jwt").isLeft shouldBe true
    }
  }
}
