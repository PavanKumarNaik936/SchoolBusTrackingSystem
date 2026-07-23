package com.schoolbus.auth.model

import com.schoolbus.common.tenant.Role
import java.time.Instant
import java.util.UUID

/** The domain shape of a user, independent of how it's stored (that's
  * Tables.scala's job) or how it appears on the wire (that's dto/'s job).
  * Keeping these three separate means changing the DB column name doesn't
  * force a JSON field rename, and vice versa.
  */
final case class User(
    id: UUID,
    schoolId: Option[UUID], // None only for SUPER_ADMIN
    email: String,
    passwordHash: String,
    role: Role,
    isActive: Boolean,
    createdAt: Instant,
    updatedAt: Instant
)
