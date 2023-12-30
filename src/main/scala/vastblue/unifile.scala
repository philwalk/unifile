package vastblue

import vastblue.Platform.envPath
import vastblue.util.ArgsUtil
//import vastblue.util.ArgsUtil.*
import vastblue.util.Utils
import vastblue.file.Util
import vastblue.MountMapper
import vastblue.util.PathExtensions

import java.io.PrintWriter
import java.nio.charset.Charset

object unifile extends PathExtensions {
  type Path = java.nio.file.Path

  def cygdrive: String    = Platform.cygdrive
  def driveRoot: String   = Platform.driveRoot
//  def pwd: Path           = Platform._pwd
//  def isWinshell: Boolean = Platform._isWinshell
//  def isWindows: Boolean  = Platform._isWindows
//  def isLinux: Boolean    = Platform._isLinux
//  def isWsl: Boolean      = Platform._unameLong.contains("WSL")

  def normPath: String => Path = MountMapper.normPath
//  def withFileWriter(p: Path, s: String = "UTF-8", b: Boolean = false)(func: PrintWriter => Any) =
//    Platform.withFileWriter(p, s, b)(func)

  def isDirectory(path: String): Boolean = Platform.isDirectory(path)

  def where(basename: String): String                             = Platform._where(basename)
  def which(basename: String): String                             = Platform.which(basename)
  def find(basename: String, dirs: Seq[String] = envPath): String = Platform.which(basename)

//  def propOrElse: (String, String) => String                      = Platform._propOrElse
//  def propOrEmpty: String => String                               = Platform._propOrEmpty


  def isSameFile(p1: Path, p2: Path): Boolean   = util.Utils.isSameFile(p1, p2)
  def sameFile(s1: String, s2: String): Boolean = util.Utils.sameFile(s1, s2)

//  def scriptName: String = Script.scriptName
  def prepArgs: Array[String] => Seq[String] = Platform.prepExecArgs

  def driveRelative(p: Path): Boolean                             = Utils.driveRelative(p)

  def hasDriveLetter(s: String): Boolean                          = Utils.hasDriveLetter(s)

  def isDriveLetter(s: String): Boolean                           = Utils.isDriveLetter(s)

  def isAlpha(c: Char): Boolean                                   = Platform.isAlpha(c)

  def derefTilde(str: String): String                             = Utils.derefTilde(str)

  def driveAndPath(filepath: String)                              = Utils.driveAndPath(filepath)

  //def segments(p: Path): Seq[Path]                                = Utils.segments(p)
  //def canExist(p: Path): Boolean                                  = Platform.canExist(p)

  /*
  def _usage: (String, Seq[String]) => Nothing    = ArgsUtil._usage
  def thisArg: String                             = ArgsUtil.thisArg
  def peekNext: String                            = ArgsUtil.peekNext
  def consumeNext: String                         = ArgsUtil.consumeNext

  def eachArg(args: Seq[String], usage: (String) => Nothing = ArgsUtil.defaultUsage)(custom: String => Unit): Unit =
    ArgsUtil.eachArg(args, usage)(custom)
  def scriptPath: Path   = Script.scriptPath
  def userhome: String                                            = Utils.userhome
  def eprint(xs: Any*): Unit                                      = Script._eprint(xs:_*)
  def eprintf(fmt: String, xs: Any*): Unit                        = Script._eprintf(fmt, xs:_*)
  def dirExists(pathstr: String): Boolean                         =z
  def dirExists(path: Path): Boolean                              =z
  def pathDriveletter(ps: String): String                         =z
  def pathDriveletter(p: Path): String                            =z
  def fileExists(p: Path): Boolean                                =z
  def exists(path: String): Boolean                               =z
  def dropshellDrive(str: String)                                 =z
  def dropDriveLetter(str: String)                                =z
  def asPosixPath(str: String)                                    =z
  def asLocalPath(str: String)                                    =z
  def fileLines(f: JFile): Seq[String]                            =z
  def envOrElse(varname: String, elseValue: String = ""): String  =z
  def normPath(_pathstr: String): Path                            =z
  def normPath(path: Path): Path                                  =z
  def pathSegments(path: String): (String, Seq[String])           =z
  def dirIsCaseSensitiveUniversal(dir: JPath): Boolean            =z
  def dirIsCaseSensitive(p: Path): Boolean                        =z
  def _chmod(path: Path, permissions: String = "rw", allusers: Boolean = true): Boolean =z

  lazy val fileRoots: List[String]                                =z
  lazy val driveLettersLc: List[String]                           =z
  lazy val herepath: Path                                         =z
  lazy val PosixCygdrive                                          =z
  lazy val LetterPath                                             =z
  lazy val home: Path                                             =z
  */
}
