// Configuração para publicação da biblioteca
ThisBuild / organization := "com.htc"
ThisBuild / scalaVersion := "3.3.6"
ThisBuild / version := "0.1.0-SNAPSHOT"

// Metadados da biblioteca
ThisBuild / homepage := Some(url("https://github.com/htc/htc-dl"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/htc/htc-dl"),
    "scm:git@github.com:htc/htc-dl.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "htc-team",
    name = "HTC Development Team",
    email = "dev@htc.com",
    url = url("https://htc.com")
  )
)

ThisBuild / licenses := List(
  "Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")
)

lazy val root = (project in file("."))
  .settings(
    name := "htc-dl",
    description := "HTC Digital Twin Language Core Library - Parser and validator for HTCDL models",
    
    libraryDependencies ++= Seq(
      // JSON processing
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",
      
      // Validation
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",
      
      // Testing
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "org.scalacheck" %% "scalacheck" % "1.17.0" % Test,
      
      // URI handling
      "org.apache.commons" % "commons-lang3" % "3.13.0"
    ),
    
    // Configurações de compilação
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
      "-language:higherKinds",
      "-explain"
    ),
    
    // Configurações de teste
    Test / parallelExecution := false,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
    
    // Configurações de JAR
    Compile / packageBin / packageOptions += Package.ManifestAttributes(
      "Implementation-Title" -> name.value,
      "Implementation-Version" -> version.value,
      "Implementation-Vendor" -> organization.value
    ),
    
    // Documentação
    Compile / doc / scalacOptions ++= Seq(
      "-project", name.value,
      "-project-version", version.value
    )
  )

// Configurações para publicação
publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true
Test / publishArtifact := false
pomIncludeRepository := { _ => false }