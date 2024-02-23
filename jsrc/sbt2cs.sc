#!/usr/bin/env -S scala
//package vast.apps

import vastblue.unifile.*

/*
 * Options:
 *   + fetch and list dependency file paths
 *   + convert sbt dependencies to scala-cli "using dep" format
 *
 * Example conversion from sbt dependency format to scala-cli lib format:
 *
 * from:
 *   org.vastblue             %% unifile       % 0.3.1
 *   org.vastblue             %% pallet        % 0.10.7
 *   org.scalanlp             %% breeze-viz    % 2.1.0
 *   org.scalanlp             %% breeze        % 2.1.0
 *   org.scala-lang.modules   %% scala-xml     % 2.2.0
 *   org.scala-lang.modules   %% scala-swing   % 3.0.0
 *   net.ruippeixotog         %% scala-scraper % 3.1.1
 *   dev.ludovic.netlib       % blas           % 3.0.3
 *   com.github.fommil.netlib % all            % 1.1.2
 *   com.github.darrenjw      %% scala-glm     % 0.8
 *
 * to:
 *   //> using dep "org.vastblue::pallet::0.10.7"
 *   //> using dep "org.vastblue::unifile::0.3.1"
 *   //> using dep "org.scalanlp::breeze-viz::2.1.0"
 *   //> using dep "org.scalanlp::breeze::2.1.0"
 *   //> using dep "org.scala-lang.modules::scala-xml::2.2.0"
 *   //> using dep "org.scala-lang.modules::scala-swing::3.0.0"
 *   //> using dep "net.ruippeixotog::scala-scraper::3.1.1"
 *   //> using dep "dev.ludovic.netlib:blas:3.0.3"
 *   //> using dep "com.github.fommil.netlib:all:1.1.2"
 *   //> using dep "com.github.darrenjw::scala-glm::0.8"
*/
object Sbt2cs {
  def usage(m: String=""): Nothing = {
    _usage(m, Seq(
      "[<inputFile>]",
     s"[-sbt [<sbt-sourcefile>]   ; default: ${defaultSbtSource}",
     s"[-cli]                     ; convert sbt deps to scala-cli format",
     s"[-fetch]                   ; fetch and list lib jars",
    ))
  }
  var (op, inputFile) = ("", "")

  def main(args: Array[String]): Unit = {
    try {
      parseArgs(args.toSeq)
      val entries = if (inputFile.nonEmpty && inputFile.path.isFile) {
        val infile = inputFile.path
        val cleaned = extractDeps(infile)
        preProcessDeps(cleaned)
      } else {
        preProcessDeps(testData)
      }
      val deps = entries.map { SbtDep(_) }
      op match {
      case "" | "-fetch" =>
        for (dep <- deps) {
          for (line <- fetch(dep)) {
            printf("%s\n", line.posx)
          }
        }
      case "-cli" =>
        for (line <- deps) {
          printf("%s\n", asDep(line))
        }
      }
    } catch {
    case t: Throwable =>
      showLimitedStack(t)
      sys.exit(1)
    }
  }

  lazy val testData = """
org.scalanlp             %% breeze        % 2.1.0
org.scala-lang.modules   %% scala-xml     % 2.2.0
org.scalanlp             %% breeze        % 2.1.0
org.scalanlp             %% breeze-viz    % 2.1.0
dev.ludovic.netlib       % blas           % 3.0.3
com.github.fommil.netlib % all            % 1.1.2
com.github.darrenjw      %% scala-glm     % 0.8
org.scala-lang.modules   %% scala-swing   % 3.0.0
net.ruippeixotog         %% scala-scraper % 3.1.1
org.vastblue             %% unifile       % 0.3.1
org.vastblue             %% pallet        % 0.10.7
""".trim.split("[\r\n]+").toList.filter { _.nonEmpty }

