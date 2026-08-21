package com.schoolbus.students.model

import java.util.UUID

/** The repository's view of a partial update - deliberately not the same
  * type as the wire-level UpdateStudentRequest DTO (that's dto/'s job,
  * built in Phase 6). Keeping this separate means the repository never
  * needs to know anything about JSON or HTTP.
  *
  * routeId is Option[Option[UUID]] on purpose:
  *   - None            -> field wasn't mentioned, leave routeId untouched
  *   - Some(None)       -> caller explicitly wants routeId cleared
  *   - Some(Some(id))   -> caller wants routeId set/reassigned to id
  */
final case class StudentPatch(
    firstName: Option[String],
    lastName: Option[String],
    grade: Option[String],
    routeId: Option[Option[UUID]]
)