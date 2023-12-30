package vastblue

//import vastblue.Platform
//import vastblue.Platform.*
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
      s.startsWith("vastblue.") ||
      s.contains("Script.scala") ||
      s.contains("ArgsUtil.scala")
    ).take(1) match {
      case Nil =>
        stackList.last
      case head :: tail =>
        head
    }
    relevant
  }

  /*
  def getScriptProp(e: Exception = new Exception()): String = {
    def stackList: Seq[String] = e.getStackTrace.toIndexedSequence.map { _.getFileName }
    def stackHead: String   = stackList.dropWhile(_.contains("vastblue")).head
    val scrPathProp: String = _propOrElse("script.path", stackHead).path.relativePath.posx
    scrPathProp
  }
  */

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
  def _scriptPath: String = _propOrElse("script.path", _scriptProp.filePath)
  def scriptName: String  = _scriptPath match {
  case "" | "MainArgs.scala" | "mainargs.scala" | "Script.scala" =>
    scriptProp().filePath
  case name =>
    name
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

  private lazy val scriptFileDataTag = "/* _DATA_"

  def _eprintln(text: String): Unit = {
    System.err.print(text)
  }
  def _eprintf(fmt: String, xs: Any*): Unit = {
    System.err.print(fmt.format(xs: _*))
  }
  def _eprint(xs: Any*): Unit = {
    System.err.print("%s".format(xs: _*))
  }

  /**
   * Comparable to perl __DATA__ section.
   */
  def _DATA_(implicit encoding: String = "utf8"): List[String] = {
    scalaScriptFile.exists match {
    case true =>
      // var data = scalaScriptFile.getLinesIgnoreEncodingErrors(encoding).dropWhile( !_.startsWith(scriptFileDataTag) )
      // val charset = java.nio.charset.Charset.forName(encoding)
      var data = scalaScriptFile.lines.dropWhile(!_.startsWith(scriptFileDataTag)).toList
      if (data.isEmpty) {
        _eprintf("# no data section found (expecting start delimiter line: [%s]\n", scriptFileDataTag)
        List[String]() // no data section found
      } else {
        if (data.head.startsWith(scriptFileDataTag)) {
          data = data.tail
        }
        if (data.isEmpty) {
          List[String]() // empty data section
        } else {
          // val endQuote = """^\*/"""
          // toss last line, necessarily a trailing end-quote, e.g. "*/"
          data.init.toList // same as reverse.tail.reverse (discard last item)
        }
      }
    case false =>
      System.err.printf("warning: no script file [%s]\n", scalaScriptFile)
      List[String]() // no data section found
    }
  }
  def _DATA_columnRows(implicit delim: Option[String] = None): List[List[String]] = {
    script_DATA_columnRows(delim)
  }
  def script_DATA_columnRows(implicit splitDelimiter: Option[String] = None): List[List[String]] = {
    val data = _DATA_
    val delim = splitDelimiter match {
    case Some(d) => d
    case None    => """\t""" // default delimiter
    }
    // convert to List[List[String]]
    // negative limit preserves empty columns
    data.map(_.split(delim, -1).map { _.trim }.toList)
  }

  def getClassName(claz: Class[_]): String = {
    claz.getName.stripSuffix("$").replaceAll(""".*[^a-zA-Z_0-9]""", "") // delete thru the last non-identifier char
  }
  def getClassName(main: AnyRef): String = {
    getClassName(main.getClass)
  }
  lazy val thisClassName: String = getClassName(this)


  lazy val verbose = Option(System.getenv("VERBY")).nonEmpty
}
