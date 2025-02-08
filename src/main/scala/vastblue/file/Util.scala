package vastblue.file

import vastblue.Platform.*
import vastblue.{Platform, Script}
import vastblue.file.DriveRoot
import vastblue.file.DriveRoot.*
import vastblue.file.Util.*
import vastblue.util.Utils.*
import vastblue.math.Cksum

import java.nio.file.{Files as JFiles, Paths as JPaths}
import java.nio.charset.Charset
import java.io.{ByteArrayInputStream, InputStream}
import java.io.{FileOutputStream, OutputStreamWriter}
import java.io.{FileWriter, OutputStreamWriter, PrintWriter}
import java.security.{DigestInputStream, MessageDigest}
import scala.jdk.CollectionConverters.*
import scala.sys.process.*
import java.nio.charset.Charset
import java.time.LocalDateTime

// code common to scala3 and scala2.13 versions
object Util {
  def Paths = vastblue.file.Paths

  type Path        = java.nio.file.Path
  type PrintWriter = java.io.PrintWriter
  type JFile       = java.io.File

//  def scriptProp(e: Exception = new Exception()): String = ScriptInfo.scriptProp(e)

  def scriptPath: Path   = vastblue.Script.scriptPath
  def scriptName: String = vastblue.Script.scriptName

  lazy val DefaultEncoding    = DefaultCodec.toString
  lazy val userHome           = sys.props("user.home").replace('\\', '/')
  lazy val userDir            = sys.props("user.dir").replace('\\', '/')
  lazy val ymd                = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  lazy val CygdrivePattern    = "/([a-z])(/.*)?".r
  lazy val DriveLetterPattern = "([a-z]): (/.*)?".r
  private def cwd: Path       = absPath(userDir)

  def absPath(s: String): Path = Paths.get(s).toAbsolutePath.normalize

  def fixHome(s: String): String = {
    s.startsWith("~") match {
    case false => s
    case true  => s.replaceFirst("~", userHome).replace('\\', '/')
    }
  }

  def aFile(s: String): Path            = Paths.get(s)
  def aFile(dir: Path, s: String): Path = Paths.get(s"$dir/$s")
  def _chmod(p: Path, permissions: String, allusers: Boolean): Boolean =
    vastblue.util.Utils._chmod(p, permissions, allusers)

  def newEx: RuntimeException = new RuntimeException("LimitedStackTrace")

  private[vastblue] def _showLimitedStack(e: Throwable = newEx): Unit = {
    System.err.println(getLimitedStackTrace(e))
  }

  def getLimitedStackTrace(implicit ee: Throwable = newEx): String = {
    getLimitedStackList.mkString("\n")
  }

  /** default filtering of stack trace removes known debris */
  def getLimitedStackList(implicit ee: Throwable = new RuntimeException("getLimitedStackTrace")): List[String] = {
    currentStackList(ee).filter { entry =>
      entry.charAt(0) == ' ' || // keep lines starting with non-space
      (!entry.contains("at scala.")
        && !entry.contains("at oracle.")
        && !entry.contains("at org.")
        && !entry.contains("at codehaus.")
        && !entry.contains("at sun.")
        && !entry.contains("at java")
        && !entry.contains("at scalikejdbc")
        && !entry.contains("at net.")
        && !entry.contains("at dotty.")
        && !entry.toLowerCase.contains("(unknown source)"))
    }
  }
  def currentStack(ee: Throwable = new RuntimeException("currentStack")): String = {
    import java.io.{StringWriter}
    val result      = new StringWriter()
    val printWriter = new PrintWriter(result)
    ee.printStackTrace(printWriter)
    result.toString
  }
  def currentStackList(ee: Throwable = new RuntimeException("currentStackList")): List[String] = {
    currentStack(ee).split("[\r\n]+").toList
  }
  def notWindows: Boolean = java.io.File.separator == "/"
  def isWindows: Boolean  = !notWindows

  // set initial codec value, affecting default usage.
  import scala.io.Codec
  def writeCodec: Codec = {
    def osDefault = if (isWindows) {
      Codec.ISO8859
    } else {
      Codec.UTF8
    }
    val lcAll: String = Option(System.getenv("LC_ALL")).getOrElse(osDefault.toString)
    lcAll match {
    case "UTF-8" | "utf-8" | "en_US.UTF-8" | "en_US.utf8" =>
      Codec.UTF8 // "mac" | "linux"
    case s if s.toLowerCase.replaceAll("[^a-zA-Z0-9]", "").contains("utf8") =>
      Codec.UTF8 // "mac" | "linux"
    case "ISO-8859-1" | "latin1" =>
      Codec(lcAll)
    case encodingName =>
      // System.err.printf("warning : unrecognized charset encoding: LC_ALL==[%s]\n", encodingName)
      Codec(encodingName)
    }
  }
  lazy val DefaultCodec = writeCodec
  object JFile {
    def apply(dir: String, fname: String): JFile = new JFile(dir, fname)
    def apply(dir: JFile, fname: String): JFile  = new JFile(dir, fname)
    def apply(fpath: String): JFile              = new JFile(fpath)
  }

