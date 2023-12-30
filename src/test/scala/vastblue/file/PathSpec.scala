package vastblue.file

import vastblue.unifile.*
import vastblue.Platform
import vastblue.Platform.{_pwd, here, hereDrive, pwdposx, uhere}
import vastblue.util.Utils.isSameFile
import org.scalatest.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.File as JFile

class PathSpec extends AnyFunSpec with Matchers with BeforeAndAfter {
  val verbose   = Option(System.getenv("VERBOSE_TESTS")).nonEmpty
  var hook: Int = 0

  // cygroot describes how to translate `driveRelative` like this:
  //     /cygdrive/c          # if cygroot == '/cygdrive'
  //     /c                   # if cygroot == '/'
  val cygroot: String = cygdrive match {
  case str if str.endsWith("/") => str
  case str                      => s"$str/"
  }

  before {
    withFileWriter(testFile) { w =>
      testDataLines.foreach { line =>
        w.print(line + "\n")
      }
    }
    printf("testFile: %s\n", testFile)
  }
  hook += 1
  describe ("invariants") {
    // verify test invariants
    describe ("working drive") {
      it (" should be correct for os") {
        val hd = Platform.hereDrive
        val workingDrive: String = Platform.workingDrive.string
        assert(hd equalsIgnoreCase workingDrive)
        if (isWindows) {
          assert(hd.matches("[a-zA-Z]:"))
        } else {
          assert(hd.isEmpty)
        }
      }
    }
    describe("pwd") {
      it ("should be correct wrt rootDrive for os") {
        val workingDrive: String = Platform.workingDrive.string
        if (isWindows) {
          val currentWorkingDrive = Platform.here.take(2).mkString
          assert(currentWorkingDrive.matches("[a-zA-Z]:"))
          assert(workingDrive.equalsIgnoreCase(currentWorkingDrive))
        } else {
          assert(workingDrive.isEmpty)
        }
      }
    }
  }
  describe("Paths.get") {
    it("should correctly apply `posixroot`") {
      if (isWinshell) {
        val etcFstab = Paths.get("/etc/fstab").posx
        val posxroot: String = Platform._shellRoot
        if (!etcFstab.startsWith(posxroot)) {
          hook += 1
        }
        assert(etcFstab.startsWith(posxroot))
      }
    }
  }
  hook += 1
  describe("Path.relpath.posixpath") {
    it("should correctly relativize Path, if below `pwd`") {
      val p     = Paths.get(s"${_pwd.posx}/src")
      val pnorm = p.posx
      val relp  = p.relpath
      val posx  = relp.posx
      val ok = (posx == pnorm) || (posx.length >= pnorm.length) && pnorm.endsWith(posx)
      if (!ok) {
        hook += 1
      }
      printf("p.posx: %s\n", pnorm)
      printf("p.relpath: %s\n", pnorm)
      printf("p.relpath.posx: %s\n", posx)
      assert(posx == pnorm || (posx.length >= pnorm.length) && pnorm.endsWith(posx))
    }
    // TODO: should NOT relativize when .. blah-blah-blah
  }
  hook += 1
  describe("File") {
    describe("#eachline") {
      it("should correctly deliver all file lines") {
        // val lines = testFile.lines
        System.out.printf("testFile[%s]\n", testFile)
        for ((line, lnum) <- testFile.lines.toSeq.zipWithIndex) {
          val expected = testDataLines(lnum)
          if (line != expected) {
            System.err.println(s"line ${lnum}:\n  [$line]\n  [$expected]")
          }
        }
        hook += 1
        for ((line, lnum) <- testFile.lines.toSeq.zipWithIndex) {
          val expected = testDataLines(lnum)
          if (line != expected) {
            System.err.println(s"failure: line ${lnum}:\n  [$line]\n  [$expected]")
          } else {
            if (verbose) println(s"success: line ${lnum}:  [$line],  [$expected]")
          }
          assert(line == expected, s"line ${lnum}:  [$line],  [$expected]")
        }
      }
    }
    hook += 1
    describe("#tilde-in-path-test") {
      printf("posixHomeDir: [%s]\n", posixHomeDir)
      it("should see file in user home directory if present") {
        val ok = testfileb.exists
        if (ok) println(s"tilde successfully converted to path '$testfileb'")
        assert(ok, s"error: cannot see file '$testfileb'")
      }
      it("should NOT see file in user home directory if NOT present") {
        val test: Boolean = testfileb.delete()
        val ok            = !testfileb.exists || !test
        if (ok)
          println(
            s"delete() successfull, and correctly detected by 'exists' method on path '$testfileb'"
          )
        assert(ok, s"error: can still see file '$testfileb'")
      }
    }
    hook += 1
    if (isWindows) {
      printf("cdrive.exists:         %s\n", cdrive.exists)
      printf("cdrive.isDirectory:    %s\n", cdrive.isDirectory)
      printf("cdrive.isRegularFile:  %s\n", cdrive.isDirectory)
      printf("cdrive.isSymbolicLink: %s\n", cdrive.isSymbolicLink)

      printf("fdrive.exists:         %s\n", fdrive.exists)
      printf("fdrive.isDirectory:    %s\n", fdrive.isDirectory)
      printf("fdrive.isRegularFile:  %s\n", fdrive.isDirectory)
      printf("fdrive.isSymbolicLink: %s\n", fdrive.isSymbolicLink)

      printf("gdrive.exists:         %s\n", gdrive.exists)
      printf("gdrive.isDirectory:    %s\n", gdrive.isDirectory)
      printf("gdrive.isRegularFile:  %s\n", gdrive.isDirectory)
      printf("gdrive.isSymbolicLink: %s\n", gdrive.isSymbolicLink)

      var loop = -1
      for ((fname, expected) <- pathDospathPairs) {
        printf("# fname [%s], expected [%s]\n", fname, expected)
        it(s"should correctly handle posix drive for dos path $fname") {
          loop += 1
          printf("fname[%s], expected[%s]\n", fname, expected)
          if (fname == "/q") {
            hook += 1
          }
          val file = Paths.get(fname)
          printf("%-22s : %s\n", file.stdpath, file.exists)
          val a = expected.toLowerCase.replace('/', '\\')
          // val b = file.toString.toLowerCase
          // val c = file.localpath.toLowerCase
          val d        = file.dospath.toLowerCase
          val df       = normPath(d)
          val af       = normPath(a)
          val sameFile = isSameFile(af, df)
          def isPwd(s: String): Boolean = s == "." || s == pwdposx
          val equivalent = a == d || a.path.abs == d.path.abs
          if (sameFile && equivalent) {
            println(s"a [$a] == d [$d]")
            if (a != d) {
              hook += 1
            }
            assert(equivalent)
          } else {
            System.err.printf("expected[%s]\n", expected.toLowerCase)
            System.err.printf("file.localpath[%s]\n", file.localpath.toLowerCase)
            System.err.printf(
              "error: expected[%s] not equal to dospath [%s]\n",
              expected.toLowerCase,
              file.localpath.toLowerCase
            )
            val x = file.exists
            val y = new JFile(expected).exists
            if (x && y) {
              assert(a == d)
            } else {
              println(s"[$file].exists: [$x]\n[$expected].exists: [$y]")
            }
          }
        }
      }
      hook += 1
      describe("# stdpath test") {
        val upairs = toStringPairs.toArray.toSeq
        var loop   = -1
        printf("%d pairs\n", upairs.size)
        for ((fname, expected) <- upairs) {
          val testName = "%-32s should map [%-12s] to [%s]".format(s"Paths.get(\"$fname\").toString", fname, expected)

          printf("%s\n", testName)
          it(testName) { // <<<<<<<<<<<<<< it should blah-blah-blah
            loop += 1
            if (verbose) {
              printf("=====================\n")
              printf("fname[%s]\n", fname)
              printf("expec[%s]\n", expected)
            }
            val file: Path = Paths.get(fname).toAbsolutePath.normalize()
            printf("file.posx[%-22s] : %s\n", file.posx, file.exists)
            printf("file.stdpath[%-22s] : %s\n", file.stdpath, file.exists)
            val exp = expected.toLowerCase
            val std = file.stdpath.toLowerCase
            val nrm = file.posx.toLowerCase
            printf("exp[%s] : std[%s] : nrm[%s]\n", exp, std, nrm)
            // val loc = file.localpath.toLowerCase
            // val dos = file.dospath.toLowerCase
            if (!std.endsWith(exp)) {
              hook += 1
            }
            if (!nrm.endsWith(exp)) {
              hook += 1
            }

            // note: in some cases (on Windows, for semi-absolute paths not on the default drive), the posix
            // version of the path must include the posix drive letter.  This test is subtle in order to
            // recognize this case.
            if (nonCanonicalDefaultDrive) {
              printf("hereDrive[%s]\n", hereDrive)
              if (std.endsWith(expected)) {
                println(s"std[$std].endsWith(expected[$expected]) for hereDrive[$hereDrive]");
              }
              // in this case, there should also be a cygroot prefix (e.g., s"${cygroot}c")
              assert(
                std.endsWith(expected)
              )
            } else {
              if (exp == std) {
                println(s"std[$std] == exp[$exp]")
              } else {
                System.err.printf("error: expected[%s] not equal to toString [%s]\n", exp, std)
              }
              assert(exp == std) // || exp.drop(2) == std.drop(2) || std.contains(exp))
            }
            hook += 1
          }
        }
      }
      hook += 1
      describe("# Path consistency") {
        for (
          fname <-
            (toStringPairs.toMap.keySet ++ pathDospathPairs.toMap.keySet).toList.distinct.sorted
        ) {
          val f1: Path            = Paths.get(fname)
          val variants: Seq[Path] = getVariants(f1).distinct
          for (v <- variants) { // not necessarily 4 variants (duplicates removed before map to Path)
            val matchtag = "%-12s to %s".format(fname, v)
            it(s"round trip conversion should match [$matchtag]") {
              // val (k1, k2) = (f1.key, v.key)
              val sameFile = isSameFile(f1, v)
              // must NOT do this: f1 != v (in Windows, relative paths NEVER equal absolute paths)
              if (!sameFile) {
                System.err.printf("f1[%s]\nv[%s]\n", f1, v)
              }
              if (f1.equals(v)) {
                println(s"f1[$f1] == v[$v]")
              }
              assert(sameFile, s"not sameFile: f1[$f1] != variant v[$v]")
//              assert(f1.equals(v), s"f1[$f1] != variant v[$v]")
            }
          }
        }
      }
    }
  }
  hook += 1
  describe("/proc files") {
    val procFiles = Seq(
      "/proc/cpuinfo",
      "/proc/devices",
      "/proc/filesystems",
//    "/proc/loadavg", // too slow
      "/proc/meminfo",
      "/proc/misc",
      "/proc/partitions",
      "/proc/stat",
      "/proc/swaps",
      "/proc/uptime",
      "/proc/version",
    )
    for (fname <- procFiles) {
      describe(s"# $fname") {
        it(s"should be readable in Linux or Windows shell") {
          if (isLinux || isWinshell) {
            val text = fname.path.contentAsString.trim.takeWhile(_ != '\n')
            System.out.printf("# %s :: [%s]\n", fname, text)
            assert(text.nonEmpty)
          }
        }
      }
    }
  }
  hook += 1

