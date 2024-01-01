package vastblue.util

import vastblue.{MainArgs, Platform, Script}
import vastblue.Platform.{DefaultCharset, _exec, readLines, relativize, standardizePath, toRealPath}
import vastblue.file.Util.dummyFilter
import vastblue.file.{Paths, Util}
//import vastblue.time.TimeDate.now

import java.io.File as JFile
import java.io.{FileWriter, OutputStreamWriter, PrintWriter}
import java.nio.file.Path
import java.nio.file.{Files as JFiles, Paths as JPaths}
import scala.collection.mutable.Map as MutMap
import java.nio.charset.Charset
import scala.jdk.CollectionConverters.*

/*
 * Supported environments:
 * Unix / Linux / OSX, Windows shell environments (CYGWIN64, MINGW64,
 * MSYS2, GIT-BASH, etc.).
 */
object PathExtensions extends PathExtensions {
}

trait PathExtensions {
  private var hook = 0
  type PrintWriter = java.io.PrintWriter
  type JFile       = java.io.File
  type Path        = java.nio.file.Path
  def Paths        = vastblue.file.Paths
//def today        = now

  def osType: String      = Platform._osType
  def osName: String      = Platform._osName
  def isLinux: Boolean    = Platform._isLinux
  def isWinshell: Boolean = Platform._isWinshell
  def isDarwin: Boolean   = Platform._isDarwin
  def isWsl: Boolean      = Platform._unameLong.contains("WSL")

  def isCygwin: Boolean  = Platform._isCygwin
  def isMsys: Boolean    = Platform._isMsys
  def isMingw: Boolean   = Platform._isMingw
  def isGitSdk: Boolean  = Platform._isGitSdk
  def isGitbash: Boolean = Platform._isGitbash
  def isWindows: Boolean = Platform._isWindows // sometimes useful

  def scalaHome: String = Platform._scalaHome
  def javaHome: String  = Platform._javaHome
  def userhome: String  = Platform._userhome
  def verbose: Boolean  = Platform._verbose
  def username: String  = Platform._username
  def hostname: String  = Platform._hostname

  def uname: (String) => String = Platform._uname

  def cygpath(exename: String, args: String*): String = Platform._cygpath(exename, args: _*)

  def unameLong: String  = Platform._unameLong
  def unameShort: String = Platform._unameShort
  def shellRoot: String  = Platform._shellRoot

//  def today     = now
//  def yesterday = now - 1.days

  def eprintf(fmt: String, xs: Any*): Unit = Platform._eprintf(fmt, xs: _*)
  def oprintf(fmt: String, xs: Any*): Unit = Platform._oprintf(fmt, xs: _*)

  def envOrEmpty: (String) => String         = Platform._envOrEmpty
  def envOrElse: (String, String) => String  = Platform._envOrElse
  def propOrEmpty: (String) => String        = Platform._propOrEmpty
  def propOrElse: (String, String) => String = Platform._propOrElse
  def where: (String) => String              = Platform._where
  def exec(args: String*): String            = Platform._exec(args: _*)

  def execLines(args: String*): LazyList[String] = Platform._execLines(args: _*)

  def shellExec(str: String): LazyList[String]                           = Platform._shellExec(str)
  def shellExec(str: String, env: Map[String, String]): LazyList[String] = Platform._shellExec(str, env)

  def pwd: Path = Platform._pwd

  // executable Paths
  def bashPath: Path  = Platform._bashPath
//  def catPath: Path   = Platform._catPath
//  def findPath: Path  = Platform._findPath
//  def whichPath: Path = Platform._whichPath
//  def unamePath: Path = Platform._unamePath
//  def lsPath: Path    = Platform._lsPath
//  def trPath: Path    = Platform._trPath
//  def psPath: Path    = Platform._psPath

  // executable Path Strings, suitable for calling exec("bash", ...)
  def bashExe: String  = Platform._bashExe
  def catExe: String   = Platform._catExe
  def findExe: String  = Platform._findExe
  def whichExe: String = Platform._whichExe
  def unameExe: String = Platform._unameExe
  def lsExe: String    = Platform._lsExe
  def trExe: String    = Platform._trExe
  def psExe: String    = Platform._psExe

  def thisArg: String                  = ArgsUtil.thisArg
  def peekNext: String                 = ArgsUtil.peekNext
  def consumeNext: String              = ArgsUtil.consumeNext
  def consumeDouble: Double            = ArgsUtil.consumeDouble
  def consumeLong: Long                = ArgsUtil.consumeLong
  def consumeInt: Int                  = ArgsUtil.consumeInt
  def consumeBigDecimal: BigDecimal    = ArgsUtil.consumeBigDecimal
  def consumeArgs(n: Int): Seq[String] = ArgsUtil.consumeArgs(n)

