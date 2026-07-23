package com.schoolbus.auth.model

import com.schoolbus.common.tenant.Role
import slick.jdbc.PostgresProfile.api._
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** Slick table definitions live here, and only here. Repositories import
  * from this file but nothing outside `auth` ever should - other modules
  * that need user data go through a UserClient trait (per Phase 2's
  * cross-module pattern), not by reaching into these tables directly.
  */
object Tables {

  // Role and Instant aren't JDBC-native types, so we teach Slick how to
  // map them to/from columns it does understand (String, Timestamp).
  // Getting this wrong is a common early mistake - it compiles fine but
  // fails at runtime the first time a query actually touches the column.
  implicit val roleColumnType: BaseColumnType[Role] =
    MappedColumnType.base[Role, String](
      role => role.wireName,
      str => Role.fromString(str).getOrElse(
        throw new IllegalStateException(s"Corrupt role value in DB: $str")
      )
    )

  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      Timestamp.from,
      _.toInstant
    )

  class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    def id           = column[UUID]("id", O.PrimaryKey)
    def schoolId     = column[Option[UUID]]("school_id")
    def email        = column[String]("email")
    def passwordHash = column[String]("password_hash")
    def role         = column[Role]("role")
    def isActive     = column[Boolean]("is_active")
    def createdAt    = column[Instant]("created_at")
    def updatedAt    = column[Instant]("updated_at")

    def * = (id, schoolId, email, passwordHash, role, isActive, createdAt, updatedAt) <> (User.tupled, User.unapply)
  }
  val users = TableQuery[UsersTable]

  class RefreshTokensTable(tag: Tag) extends Table[RefreshToken](tag, "refresh_tokens") {
    def id        = column[UUID]("id", O.PrimaryKey)
    def userId    = column[UUID]("user_id")
    def tokenHash = column[String]("token_hash")
    def expiresAt = column[Instant]("expires_at")
    def revokedAt = column[Option[Instant]]("revoked_at")
    def createdAt = column[Instant]("created_at")

    def * = (id, userId, tokenHash, expiresAt, revokedAt, createdAt) <> (RefreshToken.tupled, RefreshToken.unapply)
  }
  val refreshTokens = TableQuery[RefreshTokensTable]

  class PasswordResetTokensTable(tag: Tag) extends Table[PasswordResetToken](tag, "password_reset_tokens") {
    def id        = column[UUID]("id", O.PrimaryKey)
    def userId    = column[UUID]("user_id")
    def tokenHash = column[String]("token_hash")
    def expiresAt = column[Instant]("expires_at")
    def usedAt    = column[Option[Instant]]("used_at")
    def createdAt = column[Instant]("created_at")

    def * = (id, userId, tokenHash, expiresAt, usedAt, createdAt) <> (PasswordResetToken.tupled, PasswordResetToken.unapply)
  }
  val passwordResetTokens = TableQuery[PasswordResetTokensTable]
}
