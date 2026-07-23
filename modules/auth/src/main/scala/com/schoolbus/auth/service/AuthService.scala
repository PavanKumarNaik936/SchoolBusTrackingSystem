package com.schoolbus.auth.service

import com.schoolbus.auth.model.{PasswordResetToken, RefreshToken}
import com.schoolbus.auth.repository.{PasswordResetTokenRepository, RefreshTokenRepository, UserRepository}
import com.schoolbus.common.errors.AppError

import java.security.{MessageDigest, SecureRandom}
import java.time.Instant
import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}

final case class TokenPair(accessToken: String, refreshToken: String, expiresIn: Long, role: String)

class AuthService(
    userRepository: UserRepository,
    refreshTokenRepository: RefreshTokenRepository,
    passwordResetTokenRepository: PasswordResetTokenRepository,
    passwordHasher: PasswordHasher,
    jwtService: JwtService,
    emailService: EmailService
)(implicit ec: ExecutionContext) {

  private val refreshTokenTtlDays      = 30L
  private val passwordResetTtlMinutes  = 60L
  private val secureRandom             = new SecureRandom()

  def login(email: String, plaintextPassword: String): Future[Either[AppError, TokenPair]] =
    userRepository.findByEmail(email).flatMap {
      case None =>
        // Deliberately the same error for "no such email" and "wrong
        // password" below - never reveal which one it was, that's an
        // account-enumeration leak (flagged back in Phase 4).
        Future.successful(Left(invalidCredentials))

      case Some(user) if !user.isActive =>
        Future.successful(Left(invalidCredentials))

      case Some(user) if !passwordHasher.verify(plaintextPassword, user.passwordHash) =>
        Future.successful(Left(invalidCredentials))

      case Some(user) =>
        issueTokenPair(user.id, user.role, user.schoolId).map(Right(_))
    }

  def refresh(presentedRefreshToken: String): Future[Either[AppError, TokenPair]] = {
    val hash = sha256(presentedRefreshToken)

    refreshTokenRepository.findByHash(hash).flatMap {
      case Some(stored) if stored.isValid(Instant.now()) =>
        for {
          // Rotate: revoke the old one so a leaked-and-reused refresh
          // token can't be replayed after a legitimate refresh happens.
          _    <- refreshTokenRepository.revoke(stored.id, Instant.now())
          user <- userRepository.findById(stored.userId)
          result <- user match {
            case Some(u) => issueTokenPair(u.id, u.role, u.schoolId).map(Right(_))
            case None    => Future.successful(Left(AppError.Unauthorized("User no longer exists")))
          }
        } yield result

      case _ =>
        Future.successful(Left(AppError.Unauthorized("Refresh token is invalid, expired, or already used")))
    }
  }

  def logout(presentedRefreshToken: String): Future[Either[AppError, Unit]] = {
    val hash = sha256(presentedRefreshToken)
    refreshTokenRepository.findByHash(hash).flatMap {
      case Some(stored) => refreshTokenRepository.revoke(stored.id, Instant.now()).map(_ => Right(()))
      case None         => Future.successful(Right(())) // already gone; logout is idempotent
    }
  }

  /** Always succeeds from the caller's point of view, whether or not the
    * email belongs to an account - same account-enumeration concern as
    * login, just with no credential to compare so there's no error branch
    * to unify in the first place.
    */
  def requestPasswordReset(email: String): Future[Unit] =
    userRepository.findByEmail(email).flatMap {
      case Some(user) if user.isActive =>
        val rawToken = generateOpaqueToken()
        val record = PasswordResetToken(
          id = UUID.randomUUID(),
          userId = user.id,
          tokenHash = sha256(rawToken),
          expiresAt = Instant.now().plusSeconds(passwordResetTtlMinutes * 60),
          usedAt = None,
          createdAt = Instant.now()
        )
        for {
          _ <- passwordResetTokenRepository.store(record)
          _ <- emailService.sendPasswordResetEmail(user.email, rawToken)
        } yield ()

      case _ =>
        Future.successful(())
    }

  def confirmPasswordReset(presentedToken: String, newPassword: String): Future[Either[AppError, Unit]] = {
    val hash = sha256(presentedToken)

    passwordResetTokenRepository.findByHash(hash).flatMap {
      case None =>
        Future.successful(Left(invalidResetToken))

      case Some(stored) if stored.usedAt.isDefined =>
        Future.successful(Left(invalidResetToken))

      case Some(stored) if !Instant.now().isBefore(stored.expiresAt) =>
        Future.successful(Left(expiredResetToken))

      case Some(stored) =>
        val newHash = passwordHasher.hash(newPassword)
        for {
          _ <- userRepository.updatePasswordHash(stored.userId, newHash)
          _ <- passwordResetTokenRepository.markUsed(stored.id, Instant.now())
          // A changed password should end every existing session, not just
          // the one that requested the reset - otherwise a leaked refresh
          // token survives the very password change meant to shut it out.
          _ <- refreshTokenRepository.revokeAllForUser(stored.userId, Instant.now())
        } yield Right(())
    }
  }

  private def issueTokenPair(userId: UUID, role: com.schoolbus.common.tenant.Role, schoolId: Option[UUID]): Future[TokenPair] = {
    val (accessToken, expiresIn) = jwtService.issueAccessToken(userId, role, schoolId)

    val rawRefreshToken = generateOpaqueToken()
    val record = RefreshToken(
      id = UUID.randomUUID(),
      userId = userId,
      tokenHash = sha256(rawRefreshToken),
      expiresAt = Instant.now().plusSeconds(refreshTokenTtlDays * 86400),
      revokedAt = None,
      createdAt = Instant.now()
    )

    refreshTokenRepository.store(record).map { _ =>
      TokenPair(accessToken, rawRefreshToken, expiresIn, role.wireName)
    }
  }

  private def generateOpaqueToken(): String = {
    val bytes = new Array[Byte](32)
    secureRandom.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
  }

  private def sha256(input: String): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes("UTF-8"))
    digest.map("%02x".format(_)).mkString
  }

  private def invalidCredentials: AppError =
    AppError.Unauthorized("Invalid email or password")

  private def invalidResetToken: AppError =
    AppError.BadRequest("INVALID_RESET_TOKEN", "Reset token is invalid")

  private def expiredResetToken: AppError =
    AppError.BadRequest("EXPIRED_RESET_TOKEN", "Reset token has expired")
}
