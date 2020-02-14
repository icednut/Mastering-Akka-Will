import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerCommands

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
      mainClass in Compile := Some("com.packt.masteringakka.bookstore.server.Server"),
      dockerCommands := dockerCommands.value.filterNot {
        // ExecCmd is a case class, and args is a varargs variable, so you need to bind it with @
        case Cmd("USER", args@_*) => true
        // dont filter the rest
        case cmd => false
      },
      version in Docker := "latest",
      dockerExposedPorts := Seq(8080),
      maintainer in Docker := "mastering-akka@packt.com",
      dockerBaseImage := "java:8"
    )
  ).dependsOn(common)
    .enablePlugins(JavaAppPackaging)
}