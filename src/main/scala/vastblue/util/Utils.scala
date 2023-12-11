package vastblue.util

import vastblue.Platform
import vastblue.Platform.*
import vastblue.file.ProcfsPaths.*
import vastblue.util.PathExtensions

import java.io.{File => JFile}
import java.nio.file.{Files => JFiles, Paths => JPaths, Path => JPath}
import java.io.{BufferedReader, FileReader}
import scala.util.Using
import scala.jdk.CollectionConverters.*

object Utils extends PathExtensions {
  def driveRelative(p: Path): Boolean = {
    p.toString.startsWith("/") && !p.isAbsolute
  }

  def hasDriveLetter(s: String): Boolean = {
    val isalph = s.nonEmpty && isAlpha(s.charAt(0))
    isalph && s.take(2).endsWith(":") && !s.take(3).endsWith(":")
  }
  def isDriveLetter(s: String): Boolean = {
    val isalph = s.nonEmpty && isAlpha(s.charAt(0))
    isalph && s.take(3).endsWith(":")
  }

  def isAlpha(c: Char): Boolean = {
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
  }

  def derefTilde(str: String): String = if (str.startsWith("~")) {
    // val uh = userhome
    s"${userhome}${str.drop(1)}".replace('\\', '/')
  } else {
    str
  }

  lazy val herepath: Path = normPath(sys.props("user.dir"))
  def here = herepath.toString.replace('\\', '/')

  def driveAndPath(filepath: String) = {
    filepath match {
    case LetterPath(letter, path) =>
      (s"$letter:", path)
    case _ =>
      ("", _shellRoot)
    }
  }

  def segments(p: Path): Seq[Path] = p.iterator().asScala.toSeq

  def isDirectory(path: String): Boolean = {
    lazy val pfs = Procfs(path)
    if (_isWinshell && pfs.segs == "proc" :: Nil) {
      pfs.isDir
    } else {
      Paths.get(path).toFile.isDirectory
    }
  }

  def userhome: String = sys.props("user.home").replace('\\', '/')

  def sameFile(s1: String, s2: String): Boolean = {
    s1 == s2 || {
      // this addresses filesystem case-sensitivity
      // must NOT call get() from this object (stack overflow)
      val p1 = java.nio.file.Paths.get(s1).toAbsolutePath
      val p2 = java.nio.file.Paths.get(s2).toAbsolutePath
      p1 == p2
    }
  }
  def isSameFile(_p1: Path, _p2: Path): Boolean = {
    val cs1 = dirIsCaseSensitive(_p1)
    val cs2 = dirIsCaseSensitive(_p2)
    if (cs1 != cs2) {
      false // not the same file
    } else {
      val p1 = _p1.toAbsolutePath.normalize
      val p2 = _p2.toAbsolutePath.normalize
      // JFiles.isSameFile(p1, p2) // requires both files to exist (else crashes)
      val abs1 = p1.toString
      val abs2 = p2.toString
      if (cs1) {
        abs1 == abs2
      } else {
        val b1 = abs1.equalsIgnoreCase(abs2)
        val b2 = abs1.toLowerCase == abs2.toLowerCase
        assert(b1 == b2)
        b2
      }
    }
  }

  // only verified on linux and Windows 11
  def dirIsCaseSensitiveUniversal(dir: JPath): Boolean = {
    require(dir.toFile.isDirectory, s"not a directory [$dir]")
    val pdir = dir.toAbsolutePath.toString
    val p1   = Paths.get(pdir, "A")
    val p2   = Paths.get(pdir, "a")
    p1.toAbsolutePath == p2.toAbsolutePath
  }

//  in Windows 10+, per-directory case-sensitive filesystem is enabled or not.
//  def dirIsCaseSensitive(p: Path): Boolean = {
//    val s = p.toString.replace('\\', '/')
//    val cmd = Seq("fsutil.exe", "file", "queryCaseSensitiveInfo", s)
//    // windows filesystem case-sensitivity is not common (yet?)
//    cmd.lazyLines_!.mkString("").trim.endsWith(" enabled")
//  }

  // verified on linux and Windows 11; still needed: Darwin/OSX
  def dirIsCaseSensitive(p: Path): Boolean = {
    val pf = p.toAbsolutePath.normalize.toFile
    if (!pf.exists) {
      !(_isWindows || _isDarwin)
    } else {
      val dirpath: Path = if (pf.isFile) {
        pf.getParent.toPath
      } else if (pf.isDirectory) {
        p
      } else {
        sys.error(s"internal error: [$p]")
      }
      val dir = dirpath.toString
      val p1  = Paths.get(dir, "A")
      val p2  = Paths.get(dir, "a")
      p1.toAbsolutePath != p2.toAbsolutePath
    }
  }

  def normPath(_pathstr: String): Path = {
    val jpath: Path = _pathstr match {
    case "~" => JPaths.get(sys.props("user.dir"))
    case _   => JPaths.get(_pathstr)
    }
    normPath(jpath)
  }
  def normPath(path: Path): Path = {
    try {
      val s = path.toString
      if (s.length == 2 && s.take(2).endsWith(":")) {
        _pwd
      } else {
        path.toAbsolutePath.normalize
      }
    } catch {
      case e: java.io.IOError =>
        path
    }
  }

  // This is limited, in order to work on Windows, which is not Posix-Compliant.
  def _chmod(path: Path, permissions: String = "rw", allusers: Boolean = true): Boolean = {
    val file = path.toFile
    // set application user permissions
    val x = permissions.contains("x") || file.canExecute
    val r = permissions.contains("r") || file.canRead
    val w = permissions.contains("w") || file.canWrite

    var ok = true
    ok &&= file.setExecutable(x)
    ok &&= file.setReadable(r)
    ok &&= file.setWritable(w)
    if (allusers) {
      // change permission for all users
      ok &&= file.setExecutable(x, false)
      ok &&= file.setReadable(r, false)
      ok &&= file.setWritable(w, false)
    }
    ok
  }
}
