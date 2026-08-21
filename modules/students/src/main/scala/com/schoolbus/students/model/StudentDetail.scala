package com.schoolbus.students.model

import java.util.UUID

/** Get Student needs the linked parent ids in the response; List Students
  * doesn't (that would be an N+1 query per row for no asked-for benefit).
  * So this composed view exists only for the single-student case, rather
  * than bolting parentUserIds onto Student itself.
  */
final case class StudentDetail(student: Student, parentUserIds: List[UUID])