package com.schoolbus.schools.model

import java.time.Instant
import java.util.UUID

/** Deliberately minimal - just enough for the Students module to check
  * "does this school exist" and "is it active." Fields like address or
  * timezone can be added later when something actually needs them.
  */
final case class School(
    id: UUID,
    name: String,
    isActive: Boolean,
    createdAt: Instant,
    updatedAt: Instant
)