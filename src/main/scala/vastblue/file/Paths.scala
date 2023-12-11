package vastblue.file

import vastblue.Platform
import vastblue.Platform.*
import vastblue.file.ProcfsPaths.*
import vastblue.util.PathExtensions

import java.io.{File => JFile}
import java.nio.file.{Files => JFiles, Paths => JPaths, Path => JPath}
import java.io.{BufferedReader, FileReader}
import scala.util.Using
import scala.jdk.CollectionConverters.*

/*
 * An alternative to `java.nio.file.Paths.get()` that understands Cygwin64, MinGW64, Msys2, Gitbash, paths.
 * This version returns an instance of `java.nio.file.Path` with path strings converted to Windows equivalent.
 * On Windows:
 *   + translate posix paths to Windows equivalent, if necessary
 * On non-Windows platforms:
 *   + forwards request to `java.nio.file.Paths.get()`  
 *   + forwards request to `java.nio.file.Paths.get()`  
 */
object Paths extends PathExtensions {
  private var hook = 0
  type Path = java.nio.file.Path

  def get(dirpathstr: String, subpath: String): Path = {
    val dirpath       = derefTilde(dirpathstr) // replace leading tilde with sys.props("user.home")
    def subIsAbsolute = JPaths.get(subpath).isAbsolute
    if (subpath.startsWith("/") || subIsAbsolute) {
      sys.error(s"dirpath[$dirpath], subpath[$subpath]")
    }
    get(s"$dirpath/$subpath")
  }

  /*
   * Concepts:
   * Windows paths are internally normalized with forward slash.
   *
   * Drive Relative Path:
   *   any path starting with a slash or backslash
   *   effectively absolute (uniquely determines an absolute path)
   *
   * Explicitly-Relative Path:
   *   path without a leading forward slash or a drive letter
   *   such as ".", "./dir", "doc/text.txt", etc.
   *
   * Supported filename patterns:
   *
   * Non-windows platforms:
   *   all paths forwarded
   *
   * Windows absolute paths:
   *   all paths forwarded
   *
   * Windows fully-relative paths (excluding drive-relative)
   * Windows drive-relative paths converted:
   *   + if path prefix matches a mountMap entry, replace it
   *     /data               => C:/data                      <<-- "C:\Data" mounted to "/data"
   *     /c/Windows/system32 => C:/Windows/system32          <<-- cygdrive == '/'
   *     /cygdrive/c/Windows/system32 => C:/Windows/system32 <<-- cygdrive == '/cygdrive'
   *
   *   + files below (e.g.,) C:/msys64 treated as virtual mountMap
   *     /etc/fstab => C:/msys64/etc/fstab
   *     read-only mapping (non-existent files not in scope)
   *
   *   + otherwise:
   *     /Windows/system32 => C:/Windows/system32
   *
   * NOTE: support for "/proc/meminfo" and other procfs files by spawning `cat`.
   * See `vastblue.file.ProcfsPath` for details.
   */
  def get(_fnamestr: String): Path = {
    val _pathstr = derefTilde(_fnamestr) // replace leading tilde with sys.props("user.home")

    val psxStr = _pathstr.replace('\\', '/')

    val p: Path = {
      if (psxStr.startsWith(pwdnorm)) {
        val rel = psxStr.replace(pwdnorm, ".") // convert to "./" format
        JPaths.get(rel)
      } else if (psxStr.isEmpty || psxStr.startsWith(".")) {
        JPaths.get(psxStr) // includes ".", "..", "" 
      } else if (_notWindows || hasDriveLetter(psxStr) || psxStr.matches("/proc(/.*)?")) {
        JPaths.get(psxStr)
      } else {
        // most of the complexity is here:
        Platform.pathsGetWindows(_fnamestr)
      }
    }
    p
  }

  lazy val fileRoots: List[String] = {
    JFile.listRoots.toList.map { (f: JFile) => f.toString }
  }

  // this may be needed to replace `def canExist` in vastblue.os
  lazy val driveLettersLc: List[String] = {
//    val values = mountMap.values.toList
    val letters = {
      for {
        dl <- fileRoots.map {
          _.take(2)
        }
        if dl.drop(1) == ":"
      } yield dl.toLowerCase
    }.distinct
    letters
  }

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

