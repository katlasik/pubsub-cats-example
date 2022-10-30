val scala3Version = "3.2.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "PubSub",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.3.14",
    libraryDependencies += "com.google.cloud" % "google-cloud-pubsub" % "1.120.22",
    libraryDependencies += "co.fs2" %% "fs2-core" % "3.3.0"
  )
