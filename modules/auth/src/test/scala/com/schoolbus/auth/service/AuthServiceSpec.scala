package com.schoolbus.auth.service

import com.schoolbus.auth.model.{PasswordResetToken, RefreshToken, User}
import com.schoolbus.auth.repository.{PasswordResetTokenRepository, RefreshTokenRepository, UserRepository}
import com.schoolbus.common.errors.AppError
import com.schoolbus.common.tenant.Role
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/** In-memory fakes standing in for the Slick-backed repositories. This is
  * exactly what the trait-based repository design from earlier buys us:
  * AuthService's actual logic (credential checking, token rotation) gets
  * tested with zero database involved.
  */
class FakeUserRepository extends UserRepository {
  private val byId = mutable.Map.empty[UUID, User]

  def seed(user: User): Unit = byId.update(user.id, user)

  def findByEmail(email: String): Future[Option[User]] =
    Future.successful(byId.values.find(_.email == email))

  def findById(id: UUID): Future[Option[User]] =
    Future.successful(byId.get(id))

  def updatePasswordHash(userId: UUID, newPasswordHash: String): Future[Unit] = {
    byId.get(userId).foreach(u => byId.update(userId, u.copy(passwordHash = newPasswordHash)))
    Future.successful(())
  }
}

class FakeRefreshTokenRepository extends RefreshTokenRepository {
  private val byId = mutable.Map.empty[UUID, RefreshToken]

  def store(token: RefreshToken): Future[Unit] = {
    byId.update(token.id, token)
    Future.successful(())
  }

  def findByHash(tokenHash: String): Future[Option[RefreshToken]] =
    Future.successful(byId.values.find(_.tokenHash == tokenHash))

  def revoke(id: UUID, at: Instant): Future[Unit] = {
    byId.get(id).foreach(t => byId.update(id, t.copy(revokedAt = Some(at))))
    Future.successful(())
  }

  def revokeAllForUser(userId: UUID, at: Instant): Future[Unit] = {
    byId.values.filter(t => t.userId == userId && t.revokedAt.isEmpty).foreach { t =>
      byId.update(t.id, t.copy(revokedAt = Some(at)))
    }
    Future.successful(())
  }
}

class FakePasswordResetTokenRepository extends PasswordResetTokenRepository {
  private val byId = mutable.Map.empty[UUID, PasswordResetToken]

  def store(token: PasswordResetToken): Future[Unit] = {
    byId.update(token.id, token)
    Future.successful(())
  }

  def findByHash(tokenHash: String): Future[Option[PasswordResetToken]] =
    Future.successful(byId.values.find(_.tokenHash == tokenHash))

  def markUsed(id: UUID, at: Instant): Future[Unit] = {
    byId.get(id).foreach(t => byId.update(id, t.copy(usedAt = Some(at))))
    Future.successful(())
  }
}

class FakeEmailService extends EmailService {
  val sentResetEmails: mutable.Buffer[(String, String)] = mutable.Buffer.empty

  def sendPasswordResetEmail(toEmail: String, rawResetToken: String): Future[Unit] = {
    sentResetEmails += (toEmail -> rawResetToken)
    Future.successful(())
  }
}

class AuthServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  // bcrypt at PasswordHasher's cost factor is deliberately slow (that's the
  // point), so it routinely exceeds ScalaFutures' 150ms default patience -
  // every test below that logs in for real needs the longer window.
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(2000, Millis))

  private case class Fixture(
      authService: AuthService,
      userRepo: FakeUserRepository,
      resetTokenRepo: FakePasswordResetTokenRepository,
      emailService: FakeEmailService,
      user: User
  )

  private def newFixture(): Fixture = {
    val userRepo    = new FakeUserRepository
    val refreshRepo = new FakeRefreshTokenRepository
    val resetRepo   = new FakePasswordResetTokenRepository
    val hasher      = new PasswordHasher
    val jwt         = new JwtService("test-secret")
    val email       = new FakeEmailService

    val user = User(
      id = UUID.randomUUID(),
      schoolId = Some(UUID.randomUUID()),
      email = "admin@lincoln.edu",
      passwordHash = hasher.hash("correct-password"),
      role = Role.SchoolAdmin,
      isActive = true,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    userRepo.seed(user)

    val authService = new AuthService(userRepo, refreshRepo, resetRepo, hasher, jwt, email)
    Fixture(authService, userRepo, resetRepo, email, user)
  }

  "login" should {
    "succeed with correct credentials" in {
      val f = newFixture()

      whenReady(f.authService.login(f.user.email, "correct-password")) { result =>
        result.isRight shouldBe true
        result.map(_.role) shouldBe Right("SCHOOL_ADMIN")
      }
    }

    "fail with the same error for wrong password as for unknown email" in {
      val f = newFixture()

      val wrongPasswordResult = f.authService.login(f.user.email, "wrong-password").futureValue
      val unknownEmailResult  = f.authService.login("nobody@nowhere.edu", "anything").futureValue

      wrongPasswordResult shouldBe Left(AppError.Unauthorized("Invalid email or password"))
      unknownEmailResult shouldBe Left(AppError.Unauthorized("Invalid email or password"))
    }

    "fail for a deactivated user even with the correct password" in {
      val f = newFixture()
      f.userRepo.seed(f.user.copy(isActive = false))

      f.authService.login(f.user.email, "correct-password").futureValue.isLeft shouldBe true
    }
  }

  "refresh" should {
    "issue a new token pair and revoke the old refresh token (rotation)" in {
      val f = newFixture()
      val firstPair = f.authService.login(f.user.email, "correct-password").futureValue.getOrElse(fail("login should succeed"))

      val refreshed = f.authService.refresh(firstPair.refreshToken).futureValue
      refreshed.isRight shouldBe true

      // The old refresh token must no longer work - that's the whole
      // point of rotation.
      val reuseAttempt = f.authService.refresh(firstPair.refreshToken).futureValue
      reuseAttempt.isLeft shouldBe true
    }

    "reject an unknown refresh token" in {
      val f = newFixture()
      f.authService.refresh("not-a-real-token").futureValue.isLeft shouldBe true
    }
  }

  "logout" should {
    "revoke the refresh token so it can no longer be used" in {
      val f = newFixture()
      val pair = f.authService.login(f.user.email, "correct-password").futureValue.getOrElse(fail("login should succeed"))

      f.authService.logout(pair.refreshToken).futureValue.isRight shouldBe true
      f.authService.refresh(pair.refreshToken).futureValue.isLeft shouldBe true
    }

    "be idempotent for an already-unknown token" in {
      val f = newFixture()
      f.authService.logout("never-issued").futureValue.isRight shouldBe true
    }
  }

  "requestPasswordReset" should {
    "store a reset token and email it when the account exists" in {
      val f = newFixture()

      f.authService.requestPasswordReset(f.user.email).futureValue

      f.emailService.sentResetEmails should have size 1
      val (toEmail, rawToken) = f.emailService.sentResetEmails.head
      toEmail shouldBe f.user.email
      rawToken should not be empty
    }

    "succeed silently for an unknown email (no enumeration)" in {
      val f = newFixture()

      f.authService.requestPasswordReset("nobody@nowhere.edu").futureValue

      f.emailService.sentResetEmails shouldBe empty
    }
  }

  "confirmPasswordReset" should {
    "update the password, consume the token, and revoke all refresh tokens" in {
      val f = newFixture()
      val oldPair = f.authService.login(f.user.email, "correct-password").futureValue.getOrElse(fail("login should succeed"))

      f.authService.requestPasswordReset(f.user.email).futureValue
      val (_, rawResetToken) = f.emailService.sentResetEmails.head

      f.authService.confirmPasswordReset(rawResetToken, "new-correct-password").futureValue shouldBe Right(())

      // Old password no longer works, new one does.
      f.authService.login(f.user.email, "correct-password").futureValue.isLeft shouldBe true
      f.authService.login(f.user.email, "new-correct-password").futureValue.isRight shouldBe true

      // Every session alive before the reset is dead now.
      f.authService.refresh(oldPair.refreshToken).futureValue.isLeft shouldBe true

      // The reset token is single-use.
      f.authService.confirmPasswordReset(rawResetToken, "yet-another-password").futureValue.isLeft shouldBe true
    }

    "reject an unknown reset token" in {
      val f = newFixture()
      f.authService.confirmPasswordReset("not-a-real-token", "new-password").futureValue shouldBe
        Left(AppError.BadRequest("INVALID_RESET_TOKEN", "Reset token is invalid"))
    }
  }
}