  def _usage(m: String, info: Seq[String]): Nothing = ArgsUtil.Usage.usage(m, info)
  
  //def scalaHome: String = Utils.scalaHome

  def scala3Version: String = Utils.scala3Version

  def eachArg: (Seq[String], String => Nothing) => (String => Unit) => Unit = ArgsUtil.eachArg _

  def walkTree(file: JFile, depth: Int = 1, maxdepth: Int = -1): Iterable[JFile] = vastblue.file.Util.walkTree(file, depth, maxdepth)

//  def filesTree(dir: JFile)(func: JFile => Boolean = Util.dummyFilter): Seq[JFile] = vastblue.file.Util.filesTree(dir.toFile)(func)

  def showLimitedStack(e: Throwable = Util.newEx): Unit = vastblue.file.Util._showLimitedStack(e)

  def scriptProp(e: Exception = new Exception()): StackTraceElement = Script.searchStackTrace(e)

//  def prepArgs(args: Seq[String]) = Script.prepArgs(args)

  def scriptPath: Path        = Script.scriptPath
  def scriptName: String      = Script.scriptName
  def thisProc: MainArgs.Proc = MainArgs.thisProc

  def prepArgv(args: Seq[String]): Seq[String] = MainArgs.prepArgv(args)

  def withFileWriter(p: Path, charset: String = "utf-8", append: Boolean = false)(func: PrintWriter => Any): Unit = {
    Util.withFileWriter(p, charset, append)(func)
  }

  def bashCommand(cmdstr: String, envPairs: List[(String, String)] = Nil): (Boolean, Int, Seq[String], Seq[String]) = {
    Platform._bashCommand(cmdstr, envPairs)
  }

  def quikDate(s: String)     = Util._quikDate(s)
  def quikDateTime(s: String) = Util._quikDateTime(s)

  def tmpDir = Seq("/f/tmp", "/g/tmp", "/tmp").find { _.path.isDirectory }.getOrElse("/tmp").path.posx

