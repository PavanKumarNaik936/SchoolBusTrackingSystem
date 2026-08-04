package com.schoolbus.students.model

import java.time.Instant
import java.util.UUID

final case class Student(
    id: UUID,
    schoolId: UUID,
    firstName: String,
    lastName: String,
    grade: String,
    routeId: Option[UUID],
    isActive: Boolean,
    createdAt: Instant,
    updatedAt: Instant
)