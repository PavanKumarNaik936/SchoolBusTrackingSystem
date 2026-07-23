package com.schoolbus.common.tenant

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import java.util.UUID

class TenantContextSpec extends AnyWordSpec with Matchers {

  "TenantContext.requireSchoolId" should {
    "return the schoolId when one is present" in {
      val schoolId = UUID.randomUUID()
      val ctx = TenantContext(UUID.randomUUID(), Role.SchoolAdmin, Some(schoolId))

      ctx.requireSchoolId shouldBe schoolId
    }

    "throw for a super-admin context with no schoolId" in {
      val ctx = TenantContext(UUID.randomUUID(), Role.SuperAdmin, None)

      an[IllegalStateException] should be thrownBy ctx.requireSchoolId
    }
  }

  "TenantContext.isSuperAdmin" should {
    "be true only for SuperAdmin" in {
      TenantContext(UUID.randomUUID(), Role.SuperAdmin, None).isSuperAdmin shouldBe true
      TenantContext(UUID.randomUUID(), Role.Driver, Some(UUID.randomUUID())).isSuperAdmin shouldBe false
    }
  }

  "Role.fromString" should {
    "parse each known role" in {
      Role.fromString("SUPER_ADMIN") shouldBe Right(Role.SuperAdmin)
      Role.fromString("SCHOOL_ADMIN") shouldBe Right(Role.SchoolAdmin)
      Role.fromString("DRIVER") shouldBe Right(Role.Driver)
      Role.fromString("PARENT") shouldBe Right(Role.Parent)
    }

    "reject an unknown role rather than throwing" in {
      Role.fromString("SUPER_VILLAIN").isLeft shouldBe true
    }
  }
}
