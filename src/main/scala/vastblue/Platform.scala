package vastblue

import java.io.{File => JFile}
import java.nio.file.{FileSystemException, Path}
import java.nio.file.{Files => JFiles, Paths => JPaths}
import java.io.{PrintWriter, OutputStreamWriter, FileWriter}
import java.io.{BufferedReader, FileReader}
import scala.collection.immutable.ListMap
import scala.util.Using
import scala.util.control.Breaks._
import scala.sys.process._
import scala.collection.mutable.{Map => MutMap}
import scala.jdk.CollectionConverters._
import java.nio.charset.Charset
import java.nio.charset.Charset.*
import vastblue.DriveRoot
import vastblue.DriveRoot._
import vastblue.file.Paths

/*
 * Supported environments:
 * Unix / Linux / OSX, Windows shell environments (CYGWIN64, MINGW64,
 * MSYS2, GIT-BASH, etc.).
 */
object Platform {
  private var hook = 0
  def Paths = vastblue.file.Paths
  
  // cygdrive is mutable
  private var _cygdrive: String = "/" // default in cygwin: "/cygdrive", msys: "/"
  def cygdrive: String = _cygdrive
  def cygdrive_=(s: String): Unit = _cygdrive = s

  def DefaultCharset = defaultCharset
  type Path = java.nio.file.Path

  extension(s: String) {
    def posx: String = s.replace('\\', '/')
    def jpath: Path = JPaths.get(s)
    def path: Path = Paths.get(s)
    def toPath: Path = Paths.get(s)
    def toFile: JFile = Paths.get(s).toFile
  }
  extension(p: Path) {
    def posx: String = p.toString.posx
    def abs: String = p.toAbsolutePath.posx
    def stdpath: String = _stdpath(p)
    def dospath: String = posx.replace('/', '\\')
    def localpath: String = posx.replace('/', JFile.separatorChar)
    def isDirectory: Boolean = p.toFile.isDirectory
    def isFile: Boolean = p.toFile.isFile
    def exists: Boolean = p.toFile.exists
    def getParent: String = p.toFile.getParent
    def parentFile: JFile = p.toFile.getParentFile
    def relpath: Path = relativize(p)
    def relativePath: String = relpath.toString.posx
    def lines = readLines(p)
    def files: Seq[JFile] = p.toFile.listFiles.toSeq
    def paths: Seq[Path] = p.toFile.listFiles.map { _.toPath }.toSeq
    def noDrive: String = p.posx match {
    case s if s.take(2).endsWith(":") => s.drop(2)
    case s                            => s
    }
    def delete() = p.toFile.delete()
    def isSymbolicLink: Boolean = JFiles.isSymbolicLink(p)
    def realPath: Path = this.toRealPath(p)
    def byteArray: Array[Byte] = JFiles.readAllBytes(p)
    def contentAsString(charset: Charset = DefaultCharset): String = {
      val posx = p.posx
      if (posx.startsWith("/proc")) {
        _exec("cat", posx)
      } else {
        new String(byteArray, charset)
      }
    }
  }
  
  extension(f: JFile) {
    def posx: String = f.getAbsolutePath.posx
    def abs: String = f.toPath.toAbsolutePath.posx
    def isDirectory: Boolean = f.isDirectory
    def isFile: Boolean = f.isFile
    def exists: Boolean = f.exists
    def getParent: String = f.getParent
    def parentFile: JFile = f.getParentFile
    def byteArray: Array[Byte] = JFiles.readAllBytes(f.toPath)
    def files: Seq[JFile] = f.listFiles.toSeq
    def paths: Seq[Path] = f.listFiles.toSeq.map { _.toPath }
  }

  sealed trait PathType(s: String, p: Path)
  case class PathRel(s: String, p: Path) extends PathType(s, p)
  case class PathAbs(s: String, p: Path) extends PathType(s, p)
  case class PathDrv(s: String, p: Path) extends PathType(s, p)
  case class PathPsx(s: String, p: Path) extends PathType(s, p)
  case class PathBad(s: String, p: Path) extends PathType(s, p)

