package vastblue.file

import vastblue.Platform.*
import vastblue.file.ProcfsPaths.*
import java.io.{File => JFile}
import java.nio.file.{Files => JFiles, Paths => JPaths, Path => JPath}
import java.io.{BufferedReader, FileReader}
import scala.collection.immutable.ListMap
import scala.util.Using
import scala.jdk.CollectionConverters.*

/*
 * Enable access to the synthetic winshell filesystem provided by
 * Cygwin64, MinGW64, Msys2, Gitbash, etc.
 *
 * Permits writing of scripts that are portable portable between
 * Linux, Osx, and windows shell environments.
 *
 * To create a winshell-friendly client script:
 *    +. import vastblue.file.Paths rather than java.nio.file.Paths
 *    +. call `findInPath(binaryPath)` or `where(binaryPath)` to find executable
 *
 * The following are used to navigate the synthetic winshell filesystem.
 *
 * bashPath: String    : valid path to the bash executable
 * shellBaseDir: String: root directory of the synthetic filesystem
 * unamefull: String   : value reported by `uname -a`
 * To identify the environment:
 * isCygwin: Boolean   : true if running cygwin64
 * isMsys64: Boolean   : true if running msys64
 * isMingw64: Boolean  : true if running mingw64
 * isGitSdk64: Boolean : true if running gitsdk
 * isMingw64: Boolean  : true if running mingw64
 * isWinshell
 * wsl: Boolean
 *
 * NOTES:
 * Treats the Windows default drive root (typically C:\) as the filesystem root.
 * Other drives may be made available by symlink off of the filesystem root.
 * Shell environment (/cygwin64, /msys64, etc.) must be on the default drive.
 *
 * The preferred way to find an executable on the PATH (very fast):
 *   val p: Path = findInPath(binaryName)
 *
 * A Fallback method (much slower):
 *   val path: String = whichPath(binaryName)
 *
 * Most of the magic is available via Paths.get(), defined below.
 *
 * How to determine where msys2/ mingw64 / cygwin64 is installed?
 * best answer: norm(where(s"bash${exeSuffix}")).replaceFirst("/bin/bash.*", "")
 */
object Paths {
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

  // There are three supported filename patterns:
  //    non-windows (posix by default; a tricky case in Windows, easy elsewhere)
  //    windows drive-relative path, with default drive, e.g., /Windows/system32
  //    windows absolute path, e.g., c:/Windows/system32
  // Windows paths are normalized to forward slash
  def get(_fnamestr: String): Path = {
    val _pathstr = derefTilde(_fnamestr) // replace leading tilde with sys.props("user.home")

    val psxStr = _pathstr.replace('\\', '/')

    val p: Path = {
      if (psxStr.startsWith(pwdnorm)) {
        // relativize to "."
        val rel = psxStr.replace(pwdnorm, ".")
        JPaths.get(rel)
      } else if (psxStr.isEmpty || psxStr.startsWith(".")) {
        JPaths.get(psxStr) // includes ..
      } else if (_notWindows || hasDriveLetter(psxStr) || psxStr.matches("/proc(/.*)?")) {
        JPaths.get(psxStr)
      } else {
        pathsGetWindows(_fnamestr)
      }
    }
    p
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

//  def jget(fnamestr: String): Path = {
//    if (_isWindows && fnamestr.contains("::") || fnamestr.contains(";")) {
//      sys.error(s"internal error: called JPaths.get with a filename containing a semicolon")
//    }
//    JPaths.get(fnamestr)
//  }

  def derefTilde(str: String): String = if (str.startsWith("~")) {
    // val uh = userhome
    s"${userhome}${str.drop(1)}".replace('\\', '/')
  } else {
    str
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

  lazy val herepath: Path = normPath(sys.props("user.dir"))

  def here = herepath.toString.replace('\\', '/')

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

  lazy val fileRoots: List[String] = JFile.listRoots.toList.map { (f: JFile) =>
    f.toString
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
  def exists(p: Path): Boolean = {
    canExist(p) && {
      p.toFile match {
      case f if f.isDirectory => true
      case f => f.exists
      }
    }
  }

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

  def stdpath(path: Path): String = path.toString.replace('\\', '/')
  def stdpath(str: String)        = str.replace('\\', '/')
  def norm(p: Path): String       = p.toString.replace('\\', '/')
  def norm(str: String) =
    str.replace('\\', '/') // Paths.get(str).normalize.toString.replace('\\', '/')


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

  // only verified on linux and Windows 11
  def dirIsCaseSensitiveUniversal(dir: JPath): Boolean = {
    require(dir.toFile.isDirectory, s"not a directory [$dir]")
    val pdir = dir.toAbsolutePath.toString
    val p1   = Paths.get(pdir, "A")
    val p2   = Paths.get(pdir, "a")
    p1.toAbsolutePath == p2.toAbsolutePath
  }
  def sameFile(s1: String, s2: String): Boolean = {
    s1 == s2 || {
      // this addresses filesystem case-sensitivity
      // must NOT call get() from this object (stack overflow)
      val p1 = java.nio.file.Paths.get(s1).toAbsolutePath
      val p2 = java.nio.file.Paths.get(s2).toAbsolutePath
      p1 == p2
    }
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