  def parseArgs(args: Seq[String]): Unit = {
    eachArg(args, usage) {
    case "-cli" | "-fetch" =>
      op = thisArg
    case "-sbt" =>
      if (peekNext.nonEmpty && !peekNext.startsWith("-") ) {
        inputFile = consumeNext
        if (!inputFile.path.isFile) {
          usage(s"not found: ${inputFile}")
        }
      } else {
        inputFile = defaultSbtSource
      }
    case f if !f.startsWith("-") && f.path.isFile =>
      inputFile = f
    case arg =>
      usage(s"unrecognized arg [$arg]")
    }
  }

  val defaultSbtSource = "/opt/ue/project/Dependencies.scala"

  lazy val cs: String = find("cs")
  lazy val repos = Seq(
    "https://artifacts.alfresco.com/nexus/content/repositories/public/", // ldtp
    "https://mvnrepository.com/artifact/com.snowtide",
    "http://maven.snowtide.com/releases",
  ).mkString("|")

  def fetch(dep: SbtDep): Seq[String] = {
    val depstr = dep.toString
    if (dep.line.contains("ldtp")) {
      val cmd = s"$cs fetch $depstr"
      shellExec(cmd, Map("COURSIER_REPOSITORIES" -> repos))
    } else {
      execLines("cs", "fetch", depstr)
    }
  }

  def asDep(image: SbtDep): String = {
    s"//> using dep \"$image\""
  }

  case class SbtDep(_line: String) {
    def doublePercent = _line.contains("%%")
    val line = _line.replaceAll("[,\"]+", "")
    val fields = line.split(" *%+ *").toList
    val image = if (doublePercent) {
      fields.mkString("::")
    } else {
      fields.mkString(":")
    }
    override def toString = image // s"//> using dep \"$image\""
  }

  def linesWithoutComments(p: Path): Seq[String] = {
    var worklines = p.lines.map { line =>
      // avoid treating https://blah-blah as containing // comment
      s" $line"
        .replaceAll("[^:]//.*", "")
        .replaceAll(" *(test|withSources).*", "")
        .replaceAll("[()\"]+", "")
        .trim
    }.filter { test =>
      (test.contains("%") || test.trim.matches("^lazy val.*[0-9]")) &&
      !test.contains("printf") &&
      !test.contains("::Test") &&
      !test.contains("scalacheck") &&
      !test.contains("scalatest") &&
      !test.contains("junit")
    }
    worklines
  }

  def extractDeps(infile: Path): Seq[String] = {
    val filtered = linesWithoutComments(infile)
    var (lazyvals, lines) = filtered.partition( _.startsWith("lazy val") )

    var lazyvalMap = Map.empty[String, String]
    lazyvals.foreach { (line: String) =>
      val Seq(name, value) = line.split(" *= *").map { _.replaceAll("^lazy val *", "").replaceAll("[\"()]+", "") }.toSeq
      // printf("[%s], [%s]\n", name, value)
      lazyvalMap += (name -> value)
    }
    val valnames = lazyvalMap.keySet
    def applyMap(line: String): String = {
      val mapKey = valnames.find( (name: String) => line.contains(name) ).getOrElse("")
      if (mapKey.nonEmpty) {
        val mapValue = lazyvalMap(mapKey)
        line.replace(mapKey, mapValue)
      } else {
        line
      }
    }

    val cleaned = for {
      line <- lines
      fixed = applyMap(line)
    } yield fixed
    cleaned
  }

  def preProcessDeps(cleaned: Seq[String]): Seq[String] = {
    val sorted = cleaned.sortWith ((a, b) =>
      if (a.contains("unifile")) true else
      if (a.contains("pallet")) true else
      if (a.contains("vast")) true else
      if (a.contains("apps")) true else
      if (b.contains("unifile")) false else
      if (b.contains("pallet")) false else
      if (b.contains("vast")) false else
      if (a.contains("apps")) false else
      a > b // reverse (unifile before pallet)
    )
    sorted.distinct
  }
}
