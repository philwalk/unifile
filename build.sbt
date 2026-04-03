lazy val scala3 = "3.6.4"
lazy val scalaVer = scala3

lazy val supportedScalaVersions = List(scala3)

javacOptions ++= Seq("-source", "17", "-target", "17")

//enablePlugins(ScalaNativePlugin)
//nativeLinkStubs := true
//ThisBuild / envFileName   := "dev.env" // sbt-dotenv plugin gets build environment here
ThisBuild / scalaVersion  := scalaVer

lazy val projectName = "unifile"
ThisBuild / version       := "0.4.3"
ThisBuild / versionScheme := Some("semver-spec")

ThisBuild / organization         := "org.vastblue"
ThisBuild / organizationName     := "vastblue.org"
ThisBuild / organizationHomepage := Some(url("https://vastblue.org"))

//cancelable in Global := true

parallelExecution := false

ThisBuild / scmInfo := Some(
  ScmInfo(
    url(s"https://github.com/philwalk/$projectName"),
    s"scm:git@github.com:philwalk/$projectName.git"
  )
)

ThisBuild / developers.withRank(KeyRanks.Invisible) := List(
  Developer(
    id = "philwalk",
    name = "Phil Walker",
    email = "philwalk9@gmail.com",
    url = url("https://github.com/philwalk")
  )
)

// Remove all additional repository other than Maven Central from POM
ThisBuild / publishTo := sonatypePublishToBundle.value

ThisBuild / publishMavenStyle.withRank(KeyRanks.Invisible) := true

ThisBuild / crossScalaVersions := supportedScalaVersions

// For all Sonatype accounts created on or after February 2021
ThisBuild / sonatypeCredentialHost := "https://central.sonatype.com/"

resolvers += Resolver.mavenLocal

publishTo := sonatypePublishToBundle.value

Compile / packageBin / packageOptions +=
  Package.ManifestAttributes(java.util.jar.Attributes.Name.CLASS_PATH -> "")

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    crossScalaVersions := supportedScalaVersions,
    name               := projectName,
    description        := "Support for expressive scripting",
 // mainClass          := Some("vast.apps.ShowSysProps"),
    buildInfoKeys      := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage   := "unifile", // available as "import unifile.BuildInfo"
  )

libraryDependencies ++= Seq(
  "org.scalatest"         %% "scalatest"       % "3.2.20" % Test,
)

/*
 * build.sbt
 * SemanticDB is enabled for all sub-projects via ThisBuild scope.
 * https://www.scala-sbt.org/1.x/docs/sbt-1.3-Release-Notes.html#SemanticDB+support
 */
inThisBuild(
  List(
    scalaVersion := scalaVersion.value, // 2.13.12, or 3.x
    // semanticdbEnabled := true     // enable SemanticDB
    // semanticdbVersion := scalafixSemanticdb.revision // only required for Scala 2.x
  )
)

scalacOptions := {
  Seq(
    // "-Xmaxerrs", "10",
    "-encoding",
    "utf-8",
    "-explaintypes",
    "-language:existentials",
    "-language:experimental.macros",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-deprecation",

    // Linting options
    "-unchecked"
  )
}
scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
case Some((2, n)) if n >= 13 =>
  Seq(
    "-Ytasty-reader",
    "-Xsource:3",
    "-Xmaxerrs",
    "10",
    "-Xsource:3",
    "-Xsource-features:implicit-resolution",
    "-Yscala3-implicit-resolution",
    "-language:implicitConversions",
  )
case _ =>
  Nil
})

// key identifier, otherwise this field is ignored; passwords supplied by pinentry
credentials += Credentials(
  "GnuPG Key ID",
  "gpg",
  "1CF370113B7EE5A327DD25E7B5D88C95FC9CB6CA", // key identifier
  "ignored",
)

val credFile = Path.userHome / ".sonatype_credentials"
credentials ++= (
  if (credFile.exists) Seq(Credentials(credFile))
  else Seq(Credentials(
    "Sonatype Nexus Repository Manager",
    "central.sonatype.com",
    sys.env.getOrElse("SONATYPE_USERNAME", ""),
    sys.env.getOrElse("SONATYPE_PASSWORD", "")
  ))
)

// Set this to the same value set as your credential files host.
sonatypeCredentialHost := "central.sonatype.com"
sonatypeRepository := "https://central.sonatype.com/service/local"


