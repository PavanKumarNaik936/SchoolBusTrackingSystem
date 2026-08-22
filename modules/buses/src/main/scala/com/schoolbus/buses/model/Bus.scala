package com.schoolbus.buses.model

import java.time.Instant
import java.util.UUID

final case class Bus(
    id: UUID,
    schoolId: UUID,
    plateNumber: String,
    capacity: Int,
    status: BusStatus,
    createdAt: Instant,
    updatedAt: Instant
)
