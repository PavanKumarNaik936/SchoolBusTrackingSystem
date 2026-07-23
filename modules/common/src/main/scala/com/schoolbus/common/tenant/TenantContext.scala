package com.schoolbus.common.tenant

import java.util.UUID

/** Represents "who is making this request, and which school do they belong to."
  *
  * Every service-layer method that touches school-owned data takes a
  * TenantContext as a parameter. This is the enforcement mechanism from
  * Phase 2/3: if a method can't compile without one, a developer can't
  * forget to scope a query by school_id.
  *
  * schoolId is None only for SUPER_ADMIN, who isn't scoped to one school.
  */
final case class TenantContext(
    userId: UUID,
    role: Role,
    schoolId: Option[UUID]
) {

  /** Use this in any service method that requires a concrete school scope
    * (which is almost all of them). Fails loudly and immediately rather
    * than letting a None silently skip a filter somewhere downstream.
    */
  def requireSchoolId: UUID =
    schoolId.getOrElse(
      throw new IllegalStateException(
        "This operation requires a school-scoped context, but none was present " +
          "(a SUPER_ADMIN context was likely used where a school-scoped one was expected)"
      )
    )

  def isSuperAdmin: Boolean = role == Role.SuperAdmin
}

sealed trait Role {
  /** The one canonical string form of this role - used in JWT claims,
    * TokenResponse.role, and anywhere else a role crosses a boundary
    * (wire format, DB column). Everything reads through here instead of
    * relying on Scala's default toString, precisely so two call sites
    * can't quietly drift into two different string formats for the
    * same role.
    */
  def wireName: String
}
object Role {
  case object SuperAdmin extends Role  { val wireName = "SUPER_ADMIN"  }
  case object SchoolAdmin extends Role { val wireName = "SCHOOL_ADMIN" }
  case object Driver extends Role      { val wireName = "DRIVER"       }
  case object Parent extends Role      { val wireName = "PARENT"      }

  /** Parses the role string that comes out of a JWT claim. Returns Either
    * rather than throwing, since a malformed/tampered token is an expected
    * failure mode the auth module needs to handle gracefully, not a bug.
    */
  def fromString(s: String): Either[String, Role] = s match {
    case "SUPER_ADMIN"  => Right(SuperAdmin)
    case "SCHOOL_ADMIN" => Right(SchoolAdmin)
    case "DRIVER"       => Right(Driver)
    case "PARENT"       => Right(Parent)
    case other          => Left(s"Unknown role: $other")
  }
}