  def round(number: Double, scale: Int = 6): Double = {
    BigDecimal(number).setScale(scale, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  extension (s: String) {
    def toPath: Path    = Paths.get(s)
    def path: Path      = Paths.get(s)
    def absPath: Path   = s.toPath.toAbsolutePath.normalize
    def jpath: Path     = JPaths.get(s)
    def toFile: JFile   = Paths.get(s).toFile
    def file: JFile     = Paths.get(s).toFile
    def posx: String    = s.replace('\\', '/')
    def locl: String    = posx.replace('/', JFile.separatorChar)

    def dropSuffix: String = if (s.indexOf('.') <= 0) s else s.reverse.dropWhile(_ != '.').drop(1).reverse
  }

  extension (p: Path) {
    def name: String       = p.toFile.getName
    def basename: String   = p.toFile.getName.dropSuffix
    def lcname: String     = p.toFile.getName.toLowerCase
    def lcbasename: String = basename.toLowerCase
    def abspath: Path      = p.toAbsolutePath.normalize
    def abs: String        = p.toAbsolutePath.normalize.toString.posx
    def posx: String = p.normalize.toString match {
      case "." | "" => "."
      case ".." => ".."
      case s => s.posx
    }
    def locl: String       = p.posx.replace('/', JFile.separatorChar)

    def text: String              = p.contentAsString
    def pathFields                = p.iterator.asScala.toList
    def reversePath: String       = pathFields.reverse.mkString("/")

    //def lastModifiedTime        = whenModified(p.toFile)
    def lastModified: Long        = p.toFile.lastModified

    def lastModifiedMillisAgo: Long  = System.currentTimeMillis - p.toFile.lastModified
    def lastModSecondsAgo: Double    = (lastModifiedMillisAgo/1000L).toDouble
    def lastModMinutesAgo: Double    = lastModSecondsAgo / 60.0
    def lastModHoursAgo: Double      = lastModMinutesAgo / 60.0
    def lastModDaysAgo: Double       = round(lastModHoursAgo / 24.0)

    def lastModSeconds: Double    = lastModSecondsAgo // alias
    def lastModMinutes: Double    = lastModMinutesAgo // alias
    def lastModHours: Double      = lastModHoursAgo   // alias
    def lastModDays: Double       = lastModDaysAgo    // alias

    def lastModifiedYMD: String = {
      def lastModified = p.toFile.lastModified
      val date         = new java.util.Date(lastModified)
      val ymdHms = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
      ymdHms.format(date)
    }

    def renameViaCopy(newfile: Path, overwrite: Boolean = false): Int = {
      Util._renameViaCopy(p.toFile, newfile.toFile, overwrite)
    }

    def copyTo(destFile: Path): Int = Util.copyFile(p.file, destFile.file)

    def renameTo(s: String): Boolean = renameTo(s.path)
    def renameTo(alt: Path): Boolean = {
      p.toFile.renameTo(alt.toFile)
    }
    def renameTo(alt: JFile): Boolean = {
      p.toFile.renameTo(alt)
    }
    def isEmpty: Boolean    = p.toFile.isEmpty
    def nonEmpty: Boolean   = p.toFile.nonEmpty
    def canRead: Boolean    = p.toFile.canRead
    def canExecute: Boolean = p.toFile.canExecute


    //def weekDay: java.time.DayOfWeek = {
    //  p.lastModifiedTime.getDayOfWeek
    //}

    def subdirs: Seq[Path]  = p.paths.filter { _.isDirectory }
    def subfiles: Seq[Path] = p.paths.filter { _.isFile }

    def filesTree: Seq[JFile] = Util.filesTree(p.toFile)(dummyFilter)
    def pathsTree: Seq[Path]  = filesTree.map { _.toPath }

    def charsetAndContent: (Charset, String) = vastblue.file.Util.charsetAndContent(p)

    def linesWithCharset(charset: String = Util.DefaultEncoding): Seq[String] = {
      Util.linesCharset(p, Charset.forName(charset))
    }

    def linesAnyEncoding: Seq[String] = Util.readLinesAnyEncoding(p)

    def trimmedLines: Seq[String] = readLines(p) // alias

    def trimmedSql: Seq[String] =
      linesWithCharset().map { _.replaceAll("\\s*--.*", "") }.filter { _.trim.length > 0 }

    def mkdirs: Boolean = {
      val dir = JFiles.createDirectories(p)
      dir.toFile.isDirectory
    }

    def stdpath: String    = Platform.standardizePath(p.toAbsolutePath.normalize.toString.replace('\\', '/'))
    def dospath: String    = localpath.replace('/', '\\')

    def relpath: Path        = Util.relativize(p) // Platform?
    def relativePath: String = relpath.toString.posx
   
    // TODO: this is a jumble of overrides
    def getParent: String    = p.toFile.getParent // overrides java.nio.file.Path.getName, with different return type!
    def getParentFile: JFile = p.toFile.getParentFile
    def parentFile: JFile    = getParentFile
    def parentPath: Path     = parentFile.toPath // could use Path#getParent, but for override above
    def parent: Path         = parentPath

    def noDrive: String      = p.posx match {
    case s if s.take(2).endsWith(":") => s.drop(2)
    case s                            => s
    }
    def toFile: JFile = p.toFile
    def file: JFile   = p.toFile
    def getContentAsString(charset: Charset = DefaultCharset): String = {
      val posx = p.posx
      if (posx.startsWith("/proc")) {
        _exec("cat", posx)
      } else {
        new String(byteArray, charset)
      }
    }
    def contentAsString: String = getContentAsString()

    def length: Long  = p.toFile.length

    def isDirectory: Boolean = p.toFile.isDirectory
    def isFile: Boolean      = p.toFile.isFile
    def realpath: Path       = vastblue.file.Util._realpath(p)
    def exists: Boolean      = JFiles.exists(p) // p.toFile.exists()

    def isSymbolicLink: Boolean = JFiles.isSymbolicLink(p)

    def extension: Option[String] = p.toFile.extension
    def suffix: String            = dotsuffix.dropWhile((c: Char) => c == '.')
    def dotsuffix: String         = p.toFile.dotsuffix
    def lcsuffix: String          = suffix.toLowerCase


    // if p.files is called on a non-Directory, it returns Seq(p)
    def files: Seq[JFile]  = p.toFile.files
    def paths: Seq[Path]   = p.toFile.files.map { _.toPath }
    def lines: Seq[String] =  Platform.readLines(p).toSeq // JFiles.readAllLines(p).asScala.toSeq

    def delete() = p.toFile.delete()
    def byteArray: Array[Byte] = {
      val pstr = p.stdpath
      if (pstr.startsWith("/proc/")) {
        val str = exec("cat", "-v", pstr)
        str.getBytes
      } else {
        JFiles.readAllBytes(p)
      }
    }
    def realPath: Path         = toRealPath(p)
    def realpathLs: Path = { // ask ls what symlink references
      Platform._exec("ls", "-l", p.posx).split("\\s+->\\s+").toList match {
      case a :: b :: Nil => b.path
      case _             => p
      }
    }
    def cksum: Long          = vastblue.file.Util.cksum(p)
    def segments: Seq[Path]  = Utils.segments(p)
    def canExist: Boolean    = Platform.canExist(p)

 // def localpath: String = posx.replace('/', JFile.separatorChar)
    def localpath: String = osType match {
    case "windows" =>
      val pstr = p.toString.posx
      val pclean = pstr.take(1) match {
      case "/" | "." =>
        p.normalize.toString.posx
      case s =>
        pstr
      }
      pclean match {
      case "" =>
        "."
      case s if s.startsWith("/") =>
        Util.cygpath2driveletter(pclean.posx)
      case _ =>
        pclean
      }
    case _ =>
      p.toString.posx
    }

  
    def dateSuffix: String = {
      lcbasename match {
      case Util.DatePattern1(_, yyyymmdd, _) =>
        yyyymmdd
      case Util.DatePattern2(_, yyyymmdd) =>
        yyyymmdd
      case _ =>
        ""
      }
    }
    
    // useful for examining shebang line
    def firstline: String = Util.readLines(p).take(1).mkString("")
    def cksumNe: Long  = Util.cksumNe(p)
    def md5: String    = Util.fileChecksum(p, algorithm = "MD5")
    def sha256: String = Util.fileChecksum(p, algorithm = "SHA-256")

    def guessEncoding: String   = guessCharset.toString
    def guessCharset: Charset = {
      val (charset, _) = vastblue.file.Util.charsetAndContent(p)
      charset
    }
    def diff(other: Path): Seq[String] = Util.diffExec(p.toFile, other.toFile)

    def withWriter(charsetName: String = Util.DefaultEncoding, append: Boolean = false)(
        func: PrintWriter => Any
    ): Unit = {
      Util.withPathWriter(p, charsetName, append)(func)
    }
  }
  
  extension (f: JFile) {
    def path: Path      = f.toPath
    def realfile: JFile = path.realpath.toFile
    def name: String    = f.getName
    def lcname: String  = f.getName.toLowerCase
    def posx: String    = f.path.posx
    def abspath: Path   = f.toPath.toAbsolutePath.normalize
    def abs: String     = f.toPath.toAbsolutePath.posx

    def basename: String          = f.getName.dropSuffix
    def lcbasename: String        = basename.toLowerCase

    def isDirectory: Boolean    = f.isDirectory
    def isFile: Boolean         = f.isFile
    def exists: Boolean         = f.exists
    def isSymbolicLink: Boolean = JFiles.isSymbolicLink(f.toPath)
    def dotsuffix: String       = f.name.drop(f.basename.length) // .txt, etc.
    def suffix: String          = dotsuffix.dropWhile((c: Char) => c == '.')
    def lcsuffix: String        = suffix.toLowerCase

    def getParent: String = f.getParent
    def parentFile: JFile = f.getParentFile
    def byteArray: Array[Byte] = JFiles.readAllBytes(f.toPath)
    def files: Seq[JFile] = if (f.isDirectory) f.listFiles.toSeq else Nil
    def paths: Seq[Path] = f.listFiles.toSeq.map { _.toPath }

    def filesTree: Seq[JFile] = Util.filesTree(f)(dummyFilter)
    def pathsTree: Seq[Path] = Util.filesTree(f)(dummyFilter).map { _.toPath }

    def lines: Seq[String] = readLines(f.toPath).toSeq
    def canExist: Boolean  = Platform.canExist(f.toPath)

    // comparable to Some(dotsuffix):
    def extension: Option[String] = f.getName.reverse match {
      case s if s.contains(".") && !s.endsWith(".") => Some(s.dropWhile(_!='.').reverse)
      case s => None
    }
    def renameTo(s: String): Boolean = renameTo(s.path)
    def renameTo(alt: Path): Boolean = {
      f.renameTo(alt.file)
    }

    def diff(other: JFile): Seq[String] = Util.diffExec(f, other)

    def cksumNe: Long  = Util.cksumNe(f.toPath)
    def md5: String    = Util.fileChecksum(f.toPath, algorithm = "MD5")
    def sha256: String = Util.fileChecksum(f.toPath, algorithm = "SHA-256")
    def isEmpty: Boolean  = f.length == 0
    def nonEmpty: Boolean = f.length != 0

    def relpath: Path             = f.toPath.relpath
    def stdpath: String           = Platform.standardizePath(f.toPath.toAbsolutePath.normalize.posx)
    def lastModifiedYMD: String   = f.path.lastModifiedYMD
    def parentPath: Path          = f.parentFile.toPath
    def parent: Path              = parentPath

    def ls: Seq[JFile] = f match {
    case f if f.isDirectory => f.listFiles.toSeq
    case f => Seq(f)
    }

    def subdirs: Seq[Path] = f.toPath.subdirs
    def subfiles: Seq[Path] = f.toPath.subfiles
    def linesAnyEncoding: Seq[String] = Util.readLinesAnyEncoding(f.toPath)
  }
}