  def getVariants(p: Path): Seq[Path] = {
    val pstr = p.toString.toLowerCase
    import vastblue.DriveRoot._
    val stdpathToo = if (nonCanonicalDefaultDrive) Nil else Seq(p.stdpath)

    val variants: Seq[String] = Seq(
      p.posx,
      p.toString,
      p.localpath,
      p.dospath
    ) ++ stdpathToo // stdpath fails round-trip test when default drive != C:

    val vlist = variants.distinct.map { s =>
      val p = Paths.get(s)
      if (p.toString.take(1).toLowerCase != pstr.take(1)) {
        hook += 1
      }
      p
    }
    vlist.distinct
  }

  lazy val testFile: Path = {
    val fnamestr = s"${TMP}/youMayDeleteThisDebrisFile.txt"
    Paths.get(fnamestr)
  }

  lazy val maxLines      = 10
  lazy val testDataLines = (0 until maxLines).toList.map { _.toString }

  lazy val homeDirTestFile = "~/shellExecFileTest.out"
  lazy val testfileb: Path = {
    val p = Paths.get(homeDirTestFile)
    touch(p)
    p
  }

  lazy val dosHomeDir: String   = sys.props("user.home")
  lazy val posixHomeDir: String = dosHomeDir.jpath.stdpath

