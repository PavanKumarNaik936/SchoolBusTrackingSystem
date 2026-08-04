package com.schoolbus.auth.client

import com.schoolbus.auth.repository.UserRepository
import com.schoolbus.common.client.{UserClient, UserSummary}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Adapts the full auth.model.User (password hash and all) down to the
  * UserSummary that other modules are allowed to see.
  */
class AuthUserClient(userRepository: UserRepository)(implicit ec: ExecutionContext) extends UserClient {
  def find(userId: UUID): Future[Option[UserSummary]] =
    userRepository.findById(userId).map(_.map(u => UserSummary(u.id, u.schoolId, u.role, u.isActive)))
}