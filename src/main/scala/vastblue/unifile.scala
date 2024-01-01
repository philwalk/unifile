package vastblue

import vastblue.Platform.envPath
import vastblue.util.ArgsUtil
//import vastblue.util.ArgsUtil.*
import vastblue.util.Utils
import vastblue.file.Util
import vastblue.file.MountMapper
import vastblue.util.PathExtensions

import java.io.PrintWriter
import java.nio.charset.Charset

object unifile extends PathExtensions {
  type Path = java.nio.file.Path

  def cygdrive: String    = Platform.cygdrive
  def driveRoot: String   = Platform.driveRoot
  def isDirectory(path: String): Boolean = Platform.isDirectory(path)

  def where(basename: String): String                             = Platform._where(basename)
  def which(basename: String): String                             = Platform.which(basename)
  def find(basename: String, dirs: Seq[String] = envPath): String = Platform.which(basename)

  def isSameFile(p1: Path, p2: Path): Boolean   = util.Utils.isSameFile(p1, p2)
  def sameFile(s1: String, s2: String): Boolean = util.Utils.sameFile(s1, s2)

  def prepArgs: Array[String] => Seq[String] = Platform.prepExecArgs

  def driveRelative(p: Path): Boolean                             = Utils.driveRelative(p)

  def hasDriveLetter(s: String): Boolean                          = Utils.hasDriveLetter(s)

  def isDriveLetter(s: String): Boolean                           = Utils.isDriveLetter(s)

  def isAlpha(c: Char): Boolean                                   = Platform.isAlpha(c)

  def derefTilde(str: String): String                             = Utils.derefTilde(str)

  def driveAndPath(filepath: String)                              = Utils.driveAndPath(filepath)


  /*
  def normPath: String => Path = MountMapper.normPath // JPaths, except for "~", but ...
  def pwd: Path           = Platform._pwd
  def isWinshell: Boolean = Platform._isWinshell
  def isWindows: Boolean  = Platform._isWindows
  def isLinux: Boolean    = Platform._isLinux
  def isWsl: Boolean      = Platform._unameLong.contains("WSL")

  def withFileWriter(p: Path, s: String = "UTF-8", b: Boolean = false)(func: PrintWriter => Any) =
    Platform.withFileWriter(p, s, b)(func)

  def scriptName: String                       = Script.scriptName
  def propOrElse: (String, String) => String   = Platform._propOrElse
  def propOrEmpty: String => String            = Platform._propOrEmpty
  def segments(p: Path): Seq[Path]             = Utils.segments(p)
  def canExist(p: Path): Boolean               = Platform.canExist(p)
  def _usage: (String, Seq[String]) => Nothing = ArgsUtil._usage
  def thisArg: String                          = ArgsUtil.thisArg
  def peekNext: String                         = ArgsUtil.peekNext
  def consumeNext: String                      = ArgsUtil.consumeNext
  def scriptPath: Path                         = Script.scriptPath
  def userhome: String                         = Utils.userhome
  def eprint(xs: Any*): Unit                   = Script._eprint(xs:_*)
  def eprintf(fmt: String, xs: Any*): Unit     = Script._eprintf(fmt, xs:_*)

  def eachArg(args: Seq[String], usage: (String) => Nothing = ArgsUtil.defaultUsage)(custom: String => Unit): Unit =
    ArgsUtil.eachArg(args, usage)(custom)
  */
}
