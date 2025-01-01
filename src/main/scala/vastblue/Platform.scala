package vastblue

import java.io.File as JFile
import java.nio.file.{FileSystemException, Path}
import java.nio.file.{Files as JFiles, Paths as JPaths}
import java.io.{BufferedReader, FileReader}
import java.io.{FileWriter, OutputStreamWriter, PrintWriter}
import java.nio.charset.Charset
import java.nio.charset.Charset.*

import scala.collection.immutable.ListMap
import scala.util.control.Breaks.*
import scala.util.Using
import scala.sys.process.*
import scala.collection.mutable.Map as MutMap
import scala.jdk.CollectionConverters.*

import vastblue.file.DriveRoot
import vastblue.file.DriveRoot.*
import vastblue.file.MountMapper
import vastblue.file.Paths
import vastblue.util.PathExtensions.*

/*
 * Supported environments:
 * Unix / Linux / OSX, Windows shell environments (CYGWIN64, MINGW64,
 * MSYS2, GIT-BASH, etc.).
 */
object Platform {
  private var hook = 0
  type Path = java.nio.file.Path

  private val foundPaths: MutMap[String, Path]  = MutMap.empty[String, Path]
  private val foundExes: MutMap[String, String] = MutMap.empty[String, String]

  def DefaultCharset = defaultCharset
  
  // cygdrive is mutable, to support unit testing
  // default cygdrive values, if /etc/fstab is not customized
  //    cygwin: "/cygdrive",
  //    msys:   "/"
  def cygdrive: String = MountMapper.cygdrive

  def _stdpath(p: Path): String = {
    standardizePath(p.toAbsolutePath.normalize.toString.replace('\\', '/'))
  }

  def stdpath(p: Path): String = {
    // drop drive letter, if present and if equal to working drive
    val posix = if (isWindows) {
      val nm = nativePathString(p)
      vastblue.file.Util.withPosixDriveLetter(nm) // e.g., /c
    } else {
      p.toAbsolutePath.toString.posx
    }
    posix
  }

