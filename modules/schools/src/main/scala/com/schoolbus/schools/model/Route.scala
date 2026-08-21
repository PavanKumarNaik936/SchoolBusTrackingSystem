package com.schoolbus.schools.model

import java.time.Instant
import java.util.UUID

/** A bus route belonging to exactly one school. schoolId is mandatory
  * (unlike User.schoolId in the auth module) - there's no "unscoped route"
  * equivalent to a SUPER_ADMIN user.
  */
final case class Route(
    id: UUID,
    schoolId: UUID,
    name: String,
    isActive: Boolean,
    createdAt: Instant,
    updatedAt: Instant
)