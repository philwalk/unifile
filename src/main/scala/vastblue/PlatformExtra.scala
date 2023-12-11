package vastblue

import java.io.{File => JFile}
import java.nio.file.Path
import java.nio.file.{Files => JFiles, Paths => JPaths}
import java.io.{BufferedReader, FileReader}
import scala.collection.immutable.ListMap
import scala.util.Using
import scala.sys.process._
import java.nio.charset.Charset
import vastblue.DriveRoot._
import vastblue.Platform.*
import vastblue.file.Paths.canExist

/*
 * Supported environments:
 * Unix / Linux / OSX, Windows shell environments (CYGWIN64, MINGW64,
 * MSYS2, GIT-BASH, etc.).
 */
object PlatformExtra {
  private var hook = 0

//  def checkPath(dirs: Seq[String], prog: String): String = {
//    dirs.map { dir => JPaths.get(s"$dir/$prog") }.find { (p: Path) =>
//      p.toFile.isFile
//    } match {
//    case None    => ""
//    case Some(p) => p.normalize.toString.replace('\\', '/')
//    }
//  }
//
//  def whichInPath(prog: String): String = {
//    checkPath(envPath, prog)
//  }
//  def which(cmdname: String) = {
//    val cname = if (!exeSuffix.isEmpty && !cmdname.endsWith(exeSuffix)) {
//      s"${cmdname}${exeSuffix}"
//    } else {
//      cmdname
//    }
//    whichInPath(cname)
//  }

//  def verbyshow(str: String): Unit = if (_verbose) _eprintf("verby[%s]\n", str)

//  def dirExists(pathstr: String): Boolean = {
//    dirExists(JPaths.get(pathstr))
//  }
//  def dirExists(path: Path): Boolean = {
//    canExist(path) && JFiles.isDirectory(path)
//  }

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

  lazy val driveLetters: List[DriveRoot] = {
    val values = mountMap.values.toList
    val letters = {
      for {
        dl <- values.map { _.take(2) }
        if dl.drop(1) == ":"
      } yield DriveRoot(dl)
    }.distinct
    letters
  }

  /*
  def canExist(p: Path): Boolean = {
    val pathdrive: DriveRoot = pathDriveletter(p)
    pathdrive.string match {
    case "" =>
      true
    case letter =>
      driveLetters.contains(letter)
    }
  }
  */

  // fileExists() solves a Windows jvm problem:
  // path.toFile.exists can be VEEERRRY slow for files on a non-existent drive (e.g., q:/).
  def fileExists(p: Path): Boolean = {
    canExist(p) &&
    p.toFile.exists
  }

  def _exists(path: String): Boolean = {
    JPaths.get(path).exists
  }

  def _exists(p: Path): Boolean = {
    canExist(p) && {
      p.toFile match {
      case f if f.isDirectory => true
      case f                  => f.exists
      }
    }
  }

  // drop drive letter and normalize backslash
  def dropDefaultDrive(str: String) = str.replaceFirst(s"^${workingDrive}", "")
  def dropDriveLetter(str: String)  = str.replaceFirst("^[a-zA-Z]:", "")
  def asPosixPath(str: String)      = dropDriveLetter(str).replace('\\', '/')
//  def norm(p: Path): String         = p.toString.replace('\\', '/')

