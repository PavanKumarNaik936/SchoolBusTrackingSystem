package com.schoolbus.students.model

import java.time.Instant
import java.util.UUID

/** One row = one student-parent link. (studentId, parentUserId) is the
  * primary key - see Tables.scala - so the DB itself rejects a duplicate
  * link instead of the service needing a check-then-insert.
  */
final case class StudentParent(
    studentId: UUID,
    parentUserId: UUID,
    createdAt: Instant
)