  def _stdpath(p: Path): String = {
    _stdpath(p.abs)
  }

  // lots of subtlety here
  def _stdpath(pstr: String) = {
    if (pstr == "C:/data") {
      hook += 1
    }
    val take2: String = pstr.take(2)
    val (dlPrefix: String, stdtail: String) = take2 match {
    case s if s.endsWith(":") =>
      (s, pstr.drop(2))
    case s =>
      ("", s)
    }
    val onCurrentDrive: Boolean = dlPrefix.toUpperCase == driveRoot
    val bareDrive: Boolean = dlPrefix == pstr

    var std: String = if (onCurrentDrive) {
      if (bareDrive || pstr == ".") {
        // can only do this if bare drive
        _pwd.abs.drop(2)
      } else if (stdtail.take(4).length == 4) {
        // path must be longer than "C:/", since "/" would be `shellRoot`
        pstr.drop(2)
      } else {
        assert(pstr.length == 3)
        // e.g., C:/
        val dl: String = pstr.take(2)
        val path: String = pstr.drop(2)
        asPosixDrive(dl, path)
      }
    } else if (bareDrive) {
      // path == driveRoot ... Windows treats this pwd
      val abspath: String = JPaths.get(pstr).abs
      val dl: String = abspath.take(2)
      val path: String = abspath.drop(2)
      asPosixDrive(dl, path)
    } else if (dlPrefix.nonEmpty) {
      require(pstr.length > 2, s"internal error: [$pstr]")
      // has drive letter not on driveRoot, not a bare drive
      val abspath: String = JPaths.get(pstr).abs
      val dl: String = abspath.take(2)
      val path: String = abspath.drop(2)
      asPosixDrive(dl, path)
    } else {
      // no drive letter, nothing to do
      pstr
    }

    if (take2.length == 2 && std.endsWith(".")) {
      std = std.init // drop trailing dot
    }
    // convention: remove trailing slash unless "/"
    std match {
      case "/"                   => "/"
      case s if s.last == '/'    => s.init
      case s                     => s
    }
  }

