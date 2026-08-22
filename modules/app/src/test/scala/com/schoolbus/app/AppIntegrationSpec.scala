package com.schoolbus.app

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.schoolbus.auth.service.PasswordHasher
import com.schoolbus.schools.model.School
import io.circe.parser.parse
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import slick.jdbc.PostgresProfile.api._

import java.sql.{DriverManager, Timestamp}
import java.time.Instant
import java.util.UUID

/** The one real end-to-end test in this codebase: a genuine Postgres
  * (via Testcontainers), migrated with the exact same `DbMigrator.migrate`
  * call `Main` makes, wired with the exact same `AppWiring.build` `Main`
  * calls, and driven over real HTTP routes - no fake repositories anywhere
  * in this test. Its job is to prove the thing every other test in this
  * codebase can't: that auth's JWTs and students' tenant-scoped
  * authorization actually agree with each other against a real database,
  * not just against each other's assumptions.
  *
  * Needs a Docker daemon reachable from wherever `sbt app/test` runs -
  * Testcontainers starts and stops the Postgres container automatically.
  */
class AppIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll with ScalatestRouteTest {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(10000, Millis))

  // Self-referential subclass, not `new PostgreSQLContainer(...)` directly -
  // the Java class is generically self-typed (`PostgreSQLContainer<SELF>`)
  // so that its fluent builder methods return the right subtype; this is
  // the standard way to instantiate it from Scala without wildcard types.
  private class PostgresContainer extends PostgreSQLContainer[PostgresContainer](DockerImageName.parse("postgres:16-alpine"))

  private val postgres = new PostgresContainer()

  private var db: Database              = _
  private var routes: Route              = _
  private var schoolAId: UUID            = _
  private var schoolBId: UUID            = _
  private val passwordHasher              = new PasswordHasher()
  private val adminPassword               = "Sup3rSecret!"

  override def beforeAll(): Unit = {
    postgres.start()

    DbMigrator.migrate(postgres.getJdbcUrl, postgres.getUsername, postgres.getPassword)

    db = Database.forURL(
      url = postgres.getJdbcUrl,
      user = postgres.getUsername,
      password = postgres.getPassword,
      driver = "org.postgresql.Driver"
    )

    val components = AppWiring.build(db, jwtSecret = "test-secret")
    routes = AppRoutes(db, components.routes)

    // Seed via the same paths a real caller would have available: School
    // has a repository method for exactly this ("no HTTP layer yet" - see
    // SchoolRepository's own doc comment), but User has no create/insert
    // method on its repository trait at all (only findByEmail/findById/
    // updatePasswordHash) - so the two seeded admin users go in via a plain
    // SQL insert instead, with a real bcrypt hash so login exercises real
    // password verification.
    schoolAId = components.schoolRepository.create(newSchool("School A")).futureValue.id
    schoolBId = components.schoolRepository.create(newSchool("School B")).futureValue.id
    seedSchoolAdmin(schoolAId, "admin-a@schoolbus.test", adminPassword)
    seedSchoolAdmin(schoolBId, "admin-b@schoolbus.test", adminPassword)
  }

  override def afterAll(): Unit = {
    postgres.stop()
  }

  "the wired application" should {

    "report healthy once migrated and connected to a real Postgres" in {
      Get("/health") ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "let a school admin log in, create a student in their own school, and read it back" in {
      val tokenA = login("admin-a@schoolbus.test", adminPassword)

      val studentId = createStudent(schoolAId, tokenA, firstName = "Ada", lastName = "Lovelace")

      Get(s"/api/v1/students/$studentId") ~> withToken(tokenA) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val body = parse(responseAs[String]).getOrElse(fail("response was not valid JSON"))
        body.hcursor.get[String]("firstName") shouldBe Right("Ada")
        body.hcursor.get[String]("schoolId") shouldBe Right(schoolAId.toString)
      }
    }

    "reject a different school's admin trying to read a student that isn't theirs, with 403 not 404" in {
      val tokenA = login("admin-a@schoolbus.test", adminPassword)
      val studentId = createStudent(schoolAId, tokenA, firstName = "Grace", lastName = "Hopper")

      val tokenB = login("admin-b@schoolbus.test", adminPassword)

      Get(s"/api/v1/students/$studentId") ~> withToken(tokenB) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "let a school admin log in, create a bus in their own school, and read it back" in {
      val tokenA = login("admin-a@schoolbus.test", adminPassword)

      val busId = createBus(schoolAId, tokenA, plateNumber = "BUS-A-1", capacity = 42)

      Get(s"/api/v1/buses/$busId") ~> withToken(tokenA) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val body = parse(responseAs[String]).getOrElse(fail("response was not valid JSON"))
        body.hcursor.get[String]("plateNumber") shouldBe Right("BUS-A-1")
        body.hcursor.get[Int]("capacity") shouldBe Right(42)
      }
    }

    "reject a different school's admin trying to read a bus that isn't theirs, with 404 not found in their own tenant scope" in {
      val tokenA = login("admin-a@schoolbus.test", adminPassword)
      val busId = createBus(schoolAId, tokenA, plateNumber = "BUS-A-2", capacity = 30)

      val tokenB = login("admin-b@schoolbus.test", adminPassword)

      Get(s"/api/v1/buses/$busId") ~> withToken(tokenB) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  private def newSchool(name: String): School = {
    val now = Instant.now()
    School(id = UUID.randomUUID(), name = name, isActive = true, createdAt = now, updatedAt = now)
  }

  private def seedSchoolAdmin(schoolId: UUID, email: String, rawPassword: String): UUID = {
    val userId = UUID.randomUUID()
    val conn = DriverManager.getConnection(postgres.getJdbcUrl, postgres.getUsername, postgres.getPassword)
    try {
      val stmt = conn.prepareStatement(
        """insert into users (id, school_id, email, password_hash, role, is_active, created_at, updated_at)
          |values (?, ?, ?, ?, 'SCHOOL_ADMIN', true, ?, ?)""".stripMargin
      )
      val now = Timestamp.from(Instant.now())
      stmt.setObject(1, userId)
      stmt.setObject(2, schoolId)
      stmt.setString(3, email)
      stmt.setString(4, passwordHasher.hash(rawPassword))
      stmt.setTimestamp(5, now)
      stmt.setTimestamp(6, now)
      stmt.executeUpdate()
      stmt.close()
    } finally conn.close()
    userId
  }

  private def login(email: String, password: String): String = {
    val body = HttpEntity(ContentTypes.`application/json`, s"""{"email":"$email","password":"$password"}""")
    Post("/api/v1/auth/login", body) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val json = parse(responseAs[String]).getOrElse(fail("login response was not valid JSON"))
      json.hcursor.get[String]("accessToken").getOrElse(fail("no accessToken in login response"))
    }
  }

  private def createStudent(schoolId: UUID, token: String, firstName: String, lastName: String): UUID = {
    val body = HttpEntity(
      ContentTypes.`application/json`,
      s"""{"firstName":"$firstName","lastName":"$lastName","grade":"5"}"""
    )
    Post(s"/api/v1/schools/$schoolId/students", body) ~> withToken(token) ~> routes ~> check {
      status shouldBe StatusCodes.Created
      val json = parse(responseAs[String]).getOrElse(fail("create-student response was not valid JSON"))
      val idStr = json.hcursor.get[String]("id").getOrElse(fail("no id in create-student response"))
      UUID.fromString(idStr)
    }
  }

  private def createBus(schoolId: UUID, token: String, plateNumber: String, capacity: Int): UUID = {
    val body = HttpEntity(
      ContentTypes.`application/json`,
      s"""{"plateNumber":"$plateNumber","capacity":$capacity}"""
    )
    Post(s"/api/v1/schools/$schoolId/buses", body) ~> withToken(token) ~> routes ~> check {
      status shouldBe StatusCodes.Created
      val json = parse(responseAs[String]).getOrElse(fail("create-bus response was not valid JSON"))
      val idStr = json.hcursor.get[String]("id").getOrElse(fail("no id in create-bus response"))
      UUID.fromString(idStr)
    }
  }

  private def withToken(token: String) = addHeader(Authorization(OAuth2BearerToken(token)))
}
