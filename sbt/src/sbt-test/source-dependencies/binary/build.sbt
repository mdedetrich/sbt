ThisBuild / scalaVersion := "2.12.8"

lazy val dep = project

lazy val use = project.
  settings(
    unmanagedJars in Compile += (packageBin in (dep, Compile) map Attributed.blank).value
  )
