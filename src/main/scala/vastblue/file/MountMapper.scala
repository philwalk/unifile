package vastblue.file

import vastblue.Platform.{_execLines, _notWindows, _pwd, _shellRoot, cygpathExe, posixrootBare, workingDrive}
import vastblue.file.DriveRoot.*
import vastblue.file.Util.readLines
import vastblue.util.Utils.isAlpha
import vastblue.util.PathExtensions.*
import java.io.{File => JFile}
import java.nio.file.Path
import java.nio.file.{Files => JFiles, Paths => JPaths}
import java.io.{BufferedReader, FileReader}
import scala.collection.immutable.ListMap
import scala.util.Using
import scala.sys.process.*

/*
 * Supported environments:
 * Unix / Linux / OSX, Windows shell environments (CYGWIN64, MINGW64,
 * MSYS2, GIT-BASH, etc.).
 */
object MountMapper {
  private var hook = 0

  // Mount entries to appear below shellRoot directory (e.g., C:/msys64/)
  // /data and /etc appear to have the same parent directory, but:
  //    /data is C:/data
  //    /etc is c:/msys64/etc
  // /etc is not mounted, but also must be translated a-la cygpath:
  //    translate mounted entry, if present
  //    otherwise, replace '/' with C:/msys64
  lazy val etcFstab   = s"$shellRoot/etc/fstab".replaceAll("[\\/]+", "/")

 
  // mutable fields to support unit testing
  private var _mountMap = Map.empty[String, String]
  def mountMap: Map[String, String] = _mountMap
  def mountMap_=(map: Map[String, String]): Unit = _mountMap = map

  private var _cygdrive: String = ""
  def cygdrive: String = _cygdrive
  def cygdrive_=(s: String): Unit = _cygdrive = s

  private var _reverseMountMap = Map.empty[String, String]
  def reverseMountMap: Map[String, String] = _reverseMountMap
  def reverseMountMap_=(map: Map[String, String]): Unit = _reverseMountMap = map

  private var _posix2localMountMap = Map.empty[String, String]
  def posix2localMountMap: Map[String, String] = _posix2localMountMap
  def posix2localMountMap_=(map: Map[String, String]): Unit = _posix2localMountMap = map

  case class FsEntry(dir: String, posix: String, ftype: String) {
    override def toString = "%-22s, %-18s, %s".format(dir, posix, ftype)
  }

  {
    val map = readFstabEntries(etcFstab).map { (e: FsEntry) => (e.posix -> e.dir) }.toMap
    consumeMountMap(map)
  }

  /*
  private lazy val (_cygdrive: String, posix2localMountMap: Map[String, String], reverseMountMap: Map[String, String]) = {
    val (cygdrive, posix2local, reverseMap) = consumeMountMap()
    (cygdrive, posix2local, reverseMap)
  }
  */

  def consumeMountMap(map: Map[String, String]): Unit = {
    def emptyMap = Map.empty[String, String]
    if (_notWindows || _shellRoot.isEmpty) {
      ("", emptyMap, emptyMap)
    } else {
      // update mutable fields
      mountMap        = map.toList.map { case (k: String, v: String) => (k.toLowerCase -> v) }.toMap
      reverseMountMap = map.toList.map { case (k: String, v: String) => (v.toLowerCase -> k) }.toMap
      cygdrive = reverseMountMap.get("cygdrive").getOrElse("")
    }
  }

  def readFstabEntries(fstabPath: String): Seq[FsEntry] = {
    val f = JPaths.get(fstabPath)

    val entries = if (!f.isFile) {
      System.err.printf("not found: %f\n", f.posx)
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


  lazy val cygdrivePrefix = reverseMountMap.get("cygdrive").getOrElse("")

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

  def etcdir = JPaths.get(shellRoot, "etc") match {
  case p if p.isSymbolicLink =>
    p.toRealPath()
  case p =>
    p
  }

//  def defaultCygdrivePrefix = _unameLong match {
//  case "cygwin" => "/cygdrive"
//  case _        => ""
//  }
  
  def fileLines(f: JFile): Seq[String] = {
    Using.resource(new BufferedReader(new FileReader(f))) { reader =>
      Iterator.continually(reader.readLine()).takeWhile(_ != null).toSeq
    }
  }

//  def ostype = _uname("-s")

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
          s"$dl/" // trailing slash is needed here
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
   * Convert pathstr by applying mountMap (if mounted)
   * else by adding shellRoot prefix (if `driveRelative`)
   */
  def applyPosix2LocalMount(pathstr: String): String = {
    require(!pathstr.take(2).endsWith(":"), s"bad argument : ${pathstr}")
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

  def posix2localMountMapKeyset: Seq[String] = posix2localMountMap.keySet.toSeq.sortBy { -_.length }

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
}
