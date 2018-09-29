import com.typesafe.sbt.packager.docker.ExecCmd
import sbt.Keys.mainClass
import scalariform.formatter.preferences._

lazy val akkaHttpVersion = "10.1.4"
lazy val akkaVersion     = "2.5.16"
lazy val circeVersion    = "0.9.3"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.github.hayasshi",
      scalaVersion    := "2.12.6"
    )),
    name := "namazu-notification-chatwork",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,

      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,

      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    ),
    // scalariform settings
    scalariformPreferences := scalariformPreferences.value
      .setPreference(AlignSingleLineCaseStatements,    true)
      .setPreference(DoubleIndentConstructorArguments, true)
      .setPreference(DanglingCloseParenthesis, Preserve),
    // docker settings
    packageName in Docker := name.value,
    defaultLinuxInstallLocation in Docker := "/opt/n2chatwork",
    executableScriptName := "app",
    dockerBaseImage := "openjdk:8u171-jdk-alpine3.8",
    dockerUpdateLatest := true,
    mainClass in (Compile, bashScriptDefines) := Some("com.github.hayasshi.n2.chatwork.N2ChatWork"),
    // dockerExposedPorts := Seq(8080), // omit for Heroku
    // Run the app.  CMD is required to run on Heroku
    dockerCommands := dockerCommands.value.filter {
      case ExecCmd("CMD", _*) => false
      case _ => true
    }.map {
      case ExecCmd("ENTRYPOINT", args @ _*) => ExecCmd("CMD", args: _*)
      case other => other
    }
  ).enablePlugins(AshScriptPlugin)