  def dummyFilter(f: JFile): Boolean = f.canRead()

  import scala.annotation.tailrec

  /**
   * Recursive list of all files below rootfile.
   *
   * Filter for directories to be descended and/or files to be retained.
   */
  def filesTree(dir: JFile)(func: JFile => Boolean = dummyFilter): Seq[JFile] = {
    assert(dir.isDirectory, s"error: not a directory [$dir]")
    @tailrec
    def filesTree(files: List[JFile], result: List[JFile]): List[JFile] = {
      files match {
      case Nil => result
      case head :: tail if Option(head).isEmpty =>
        Nil
      case head :: tail if head.isDirectory =>
        // filtered directories are pruned
        if (head.canRead()) {
          val subs: List[JFile] = head.listFiles.toList.filter { func(_) }
          filesTree(subs ::: tail, result) // depth-first
        } else {
          Nil
        }
      // filesTree(tail ::: subs, result) // width-first
      case head :: tail => // if head.isFile =>
        val newResult = func(head) match {
        case true  => head :: result // accepted
        case false => result         // rejected
        }
        filesTree(tail, newResult)
      }
    }
    filesTree(List(dir), Nil).toSeq
  }

  /* will not quit on error unless override ignoreErrors=false */
  def _autoDetectDelimiter(sampleText: String, fname: String, ignoreErrors: Boolean = true): String = {
    var (tabs, commas, semis, pipes) = (0, 0, 0, 0)
    sampleText.toCharArray.foreach {
      case '\t' => tabs += 1
      case ','  => commas += 1
      case ';'  => semis += 1
      case '|'  => pipes += 1
      case _    =>
    }
    // Premise:
    //   tab-delimited files contain more tabs than commas,
    //   comma-delimited files contain more commas than tabs.
    // Provides a reasonably fast guess, but can potentially fail.
    //
    // A much slower but more thorough approach would be:
    //    1. replaceAll("""(?m)"[^"]*", "") // remove quoted strings
    //    2. split("[\r\n]+") // extract multiple lines
    //    3. count columns-per-row tallies using various delimiters
    //    4. the tally with the most consistency is the "winner"
    (commas, tabs, pipes, semis) match {
    case (cms, tbs, pps, sms) if cms > tbs && cms >= pps && cms >= sms  => ","
    case (cms, tbs, pps, sms) if tbs >= cms && tbs >= pps && tbs >= sms => "\t"
    case (cms, tbs, pps, sms) if pps > cms && pps > tbs && pps > sms    => "|"
    case (cms, tbs, pps, sms) if sms > cms && sms > tbs && sms > pps    => ";"

    case _ if ignoreErrors => ""

    case _ =>
      sys.error(
        s"unable to choose delimiter: tabs[$tabs], commas[$commas], semis[$semis], pipes[$pipes] for file:\n[${fname}]"
      )
    }
  }

  def posx(p: Path): String    = nativePathString(p)
  def lcnorm(p: Path): String  = posx(p).toLowerCase
  def exists(p: Path): Boolean = p.toFile.exists
  def isFile(p: Path): Boolean = p.toFile.isFile
  def isDir(p: Path): Boolean  = p.toFile.isDirectory
  def name(p: Path): String    = p.toFile.getName

  def toRealPath(p: Path): Path = {
    val pnorm: String = nativePathString(p)
    val preal: String = _exec(realpathExe, pnorm)
    Paths.get(preal)
  }
  private lazy val realpathExe = {
    val rp = _where(s"realpath${exeSuffix}")
    rp
  }
  private[vastblue] def _realpath(p: Path): Path = if (JFiles.isSymbolicLink(p)) {
    try {
      // p.toRealPath() // good symlinks
      JFiles.readSymbolicLink(p);
    } catch {
      case fse: java.nio.file.FileSystemException =>
        realpathLs(p) // bad symlinks, or file access permission
    }
  } else {
    p // not a symlink
  }

  def realpathLs(p: Path): Path = { // ask ls what symlink references
    val pnorm = nativePathString(p)
    _exec("ls", "-l", pnorm).split("\\s+->\\s+").toList match {
    case a :: b :: Nil =>
      Paths.get(b)
    case _ =>
      p
    }
  }

