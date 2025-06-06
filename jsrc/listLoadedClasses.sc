#!/usr/bin/env -S scala-cli shebang
//package vast.apps

//> using jvm 17
//> using scala 3.6.4
//> using dep org.vastblue::unifile:0.4.1

import vastblue.unifile.*

object ListLoadedClasses {
  private def verby: Boolean = {
    def verbyVar: String = Option(System.getenv("SCRIPT_VERBY")).getOrElse("")
    verbyVar.nonEmpty
  }

  val isWindows: Boolean = System.getProperty("os.name").toLowerCase.contains("win")

  import scala.jdk.CollectionConverters.*
  lazy val classNames: Set[String] = Thread.getAllStackTraces.keySet().asScala
    .flatMap(_.getStackTrace.map(_.getClassName))
    .toSet

  def stackHeadFilename: String = new Exception().getStackTrace.head.getFileName

  lazy val scalaCliScript = {
    stackHeadFilename match {
    case s if s.nonEmpty =>
      s
    case _ =>
      classNames.find { _.endsWith("$_") } match {
      case Some(name) =>
        // not always correct w.r.t. extension
        s"${name.reverse.drop(2).reverse}.sc"
      case None =>
        classNames.find { _.endsWith("_sc") } match {
        case Some(name) =>
          // not always correct w.r.t. extension
          s"${name.replaceFirst("_sc$", ".sc")}"
        case None =>
          ""
        }
      }
    }
  }

  def scalaCliScriptPath: String = {
    import java.nio.file.Files
    if (scalaCliScript.nonEmpty) {
      if (java.nio.file.Files.isRegularFile(Paths.get(scalaCliScript))) {
        scalaCliScript
      } else {
        where(scalaCliScript)
      }
    } else {
      Option(sys.props("script.path")).getOrElse("")
    }
  }

  private lazy val finder = if (isWindows) "where.exe" else "which"
  def where(progname: String): String = {
    import scala.sys.process.*
    val cliScriptPath = Seq(finder, scalaCliScript).lazyLines_!.take(1).filter { (s: String) => s.trim.nonEmpty }.mkString.replace('\\', '/')
    if (verby) System.err.printf("cliScriptPath[%s]\n", cliScriptPath)
    cliScriptPath
  }

  def main(args: Array[String]): Unit = {
    printf("scalaCliScript: [%s]\n", scalaCliScript)
    printf("scalaCliScript: [%s]\n", scalaCliScript)
    printf("scalaCliScriptPath: [%s]\n", scalaCliScriptPath)
  }
}
