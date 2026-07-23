package com.schoolbus.auth.service

import scala.concurrent.{ExecutionContext, Future}

/** Abstracts "send an email" so AuthService never depends on a concrete mail
  * provider. Swapping SES/SendGrid/SMTP in later means implementing this
  * trait - AuthService and its tests don't change.
  */
trait EmailService {
  def sendPasswordResetEmail(toEmail: String, rawResetToken: String): Future[Unit]
}

/** Stub implementation until a real provider is wired up. Deliberately a
  * no-op rather than throwing, so password-reset requests still succeed
  * end-to-end in the meantime.
  */
class NoOpEmailService(implicit ec: ExecutionContext) extends EmailService {
  def sendPasswordResetEmail(toEmail: String, rawResetToken: String): Future[Unit] =
    Future.successful(())
}