  import scala.util.matching.Regex
  lazy val DatePattern1: Regex = """(.+)(\d\d\d\d\d\d\d\d)(\D.*)?""".r
  lazy val DatePattern2: Regex = """(.+)(\d\d\d\d\d\d\d\d)""".r

  lazy val bintools = true // faster than MessageDigest

  def toFile(s: String): JFile = {
    Paths.get(s).toFile
  }
  def isFile(s: String): Boolean = {
    toFile(s).isFile
  }
  def fileChecksum(p: Path, algorithm: String): String = {
    fileChecksum(p.toFile, algorithm)
  }
  def fileChecksum(file: JFile, algorithm: String): String = {
    val toolName = algorithm match {
    case "SHA-256" => "sha256sum"
    case "MD5"     => "md5sum"
    case _         => ""
    }
    val toolPath: String = _where(toolName)

    val sum = if (bintools && !toolPath.isEmpty && isFile(toolPath)) {
      // very fast
      val fileNorm = nativePathString(file.toPath)
      val binstr   = _execLines(toolPath, fileNorm).take(1).mkString("")
      binstr.replaceAll(" .*", "")
    } else {
      // very slow
      val is = JFiles.newInputStream(file.toPath)
      checkSum(is, algorithm)
    }
    sum
  }

  lazy val PosixDriveLetterPrefix   = "(?i)/([a-z])(/.*)".r
  lazy val WindowsDriveLetterPrefix = "(?i)([a-z]):(/.*)".r

  def cygpath2driveletter(str: String): String = {
    val strtmp = str.replace('\\', '/')
    strtmp match {
    case PosixDriveLetterPrefix(dl, tail) =>
      val tailstr = Option(tail).getOrElse("/")
      s"$dl:$tailstr"
    case WindowsDriveLetterPrefix(dl, tail) =>
      val tailstr = Option(tail).getOrElse("/")
      s"$dl:$tailstr"
    case _ =>
      val wd = workingDrive
      s"$wd$strtmp"
    }
  }
  def cygpath2driveletter(p: Path): String = {
    cygpath2driveletter(p.toString)
  }

  // return posix path string, with cygdrive prefix, if not the default drive
  def withPosixDriveLetter(str: String): String = {
    if (notWindows) {
      str
    } else {
      val hasWindowsDrivePrefix: Boolean = str.drop(1).startsWith(":")
      val posix = if (hasWindowsDrivePrefix) {
        val driveRoot = DriveRoot(str.take(2))
        str.drop(2) match {
        case "/" =>
          driveRoot.posix
        case pathstr if pathstr.startsWith("/") =>
          if (driveRoot == workingDrive) {
            pathstr // implicit drive prefix
          } else {
            s"${driveRoot.posix}$pathstr" // explicit drive prefix
          }
        case pathstr =>
          // Windows drive letter not followed by a slash resolves to
          // the "current working directory" for the drive.
          val cwd = driveRoot.workingDir.toString.replace('\\', '/')
          s"$cwd/$pathstr"
        }
      } else {
        // if str prefix matches workingDrive.posix, remove it
        if (!str.startsWith(cygdrive) || !workingDrive.isDrive) {
          str // no change
        } else {
          val prefixLength = cygdrive.length + 3 // "/cygdrive" + "/c/"
          if (str.take(prefixLength).equalsIgnoreCase(workingDrive.posix + "/")) {
            // drop working drive prefix, for implicit root-relative path
            str.drop(prefixLength - 1) // don't drop slash following workingDrive.posix prefix
          } else {
            str
          }
        }
      }
      posix
    }
  }

  def dropDotSuffix(s: String): String = {
    val (basename: String, suffix: String) = basenameAndExtension(s)
    basename
    // if (!s.contains(".")) s else s.replaceFirst("[.][^.\\/]+$", "")
  }

  // supported algorithms: "MD5" and "SHA-256"
  def checkSum(bytes: Array[Byte], algorithm: String): String = {
    val is: InputStream = new ByteArrayInputStream(bytes)
    checkSum(is, algorithm)
  }
  def checkSum(is: InputStream, algorithm: String): String = {
    val md  = MessageDigest.getInstance(algorithm)
    val dis = new DigestInputStream(is, md)
    var num = 0
    while (dis.available > 0) {
      num += dis.read
    }
    dis.close
    val sum = md.digest.map(b => String.format("%02x", Byte.box(b))).mkString
    sum
  }