  lazy val cdrive = Paths.get("c:/")
  lazy val gdrive = Paths.get("g:/")
  lazy val fdrive = Paths.get("f:/")

  lazy val gdriveTests = List(
    (s"${cygroot}g", "g:\\"),
    (s"${cygroot}g/", "g:\\")
  )

  lazy val pathDospathPairs = {
    List(
      (".", "."),
      (hereDrive, here),         // jvm treats bare "C:" as pwd for that drive
      (s"${cygroot}q/", "q:\\"), // assumes /etc/fstab mounts /cygroot to /
      (s"${cygroot}q", "q:\\"),  // assumes /etc/fstab mounts /cygroot to /
      (s"${cygroot}c", "c:\\"),
      (s"${cygroot}c/", "c:\\"),
      ("~", dosHomeDir),
      ("~/", dosHomeDir),
      (s"${cygroot}g", "g:\\"),
      (s"${cygroot}g/", "g:\\"),
      (s"${cygroot}c/data/", "c:\\data")
    ) ::: gdriveTests
  }.distinct

  lazy val nonCanonicalDefaultDrive = driveRoot.toUpperCase != "C:"
  def driveRootLc = driveRoot.toLowerCase

  lazy val username = sys.props("user.name").toLowerCase

  lazy val toStringPairs = List(
    (".", uhere),
    (s"${cygroot}q/", s"${cygroot}q"),
    (s"${cygroot}q/file", s"${cygroot}q/file"), // assumes there is no Q: drive
    (hereDrive, uhere),                         // jvm: bare drive == cwd
    (s"${cygroot}c/", s"${cygroot}c"),
    ("~", posixHomeDir),
    ("~/", posixHomeDir),
    (s"${cygroot}g", s"${cygroot}g"),
    (s"${cygroot}g/", s"${cygroot}g"),
    (s"${cygroot}c/data/", "/data")
  )
  lazy val TMP: String = {
    val driveLetter = "g"
    val driveRoot   = s"${cygroot}${driveLetter}"
    if (vastblue.Platform.canExist(driveRoot.path)) {
      val tmpdir = Paths.get(driveRoot)
      // val str = tmpdir.localpath
      tmpdir.isDirectory && tmpdir.paths.contains("/tmp") match {
      case true =>
        s"${cygroot}g/tmp"
      case false =>
        "/tmp"
      }
    } else {
      "/tmp"
    }
  }

  /** similar to gnu 'touch <filename>' */
  def touch(p: Path): Int = {
    var exitCode = 0
    try {
      p.toFile.createNewFile()
    } catch {
      case _: Exception =>
        exitCode = 17
    }
    exitCode
  }
  def touch(file: String): Int = {
    touch(file.toPath)
  }
}
