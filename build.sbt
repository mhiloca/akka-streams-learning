name := "AkkaStreams"

version := "0.1"

scalaVersion := "2.13.3"

lazy val akkaVersion = "2.6.10"
lazy val scalaTestVersion = "3.2.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
)

