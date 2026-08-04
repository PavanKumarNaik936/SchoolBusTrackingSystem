package com.schoolbus.common.client

import com.schoolbus.common.tenant.Role
import java.util.UUID
import scala.concurrent.Future

/** The minimal view of a user that other modules are allowed to see -
  * enough to check "is this a parent, and which school are they in,"
  * nothing about credentials.
  */
final case class UserSummary(id: UUID, schoolId: Option[UUID], role: Role, isActive: Boolean)

/** Lives in `common` so a module like `students` can depend on this
  * interface without depending on all of `auth` (its JWT/password-hashing
  * internals). `auth` provides the real implementation; whatever wires the
  * application together at startup passes that implementation in.
  */
trait UserClient {
  def find(userId: UUID): Future[Option[UserSummary]]
}