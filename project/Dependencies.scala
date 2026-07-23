import sbt._

object Dependencies {
  val akkaVersion     = "2.8.5"
  val akkaHttpVersion = "10.5.3"
  val circeVersion    = "0.14.9"

  // common is the shared kernel: DB access, JSON, and eventually akka-http
  // types for the error-mapping helper. Every feature module will add its
  // own libraryDependencies on top of these via `.dependsOn(common)`.
  val common: Seq[ModuleID] = Seq(
    "com.typesafe.slick" %% "slick"          % "3.5.1",
    "org.postgresql"      % "postgresql"     % "42.7.3",
    "io.circe"           %% "circe-core"     % circeVersion,
    "io.circe"           %% "circe-generic"  % circeVersion,
    "io.circe"           %% "circe-parser"   % circeVersion,
    "com.typesafe.akka"  %% "akka-http"      % akkaHttpVersion,
    "com.typesafe"        % "config"         % "1.4.3",
    "org.scalatest"      %% "scalatest"      % "3.2.19" % Test
  )

  // auth additionally needs: JWT encode/decode, password hashing, and
  // akka-http <-> circe (de)serialization for its routes layer. Later
  // modules that also expose routes will need this last one too - worth
  // promoting into `common` once a second module needs it, rather than
  // guessing now that every module will.
  val auth: Seq[ModuleID] = common ++ Seq(
    "com.github.jwt-scala" %% "jwt-circe"      % "10.0.1",
    "org.mindrot"           % "jbcrypt"        % "0.4",
    "de.heikoseeberger"      %% "akka-http-circe" % "1.39.2",
    "org.scalatest"        %% "scalatest"      % "3.2.19" % Test
  )
}