  // lots of subtlety here
  // drop drive letter, if present and if equal to working drive
  def standardizePath(pstr: String): String = {
    val take2: String = pstr.take(2) match {
      case s if !s.contains(":") => ""
      case s => s
    }
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
  def BadPath(psxStr: String) = JPaths.get(s"BadPath-$psxStr")
  def isAlpha(c: Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
  def isAlphaNum(c: Char): Boolean = isAlpha(c) || isNumeric(c)
  def isNumeric(c: Char): Boolean = c >= '0' && c <= '9'
  def isLegalVarName(s: String): Boolean = {
    s.matches("[a-zA-Z_$][a-zA-Z_0-9$]*")
  }

  // other constraints exist, especially in Windows, but these characters are not
  // portable, and are not valid somewhere (Windows, Linux or Mac).  Note that this
  // limitation applies only to file path Path segments, excluding drive prefix.
  lazy val illegalFilenameBytes: Seq[Byte] = {
    Seq('\u0000', '\\', '/', ':', '*', '?', '"', '<', '>', '|').map { _.toByte }.distinct
  }

  def legalFilenameSegment(nameseg: String, verbose: Boolean = false): Boolean = {
    var bytes = nameseg.getBytes
    if (!isWindows && bytes.take(2).contains(':')) {
      hook += 1 // client code problem, or WSL?
    }
    if ((isWindows || isWsl) && bytes.take(2).contains(':')) {
      bytes = bytes.drop(2) // remove drive letter (not legal :)
    }
    val badBytes = bytes.filter { (b: Byte) =>
      illegalFilenameBytes.contains(b)
    }
    if (verbose && badBytes.nonEmpty) {
      val str = new String(badBytes.map { _.toChar })
      printf("illegal bytes: [%s]\n", str)
    }
    badBytes.isEmpty // return Boolean
  }

  def legalFilename(name: String, verbose: Boolean = false): Boolean = {
    legalPosixFilename(name.replace('\\', '/'))
  }
  def legalPosixFilename(name: String, verbose: Boolean = false): Boolean = {
    name.split("/").forall {
      legalFilenameSegment(_, verbose)
    }
  }
  def scriptName: String = Script.scriptName
  def scriptPath: Path   = Script.scriptPath

  def _pwd: Path = JPaths.get(".").toAbsolutePath.normalize
  lazy val cwd: Path = _pwd

  // returns a String
  def _exec(args: String*): String = {
    _execLines(args *).toList.mkString("")
  }

  // returns a LazyList
  def _execLines(args: String*): LazyList[String] = {
    // depends on PATH
    require(args.nonEmpty)
    val arg0: String = args(0).replace('\\', '/')
    val argz: Seq[String] = if (arg0.contains("/")) {
      args
    } else {
      val args0 = where(arg0)
      arg0 :: args.drop(1).toList
    }
    Process(argz).lazyLines_!
  }
  def _notWindows: Boolean = java.io.File.separator == "/"
  def _isWindows: Boolean  = !_notWindows

  def readLines(p: Path): Seq[String] = {
    vastblue.file.Util.readLines(p)
    // JFiles.readAllLines(p).asScala.toSeq
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
  lazy val nonBinaries = Seq(
    "scala",
  )
  def _where(progName: String): String = {
    if (nonBinaries.contains(progName)) {
      hook += 1
    }
    if (_isWindows) {
      // prefer binary with .exe extension, ceteris paribus
      val binName = if (nonBinaries.contains(progName)) progName else setSuffix(progName)
      // getStdout hides stderr: INFO: Could not find files for the given pattern(s)
      val fname: String = getStdout(whereExe, binName).take(1).toList match {
      case Nil =>
        discovered(binName)
      case str :: tail =>
        str
      }
      fname.replace('\\', '/')
    } else {
      _exec("which", progName)
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

  def _osName: String = sys.props("os.name")

  lazy val _osType: String = _osName.toLowerCase match {
  case s if s.contains("windows")  => "windows"
  case s if s.contains("linux")    => "linux"
  case s if s.contains("mac os x") => "darwin"
  case other =>
    sys.error(s"osType is [$other]")
  }

  def unameTest(s: String): Boolean = _unameShort.toLowerCase.startsWith(s)

  lazy val _isLinux: Boolean   = unameTest("linux")
  lazy val _isCygwin: Boolean  = unameTest("cygwin")
  lazy val _isMsys: Boolean    = unameTest("msys")
  lazy val _isMingw: Boolean   = unameTest("mingw")
  lazy val _isGitSdk: Boolean  = unameTest("git-sdk")
  lazy val _isGitbash: Boolean = unameTest("gitbash")

  def _isWinshell: Boolean = _isMsys | _isCygwin | _isMingw | _isGitSdk | _isGitbash
  def _isDarwin: Boolean   = _osType == "darwin"

  def _javaHome: String  = _propOrElse("java.home", JAVA_HOME)

  def _scalaHome: String = _propOrElse("scala.home", SCALA_HOME) match {
    case "" =>
      // fallback in IDEA, or if environment misconfigured
      // (should lead to a more helpful error message)
      "/opt/scala"
    case s =>
      s
  }

  def _username: String  = _propOrElse("user.name", "unknown")
  def _userhome: String  = _propOrElse("user.home", _envOrElse("HOST", "unknown")).replace('\\', '/')
  def _hostname: String  = _envOrElse("HOSTNAME", _envOrElse("COMPUTERNAME", _exec("hostname"))).trim

  def _eprint(xs: Any*): Unit               = System.err.print("%s".format(xs *))
  def _oprintf(fmt: String, xs: Any*): Unit = System.out.printf(fmt, xs) // suppresswarnings:discarded-value
  def _eprintf(fmt: String, xs: Any*): Unit = System.err.print(fmt.format(xs *))

  def _propOrElse(name: String, alt: => String): String = System.getProperty(name, alt)

  def _propOrEmpty(name: String): String = _propOrElse(name, "")

  def _envOrElse(name: String, alt: => String): String = Option(System.getenv(name)).getOrElse(alt)

  def _envOrEmpty(name: String) = _envOrElse(name, "")

  def _propElseEnv(propName: String, envName: String, alt: String = ""): String = {
    // Option(sys.props(propName)).getOrElse(_envOrElse(envName, alt))
    val propval = sys.props(propName)
    if (Option(propval).nonEmpty) {
      propval
    } else {
      _envOrElse(envName, alt)
    }
  }

  // executable Paths
  def _bashPath: Path  = _pathCache("bash")


  def shell: String = _envOrElse("SHELL", ideShell)
  
  def ideShell: String = {
    if (intellij && isWinshell){
      "/usr/bin/bash"
    } else {
      "/bin/bash"
    }
  }
  def intellij : Boolean = {
    var intellij = try {
      classOf[Nothing].getClassLoader.loadClass("com.intellij.rt.execution.application.AppMainV2") != null
    } catch {
      case e: ClassNotFoundException =>
        false
    }
    intellij
  }

  // executable Path Strings, suitable for calling exec("bash", ...)
  def _bashExe: String  = _exeCache("bash")
  def _catExe: String   = _exeCache("cat")
  def _findExe: String  = _exeCache("find")
  def _whichExe: String = _exeCache("which")
  def _unameExe: String = _exeCache("uname")
  def _lsExe: String    = _exeCache("ls")
  def _trExe: String    = _exeCache("tr")
  def _psExe: String    = _exeCache("ps")

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
    if (name == "scala") {
      hook += 1
    }
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
      case exe :: tail => exe.toFile.posx
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
      foundExes(name) = posx(pexe)
      pexe
    }
    exePath
  }

  def _exeCache(_name: String): String = {
    val name = _name.posx.replaceAll("[.]exe$", "").replaceAll(".*/", "")
    val pathString: Option[String] = foundExes.get(name)
    pathString match {
    case Some(pathString) =>
      pathString
    case None =>
      val p: Path = _pathCache(name)
      posx(p)
    }
  }

  def prepExecArgs(args: String*): Seq[String] = {
    args.take(1) match {
    case Nil =>
      sys.error(s"missing program name")
      Nil
    case Seq(progname) =>
      val exe = _exeCache(progname)
      exe :: args.drop(1).toList
    }
  }

  /*
   * Generic command line, return stdout.
   * Stderr is handled by `func` (println by default).
   */
  def executeCmd(_cmd: Seq[String])(func: String => Unit = System.err.println): (Int, List[String]) = {
    val cmd    = prepExecArgs(_cmd *).toArray
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

  def _shellExec(str: String, env: Map[String, String]): LazyList[String] = {
    val cmd      = Seq(_exeCache("bash"), "-c", str)
    val envPairs = env.map { case (a, b) => (a, b) }.toList
    val proc     = Process(cmd, hereJfile, envPairs *)
    proc.lazyLines_!
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

  lazy val hereJfile: java.io.File = JPaths.get(".").toFile

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
      val bashPath: String = posx(_bashPath)
      val guess     = bashPath.replaceFirst("(/usr)?/bin/[^/]*exe$", "")
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
  lazy val binDir: String = {
    val binDirString = s"${posixroot}/bin"
    val binDirPath   = JPaths.get(binDirString)
    binDirPath.toFile.isDirectory match {
    case true =>
      binDirString
    case false =>
      sys.error(s"unable to find binDir at [${binDirString}]")
    }
  }

//  def posx(p: Path): String = {
//    try {
//      p.normalize.toString match {
//      case "." => "."
//      case p   => p.replace('\\', '/')
//      }
//    } catch {
//      case e: Exception =>
//        p.toString.replace('\\', '/')
//    }
//  }

//  def posx(str: String): String = {
//    try {
//      JPaths.get(str).normalize.toString match {
//      case "." => "."
//      case p   => p.replace('\\', '/')
//      }
//    } catch {
//      case e: Exception =>
//        str.replace('\\', '/')
//    }
//  }

  def pwdposx: String = posx(_pwd) // TODO: rename to pwdposx

  def relativize(p: Path): Path = {
    val pnorm = nativePathString(p)
    if (pnorm == pwdposx) {
      _pwd
    } else if (pnorm.startsWith(pwdposx)) {
      _pwd.relativize(p).normalize
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

  sealed trait PathType(s: String, p: Path)
  case class PathRel(s: String, p: Path) extends PathType(s, p)
  case class PathAbs(s: String, p: Path) extends PathType(s, p)
  case class PathDrv(s: String, p: Path) extends PathType(s, p)
  case class PathPsx(s: String, p: Path) extends PathType(s, p)
  case class PathBad(s: String, p: Path) extends PathType(s, p)

  lazy val platVerby = _envOrEmpty("PLAT_VERBY").nonEmpty

  def windowsPathType(str: String): PathType = {
    require(_isWindows)
    val psxStr = posx(str) match {
    case s if s.startsWith("~") =>
      s"$_userHome${s.drop(1)}"
    case s =>
      s
    }
    val first3 = psxStr.take(3).toSeq
    if (platVerby) {
      eprintf("windowsPathType: str[%s]\n", str)
      eprintf("windowsPathType: psx[%s]\n", psxStr)
      eprintf("windowsPathType: first3[%s]\n", first3)
    }

    first3 match {
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

    case _ =>
      PathRel(psxStr, JPaths.get(psxStr))
    }
  }

  def cygPath(psxStr: String): String = {
    val cygpath = _where("cygpath")
    _exec(cygpath, "-m", psxStr)
  }

  /*
   * By default, converts by spawning cygpath.exe
   */
  def pathsGetWindows(psxStr: String): Path = {
    if (!_isWindows) {
      JPaths.get(psxStr)
    } else {
      val wpt = windowsPathType(psxStr)
      wpt match {
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

  lazy val WINDIR = Option(System.getenv("SYSTEMROOT")).getOrElse("").replace('\\', '/')
  lazy val whereExe = {
    WINDIR match {
    case "" =>
      _whereFunc("where")
    case path =>
      s"$path/System32/where.exe"
    }
  }

  // TODO: for WSL, provide `wslpath`
  // by default, returns -m path
  def _cygpath(exename: String, args: String*): String = {
    if (cygpathExe.nonEmpty) {
      val cmd   = cygpathExe :: (args.toList ::: List(exename))
      val lines = Process(cmd).lazyLines_!
      lines.toList.mkString("").trim
    } else {
      exename
    }
  }

  def cygpathM(path: Path): Path = {
    val cygstr = cygpathM(path.normalize.toString)
    JPaths.get(cygstr)
  }

  def cygpathM(pathstr: String): String = {
    val normed = pathstr.replace('\\', '/')
    val tupes: Option[(String, String)] = MountMapper.reverseMountMap.find { case (k, v) =>
      val normtail = normed.drop(k.length)
      // detect whether a fstab prefix is an exactly match of a normed path string.
      normed.startsWith(k) && (normtail.isEmpty || normtail.startsWith("/"))
    }
    val cygMstr: String = tupes match {
    case Some((k, v)) =>
      val normtail = normed.drop(k.length)
      s"$v$normtail"
    case None =>
      // apply the convention that single letter paths below / are cygdrive references
      if (normed.take(3).matches("/./?")) {
        val dl: String = normed.drop(1).take(1) + ":"
        normed.drop(2) match {
        case "" =>
          s"$dl/" // trailing slash is required here
        case str =>
          s"$dl$str"
        }
      } else {
        normed
      }
    }
    // replace multiple slashes with single slash
    cygMstr.replaceAll("//+", "/")
  }

  /*
  lazy val mountMap = {
    fstabEntries.map { (e: FsEntry) => (e.posix -> e.dir) }.toMap
  }
  */

  /*
  lazy val (
    posix2localMountMap: Map[String, String],
    reverseMountMap: Map[String, String]
  ) = {
    def emptyMap = Map.empty[String, String]
    if (_notWindows || _shellRoot.isEmpty) {
      (emptyMap, emptyMap)
    } else {
      val mmap = mountMap.toList.map { case (k: String, v: String) => (k.toLowerCase -> v) }.toMap
      val rmap = mountMap.toList.map { case (k: String, v: String) => (v.toLowerCase -> k) }.toMap

      // _cygdrive is mutable, to support unit testing
      _cygdrive = rmap.get("cygdrive").getOrElse("") match {
      case "/" =>
        "/"
      case "" =>
        "" // must not be Windows
      case s =>
        s"$s/" // need trailing slash
      }
      // to speed up map access, convert keys to lowercase
      (mmap, rmap)
      // readWinshellMounts
    }
  }
  */

  case class FsEntry(dir: String, posix: String, ftype: String) {
    override def toString = "%-22s, %-18s, %s".format(dir, posix, ftype)
  }

  lazy val fstabEntries: Seq[FsEntry] = {
    val rr: String = posixroot
    val etcFstab   = s"$rr/etc/fstab".replaceAll("[\\/]+", "/")
    val p          = JPaths.get(etcFstab)
    val entries = if (!p.isFile) {
      Nil
    } else {
      val lines = readLines(p)
        .map {
          _.trim.replaceAll("\\s*#.*", "")
        }
        .filter {
          !_.trim.isEmpty
        }
      for {
        trimmed <- lines
        ff = trimmed.split("\\s+", -1)
        if ff.size >= 3
        Array(local, posix, ftype) = ff.take(3).map { _.trim }
        dir                        = if (ftype == "cygdrive") "cygdrive" else local
      } yield FsEntry(dir, posix, ftype)
    }
    entries
  }

  // private fields to be imported by unifile.*
  lazy val _unameLong: String  = _uname("-a")
  lazy val _unameShort: String = _unameLong.toLowerCase.replaceAll("[^a-z0-9].*", "")

  def getPath(s: String): Path = Paths.get(s)

  def getPath(dir: Path, s: String): Path = JPaths.get(s"$dir/$s") // JPaths

  def getPath(dir: String, s: String = ""): Path = Paths.get(s"$dir/$s")

  lazy val driveLetters: List[DriveRoot] = {
    val values = MountMapper.mountMap.values.toList
    val letters = {
      for {
        dl <- values.map { _.take(2) }
        if dl.drop(1) == ":"
      } yield DriveRoot(dl)
    }.distinct
    letters
  }

  def dumpPath(): Unit = {
    envPath.foreach { println }
  }

  def checkPath(dirs: Seq[String], prog: String): String = {
    dirs.map { dir => JPaths.get(s"$dir/$prog") }.find { (p: Path) =>
      p.toFile.isFile
    } match {
    case None    => ""
    case Some(p) => p.normalize.toString.replace('\\', '/')
    }
  }

  def whichInPath(prog: String): String = {
    checkPath(envPath, prog)
  }
  def which(cmdname: String) = {
    val cname = if (!exeSuffix.isEmpty && !cmdname.endsWith(exeSuffix)) {
      s"${cmdname}${exeSuffix}"
    } else {
      cmdname
    }
    whichInPath(cname)
  }

  def verbyshow(str: String): Unit = if (_verbose) _eprintf("verby[%s]\n", str)

  def dirExists(pathstr: String): Boolean = {
    dirExists(JPaths.get(pathstr))
  }
  def dirExists(path: Path): Boolean = {
    canExist(path) && JFiles.isDirectory(path)
  }

  def pathDriveletter(ps: String): DriveRoot = {
    ps.take(2) match {
    case str if str.drop(1) == ":" =>
      DriveRoot(str.take(2))
    case _ =>
      DriveRoot("")
    }
  }
  def pathDriveletter(p: Path): DriveRoot = {
    pathDriveletter(p.toAbsolutePath.toString)
  }

  def canExist(p: Path): Boolean = {
    val pathdrive: DriveRoot = pathDriveletter(p)
    pathdrive.string match {
    case "" =>
      true
    case letter =>
      driveLetters.contains(letter)
    }
  }

  // fileExists() solves the Windows jvm problem that path.toFile.exists
  // is VEEERRRY slow for files on a non-existent drive (e.g., q:/).
  def fileExists(p: Path): Boolean = {
    canExist(p) &&
    p.toFile.exists
  }
  def exists(path: String): Boolean = {
    Paths.get(path).toFile.exists
  }
//  def exists(p: Path): Boolean = {
//    canExist(p) && {
//      p.toFile match {
//      case f if f.isDirectory => true
//      case f                  => f.exists
//      }
//    }
//  }

  // drop drive letter and normalize backslash
  def dropDefaultDrive(str: String) = str.replaceFirst(s"^${workingDrive}", "")
  def dropDriveLetter(str: String)  = str.replaceFirst("^[a-zA-Z]:", "")
  def asPosixPath(str: String)      = dropDriveLetter(str).replace('\\', '/')

  def etcdir = getPath(posixroot, "etc") match {
  case p if JFiles.isSymbolicLink(p) =>
    p.toRealPath()
  case p =>
    p
  }

  def defaultCygdrivePrefix = _unameLong match {
  case "cygwin" => "/cygdrive"
  case _        => ""
  }
  lazy val (_mountMap, cygdrive2root) = {
    if (_verbose) printf("etcdir[%s]\n", etcdir)
    val fpath = JPaths.get(s"$etcdir/fstab")
    // printf("fpath[%s]\n", fpath)
    val lines: Seq[String] = if (fpath.toFile.isFile) {
      val src = scala.io.Source.fromFile(fpath.toFile, "UTF-8")
      src.getLines().toList.map { _.replaceAll("#.*$", "").trim }.filter { !_.isEmpty }
    } else {
      Nil
    }

    // printf("fpath.lines[%s]\n", lines.toSeq.mkString("\n"))
    var (cygdrive, _usertemp) = ("", "")
    // map order prohibits any key to contain an earlier key as a prefix.
    // this implies the use of an ordered Map, and is necessary so that
    // when converting posix-to-windows paths, the first matching prefix terminates the search.
    var localMountMap = ListMap.empty[String, String]
    var cd2r          = true // by default /c should mount to c:/ in windows
    if (_isWindows) {
      // cygwin provides default values, potentially overridden in fstab
      val rr = posixroot.reverse.dropWhile(_ == '/').reverse
      localMountMap += "/usr/bin" -> s"$rr/bin"
      localMountMap += "/usr/lib" -> s"$rr/lib"
      // next 2 are convenient, but MUST be added before reading fstab
      localMountMap += "/bin" -> s"$rr/bin"
      localMountMap += "/lib" -> s"$rr/lib"
      for (line <- lines) {
        // printf("line[%s]\n", line)
        val cols = line.split("\\s+", -1).toList
        val List(winpath, _mountpoint, fstype) = cols match {
        case a :: b :: Nil       => a :: b :: "" :: Nil
        case a :: b :: c :: tail => a :: b :: c :: Nil
        case list                => sys.error(s"bad line in ${fpath}: ${list.mkString("|")}")
        }
        val mountpoint = _mountpoint.replaceAll("\\040", " ")
        fstype match {
        case "cygdrive" =>
          cygdrive = mountpoint
        case "usertemp" =>
          _usertemp = mountpoint // need to parse it, but unused here
        case _ =>
          // fstype ignored
          localMountMap += mountpoint -> winpath
        }
      }
      cd2r = cygdrive == "/" // cygdrive2root (the cygwin default mapping)
      if (cygdrive.isEmpty) {
        cygdrive = defaultCygdrivePrefix
      }
      localMountMap += "/cygdrive" -> cygdrive

      for (drive <- driveLetters) {
        // lowercase posix drive letter, e.g. "C:" ==> "/c"
        val letter = drive.string.toLowerCase // .take(1).toLowerCase
        // winpath preserves uppercase DriveRoot (cygpath.exe behavior)
        val winpath = stdpath(s"$drive/".path.toAbsolutePath)
        localMountMap += s"/$letter" -> winpath
      }
    }
    localMountMap += "/" -> posixroot // this must be last
    (localMountMap, cd2r)
  }

  lazy val cygdrivePrefix = MountMapper.reverseMountMap.get("cygdrive").getOrElse("")

  def fileLines(f: JFile): Seq[String] = {
    Using.resource(new BufferedReader(new FileReader(f))) { reader =>
      Iterator.continually(reader.readLine()).takeWhile(_ != null).toSeq
    }
  }

  lazy val wsl: Boolean = {
    val f                       = JPaths.get("/proc/version").toFile
    def lines: Seq[String]      = fileLines(f)
    def contentAsString: String = lines.mkString("\n")
    val test0                   = f.isFile && contentAsString.contains("Microsoft")
    val test1                   = _unameLong.contains("microsoft")
    test0 || test1
  }

  def ostype = _uname("-s")

  // this may be needed to replace `def canExist` in vastblue.os
  lazy val driveLettersLc: List[String] = {
    val values = MountMapper.mountMap.values.toList
    val letters = {
      for {
        dl <- values.map { _.take(2) }
        if dl.drop(1) == ":"
      } yield dl.toLowerCase
    }.distinct
    letters
  }

  // useful for benchmarking functions
  def time(n: Int, func: (String) => Any): Unit = {
    val t0 = System.currentTimeMillis
    for (i <- 0 until n) {
      func("bash${exeSuffix}")
    }
    for (i <- 0 until n) {
      func("bash")
    }
    val elapsed = System.currentTimeMillis - t0
    printf("%d iterations in %9.6f seconds\n", n * 2, elapsed.toDouble / 1000.0)
  }

  def _scalaPath: Path  = _pathCache("scala")

  lazy val SCALA_HOME = {
    val sp = _scalaPath
    if (Option(sp).nonEmpty) {
      sp.getParent.posx.replaceFirst("/bin$", "")
    } else {
      val sh = System.getenv("SCALA_HOME")
      Option(sh).getOrElse("")
    }
  }

  def _javaPath: Path  = _pathCache("java")
  lazy val JAVA_HOME = {
    val _javaHome: String = _javaPath.getParent.posx
    val jh = System.getenv("JAVA_HOME")
    Option(jh).getOrElse(_javaHome)
  }

  lazy val here  = _pwd.toAbsolutePath.normalize.toString.toLowerCase.replace('\\', '/')
  lazy val uhere = here.replaceFirst("^[a-zA-Z]:", "")
  lazy val hereDrive = {
    val hd = here.replaceAll(":.*$", "")
    hd match {
    case drive if drive >= "a" && drive <= "z" =>
      s"$drive:"
    case _ =>
      if (isWindows) {
        System.err.println(s"internal error: _pwd[$_pwd], here[$here], uhere[$uhere]")
        sys.error("hereDrive error")
      }
      ""
    }
  }
}
