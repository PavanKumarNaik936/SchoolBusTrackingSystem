package com.schoolbus.auth.repository

import com.schoolbus.auth.model.{PasswordResetToken, Tables}
import slick.jdbc.PostgresProfile.api._
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait PasswordResetTokenRepository {
  def store(token: PasswordResetToken): Future[Unit]
  def findByHash(tokenHash: String): Future[Option[PasswordResetToken]]
  def markUsed(id: UUID, at: Instant): Future[Unit]
}

class SlickPasswordResetTokenRepository(db: Database)(implicit ec: ExecutionContext)
    extends PasswordResetTokenRepository {
  import Tables._

  def store(token: PasswordResetToken): Future[Unit] =
    db.run(passwordResetTokens += token).map(_ => ())

  def findByHash(tokenHash: String): Future[Option[PasswordResetToken]] =
    db.run(passwordResetTokens.filter(_.tokenHash === tokenHash).result.headOption)

  def markUsed(id: UUID, at: Instant): Future[Unit] =
    db.run(passwordResetTokens.filter(_.id === id).map(_.usedAt).update(Some(at))).map(_ => ())
}