  def asPosixDrive(dl: String, path: String): String = {
    val root = cygdrive
    val cygified = s"$root${dl.take(1).toLowerCase}$path"
    cygified
  }
  def _userHome = sys.props("user.home")
  def _pwd = JPaths.get(".").toAbsolutePath
  def BadPath(psxStr: String) = JPaths.get(s"BadPath-$psxStr")
  def isAlpha(c: Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
  def scriptName: String = Script.scriptName
  def scriptPath: Path   = Script.scriptPath

  def windowsPathType(str: String): PathType = {
    require(_isWindows)
    val psxStr = str.posx match {
    case s if s.startsWith("~") =>
      s"$_userHome${s.drop(1)}"
    case s =>
      s
    }
    psxStr.take(3).toSeq match {
    case Seq('/') =>
      PathPsx(psxStr, JPaths.get(_shellRoot))
    case Seq(a, ':', _) =>
      if (isAlpha(a)) {
        // val drive = s"a:"
        PathAbs(psxStr, JPaths.get(psxStr))
      } else {
        PathBad(psxStr, BadPath(psxStr))
      }

    case Seq(a, ':') =>
      if (isAlpha(a)) {
        val drive = s"$a:"
        PathDrv(psxStr, JPaths.get(psxStr))
      } else {
        PathBad(psxStr, BadPath(psxStr))
      }

    case Seq('/', b)  =>
      if (isAlpha(b)) {
        val drive = s"$b:/"
        PathDrv(psxStr, JPaths.get(drive))
      } else {
        PathBad(psxStr, BadPath(psxStr))
      }
    case Seq('/', _, _) =>
      // need to do cygpath -m psxStr
      val cpath = cygPath(psxStr).jpath
      PathPsx(psxStr, cpath)

    case Seq(a, _, _) if isAlpha(a) =>
      PathRel(psxStr, JPaths.get(psxStr))
    }
  }

  def cygPath(psxStr: String): String = {
    _exec("cygpath", "-m", psxStr)
  }

  def pathsGetWindows(psxStr: String): Path = {
    if (!_isWindows) {
      JPaths.get(psxStr)
    } else {
      windowsPathType(psxStr) match {
      case pt: PathAbs =>
        pt.p
      case pt: PathDrv =>
        // tricky ..
        pt.p.toAbsolutePath // drive only resolves to `pwd`
      case pt: PathRel =>
        pt.p
      case pt: PathBad =>
        BadPath(psxStr)
      case pt: PathPsx =>
        pt.p
      }
    }
  }

  // returns a String
  def _exec(args: String*): String = {
    _execLines(args: _*).toList.mkString("")
  }

  // returns a LazyList
  def _execLines(args: String*): LazyList[String] = {
    // depends on PATH
    require(args.nonEmpty)
    Process(args).lazyLines_!
  }
  def _notWindows: Boolean = java.io.File.separator == "/"
  def _isWindows: Boolean  = !_notWindows

  def readLines(p: Path): Seq[String] = {
    JFiles.readAllLines(p).asScala.toSeq
  }

  def exeSuffix: String = if (_isWindows) ".exe" else ""

  def setSuffix(exeName: String): String = {
    if (_isWindows && !exeName.endsWith(".exe")) {
      s"$exeName$exeSuffix"
    } else {
      exeName
    }
  }

  def spawnCmd(cmd: Seq[String], verbose: Boolean = false): (Int, List[String], List[String]) = {
    var (out, err) = (List[String](), List[String]())

    def toOut(str: String): Unit = {
      if (verbose) printf("stdout[%s]\n", str)
      out ::= str
    }

    def toErr(str: String): Unit = {
      err ::= str
      if (verbose) System.err.printf("stderr[%s]\n[%s]\n", str, cmd.mkString("|"))
    }
    val exit = cmd ! ProcessLogger((o) => toOut(o), (e) => toErr(e))
    (exit, out.reverse, err.reverse)
  }

  lazy val _verbose = Option(System.getenv("VERBY")).nonEmpty

  def exeFilterList: Seq[String] = {
    Seq(
      // intellij provides anemic Path; filter problematic versions of various Windows executables.
      s"${_userHome}/AppData/Local/Programs/MiKTeX/miktex/bin/x64/pdftotext.exe",
      "C:/ProgramData/anaconda3/Library/usr/bin/cygpath.exe",
      s"${_userHome}/AppData/Local/Microsoft/WindowsApps/bash.exe",
      "C:/Windows/System32/find.exe",
    )
  }

  /*
   * capture stdout, discard stderr.
   */
  def getStdout(prog: String, arg: String): Seq[String] = {
    val cmd = Seq(prog, arg)

    val (exit, out, err) = spawnCmd(cmd, _verbose)

    out.map { _.replace('\\', '/') }.filter { s =>
      !exeFilterList.contains(s)
    }
  }

  def getStdout(prog: String, args: Seq[String]): Seq[String] = {
    val cmd = prog :: args.toList

    val (exit, out, err) = spawnCmd(cmd, _verbose)

    out.map { _.replace('\\', '/') }.filter { s =>
      !exeFilterList.contains(s)
    }
  }

  lazy val WINDIR = Option(System.getenv("SYSTEMROOT")).getOrElse("").replace('\\', '/')
  lazy val whereExe = {
    WINDIR match {
    case "" =>
      _whereFunc("where")
    case path =>
      s"$path/System32/where.exe"
    }
  }

  def _whereFunc(s: String): String = {
    Seq("where.exe", s).lazyLines_!.toList
      .map { _.replace('\\', '/') }
      .filter { (s: String) =>
        !exeFilterList.contains(s)
      }
      .take(1)
      .mkString
  }

  // the following is to assist finding a usable posix environment
  // when cygpath.exe is not found in the PATH.
  lazy val winshellBinDirs: Seq[String] = Seq(
    "c:/msys64/usr/bin",
    "c:/cygwin64/bin",
    "c:/rtools42/usr/bin",
    "c:/Program Files/Git/bin",
    "c:/Program Files/Git/usr/bin",
  ).filter { _.jpath.isDirectory }

  def discovered(progname: String): String = {
    val found = winshellBinDirs
      .find { (dir: String) =>
        val cygpath: Path = JPaths.get(dir, progname)
        cygpath.isFile
      }
      .map { (dir: String) => s"$dir/$progname" }
    found.getOrElse("")
  }

  // get path to binaryName via 'which.exe' or 'where'
  def _where(binaryName: String): String = {
    if (_isWindows) {
      // prefer binary with .exe extension, ceteris paribus
      val binName = setSuffix(binaryName)
      // getStdout hides stderr: INFO: Could not find files for the given pattern(s)
      val fname: String = getStdout(whereExe, binName).take(1).toList match {
      case Nil =>
        discovered(binName)
      case str :: tail =>
        str
      }
      fname.replace('\\', '/')
    } else {
      _exec("which", binaryName)
    }
  }

  def listPossibleRootDirs(startDir: String): Seq[JFile] = {
    JPaths.get(startDir).toAbsolutePath.toFile match {
    case dir if dir.isDirectory =>
      def defaultRootNames = Seq(
        "cygwin64",
        "msys64",
        "git-sdk-64",
        "Git",
        "gitbash",
        "MinGW",
        "rtools42",
      )
      dir.listFiles.toList.filter { f =>
        f.isDirectory && defaultRootNames.exists { name =>
          f.getName.contains(name)
        }
      }
    case path =>
      Nil
    }
  }

  lazy val programFiles = Option(System.getenv("PROGRAMFILES")).getOrElse("")
  def possibleWinshellRootDirs = {
    listPossibleRootDirs("/") ++ listPossibleRootDirs("/opt") ++ listPossibleRootDirs(programFiles)
  }

  def _uname(arg: String) = {
    val unamepath: String = _where("uname") match {
    case "" =>
      val prdirs = possibleWinshellRootDirs.sortBy { -_.lastModified }.take(1)
      prdirs match {
      case Seq(dir) =>
        dir.toString
      case _ =>
        "uname"
      }
    case str =>
      str
    }
    try {
      Process(Seq(unamepath, arg)).lazyLines_!.mkString("")
    } catch {
      case _: Exception =>
        ""
    }
  }

  lazy val _unameLong: String  = _uname("-a")
  lazy val _unameShort: String = _unameLong.toLowerCase.replaceAll("[^a-z0-9].*", "")

  def _osName: String = sys.props("os.name")

  lazy val _osType: String = _osName.toLowerCase match {
  case s if s.contains("windows")  => "windows"
  case s if s.contains("linux")    => "linux"
  case s if s.contains("mac os x") => "darwin"
  case other =>
    sys.error(s"osType is [$other]")
  }

  def _isWinshell: Boolean = _isMsys | _isCygwin | _isMingw | _isGitSdk | _isGitbash
  def _isDarwin: Boolean   = _osType == "darwin"

  def unameTest(s: String): Boolean = _unameShort.toLowerCase.startsWith(s)

  lazy val _isLinux: Boolean   = unameTest("linux")
  lazy val _isCygwin: Boolean  = unameTest("cygwin")
  lazy val _isMsys: Boolean    = unameTest("msys")
  lazy val _isMingw: Boolean   = unameTest("mingw")
  lazy val _isGitSdk: Boolean  = unameTest("git-sdk")
  lazy val _isGitbash: Boolean = unameTest("gitbash")

  def _javaHome: String  = _propElseEnv("java.home", "JAVA_HOME")
  def _scalaHome: String = _propElseEnv("scala.home", "SCALA_HOME")
  def _username: String  = _propOrElse("user.name", "unknown")
  def _userhome: String  = _propOrElse("user.home", _envOrElse("HOST", "unknown")).replace('\\', '/')
  def _hostname: String  = _envOrElse("HOSTNAME", _envOrElse("COMPUTERNAME", _exec("hostname"))).trim

  def _eprint(xs: Any*): Unit               = System.err.print("%s".format(xs: _*))
  def _oprintf(fmt: String, xs: Any*): Unit = System.out.printf(fmt, xs) // suppresswarnings:discarded-value
  def _eprintf(fmt: String, xs: Any*): Unit = System.err.print(fmt.format(xs: _*))

  def _propOrElse(name: String, alt: String): String = System.getProperty(name, alt)

  def _propOrEmpty(name: String): String = _propOrElse(name, "")

  def _envOrElse(name: String, alt: String): String = Option(System.getenv(name)).getOrElse(alt)

  def _envOrEmpty(name: String) = _envOrElse(name, "")

  def _propElseEnv(propName: String, envName: String, alt: String = ""): String = {
    Option(sys.props("java.home")) match {
    case None       => altJavaHome
    case Some(path) => path
    }
  }

  // executable Paths
  def _bashPath: Path  = _pathCache("bash")
  def _catPath: Path   = _pathCache("cat")
  def _findPath: Path  = _pathCache("find")
  def _whichPath: Path = _pathCache("which")
  def _unamePath: Path = _pathCache("uname")
  def _lsPath: Path    = _pathCache("ls")
  def _trPath: Path    = _pathCache("tr")
  def _psPath: Path    = _pathCache("ps")

  // executable Path Strings, suitable for calling exec("bash", ...)
  def _bashExe: String  = _exeCache("bash")
  def _catExe: String   = _exeCache("cat")
  def _findExe: String  = _exeCache("find")
  def _whichExe: String = _exeCache("which")
  def _unameExe: String = _exeCache("uname")
  def _lsExe: String    = _exeCache("ls")
  def _trExe: String    = _exeCache("tr")
  def _psExe: String    = _exeCache("ps")

  private val foundPaths: MutMap[String, Path]  = MutMap.empty[String, Path]
  private val foundExes: MutMap[String, String] = MutMap.empty[String, String]

  def isDirectory(path: String): Boolean = {
    JPaths.get(path).toFile.isDirectory
  }

  def cygPath: String = localPath("cygpath")

  def altJavaHome: String = _envOrElse("JAVA_HOME", "")

  def localPath(exeName: String): String = _where(exeName)

  lazy val (shellDrive, shellBaseDir) = driveAndPath(_shellRoot)

  def driveAndPath(filepath: String) = {
    filepath match {
    case LetterPath(letter, path) =>
      (DriveRoot(letter), path)
    case _ =>
      (DriveRoot(""), posixroot)
    }
  }
  lazy val LetterPath = """([a-zA-Z]): ([$/a-zA-Z_0-9]*)""".r

  lazy val driveRoot = JPaths.get("").toAbsolutePath.getRoot.toString.take(2)

  def _pathCache(name: String): Path = {
    val exePath = foundPaths.get(name) match {
    case Some(path) =>
      path
    case None =>
      val rr = posixroot
      // search for `name` below posixroot, else take first in PATH
      val candidates: Seq[Path] = Seq(
        s"${rr}usr/bin/$name$exeSuffix",
        s"${rr}bin/$name$exeSuffix"
      ).map { (s: String) =>
        JPaths.get(s)
      }.filter {
        JFiles.isRegularFile(_)
      }

      val pathstr: String = candidates.toList match {
      case exe :: tail => exe.posx
      case Nil         => _where(name)
      }

      var pexe: Path = JPaths.get(pathstr)
      try {
        pexe = pexe.toRealPath()
      } catch {
        case fse: FileSystemException =>
        // no permission to follow link
      }
      foundPaths(name) = pexe
      foundExes(name) = pexe.posx
      pexe
    }
    exePath
  }

  def _exeCache(name: String): String = {
    foundExes.get(name) match {
    case Some(pathString) => pathString
    case None             => _pathCache(name).posx
    }
  }

  def prepArgs(args: String*): Seq[String] = {
    args.take(1) match {
    case Nil =>
      sys.error(s"missing program name")
      Nil
    case Seq(progname) =>
      val exe = _exeCache(progname)
      exe :: args.toList
    }
  }

  /*
   * Generic command line, return stdout.
   * Stderr is handled by `func` (println by default).
   */
  def executeCmd(_cmd: Seq[String])(func: String => Unit = System.err.println): (Int, List[String]) = {
    val cmd    = prepArgs(_cmd: _*).toArray
    var stdout = List[String]()

    val exit = Process(cmd) ! ProcessLogger(
      (out) => stdout ::= out,
      (err) => func(err)
    )
    (exit, stdout.reverse)
  }

  def _shellExec(str: String): LazyList[String] = {
    val cmd = Seq(_exeCache("bash"), "-c", str)
    Process(cmd).lazyLines_!
  }

  // similar to _shellExec, but more control
  def _bashCommand(cmdstr: String, envPairs: List[(String, String)] = Nil): (Boolean, Int, Seq[String], Seq[String]) = {
    import scala.sys.process.*
    var (stdout, stderr) = (List.empty[String], List.empty[String])
    if (_bashExe.toFile.exists) {
      val cmd  = Seq(_bashExe, "-c", cmdstr)
      val proc = Process(cmd, None, envPairs*)

      val exitVal = proc ! ProcessLogger((out: String) => stdout ::= out, (err: String) => stderr ::= err)

      // a misconfigured environment (e.g., script is not executable) can prevent script execution
      val validTest = !stderr.exists(_.contains("Permission denied"))
      if (!validTest) {
        printf("\nunable to execute script, return value is %d\n", exitVal)
        stderr.foreach { System.err.printf("stderr [%s]\n", _) }
      }

      (validTest, exitVal, stdout.reverse, stderr.reverse)
    } else {
      (false, -1, Nil, Nil)
    }
  }

  lazy val here: java.io.File = JPaths.get(".").toFile

  def _shellExec(str: String, env: Map[String, String]): LazyList[String] = {
    val cmd      = Seq(_exeCache("bash"), "-c", str)
    val envPairs = env.map { case (a, b) => (a, b) }.toList
    val proc     = Process(cmd, here, envPairs: _*)
    proc.lazyLines_!
  }

  def procCmdlineReader(pidfile: String) = {
    import scala.sys.process._
    val cmd = Seq(_catExe, "-A", pidfile)
    val str = cmd.lazyLines_!.mkString("\n")
    // -v causes non-printing characters to be displayed (zero becomes '^@'?)
    // val str = Seq(bashExe, "-c", "bash", bashcmd).lazyLines_!.mkString("\n")
    str
  }

  // find binaryName in PATH
  def findInPath(binaryName: String): Option[Path] = {
    findAllInPath(binaryName, findAll = false) match {
    case Nil          => None
    case head :: tail => Some(head)
    }
  }

  // find all occurences of binaryName in PATH
  def findAllInPath(prog: String, findAll: Boolean = true): Seq[Path] = {
    // isolate program name
    val progname = prog.replace('\\', '/').split("/").last
    var found    = List.empty[Path]
    breakable {
      for (dir <- envPath) {
        // sort .exe suffix ahead of no .exe suffix
        for (name <- Seq(s"$dir$fsep$progname$exeSuffix", s"$dir$fsep$progname").distinct) {
          val p = JPaths.get(name)
          if (p.toFile.isFile) {
            found ::= p.normalize
            if (!findAll) {
              break() // quit on first one
            }
          }
        }
      }
    }
    found.reverse.distinct
  }

  // root from the perspective of shell environment
  lazy val _shellRoot: String = {
    if (_notWindows) "/"
    else {
      val guess     = _bashPath.posx.replaceFirst("/[^/]*exe$", "")
      val guessPath = JPaths.get(guess) // call JPaths.get here to avoid circular reference
      if (JFiles.isDirectory(guessPath)) {
        guess
      } else {
        // sys.error(s"unable to determine winshell root dir in $osName")
        "" // no path prefix applicable
      }
    }
  }

  lazy val envPath: List[String] = Option(System.getenv("PATH")) match {
  case None      => Nil
  case Some(str) => str.split(psep).toList.map { canonical(_) }.distinct
  }

  def canonical(str: String): String = {
    JPaths.get(str) match {
    case p if p.toFile.exists => p.normalize.toString
    case p                    => p.toString
    }
  }

  def currentWorkingDir(drive: DriveRoot): Path = {
    JPaths.get(drive.string).toAbsolutePath
  }

  def cwdstr = _pwd.toString.replace('\\', '/')

  def fsep = java.io.File.separator
  def psep = sys.props("path.separator")

  lazy val workingDrive: DriveRoot = DriveRoot(cwdstr.take(2))
//lazy val workingDrive = if (isWindows) cwdstr.replaceAll(":.*", ":") else ""

  lazy val cygpathExes = Seq(
    "c:/msys64/usr/bin/cygpath.exe",
    "c:/cygwin64/bin/cygpath.exe",
    "c:/rtools64/usr/bin/cygpath.exe",
  )

  lazy val cygpathExe: String = {
    if (_notWindows) {
      ""
    } else {
      val cpexe = _where(s"cygpath${exeSuffix}")
      val cp = cpexe match {
      case "" =>
        // scalafmt: { optIn.breakChainOnFirstMethodDot = false }
        cygpathExes.find { s => JPaths.get(s).toFile.isFile }.getOrElse(cpexe)
      case f =>
        f
      }
      cp
    }
  }

  lazy val posixroot: String = {
    if (_notWindows) {
      "/"
    } else {
      val cpe: String = cygpathExe
      if (cpe.isEmpty) {
        "/"
      } else {
        _exec(cygpathExe, "-m", "/").mkString("")
      }
    }
  }

  lazy val posixrootBare = {
    posixroot.reverse.dropWhile(_ == '/').reverse
  }

  def norm(str: String) = {
    try {
      JPaths.get(str).normalize.toString match {
      case "." => "."
      case p   => p.replace('\\', '/')
      }
    } catch {
      case e: Exception =>
        str.replace('\\', '/')
    }
  }

  def pwdnorm: String = _pwd.posx

  def relativize(p: Path): Path = {
    val pnorm = nativePathString(p)
    if (pnorm == pwdnorm) {
      _pwd
    } else if (pnorm.startsWith(pwdnorm)) {
      _pwd.relativize(p)
    } else {
      p
    }
  }

  def toRealPath(p: Path): Path = {
    val pnorm: String = nativePathString(p)
    val preal: String = _exec("realpath", pnorm)
    JPaths.get(preal)
  }

  lazy val realpathExe = {
    val rp = _where(s"realpath${exeSuffix}")
    rp
  }

  def nativePathString(p: Path): String = {
    p.normalize.toString match {
    case "" | "." => "."
    case s        => s.replace('\\', '/')
    }
  }

  // TODO: this probably goes somewhere else
  def withFileWriter(p: Path, charsetName: String = "UTF-8", append: Boolean = false)(func: PrintWriter => Any): Unit = {
    val jfile  = p.toFile
    val lcname = jfile.getName.toLowerCase
    var hook   = 0
    if (lcname != "stdout") {
      Option(jfile.getParentFile) match {
      case Some(parent) =>
        if (parent.exists) {
          hook += 1
        } else {
          throw new IllegalArgumentException(s"parent directory not found [${parent}]")
        }
      case None =>
        throw new IllegalArgumentException(s"no parent directory")
      }
    }
    val writer = lcname match {
    case "stdout" =>
      new PrintWriter(new OutputStreamWriter(System.out, charsetName), true)
    case _ =>
      new PrintWriter(new FileWriter(jfile, append))
    }
    var junk: Any = 0
    try {
      junk = func(writer)
    } finally {
      writer.flush()
      if (lcname != "stdout") {
        // don't close stdout!
        writer.close()
      }
    }
  }

}
