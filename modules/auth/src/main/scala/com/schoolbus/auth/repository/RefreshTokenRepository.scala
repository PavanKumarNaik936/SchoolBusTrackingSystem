package com.schoolbus.auth.repository

import com.schoolbus.auth.model.{RefreshToken, Tables}
import slick.jdbc.PostgresProfile.api._
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait RefreshTokenRepository {
  def store(token: RefreshToken): Future[Unit]
  def findByHash(tokenHash: String): Future[Option[RefreshToken]]
  def revoke(id: UUID, at: Instant): Future[Unit]
  def revokeAllForUser(userId: UUID, at: Instant): Future[Unit]
}

class SlickRefreshTokenRepository(db: Database)(implicit ec: ExecutionContext)
    extends RefreshTokenRepository {
  import Tables._

  def store(token: RefreshToken): Future[Unit] =
    db.run(refreshTokens += token).map(_ => ())

  def findByHash(tokenHash: String): Future[Option[RefreshToken]] =
    db.run(refreshTokens.filter(_.tokenHash === tokenHash).result.headOption)

  def revoke(id: UUID, at: Instant): Future[Unit] =
    db.run(refreshTokens.filter(_.id === id).map(_.revokedAt).update(Some(at))).map(_ => ())

  def revokeAllForUser(userId: UUID, at: Instant): Future[Unit] =
    db.run(
      refreshTokens
        .filter(t => t.userId === userId && t.revokedAt.isEmpty)
        .map(_.revokedAt)
        .update(Some(at))
    ).map(_ => ())
}
