package com.schoolbus.auth.repository

import com.schoolbus.auth.model.{Tables, User}
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

/** Defined as a trait, not a concrete class, so AuthService can depend on
  * this interface and be tested with a fake in-memory implementation - no
  * real Postgres needed to run the service-layer unit tests.
  */
trait UserRepository {
  def findByEmail(email: String): Future[Option[User]]
  def findById(id: UUID): Future[Option[User]]
  def updatePasswordHash(userId: UUID, newPasswordHash: String): Future[Unit]
}

class SlickUserRepository(db: Database)(implicit ec: ExecutionContext) extends UserRepository {
  import Tables._

  def findByEmail(email: String): Future[Option[User]] =
    db.run(users.filter(_.email === email).result.headOption)

  def findById(id: UUID): Future[Option[User]] =
    db.run(users.filter(_.id === id).result.headOption)

  def updatePasswordHash(userId: UUID, newPasswordHash: String): Future[Unit] =
    db.run(users.filter(_.id === userId).map(_.passwordHash).update(newPasswordHash)).map(_ => ())
}
