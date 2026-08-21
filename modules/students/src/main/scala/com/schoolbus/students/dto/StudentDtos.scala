package com.schoolbus.students.dto

import io.circe.{Decoder, Encoder, HCursor}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import com.schoolbus.common.json.CommonCodecs._
import com.schoolbus.students.model.{Student, StudentDetail, StudentPage}

import java.time.Instant
import java.util.UUID

final case class CreateStudentRequest(firstName: String, lastName: String, grade: String, routeId: Option[UUID])
object CreateStudentRequest {
  implicit val decoder: Decoder[CreateStudentRequest] = deriveDecoder
}

/** Hand-written rather than derived: deriveDecoder can't tell "routeId key
  * absent" apart from "routeId key present with value null," and PATCH
  * semantics need exactly that distinction (see StudentPatch's doc
  * comment). firstName/lastName/grade don't need this trick - for a plain
  * string field, "key missing" and "key present but null" both just mean
  * "don't change this field," so Circe's ordinary Option handling is fine.
  */
final case class UpdateStudentRequest(
    firstName: Option[String],
    lastName: Option[String],
    grade: Option[String],
    routeId: Option[Option[UUID]]
)
object UpdateStudentRequest {
  implicit val decoder: Decoder[UpdateStudentRequest] = (c: HCursor) =>
    for {
      firstName <- c.get[Option[String]]("firstName")
      lastName  <- c.get[Option[String]]("lastName")
      grade     <- c.get[Option[String]]("grade")
      routeId   <- decodeRouteIdPatch(c)
    } yield UpdateStudentRequest(firstName, lastName, grade, routeId)

  private def decodeRouteIdPatch(c: HCursor): Decoder.Result[Option[Option[UUID]]] = {
    val fieldPresent = c.value.asObject.exists(_.contains("routeId"))
    if (!fieldPresent) Right(None) else c.get[Option[UUID]]("routeId").map(Some(_))
  }
}

final case class LinkParentRequest(parentUserId: UUID)
object LinkParentRequest {
  implicit val decoder: Decoder[LinkParentRequest] = deriveDecoder
}

/** The shape returned by Create/Update/List - no parent list, so listing a
  * page of students doesn't cost one extra query per row.
  */
final case class StudentResponse(
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
object StudentResponse {
  implicit val encoder: Encoder[StudentResponse] = deriveEncoder

  def from(s: Student): StudentResponse =
    StudentResponse(s.id, s.schoolId, s.firstName, s.lastName, s.grade, s.routeId, s.isActive, s.createdAt, s.updatedAt)
}

/** The shape returned by Get Student only - same fields as StudentResponse
  * plus parentUserIds, since a single-resource fetch can afford the extra
  * query that a whole page of students can't.
  */
final case class StudentDetailResponse(
    id: UUID,
    schoolId: UUID,
    firstName: String,
    lastName: String,
    grade: String,
    routeId: Option[UUID],
    isActive: Boolean,
    createdAt: Instant,
    updatedAt: Instant,
    parentUserIds: List[UUID]
)
object StudentDetailResponse {
  implicit val encoder: Encoder[StudentDetailResponse] = deriveEncoder

  def from(d: StudentDetail): StudentDetailResponse =
    StudentDetailResponse(
      d.student.id,
      d.student.schoolId,
      d.student.firstName,
      d.student.lastName,
      d.student.grade,
      d.student.routeId,
      d.student.isActive,
      d.student.createdAt,
      d.student.updatedAt,
      d.parentUserIds
    )
}

final case class StudentListResponse(items: List[StudentResponse], page: Int, pageSize: Int, totalCount: Int)
object StudentListResponse {
  implicit val encoder: Encoder[StudentListResponse] = deriveEncoder

  def from(p: StudentPage): StudentListResponse =
    StudentListResponse(p.items.map(StudentResponse.from), p.page, p.pageSize, p.totalCount)
}