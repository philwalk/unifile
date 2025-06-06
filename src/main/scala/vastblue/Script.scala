package vastblue

import vastblue.Platform.{_propOrElse, _propOrEmpty}
import vastblue.file.Paths

import java.nio.file.Path
import vastblue.util.PathExtensions.*

object Script {
  private def verby: Boolean = {
    def verbyVar: String = Option(System.getenv("SCRIPT_VERBY")).getOrElse("")
    verbyVar.nonEmpty
  }

  import scala.jdk.CollectionConverters.*
  def classNames: Set[String] = Thread.getAllStackTraces.keySet().asScala
    .flatMap(_.getStackTrace.map(_.getClassName))
    .toSet

  lazy val scalaCliScript = {
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
        Option(sys.props("script.path")).getOrElse("")
      }
    }
  }

  def mainProgramPath: String = {
    if (scalaCliScript.nonEmpty) {
      if (java.nio.file.Files.isRegularFile(Paths.get(scalaCliScript))) {
        scalaCliScript
      } else {
        val scrip = where(scalaCliScript)
        scrip
      }
    } else {
      Option(sys.props("script.path")).getOrElse("")
    }
  }

  private val isWindows: Boolean = System.getProperty("os.name").toLowerCase.contains("win")
  private lazy val finder = if (isWindows) "where.exe" else "which"
  private def where(progname: String): String = {
    import scala.sys.process.*
    var (stdout, stderr) = (Vector.empty[String], Vector.empty[String])
    val cmd = Seq(finder, scalaCliScript)
    val status = cmd ! ProcessLogger(stdout :+= _.trim, stderr :+= _.trim)
    val cliScriptPath = stdout.take(1).mkString.trim.replace('\\', '/') match {
      case "" => progname
      case str => str
    }
    cliScriptPath
  }

  private val propsScriptPath = _propOrElse("script.path", "")

  // produce `scriptPath` or equivalent in various contexts:
  //    scala-cli script
  //    legacy scriptQ
  //    IDE debugger session
  def guessMainClass: String = {
    if (mainProgramPath.nonEmpty) {
      mainProgramPath
    } else if (propsScriptPath.nonEmpty){
      propsScriptPath
    } else if (mainFromStack.nonEmpty) {
      mainFromStack
    } else {
      "unknownMainClass"
    }
  }

  def scriptNameSources = Seq(
    _propOrElse("script.path", mainFromStack),
    scalaCliScript
  )
  private def mainFromStack: String = {
    // might return empty string?
    val result = new java.io.StringWriter()
    new RuntimeException("stack").printStackTrace(new java.io.PrintWriter(result))
    val stack = result.toString.split("[\r\n]+").toList
    // if (verbose ){ for( s <- stack) { printf("[%s]\n",s) } }
    stack.filter { str => str.contains(".main(") }.map {
      _.replaceAll(".*[(]","").
      replaceAll("[:)].*","")
    }.distinct.take(1).mkString("")
  }
  def stackToClassName(e: StackTraceElement): String = {
    e.getClassName
  }

  def _scriptPath: String = {
    guessMainClass
  }
  def scriptName: String = _scriptPath match {
  case "" | "MainArgs.scala" | "mainargs.scala" | "Script.scala" =>
    guessMainClass.path.posx
  case name =>
    name.posx
  }

  // TODO: this works if running from a script, but need to gracefully do something
  // otherwise.  If executing from a jar file, read manifest to get main class
  // else if running in an IDE, pretend that main class name is script name.
  lazy val scriptPath: Path = {
    val rp = Paths.get(scriptName).relpath
    java.nio.file.Paths.get(rp.relativePath)
  }

  def scalaScriptFile: Path = scriptPath
  def scriptFile            = scalaScriptFile

  // scriptName, or legal fully qualified class name (must include package)
  def validScriptOrClassName(s: String): Boolean = {
    val validScript             = s.posx.matches("[./a-zA-Z_0-9$]+")
    def validMainClass: Boolean = s.posx.matches("([a-zA-Z_$][a-zA-Z_$0-9]*[.]) {1,}[a-zA-Z_$0-9]+")
    val notMgr: Boolean         = s != "dotty.tools.MainGenericRunner"
    notMgr && (validScript || validMainClass)
  }

  // def progName = scriptPath.name

  def stripPackagePrefix(fullname: String): String = fullname.replaceAll(""".*[^a-zA-Z_0-9]""", "")

  def fullClassname(ref: AnyRef): String = ref.getClass.getName.stripSuffix("$")
  def bareClassname(ref: AnyRef): String = stripPackagePrefix(fullClassname(ref))

  def _eprintln(text: String): Unit = {
    System.err.print(text)
  }
  def _eprintf(fmt: String, xs: Any*): Unit = {
    System.err.print(fmt.format(xs*))
  }
  def _eprint(xs: Any*): Unit = {
    System.err.print("%s".format(xs*))
  }

  def getClassName(claz: Class[?]): String = {
    claz.getName.stripSuffix("$").replaceAll(""".*[^a-zA-Z_0-9]""", "") // delete thru the last non-identifier char
  }
  def getClassName(main: AnyRef): String = {
    getClassName(main.getClass)
  }
  lazy val thisClassName: String = getClassName(this)

  lazy val verbose = Option(System.getenv("VERBY")).nonEmpty
}
