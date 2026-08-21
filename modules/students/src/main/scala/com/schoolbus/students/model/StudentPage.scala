package com.schoolbus.students.model

/** One page of a student listing, plus enough to compute total pages
  * without a second round trip from the caller.
  */
final case class StudentPage(items: List[Student], page: Int, pageSize: Int, totalCount: Int)