  def walkTree(file: JFile, depth: Int = 1, maxdepth: Int = -1): Iterable[JFile] = {
    val children = new Iterable[JFile] {
      def iterator = if (file.isDirectory) file.listFiles.iterator else Iterator.empty
    }
    Seq(file) ++ children.flatMap((f: JFile) =>
      if ((maxdepth < 0 || depth < maxdepth) && f.isDirectory) {
        walkTree(f, depth + 1, maxdepth)
      } else {
        Seq(f)
      }
    )
  }
  def walkTreeFiltered(file: JFile, depth: Int = 1, maxdepth: Int = -1)(filt: JFile => Boolean): Iterable[JFile] = {
    val children = new Iterable[JFile] {
      def iterator = if (file.isDirectory) file.listFiles.iterator else Iterator.empty
    }
    Seq(file) ++ children.flatMap((f: JFile) =>
      if ((maxdepth < 0 || depth < maxdepth) && f.isDirectory) {
        walkTree(f, depth + 1, maxdepth)
      } else if (filt(f)) {
        Seq(f)
      } else {
        Nil
      }
    )
  }
  lazy val cwdnorm = cwd.toString.replace('\\', '/')

  def relativize(p: Path): Path = {
    val pnorm = nativePathString(p)
    val cwd = cwdnorm
    if (pnorm == cwd) {
      Paths.get("./")
    } else if (pnorm.startsWith(cwdnorm)) {
      _pwd.relativize(p)
    } else {
      p
    }
  }

  def nativePathString(p: Path): String = {
    p.normalize.toString match {
    case "" | "." =>
      "."
    case ".." =>
      _pwd.toAbsolutePath.getParent.toString.replace('\\', '/')
    case s =>
      s.replace('\\', '/')
    }
  }

  def bytesNoBom(p: Path): Array[Byte] = {
    val arr = JFiles.readAllBytes(p)
    arr.take(3) match {
    case Array(-17, -69, -65) => arr.drop(3)
    case bytes => bytes
    }
  }
  def bytesNoBom(f: JFile): Array[Byte] = {
    bytesNoBom(f.toPath)
  }

  def readAllBytes(p: Path): Array[Byte] = {
    def segments: Seq[Path]  = p.iterator().asScala.toSeq
    def firstSegment: String = segments.head.toString
    if (p.toFile.isFile) {
      JFiles.readAllBytes(p)
    } else if (_isWinshell && firstSegment == "proc") {
      // e.g., "/proc/meminfo"
      val fileNorm = nativePathString(p)
      _execLines(_catExe, fileNorm).mkString("\n").getBytes
    } else {
      Array.empty[Byte]
    }
  }

  def contentAsString(p: Path, charset: Charset): String = {
    linesCharset(p, charset).mkString("\n")
  }
  def linesCharset(p: Path, charset: Charset): Seq[String] = {
    def readLines: Seq[String] = {
      try {
        if (p.toFile.isFile) {
          JFiles.readAllLines(p, charset).asScala.toSeq
        } else {
          Nil
        }
      } catch {
        case mie: java.nio.charset.MalformedInputException =>
          sys.error(s"malformed input reading file [$p] with charset [$charset]")
      }
    }
    if (!isWindows && p.toFile.isFile) {
      readLines
    } else {
      val segments = p.iterator().asScala.toSeq
      if (segments.head.toString == "proc") {
        val pnorm: String = nativePathString(p)
        _execLines(_catExe, pnorm)
      } else {
        readLines
      }
    }
  }

