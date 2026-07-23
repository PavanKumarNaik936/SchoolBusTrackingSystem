package com.schoolbus.auth.service

import com.schoolbus.common.tenant.Role
import io.circe.Json
import io.circe.parser.parse
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.time.Instant
import java.util.UUID
import scala.util.Try

/** The custom claims we embed in a token, once decoded back out.
  * `userId` comes from the standard "sub" claim; role and schoolId are
  * custom claims we add ourselves since they're specific to this system.
  */
final case class JwtClaimsData(userId: UUID, role: Role, schoolId: Option[UUID])

/** Wraps jwt-scala so nothing outside this file needs to know the token
  * format, signing algorithm, or claim structure. If we ever rotate
  * algorithms (HS256 -> RS256, say, to allow other services to verify
  * tokens without holding the signing secret) this is the only file that
  * changes.
  */
class JwtService(secretKey: String) {
  private val algorithm             = JwtAlgorithm.HS256
  private val accessTokenTtlSeconds = 3600L

  def issueAccessToken(userId: UUID, role: Role, schoolId: Option[UUID]): (String, Long) = {
    val now = Instant.now()

    val content = Json
      .obj(
        "role"     -> Json.fromString(role.wireName),
        "schoolId" -> schoolId.map(id => Json.fromString(id.toString)).getOrElse(Json.Null)
      )
      .noSpaces

    val claim = JwtClaim(
      content = content,
      subject = Some(userId.toString),
      issuedAt = Some(now.getEpochSecond),
      expiration = Some(now.plusSeconds(accessTokenTtlSeconds).getEpochSecond)
    )

    (JwtCirce.encode(claim, secretKey, algorithm), accessTokenTtlSeconds)
  }

  def decode(token: String): Either[String, JwtClaimsData] =
    for {
      claim <- JwtCirce.decode(token, secretKey, Seq(algorithm)).toEither.left.map(_.getMessage)
      json  <- parse(claim.content).left.map(_.getMessage)
      roleStr <- json.hcursor.get[String]("role").left.map(_.getMessage)
      role  <- Role.fromString(roleStr)
      schoolIdStrOpt <- json.hcursor.get[Option[String]]("schoolId").left.map(_.getMessage)
      subjectStr <- claim.subject.toRight("Token is missing a subject claim")
      userId <- Try(UUID.fromString(subjectStr)).toEither.left.map(_ => "Subject claim is not a valid UUID")
      schoolId <- schoolIdStrOpt.traverseUuid
    } yield JwtClaimsData(userId, role, schoolId)

  private implicit class OptStringOps(opt: Option[String]) {
    // Small local helper: turns Option[String] into Either[String, Option[UUID]]
    // without pulling in cats just for this one conversion.
    def traverseUuid: Either[String, Option[UUID]] = opt match {
      case None    => Right(None)
      case Some(s) => Try(UUID.fromString(s)).toEither.left.map(_ => "schoolId claim is not a valid UUID").map(Some(_))
    }
  }
}
