scalaVersion := "2.12.19"

val SONATYPE_VERSION = sys.env.getOrElse("SONATYPE_VERSION", "3.10.0") // "3.9.21")

//addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.17")

addSbtPlugin("ch.epfl.scala"  % "sbt-bloop"     % "1.5.15")
addSbtPlugin("ch.epfl.scala"  % "sbt-scalafix"  % "0.12.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype"  % SONATYPE_VERSION)
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"  % "2.5.2")
addSbtPlugin("com.github.sbt" % "sbt-dynver"    % "5.0.1")
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo" % "0.12.0")
addSbtPlugin("com.github.sbt" % "sbt-pgp"       % "2.2.1")

addDependencyTreePlugin

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

//resolvers += Resolver.sonatypeRepo("snapshots")
resolvers ++= Resolver.sonatypeOssRepos("snapshots")
resolvers ++= Resolver.sonatypeOssRepos("releases")
