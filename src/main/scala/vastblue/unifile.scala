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
  
  def cygdrive: String                   = Platform.cygdrive
  def driveRoot: String                  = Platform.driveRoot
  def posixroot: String                  = Platform.posixroot
  def isDirectory(path: String): Boolean = Platform.isDirectory(path)

  def which(basename: String): String                             = Platform.which(basename)
  def find(basename: String, dirs: Seq[String] = envPath): String = Platform.which(basename)

  def isSameFile(p1: Path, p2: Path): Boolean   = util.Utils.isSameFile(p1, p2)
  def sameFile(s1: String, s2: String): Boolean = util.Utils.sameFile(s1, s2)

  def prepArgs: Array[String] => Seq[String] = Platform.prepExecArgs

  def driveRelative(p: Path): Boolean = Utils.driveRelative(p)

  def hasDriveLetter(s: String): Boolean = Utils.hasDriveLetter(s)

  def isDriveLetter(s: String): Boolean = Utils.isDriveLetter(s)

  def isAlpha(c: Char): Boolean = Platform.isAlpha(c)

  def derefTilde(str: String): String = Utils.derefTilde(str)

  def driveAndPath(filepath: String) = Utils.driveAndPath(filepath)
}
