// https://github.com/sbt/sbt/issues/1730
lazy val check = taskKey[Unit]("Runs the check")
val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"
val junit = "junit" % "junit" % "4.13.1"

ThisBuild / scalaVersion := "2.12.18"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

lazy val transitiveTest = project.
  settings(
    commonSettings,
    libraryDependencies += junit % Test
  )

lazy val transitiveTestDefault = project.
  settings(
    commonSettings,
    libraryDependencies += scalatest
  )

lazy val a = project.
  dependsOn(transitiveTestDefault % Test, transitiveTest % "test->test").
  settings(commonSettings)

lazy val root = (project in file(".")).
  aggregate(a).
  settings(inThisBuild(Seq(
    organization := "org.example",
    version := "1.0",
    updateOptions := updateOptions.value.withCachedResolution(true),
    check := {
      val ur = (update in a).value
      val acp = (externalDependencyClasspath in Compile in a).value.map {_.data.getName}
      val atestcp0 = (fullClasspath in Test in a).value
      val atestcp = (externalDependencyClasspath in Test in a).value.map {_.data.getName}
      // This is checking to make sure interproject dependency works
      if (acp exists { _ contains "scalatest" }) {
        sys.error("scalatest found when it should NOT be included: " + acp.toString)
      }
      // This is checking to make sure interproject dependency works
      if (!(atestcp exists { _ contains "scalatest" })) {
        sys.error("scalatest NOT found when it should be included: " + atestcp.toString)
      }
      // This is checking to make sure interproject dependency works
      if (!(atestcp exists { _ contains "junit" })) {
        sys.error("junit NOT found when it should be included: " + atestcp.toString)
      }
    }
  )))
