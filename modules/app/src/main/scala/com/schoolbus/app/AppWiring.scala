package com.schoolbus.app

import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import com.schoolbus.auth.client.{AuthUserClient, JwtTokenAuthenticator}
import com.schoolbus.auth.repository.{SlickPasswordResetTokenRepository, SlickRefreshTokenRepository, SlickUserRepository}
import com.schoolbus.auth.routes.AuthRoutes
import com.schoolbus.auth.service.{AuthService, JwtService, NoOpEmailService, PasswordHasher}
import com.schoolbus.common.auth.TokenAuthenticator
import com.schoolbus.schools.repository.{SchoolRepository, SlickRouteRepository, SlickSchoolRepository}
import com.schoolbus.students.repository.{SlickStudentParentRepository, SlickStudentRepository}
import com.schoolbus.students.routes.StudentRoutes
import com.schoolbus.students.service.StudentService
import slick.jdbc.PostgresProfile.api.Database

import scala.concurrent.ExecutionContext

/** Everything the composition root exposes once it's wired: the combined
  * module route tree, and the one repository the integration test needs
  * directly for seeding (`SchoolRepository.create` - see its own doc comment
  * on why that method exists at all: there's no `schools` HTTP layer, so
  * tests/seeds are its only caller).
  */
final case class AppComponents(routes: Route, schoolRepository: SchoolRepository)

/** The composition root: the one place in the whole codebase that constructs
  * every module's *real*, Slick-backed chain and threads them into each
  * other via the cross-module client traits (`TokenAuthenticator`,
  * `UserClient`) defined in `common`.
  *
  * Called from both `Main` (against the real configured `Database`) and
  * `AppIntegrationSpec` (against a Testcontainers `Database`) with the exact
  * same code path - the integration test is proof that *this* wiring works,
  * not a separately-hand-rolled stand-in for it. Contrast with
  * `AuthServiceSpec`/`StudentServiceSpec`-style unit tests, which construct
  * `AuthService`/`StudentService` directly against in-memory fake
  * repositories - those want speed and isolation from a real DB; this wants
  * to prove the real thing boots.
  */
object AppWiring {
  def build(db: Database, jwtSecret: String)(implicit ec: ExecutionContext): AppComponents = {
    // auth
    val userRepository             = new SlickUserRepository(db)
    val refreshTokenRepository     = new SlickRefreshTokenRepository(db)
    val passwordResetTokenRepository = new SlickPasswordResetTokenRepository(db)
    val passwordHasher             = new PasswordHasher()
    val jwtService                 = new JwtService(jwtSecret)
    val emailService                = new NoOpEmailService()
    val authService = new AuthService(
      userRepository,
      refreshTokenRepository,
      passwordResetTokenRepository,
      passwordHasher,
      jwtService,
      emailService
    )

    // Cross-module interfaces (defined in `common`, implemented in `auth`) -
    // this is the only place that's allowed to know both sides of that
    // interface at once.
    val tokenAuthenticator: TokenAuthenticator = new JwtTokenAuthenticator(jwtService)
    val userClient = new AuthUserClient(userRepository)

    // schools (no HTTP layer of its own - see build.sbt's comment on the
    // module; its repositories are consumed directly by students and by
    // test/seed code)
    val schoolRepository = new SlickSchoolRepository(db)
    val routeRepository   = new SlickRouteRepository(db)

    // students
    val studentRepository       = new SlickStudentRepository(db)
    val studentParentRepository = new SlickStudentParentRepository(db)
    val studentService = new StudentService(
      studentRepository,
      studentParentRepository,
      schoolRepository,
      routeRepository,
      userClient
    )

    val authRoutes    = new AuthRoutes(authService)
    val studentRoutes = new StudentRoutes(studentService, tokenAuthenticator)

    AppComponents(
      routes = concat(authRoutes.routes, studentRoutes.routes),
      schoolRepository = schoolRepository
    )
  }
}
