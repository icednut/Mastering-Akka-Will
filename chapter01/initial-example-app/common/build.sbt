name := "initial-example-common"

lazy val akkaVersion = "2.6.3"
lazy val unfilteredVersion = "0.10.0-M6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "com.typesafe.slick" %% "slick" % "3.3.2",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2",
  "ws.unfiltered" %% "unfiltered-filter" % unfilteredVersion,
  "ws.unfiltered" %% "unfiltered-netty" % unfilteredVersion,
  "ws.unfiltered" %% "unfiltered-netty-server" % unfilteredVersion,
  "ws.unfiltered" %% "unfiltered-json4s" % unfilteredVersion,
  "org.json4s" %% "json4s-ext" % "3.6.7",
  "org.postgresql" % "postgresql" % "42.2.10",
  "org.dispatchhttp" %% "dispatch-core" % "1.1.2"
)