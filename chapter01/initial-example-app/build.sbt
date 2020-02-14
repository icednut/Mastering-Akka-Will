name := "initial-example-app"

lazy val commonSettings = Seq(
  organization := "com.packt.masteringakka",
  version := "0.1.0",
  scalaVersion := "2.13.1"
)

lazy val root = (project in file(".")).
  aggregate(common, server)

lazy val common = (project in file("common")).settings(commonSettings: _*)

lazy val server = {
  Project(
    id = "server",
    base = file("server")
  ).settings(
    commonSettings ++ Seq(
      mainClass in Compile := Some("com.packt.masteringakka.bookstore.server.Server")
    )
  ).dependsOn(common)
}