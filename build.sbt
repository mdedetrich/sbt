import Dependencies._
import Util._
import com.typesafe.tools.mima.core._, ProblemFilters._

def internalPath = file("internal")

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := scala212,
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.mavenLocal,
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-Xlint", "-Xlint:-serial"),
  crossScalaVersions := Seq(scala211, scala212),
  scalacOptions := {
    val old = scalacOptions.value
    scalaVersion.value match {
      case sv if sv.startsWith("2.10") =>
        old diff List("-Xfuture", "-Ywarn-unused", "-Ywarn-unused-import")
      case sv if sv.startsWith("2.11") => old ++ List("-Ywarn-unused", "-Ywarn-unused-import")
      case _                           => old ++ List("-Ywarn-unused", "-Ywarn-unused-import", "-YdisableFlatCpCaching")
    }
  },
  scalacOptions in console in Compile -= "-Ywarn-unused-import",
  scalacOptions in console in Test -= "-Ywarn-unused-import",
  publishArtifact in Compile := true,
  publishArtifact in Test := false
)

val mimaSettings = Def settings (
  mimaPreviousArtifacts := Set(
    "1.0.0", "1.0.1", "1.0.2", "1.0.3",
    "1.1.0", "1.1.1", "1.1.2", "1.1.3",
    "1.2.0",
  ) map (version =>
    organization.value %% moduleName.value % version
      cross (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
  ),
)

lazy val utilRoot: Project = (project in file("."))
  .aggregate(
    utilInterface,
    utilControl,
    utilPosition,
    utilLogging,
    utilRelation,
    utilCache,
    utilTracking,
    utilScripted
  )
  .settings(
    inThisBuild(
      Seq(
        git.baseVersion := "1.2.1",
        version := {
          val v = version.value
          if (v contains "SNAPSHOT") git.baseVersion.value + "-SNAPSHOT"
          else v
        },
        bintrayPackage := "util",
        homepage := Some(url("https://github.com/sbt/util")),
        description := "Util module for sbt",
        scmInfo := Some(ScmInfo(url("https://github.com/sbt/util"), "git@github.com:sbt/util.git")),
        scalafmtOnCompile in Sbt := false,
      )),
    commonSettings,
    name := "Util Root",
    publish := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publishArtifact in Test := false,
    publishArtifact := false,
    customCommands
  )

// defines Java structures used across Scala versions, such as the API structures and relationships extracted by
//   the analysis compiler phases and passed back to sbt.  The API structures are defined in a simple
//   format from which Java sources are generated by the datatype generator Projproject
lazy val utilInterface = (project in internalPath / "util-interface").settings(
  commonSettings,
  javaOnlySettings,
  name := "Util Interface",
  exportJars := true,
  mimaSettings,
)

lazy val utilControl = (project in internalPath / "util-control").settings(
  commonSettings,
  name := "Util Control",
  mimaSettings,
)

val utilPosition = (project in file("internal") / "util-position")
  .settings(
    commonSettings,
    name := "Util Position",
    scalacOptions += "-language:experimental.macros",
    libraryDependencies ++= Seq(scalaReflect.value, scalaTest),
    mimaSettings,
  )

lazy val utilLogging = (project in internalPath / "util-logging")
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(utilInterface)
  .settings(
    commonSettings,
    crossScalaVersions := Seq(scala210, scala211, scala212),
    name := "Util Logging",
    libraryDependencies ++=
      Seq(jline, log4jApi, log4jCore, disruptor, sjsonnewScalaJson.value, scalaReflect.value),
    libraryDependencies ++= Seq(scalaCheck, scalaTest),
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := { tpe =>
      val old = (contrabandFormatsForType in generateContrabands in Compile).value
      val name = tpe.removeTypeParameters.name
      if (name == "Throwable") Nil
      else old(tpe)
    },
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[DirectMissingMethodProblem]("sbt.internal.util.SuccessEvent.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.TraceEvent.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.StringEvent.copy*"),
    ),
  )
  .configure(addSbtIO)

lazy val utilRelation = (project in internalPath / "util-relation")
  .settings(
    commonSettings,
    name := "Util Relation",
    libraryDependencies ++= Seq(scalaCheck),
    mimaSettings,
  )

// Persisted caching based on sjson-new
lazy val utilCache = (project in file("util-cache"))
  .settings(
    commonSettings,
    name := "Util Cache",
    libraryDependencies ++=
      Seq(sjsonnewScalaJson.value, sjsonnewMurmurhash.value, scalaReflect.value),
    libraryDependencies ++= Seq(scalaTest),
    mimaSettings,
  )
  .configure(addSbtIO)

// Builds on cache to provide caching for filesystem-related operations
lazy val utilTracking = (project in file("util-tracking"))
  .dependsOn(utilCache)
  .settings(
    commonSettings,
    name := "Util Tracking",
    libraryDependencies ++= Seq(scalaTest),
    mimaSettings,
  )
  .configure(addSbtIO)

lazy val utilScripted = (project in internalPath / "util-scripted")
  .dependsOn(utilLogging, utilInterface)
  .settings(
    commonSettings,
    name := "Util Scripted",
    libraryDependencies ++= {
      scalaVersion.value match {
        case sv if sv startsWith "2.11" => Seq(parserCombinator211)
        case sv if sv startsWith "2.12" => Seq(parserCombinator211)
        case _                          => Seq()
      }
    },
    mimaSettings,
  )
  .configure(addSbtIO)

def customCommands: Seq[Setting[_]] = Seq(
  commands += Command.command("release") { state =>
    // "clean" ::
    "+compile" ::
      "+publishSigned" ::
      "reload" ::
      state
  }
)

inThisBuild(Seq(
  whitesourceProduct                   := "Lightbend Reactive Platform",
  whitesourceAggregateProjectName      := "sbt-util-master",
  whitesourceAggregateProjectToken     := "b9b11b2f43d34c44b28d8922624eef07a3f1b20d95ad45a5b5d973513ab173f4",
  whitesourceIgnoredScopes             += "scalafmt",
  whitesourceFailOnError               := sys.env.contains("WHITESOURCE_PASSWORD"), // fail if pwd is present
  whitesourceForceCheckAllDependencies := true,
))