  def getPath(dir: Path, tail: String): Path = {
    JPaths.get(dir.toString, tail)
  }
  def etcdir = JPaths.get(posixroot, "etc") match {
  case p if p.isSymbolicLink =>
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
      val rr = posixrootBare
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
        val dp = JPaths.get(s"$drive/").toAbsolutePath
        val winpath = stdpath(dp)
        localMountMap += s"/$letter" -> winpath
      }
    }
    localMountMap += "/" -> posixroot // this must be last
    (localMountMap, cd2r)
  }

  lazy val cygdrivePrefix = reverseMountMap.get("cygdrive").getOrElse("")

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
    val values = mountMap.values.toList
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
    val tupes: Option[(String, String)] = reverseMountMap.find { case (k, v) =>
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

  def fstabEntries: Seq[FsEntry] = {
    val rr: String = posixroot
    val etcFstab   = s"$rr/etc/fstab".replaceAll("[\\/]+", "/")
    val f          = JPaths.get(etcFstab)
    val entries = if (!f.isFile) {
      Nil
    } else {
      val lines = readLines(f)
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

  // mount map 
  lazy val mountMap = {
    fstabEntries.map { (e: FsEntry) => (e.posix -> e.dir) }.toMap
  }
  lazy val (
    _cygdrive: String,
    posix2localMountMap: Map[String, String],
    reverseMountMap: Map[String, String]
  ) = {
    def emptyMap = Map.empty[String, String]
    if (_notWindows || _shellRoot.isEmpty) {
      ("", emptyMap, emptyMap)
    } else {
      val mmap = mountMap.toList.map { case (k: String, v: String) => (k.toLowerCase -> v) }.toMap
      val rmap = mountMap.toList.map { case (k: String, v: String) => (v.toLowerCase -> k) }.toMap

      val cygdrive = rmap.get("cygdrive").getOrElse("") match {
      case "/" =>
        "/"
      case "" =>
        ""
      case s =>
        s"$s" // need trailing slash (why?)
      }

      // TODO: mount entries cause things to appear below C:/msys64/ directory
      // subtlety: /data and /etc seem to have the same parent directory, but:
      //    /data is C:/data
      //    /etc is c:/msys64/etc
      // /etc is not mounted, but also must be translated a-la cygpath:
      // if mounted, apply mount;
      // if `driveRelative`, replace '/' with C:/msys64

      // lowercase keys to speed up map key searches
      vastblue.Platform.cygdrive = cygdrive // notify Plat
      (cygdrive, mmap, rmap)
    }
  }

  case class FsEntry(dir: String, posix: String, ftype: String) {
    override def toString = "%-22s, %-18s, %s".format(dir, posix, ftype)
  }

  lazy val DriveLetterColonPattern = "([a-zA-Z]):(.*)?".r
  lazy val CygdrivePattern         = s"${_cygdrive}([a-zA-Z])(/.*)?".r

  def pathsGetPrev(psxStr: String): Path = {
    val _normpath = psxStr.replace('\\', '/')
    val normpath = _normpath.take(_cygdrive.length+1) match {
    case dl if dl.startsWith("/") =>
      // apply mount map to paths with leading slash
      applyPosix2LocalMount(_normpath) // becomes absolute, if mounted
    case _ =>
      _normpath
    }
    def dd = driveRoot.toUpperCase.take(1)
    val (literalDrive, impliedDrive, pathtail) = normpath match {
    case DriveLetterColonPattern(dl, tail) => // windows drive letter
      (dl, dl, tail)
    case CygdrivePattern(dl, tail) => // cygpath drive letter
      (dl, dl, tail)
    case pstr if pstr.matches("/proc(/.*)?") => // /proc file system
      ("", "", pstr) // must not specify a drive letter!
    case pstr if pstr.startsWith("/") => // drive-relative path, with no drive letter
      // drive-relative paths are on the current-working-drive,
      ("", dd, pstr)
    case pstr => // relative path, implies default drive
      (dd, "", pstr)
    }
    val semipath          = Option(pathtail).getOrElse("/")
    val neededDriveLetter = if (impliedDrive.nonEmpty) s"$impliedDrive:" else ""
    val fpstr             = s"${neededDriveLetter}$semipath" // derefTilde(psxStr)
    if (literalDrive.nonEmpty) {
      // no need for cygpath if drive is unambiguous.
      val fpath =
        if (fpstr.endsWith(":") && fpstr.take(3).length == 2 && fpstr.equalsIgnoreCase(driveRoot)) {
          // fpstr is a drive letter expression.
          // Windows interprets a bare drive letter expression as
          // the "working directory" each drive had at jvm startup.
          _pwd
        } else {
          JPaths.get(fpstr)
        }
      normPath(fpath)
    } else {
      JPaths.get(fpstr)
    }
  }

  def normPath(_pathstr: String): Path = {
    val jpath: Path = _pathstr match {
    case "~" => JPaths.get(sys.props("user.dir"))
    case _   => JPaths.get(_pathstr)
    }
    normPath(jpath)
  }
  def normPath(path: Path): Path = try {
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
  /*
   * Convert pathstr by applying mountMap (if mounted)
   * else by add shellRoot prefix (if `driveRelative`)
   */
  def applyPosix2LocalMount(pathstr: String): String = {
    require(pathstr.take(2).last != ':', s"bad argument : ${pathstr}")
    if (pathstr == "/etc/fstab") {
      hook += 1
    }
    // NOTE: /etc/fstab 
    val mountPrefix = getMounted(pathstr)

    val mountTarget = if (mountPrefix.isEmpty) {
      if (pathstr.matches(s"$_cygdrive/[a-zA-Z]/.*")) {
        s"$_cygdrive$pathstr"
      } else {
        pathstr
      }
    } else {
      val cyg: String        = _cygdrive
      val mountpoint: String = mountPrefix.getOrElse("")
      val hasSegments: Boolean = mountpoint != "/"
      if (hasSegments && mountpoint == cyg) {
        val segments = pathstr.drop(cyg.length).split("/").dropWhile(_.isEmpty)
        require(segments.nonEmpty, s"empty segments for pathstr [$pathstr]")
        val firstSeg = segments.head
        if (firstSeg.length == 1 && isAlpha(firstSeg.charAt(0))) {
          // TODO: need more rigor
          // looks like a cygdrive designator replace '/cygdrive/X' with 'X:/'
          s"$firstSeg:/${segments.tail.mkString("/")}"
        } else {
          val rr = posixrootBare
          s"$rr$pathstr"
        }
      } else {
        val Some(posix) = mountPrefix: @unchecked
        val local       = posix2localMountMap(posix)
        pathstr.replaceAll(s"^$posix", local)
      }
    }
    mountTarget
  }

  /*
   * Translate pathstr to posix alias of local path.
   */
  def applyLocal2PosixMount(pathstr: String): String = {
    var nup            = pathstr
    val (dl, segments) = pathSegments(pathstr) // TODO: toss dl?
    require(segments.nonEmpty, s"empty segments for pathstr [$pathstr]")
    var firstSeg = segments.head match {
    case "/" | "" => ""
    case s        => s
    }
    val mounts = posix2localMountMap.keySet.toArray
    val lcpath = pathstr.toLowerCase
    val mounted = mounts.find { (s: String) =>
      lcpath.startsWith(s) && !lcpath.drop(s.length).startsWith("/")
    }
    if (mounted.nonEmpty) {
      // map keys are all lowercase, to provide performant case-insensitive map.
      firstSeg = posix2localMountMap(firstSeg.toLowerCase)
      nup = (firstSeg :: segments.tail.toList).mkString("/")
    } else {
      val driveLetter = (_cygdrive, segments.head) match {
      case ("", d) if canBeDriveLetter(d) =>
        s"$d:"
      case _ =>
        ""
      }
      val abs = s"$driveLetter/${segments.tail.mkString("/")}"
      nup = abs
    }
    nup
  }

  // return drive letter, segments
  def pathSegments(path: String): (String, Seq[String]) = {
    // remove windows drive letter, if present
    val (dl, pathRelative) = path.take(2) match {
    case s if s.endsWith(":") =>
      (path.take(2), path.drop(2))
    case s =>
      (_cygdrive, path)
    }
    pathRelative match {
    case "/" | "" =>
      (dl, Seq(pathRelative))
    case _ =>
      (dl, pathRelative.split("[/\\\\]+").filter { _.nonEmpty }.toSeq)
    }
  }
  def canBeDriveLetter(s: String): Boolean = {
    s.length == 1 && isAlpha(s.charAt(0))
  }

  def readWinshellMounts: Map[String, String] = {
    // Map must be ordered: no key may contain an earlier key as a prefix.
    // With an ordered Map, the first match terminates the search.
    var localMountMap = ListMap.empty[String, String]

    // default mounts for cygwin, potentially overridden in fstab
    val bareRoot = _shellRoot
    localMountMap += "/usr/bin" -> s"$bareRoot/bin"
    localMountMap += "/usr/lib" -> s"$bareRoot/lib"
    // next 2 are convenient, but MUST be added before reading fstab
    localMountMap += "/bin" -> s"$bareRoot/bin"
    localMountMap += "/lib" -> s"$bareRoot/lib"

    var (cygdrive, usertemp) = ("", "")
    val fpath                = "/proc/mounts"
    val lines: Seq[String] = {
      if (_notWindows || _shellRoot.isEmpty) {
        Nil
      } else {
        _execLines("cat.exe", "/proc/mounts")
      }
    }

    for (line <- lines) {
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
        usertemp = mountpoint // need to parse it, but unused here
      case _ =>
        // fstype ignored
        localMountMap += mountpoint -> winpath
      }
    }

    if (cygdrive.isEmpty) {
      cygdrive = "/cygdrive"
    }
    localMountMap += "/cygdrive" -> cygdrive

    val driveLetters: Array[JFile] = {
      if (false) {
        java.io.File.listRoots() // veeery slow (potentially)
      } else {
        // 1000 times faster
        val dlfiles = for {
          locl <- localMountMap.values.toList
          dl = locl.take(2)
          if dl.drop(1) == ":"
          ff = new JFile(s"$dl/")
        } yield ff
        dlfiles.distinct.toArray
      }
    }

    for (drive <- driveLetters) {
      // lowercase is typical user expectation for cygdrive letter
      val letter: String = drive.getAbsolutePath.take(1).toLowerCase
      // retain uppercase, to match cygpath.exe behavior
      val winpath: String = stdpath(drive.toPath.toAbsolutePath)

      // printf("letter[%s], path[%s]\n", letter, winpath)
      localMountMap += s"/$letter" -> winpath
    }
    // printf("bareRoot[%s]\n", bareRoot)
    localMountMap += "/" -> _shellRoot // this must be last
    localMountMap
  }

  lazy val posix2localMountMapKeyset = posix2localMountMap.keySet.toSeq.sortBy { -_.length }

  /*
   * @return Some(mapKey) matching pathstr prefix, else None.
   *   posix2localMountMapKeyset pre-sorted by key length (longest first),
   *   to prevent spurious matches if cygpath == "/".
   *   Matched prefix of pathstr must be followed by '/' or end of string.
   */
  def getMounted(pathstr: String): Option[String] = {
    val mounts = posix2localMountMapKeyset
    val lcpath = pathstr.toLowerCase
    if (lcpath.matches("/proc\\b(.*)?")) {
      hook += 1
    }
    val mount = mounts.find { (target: String) =>
      def exactMatch: Boolean = {
        target match {
        case "/" =>
          true
        case _ =>
          val pathTail = lcpath.drop(target.length).take(1)
          pathTail match {
          case "" | "/" =>
            true // full segment match
          case _ =>
            false // partial suffix match
          }
        }
      }
      val prefixMatches = lcpath.startsWith(target)
      if (prefixMatches) {
        hook += 1
      }
      prefixMatches && exactMatch
    }
    mount
  }

}