  import java.nio.{ByteBuffer, CharBuffer}
  import java.nio.charset.{Charset, StandardCharsets, _}
  /*
   * Presume UTF-8, revert to ISO-8859-1 on errors.
   * UTF-8 detects some (not all) malformed input.
   * ISO-8859-1 does not validate.
   */
  def charsetAndContent(p: Path): (Charset, String) = {
    val bytes: Array[Byte] = readAllBytes(p)
    def decodeStrict(bytes: Array[Byte], charset: Charset): String = {
      charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
    }
    def encodeStrict(str: String, charset: Charset): Array[Byte] = {
      val buf: ByteBuffer = charset.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(str))
      val bytes = buf.array()
      if (bytes.length == buf.limit()) {
        bytes
      } else {
        bytes.take(buf.limit())
      }
    }
    var charset = StandardCharsets.UTF_8
    var string =
      try {
        decodeStrict(bytes, charset)
      } catch {
        case e: CharacterCodingException =>
          charset = StandardCharsets.ISO_8859_1
          new String(bytes, charset)
      }
    (charset, string)
  }

  def readContentAsString(p: Path): String = {
    val (_, string) = charsetAndContent(p)
    string
  }
  def readLines(p: Path): Seq[String] = {
    readContentAsString(p).split("[\r]?\n").toSeq
  }

  def renameToUnique(p: Path, newfile: Path, backupDir: String = "/tmp"): (Boolean, Option[Path]) = {
    renameFileUnique(p, newfile, Paths.get(backupDir))
  }

  /**
  * Rename this file without clobbering an existing file.
  *
  * Existing file, if present, is moved to backupDir (also not clobbering,
  * but not necessarily retaining basename).
  * @return value includes a flag (true==success) plus the name of the
  * collision backup file, or None if no collision.
  * throws java.lang.RuntimeException if problems encountered with backup.
  */
  def renameFileUnique(srcfile: Path, newfile: Path, backupDir: Path): (Boolean, Option[Path]) = {
    if (isWindows) {
      assert(lcnorm(srcfile) != lcnorm(newfile))
    } else {
      assert(posx(srcfile) != posx(newfile))
    }
    // backup existing file with desired name, so we don't clobber it.
    val backupFile: Option[Path] = if (exists(newfile)) {
      val baqfile: Path = {
        val maybe = Paths.get(s"$backupDir/${name(newfile)}")
        nextUniquePath(maybe) // basename might be changed
      }

      if (exists(baqfile)) {
        sys.error(s"error: lowestUniquePath returned [${baqfile}], an existing file.")
      }
      // do a file copy followed by a delete (renameTo has OS-specific limitations)
      val err = _renameViaCopy(newfile.toFile, baqfile.toFile, overwrite = true)
      if (err != 0) {
        if (exists(newfile)) {
          sys.error(s"error: unable to delete collision file [${newfile}] after backup to ${baqfile}")
        }
        if (!exists(baqfile)) {
          sys.error(s"error: unable to backup [${newfile}] to [${baqfile}]")
        }
        sys.error(s"unable to avoid collision, rename of existing file [$newfile] failed.")
      }
      Some(baqfile)
    } else {
      None
    }
    // convert numeric return value to true / false
    if (!newfile.toFile.isDirectory && !newfile.toFile.getParentFile.exists) {
      sys.error(s"unable to write file to non-existent parent directory [$newfile]")
    }
    _renameViaCopy(srcfile.toFile, newfile.toFile, overwrite = true) match {
    case 0 =>
      (true, backupFile) // return success plus Option(backupFile)
    case _: Int =>
      if (srcfile.toFile.exists) {
        sys.error(s"error: file [${srcfile}] still exists after trying to rename it to ${newfile}")
      }
      if (!newfile.toFile.exists) {
        sys.error(s"error: after trying to rename ${srcfile}] to [${newfile}], new file not found")
      }
      sys.error(s"unable to rename [$srcfile] to [$newfile].")
    }
  }

  /**
  * Create new File with filename based on this file, but
  * with field appended to the basename, before the suffix.
  */
  def withBasenameSuffix(p: Path, tag: String = "-", numstr: String): Path = {
    def tagregex: String = tag+"[0-9]+$"
    val newBasename = p.toFile.getName.
      replaceAll("[.][^.]+$", "").
      replaceAll(tagregex, "") // remove tag

    // remove suffix, if present
    val sfx: String = suffix(p)
    Paths.get(p.toFile.getParent, s"$newBasename$numstr.$sfx")
  }

  /**
  * Find the next free file path consisting of basename with
  * a hyphenated number appended, e.g. "-000".  The number is
  * incremented until a free file path is encountered, or
  * until the limit is reached.
  */
  def nextUniquePath(p: Path, tag: String = "-", limit: Int = 999): Path = {
    val fmt: String         = "%s%%0%dd".format(tag, limit.toString.length)
    var candidateFile: Path = p

    var n = 0
    var firstPass = false
    while (n < limit && (firstPass || candidateFile.toFile.exists)) {
      n += 1
      firstPass = false // emulate a do-while loop
      val numstr = fmt.format(n)
      candidateFile = withBasenameSuffix(p, tag, numstr)
    }
    if (candidateFile.toFile.exists) {
      throw new RuntimeException("limit [%d] reached w/out success".format(limit))
    }
    candidateFile
  }

  /**
   * Like nextUniquePath, but removes former basename suffix, if present.
   * Will return a path without a suffix, if the pristine path is free,
   * even if the current basename has a suffix.  Returned path does not
   * necessarily have the lowest numbered suffix, if a free "hole" is
   * encountered in the sequence.
   */
  def lowestUniquePath(p: Path, tag: String = "-", limit: Int = 999): Path = {
    val PreSuffixPattern = (s"(.+)$tag\\d$limit").r
    val strippedBasename = p.toFile.getName match {
    case PreSuffixPattern(formerBase: String) =>
      formerBase
    case other =>
      other
    }
    val dotsuffix: String = "." + suffix(p)
    val pristineOriginal  = Paths.get(p.getParent.toString, strippedBasename + dotsuffix)
    if (!pristineOriginal.toFile.exists) {
      pristineOriginal
    } else {
      // restart from zero looking for an available free path
      nextUniquePath(p, tag, limit)
    }
  }

  def nameWithoutWhitespace(p: Path, rep: String = "-"): String = {
    // format: off
    p.toFile.getName.
      replaceAll("""[^-a-zA-Z_0-9\.]+""", rep).
      replaceAll(s"""${rep}+""", rep).
      replaceAll(s"^${rep}", "").
      replaceAll(s"${rep}" + "$", "").
      replaceAll(s"${rep}" + """\.""", ".")
    // format: on
  }

  // Replace problematic baseame characters with hyphen (or `rep`).
  // Then combine leading, trailing, and consecutive `rep` characters.
  // Does not do anything to the extension, if present.
  def uncomplicateBasename(p: Path, rep: String="-"): String = {
    val extension = p.toFile.getName.dropWhile(_=='.').split(".").reverse.headOption match {
      case None => ""
      case Some(s) => s".$s"
    }
    val fixedBasename = basename(p).
      replaceAll("""[^-a-zA-Z_0-9\.]+""", rep).
      replaceAll(s"""$rep+""",rep).
      replaceAll(s"^$rep","").
      replaceAll(s"$rep" + "$","").
      replaceAll(s"$rep\\.",".")
    s"$fixedBasename$extension"
  }

  // Fix ugly basenames, removing and collapsing debris.
  // returns (newPath, true) if successful, (attemptedNewPath, false) otherwise.
  def renameRemovingCruft(p: Path): (Path, Boolean) = {
    val newName = uncomplicateBasename(p)
    // don't bother if no change would result
    if (newName == name(p)) {
      (p, false) // not renamed
    } else {
      val newPath = Paths.get(p.toFile.getParent.toString, newName)
      if( _renameViaCopy(p.toFile, newPath.toFile, overwrite=false) != 0 ){
        if (_verbose){
          System.err.printf("unable to rename from [%s] to [%s]".format(name(p), newName))
        }
        (newPath, false) // not renamed
      } else {
        (newPath, true) // successful rename
      }
    }
  }


  /**
  * Assertion error if newfile already exists and !overwrite.
  */
  private[vastblue] def _renameViaCopy(oldfile: JFile, newfile: JFile, overwrite: Boolean = false): Int = {
    if (newfile.exists) {
      if (overwrite) {
        newfile.delete
      } else {
        throw new RuntimeException(s"error: cannot overwrite [$newfile] unless overwrite==true")
      }
    }
    assert(!newfile.exists)

    JFiles.copy(oldfile.toPath, newfile.toPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    val err = (oldfile.exists, newfile.exists) match {
    case (true, true) =>
      if (oldfile.delete) {
        0
      } else {
        22
      }
    case _ =>
      23
    }
    err
  }

  def removeNonprinting(s: String, replacement: String = ""): String = {
    s.replaceAll("[^\\n\\r\\p{Print}]", replacement)
  }

  def cksum(p: Path): Long = {
    if (p.toFile.length < (Integer.MAX_VALUE-8)) {
      val bytes = readAllBytes(p)
      Cksum.gnuCksum(bytes.iterator)._1
    } else {
      cliCksum(p)._1
    }
  }

  def gnuCksum(p: Path): (Long, Long) = {
    if (p.toFile.length < (Integer.MAX_VALUE-8)) {
      Cksum.gnuCksum(readContentAsString(p).getBytes)
    } else {
      cliCksum(p)
    }
  }

  def cliCksum(p: Path): (Long, Long) = {
    def q = "\""
    val cmd = s"$q${posx(p)}$q"
    val cksumstr = Platform._shellExec(s"cksum $cmd").mkString.trim
    val Array(cksum, bytes) = cksumstr.replaceAll(" *([0-9]+) *([0-9]+) .*", "$1 $2").split(" ")
    (cksum.trim.toLong, bytes.trim.toLong)
  }

  def cksumNe(p: Path): Long = {
    // TODO: only supports files of 2Gbytes or less, due to jvm max array size
    val content = removeNonprinting(readContentAsString(p))
    Cksum.gnuCksum(content.getBytes)._1
  }

  // Copy source file to destination.
  def copyFile[A, B](srcFile: A, dstFile: B): Int = {
    def srcAbs: String = srcFile.toString
    def dstAbs: String = dstFile.toString

    val (srcF: Path, dstF: Path) = (srcFile, dstFile) match {
    case (a: String, b: String) =>
      (Paths.get(a), Paths.get(b))
    case (a: JFile, b: JFile) =>
      (Paths.get(srcAbs), Paths.get(dstAbs))
    case (a: String, b: JFile) =>
      (Paths.get(a), Paths.get(dstAbs))
    case (a: JFile, b: String) =>
      (Paths.get(srcAbs), Paths.get(b))
    // case (a: File, b: File) => (Paths.get(a), Paths.get(b))
    case other =>
      sys.error(s"error on [$other]")
    }
    import java.nio.file.StandardCopyOption
    import java.nio.file.LinkOption
    val rf = JFiles.copy(srcF, dstF, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    if (rf == dstF) {
      0 // success
    } else {
      1 // problem occurred
    }
  }

//  def nameWithoutWhitespace(rep: String = "-"): String = {
//    name(p).
//      replaceAll("""[^-a-zA-Z_0-9\.]+""", rep).
//      replaceAll(s"""${rep}+""", rep).
//      replaceAll(s"^${rep}", "").
//      replaceAll(s"${rep}"+"$", "").
//      replaceAll(s"${rep}"+"""\.""", ".")
//  }

  def withFileWriter(p: Path, charsetName: String = DefaultEncoding, append: Boolean = false)(func: PrintWriter => Any): Unit = {
    val jfile  = p.toFile
    val lcname = jfile.getName.toLowerCase
    var hook   = 0
    if (lcname != "stdout") {
      import vastblue.util.PathExtensions.*
      Option(p.getParentPath) match {
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
  def withPathWriter(p: Path, charsetName: String, append: Boolean)(func: PrintWriter => Any): Unit = {
    withFileWriter(p, charsetName, append)(func) // alias
  }
  def readLinesAnyEncoding(p: Path, encoding: String = "utf-8"): Seq[String] = {
    readLinesIgnoreEncodingErrors(p, encoding)
  }
  def readLinesIgnoreEncodingErrors(p: Path, encoding: String = "utf-8"): Seq[String] = {
    import java.nio.charset.CodingErrorAction
    implicit val codec = Codec(encoding)
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)
    try {
      JFiles.readAllLines(p, codec.charSet).asScala.toSeq
    } catch {
      case _: Exception =>
        encoding match {
        case "utf-8" =>
          implicit val codec = Codec("latin1")
          codec.onMalformedInput(CodingErrorAction.REPLACE)
          codec.onUnmappableCharacter(CodingErrorAction.REPLACE)
          JFiles.readAllLines(p, codec.charSet).asScala.toSeq
        case _ =>
          implicit val codec = Codec("utf-8")
          codec.onMalformedInput(CodingErrorAction.REPLACE)
          codec.onUnmappableCharacter(CodingErrorAction.REPLACE)
          JFiles.readAllLines(p, codec.charSet).asScala.toSeq
        }
    }
  }

  def diffExec(file1: JFile, file2: JFile): List[String] = {
    val fpath1: String = file1.getAbsolutePath.replace('\\', '/')
    val fpath2: String = file2.getAbsolutePath.replace('\\', '/')
    if (file1.isFile != file2.isFile) {
      assert(file1.isFile == file2.isFile, s"expecting 2 files or 2 directories : [$fpath1]\n[$fpath2]")
    }

    val cmd    = Seq("diff", "-wr", fpath1, fpath2)
    val proc   = Process(cmd, Platform.hereJfile)
    var result = List.empty[String]

    val exitValue = proc ! ProcessLogger(
      (out) => if (out.trim.length > 0) result ::= out.trim,
      (err) => printf("e:%s\n", err)
    )
    if (exitValue != 0 && false) { System.err.printf("diff exit value: %s\n", exitValue) }
    result.reverse
  }

//  import vastblue.time.TimeDate.*

  private def fix(s: String) = s.take(4)+"-"+s.drop(4).take(2)+"-"+s.drop(6)

  private[vastblue] def _quikDate(s: String): LocalDateTime = {
    assert(s.length >= 8, s"ymd[$s]")
    val ymdtest = s.take(8)
    val ymd = if ymdtest.matches("[0-9]+") then
      fix(ymdtest)
    else
      s.take(10)
    _quikDateTime(ymd)
  }

  private[vastblue] def _quikDateTime(s: String): LocalDateTime = {
    require(s.matches("""[0-9]{4}\D[0-9]{2}\D[0-9]{2}.*"""))
    var ff = s.split("[^0-9]+").filter { _.trim.nonEmpty }.map { _.toInt }
    ff match {
    case Array(y, m, d) =>
      LocalDateTime.of(y, m, d, 0, 0, 0)
    case Array(y, m, d, h, mn) =>
      LocalDateTime.of(y, m, d, h, mn, 0)
    case Array(y, m, d, h, mn, s) =>
      LocalDateTime.of(y, m, d, h, mn, s)
    case Array(y, m, d, h) =>
      LocalDateTime.of(y, m, d, h, 0, 0)
    case _ =>
      sys.error(s"bad dateTime: [$s]")
    }
  }

  def muzzle_slf4j(): Unit = {
    java.util.logging.LogManager.getLogManager.reset() // prevent noise logging
    import java.io.PrintStream
    val filterOut: PrintStream = new PrintStream(System.err) {
      override def println(l: String): Unit = {
        if ( !l.startsWith("SLF4J") ) {
          super.println(l)
        }
      }
    };
    System.setErr(filterOut)
  }

  // windows-specific
  def isWindowsJunction(_filename: String, enable: Boolean=true): (Boolean, String) = {
    import scala.sys.process.*
    val filename = Paths.get(_filename).toAbsolutePath.normalize.toString.replace('\\', '/')
    // also known as a junction point
    var (junctionFlag, substituteName) = (false, "")
    if( isWindows && enable ){
      val cmd = Seq("fsutil", "reparsepoint", "query", filename)
      val lines = Process(cmd).lazyLines_!.toList
      junctionFlag = lines.contains("Tag value: Mount Point") //|| lines.contains("Tag value: Symbolic Link")
      if( junctionFlag ){
        val linetag = "Substitute Name:"
        substituteName = lines.filter { _.startsWith(linetag) } match {
        case line :: Nil =>
          // remove leading debris,  leaving "cygpath -m" style path
          val fixed = line.replace('\\', '/').replaceFirst(""".+/([A-Za-z]:/.*)""", "$1")
          fixed
        case _ => sys.error("internal error: missing fsutils Substitute Name")
        }
      }
    }
    (junctionFlag, substituteName)
  }

  def basename(p: Path): String = {
    val name = p.toFile.getName
    val dotx = dotsuffix(name)
    if (dotx.isEmpty) {
      name
    } else {
      name.dropRight(dotx.length)
    }
  }

  def basenameAndExtension(p: Path): (String, String) = {
    basenameAndExtension(p.toFile.getName)
  }

  def basenameAndExtension(name: String): (String, String) = {
    val dotx = dotsuffix(name)
    val basename = if (dotx.isEmpty) {
      name
    } else {
      name.dropRight(dotx.length)
    }
    (basename, dotx.drop(1))
  }

  def dotsuffix(name: String): String = {
    name match {
    case s if s.drop(1).contains(".") =>
      "." + s.reverse.takeWhile(_ != '.').reverse
    case _ =>
      ""
    }
  }
  def dotsuffix(p: Path): String = {
    val name = p.toFile.getName
    val dot = dotsuffix(name)
    dot
  }

  def suffix(p: Path): String = {
    val ext = dotsuffix(p)
    ext.drop(1) // noop if empty String
  }

  /*
   * TODO: rename method to reflect that it removes more than spaces
   */
  def renameRemovingWhitespace(p: Path, rep: String = "-"): Path = {
    val newName = nameWithoutWhitespace(p, rep)
    // don't bother if no change would result
    if (newName == name(p)) {
      p // not renamed
    } else {
      val newFile: Path = Paths.get(p.getParent.toString, newName)
      // will not overwrite newFile
      if (_renameViaCopy(p.toFile, newFile.toFile, overwrite = false) != 0) {
        sys.error("unable to rename from [%s] to [%s]".format(name(p), newName))
      }
      newFile
    }
  }
  var hook = 0
  def posxPath(_pathstr: String): Path = {
    if (_pathstr.drop(2).contains(":")) {
      hook += 1
    }
    val jpath: Path = _pathstr match {
    case "." => JPaths.get(sys.props("user.dir"))
    case _   => JPaths.get(_pathstr)
    }
    posxPath(jpath)
  }
  def posxPath(path: Path): Path = try {
    val s = path.toString
    if (s.length == 2 && s.take(2).endsWith(":")) {
      cwd
    } else {
      path.toAbsolutePath.normalize
    }
  } catch {
    case e: java.io.IOError =>
      path
  }

  def isSameFile(p1: Path, p2: Path): Boolean = {
    val cs1 = dirIsCaseSensitive(p1)
    val cs2 = dirIsCaseSensitive(p2)
    if (cs1 != cs2) {
      false // not the same file
    } else {
      // JFiles.isSameFile(p1, p2) // requires both files to exist (else crashes)
      val abs1 = p1.toAbsolutePath.toString
      val abs2 = p2.toAbsolutePath.toString
      if (cs1) {
        abs1.equalsIgnoreCase(abs2)
      } else {
        abs1 == abs2
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
    val pf = p.toFile
    if (!pf.exists) {
      false
    } else {
      val dirpath = if (pf.isFile) {
        pf.getParent
      } else if (pf.isDirectory) {
        p
      } else {
        sys.error(s"internal error: [$p]")
      }
      val dir = dirpath.toString
      val p1  = Paths.get(dir, "A")
      val p2  = Paths.get(dir, "a")
      p1.toAbsolutePath == p2.toAbsolutePath
    }
  }

}
