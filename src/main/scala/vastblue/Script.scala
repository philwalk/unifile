package vastblue

import vastblue.Platform.{_propOrElse, _propOrEmpty}
import vastblue.file.Paths

import java.nio.file.Path
import vastblue.util.PathExtensions.*

object Script {
  private lazy val verby: String = Option(System.getenv("SCRIPT_VERBY")).getOrElse("")
  private def verbyFlag = verby.nonEmpty

  def searchStackTrace(e: Exception = new Exception()): StackTraceElement = {
    val stackList: List[StackTraceElement] = e.getStackTrace.toList
    val relevant: StackTraceElement = stackList.dropWhile( (e: StackTraceElement) =>
      val s = e.toString
      s.contains("unifile.") ||
      s.contains("vastblue.Script.scala") ||
      s.contains("vastblue.ArgsUtil.scala") ||
      s.contains("vastblue.MainArgs") ||
      s.contains("vastblue.util.") ||
      s.contains("scalatest") ||
      s.contains("junit")
    ).take(1) match {
      case Nil =>
        stackList.last
      case head :: tail =>
        head
    }
    relevant
  }

  def scriptNameSources = Seq(
    _propOrEmpty("script.path"),
    Script.searchStackTrace(new Exception()),
  )
  def stackToClassName(e: StackTraceElement): String = {
    e.getClassName
  }

//  def stackFilePath(e: StackTraceElement): String = {
//    e.filePath
//  }
  def _scriptPath: String = {
    val scriptPath = sys.props("script.path")
    if (Option(scriptPath).nonEmpty) {
      scriptPath
    } else {
      _scriptProp.filePath
    }
  }
  def scriptName: String  = _scriptPath match {
  case "" | "MainArgs.scala" | "mainargs.scala" | "Script.scala" =>
    scriptProp().filePath.posx
  case name =>
    name.posx
  }
  extension(e: StackTraceElement) {
    def filePath: String = {
      val fname = e.getFileName
      val class2path = e.getClassName.replace('.', '/')
      class2path.replaceFirst("[^/]*$", fname)
    }
  }
  def _scriptProp: StackTraceElement = Script.searchStackTrace(new Exception())
  def stackElementFilePath: String = _scriptProp.filePath
  def stackElementClassName: String = _scriptProp.getClassName

  // TODO: this works if running from a script, but need to gracefully do something
  // otherwise.  If executing from a jar file, read manifest to get main class
  // else if running in an IDE, pretend that main class name is script name.
  lazy val scriptPath: Path = {
    val rp = Paths.get(scriptName).relpath
    java.nio.file.Paths.get(rp.relativePath)
  }

  def scalaScriptFile: Path = scriptPath
  def scriptFile = scalaScriptFile

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
    System.err.print(fmt.format(xs *))
  }
  def _eprint(xs: Any*): Unit = {
    System.err.print("%s".format(xs *))
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