  lazy val LetterPath = """([a-zA-Z]):([$\\/a-zA-Z_0-9]*)""".r

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
  lazy val home: Path  = Paths.get(userhome)

  def findPath(prog: String, dirs: Seq[String] = envPath): String = {
    // format: off
    dirs.map { dir =>
      Paths.get(s"$dir/$prog")
    }.find { (p: Path) =>
      p.toFile.isFile
    } match {
    case None => ""
    case Some(p) => p.normalize.toString.replace('\\', '/')
    }
  }

  def which(cmdname: String) = {
    val cname = if (exeSuffix.nonEmpty && !cmdname.endsWith(exeSuffix)) {
      s"${cmdname}${exeSuffix}"
    } else {
      cmdname
    }
    findPath(cname)
  }

  def canExist(p: Path): Boolean = {
    // val letters = driveLettersLc.toArray
    val pathdrive = pathDriveletter(p)
    pathdrive match {
    case "" =>
      true
    case letter =>
      driveLettersLc.contains(letter)
    }
  }


  def dirExists(pathstr: String): Boolean = {
    dirExists(Paths.get(pathstr))
  }
  def dirExists(path: Path): Boolean = {
    canExist(path) && JFiles.isDirectory(path)
  }

  def pathDriveletter(ps: String): String = {
    ps.take(2) match {
    case str if str.drop(1) == ":" =>
      str.take(2).toLowerCase
    case _ =>
      ""
    }
  }
  def pathDriveletter(p: Path): String = {
    pathDriveletter(p.toAbsolutePath.toFile.toString)
  }

  // path.toFile.exists very slow if drive not found, fileExists() is faster.
  def fileExists(p: Path): Boolean = {
    canExist(p) &&
      p.toFile.exists
  }
  def exists(path: String): Boolean = {
    exists(Paths.get(path))
  }
//  def exists(p: Path): Boolean = {
//    canExist(p) && {
//      p.toFile match {
//      case f if f.isDirectory => true
//      case f => f.exists
//      }
//    }
//  }

  // drop drive letter and normalize backslash
  def dropshellDrive(str: String)  = str.replaceFirst(s"^${shellDrive}:", "")
  def dropDriveLetter(str: String) = str.replaceFirst("^[a-zA-Z]:", "")
  def asPosixPath(str: String)     = dropDriveLetter(str).replace('\\', '/')
  def asLocalPath(str: String) = if (_notWindows) str
  else
    str match {
    case PosixCygdrive(dl, tail) => s"$dl:/$tail"
    case _                       => str
    }
  lazy val PosixCygdrive = "[\\/]([a-z])([\\/].*)?".r

//  def stdpath(path: Path): String = path.toString.replace('\\', '/')
//  def stdpath(str: String)        = str.replace('\\', '/')
//  def norm(p: Path): String       = p.toString.replace('\\', '/')
//  def norm(str: String) = str.replace('\\', '/')

  def eprint(xs: Any*): Unit = {
    System.err.print("%s".format(xs: _*))
  }
  def eprintf(fmt: String, xs: Any*): Unit = {
    System.err.print(fmt.format(xs: _*))
  }

  def fileLines(f: JFile): Seq[String] = {
    val fnorm = f.toString.replace('\\', '/')
    if (_isWindows && fnorm.matches("/(proc|sys)(/.*)?")) {
      _execLines("cat.exe", fnorm)
    } else {
      Using
        .resource(new BufferedReader(new FileReader(f))) { reader =>
          Iterator.continually(reader.readLine()).takeWhile(_ != null).toSeq
        }
        .toSeq
    }
  }

  def envOrElse(varname: String, elseValue: String = ""): String = Option(
    System.getenv(varname)
  ) match {
  case None      => elseValue
  case Some(str) => str
  }

  // return drive letter, segments
  def pathSegments(path: String): (String, Seq[String]) = {
    // remove windows drive letter, if present
    val (dl, pathRelative) = path.take(2) match {
    case s if s.endsWith(":") =>
      (path.take(2), path.drop(2))
    case s =>
      (cygdrive, path)
    }
    pathRelative match {
    case "/" | "" =>
      (dl, Seq(pathRelative))
    case _ =>
      (dl, pathRelative.split("[/\\\\]+").filter { _.nonEmpty }.toSeq)
    }
  }

}
