import sbt._

object Dependencies {
  val akkaVersion     = "2.8.5"
  val akkaHttpVersion = "10.5.3"
  val circeVersion    = "0.14.9"

  // common is the shared kernel: DB access, JSON, and akka-http types for
  // the error-mapping helper and shared auth directives. akka-http-circe
  // lives here (not just in auth) now that a second module (students, in
  // Phase 6) also has a routes layer that needs to complete circe JSON -
  // exactly the promotion the old comment here used to say to wait for.
  val common: Seq[ModuleID] = Seq(
    "com.typesafe.slick" %% "slick"          % "3.5.1",
    "org.postgresql"      % "postgresql"     % "42.7.3",
    "io.circe"           %% "circe-core"     % circeVersion,
    "io.circe"           %% "circe-generic"  % circeVersion,
    "io.circe"           %% "circe-parser"   % circeVersion,
    "com.typesafe.akka"  %% "akka-http"      % akkaHttpVersion,
    "com.typesafe.akka"  %% "akka-stream"    % akkaVersion,
    "de.heikoseeberger"  %% "akka-http-circe" % "1.39.2",
    "com.typesafe"        % "config"         % "1.4.3",
    "org.scalatest"      %% "scalatest"      % "3.2.19" % Test
  )

  // auth additionally needs: JWT encode/decode and password hashing.
  val auth: Seq[ModuleID] = common ++ Seq(
    "com.github.jwt-scala" %% "jwt-circe" % "10.0.1",
    "org.mindrot"           % "jbcrypt"   % "0.4",
    "org.scalatest"        %% "scalatest" % "3.2.19" % Test
  )

  // schools has no routes/HTTP layer - just models + Slick tables +
  // repositories - so it needs nothing beyond what `common` already
  // provides.
  val schools: Seq[ModuleID] = common

  // students' routes layer (Phase 6) needs circe + akka-http-circe, both
  // of which now come from `common`.
  val students: Seq[ModuleID] = common

  // buses' routes layer needs circe + akka-http-circe too, both of which
  // already come from `common` - same as students.
  val buses: Seq[ModuleID] = common

  // app is the composition root: everything from the four feature modules
  // already arrives transitively via .dependsOn in build.sbt, so this only
  // lists what's genuinely new here - running migrations and standing up an
  // in-process Postgres for the one real integration test.
  //
  // Pinned to the last Java-8-compatible line of each: Flyway 10.x and
  // Testcontainers 1.20.x both raised their minimum JDK to 11/17, and this
  // project currently targets Java 8.
  val app: Seq[ModuleID] = Seq(
    "org.flywaydb"       % "flyway-core"        % "9.22.3",
    "org.testcontainers"  % "postgresql"         % "1.19.8" % Test,
    "com.typesafe.akka"  %% "akka-http-testkit"  % akkaHttpVersion % Test,
    // Test-scoped deps don't flow transitively across .dependsOn project
    // references, unlike Compile-scoped ones - common's scalatest is only
    // on common's own test classpath, so app needs its own declaration.
    "org.scalatest"      %% "scalatest"          % "3.2.19" % Test
  )
}
