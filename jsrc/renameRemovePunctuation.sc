#!/usr/bin/env -S scala
//package vast.apps

/**
* Rename input files, removing punctuation.
* Not intended to affect path separators, dots, hyphens or underscores.
* First renames parent directories, then works its' way down the paths.
*/
import vastblue.pallet.*
import vastblue.math.Cksum
import scala.collection.immutable
import scala.util.matching.Regex

object RenameRemovePunctuation {
  def usage(msg: String = "") = {
    _usage(
      msg,
      Seq(
        "[-b] <base-directory> | <file1> [<file2> ...]",
        "NOTE: <fileN> cannot be a directory.",
        "-v      verbose (show each to-be-renamed file, etc.)",
        "-b      show bad characters in files to-be-renamed"
      )
    )
  }
  var dir: Option[Path]       = None
  var files                   = List.empty[Path]
  var (verbose, showBadParts) = (false, false)

  def main(args: Array[String]): Unit = {
    parseArgs(args.toSeq)
    var totalRenames = 0
    try {
      if (dir != None) {
        totalRenames += renameWidthFirst(dir.get)
      }
      if (files.nonEmpty) {
        for (f <- files) {
          processPath(f)
        }
      }
    } catch {
      case e: Exception =>
        eprintf("total renames so far [%s]\n", totalRenames)
        showLimitedStack(e)
        sys.exit(1)
    }
  }

  def renameWidthFirst(dir: Path): Int = {
    val renameCount = processPath(dir)
    // eprintf("# %d\n",renameCount)
    if (renameCount == 0 && dir.isDirectory) {
      val (subfiles, subdirs) = dir.paths.partition { _.isFile }
      if (subdirs.nonEmpty) {
        if (verbose) eprintf("# recurse into subdirs: [%s]\n", subdirs)
        for (sub <- subdirs) {
          renameWidthFirst(sub)
        }
      }
      if (subfiles.nonEmpty) {
        for (file <- subfiles) {
          renameWidthFirst(file)
        }
      }
    }
    renameCount
  }

  def parseArgs(args: Seq[String]): Unit = {
    if (args.isEmpty) {
      usage()
    }
    eachArg(args.toSeq, usage) {
      case fname if Paths.get(fname).isDirectory =>
        dir = Some(Paths.get(fname))
      case fname if Paths.get(fname).isFile =>
        files ::= Paths.get(fname)
      case "-v" =>
        verbose = true
      case "-b" =>
        showBadParts = true
      case other =>
        sys.error(s"not a file or a directory [$other]")
    }
    if (files.isEmpty && dir == None) {
      dir = Some(Paths.get("."))
    }
  }
  /*
    if( file.toString.contains(" ") ){
      eprintf("rename [%s] to ",file)
      val (newFile, success) = file.renameRemovingCruft()
      if( newFile.exists ){
        eprintf("collision with mv '%s' %s\n",file.toString,newFile.toString)
      }
    }
   */

  lazy val pathChars         = "[-:_a-zA-Z0-9\\./]"                // defines "legal" path characters
  lazy val probChars: String = pathChars.replaceFirst("\\[", "[^") // matches illegal portions of the path

  lazy val bogusChars = " \t\b\r\n()@$~" // chars that should NOT appear in filenames
  lazy val probSubstrings: immutable.IndexedSeq[String] = for {
    i <- 0 until bogusChars.size
    sub = bogusChars.substring(i, i + 1)
  } yield sub

  lazy val NormalPathPattern: Regex = s"(${pathChars}+)".r
  lazy val FunkyPathPattern: Regex  = s"(.*)(${probChars}+)(.*)".r
  lazy val q                        = "\""
  def processPath(file: Path): Int = {
    // sys.error(s"\nNormalPathPattern[${NormalPathPattern}]\nFunkyPathPattern[${FunkyPathPattern}]")
    var renameNumber = 0
    val spath        = file.posx
    if (spath.contains("(")) {
      hook += 1
    }
    spath match {
    case NormalPathPattern(ff @ _) =>
//      eprintf("normal: %s\n",ff)
    case FunkyPathPattern(pre, mid, post) =>
      val badparts = reportDiscrepancy(file)
      var fixed    = spath.replaceAll("[$]", "S").replaceAll("[#]", "P").replaceAll("[~]$", "9").replaceAll(s"${probChars}+", "-")

      // we only want to modify the basename, not the parent path
      val ff   = fixed.split("/").reverse.toList
      var last = ff.head
      last = last.replaceAll("[-]{2,}", "-")
      last = last.replaceAll("^[-]+|[-_]+$", "")
      last = last.replaceAll("/[-]+", "/")
      last = last.replaceAll("[-]+/", "/")
      last = last.replaceAll("[-]+\\.", ".")
      fixed = (last :: ff.tail).reverse.mkString("/")

      if (showBadParts || badparts.contains("$") || badparts.contains("~")) {
        eprintf("# requires rename:\n file[%s]\n", file)
        eprintf("fixed[%s]\n", fixed)
        eprintf("pre[%s] mid[%s] post[%s]\n", pre, mid, post)
        eprintf("file[%s]\nfixed[%s]\n", file, fixed)
        eprintf("bad parts: [%s]\n", badparts)
      }
      val testFile = Paths.get(fixed)
      eprintf("# rename file:\n# [%s]\n# [%s]\n", file.posx, testFile.posx)

      val fileParent = file.parent.realpath.posx.toLowerCase
      val testParent = testFile.parent.realpath.posx.toLowerCase
      if (testParent != fileParent) {
        eprintf("original file parent[%s]\n", fileParent)
        eprintf("renamed  file parent[%s]\n", testParent)
        sys.error(s"original file and intended rename not below same parent:\n${file.posx}\n${testFile.posx}")
      }
      def cksum(p: Path): String = {
        val fname       = p.posx
        val (sum, size) = Cksum.gnuCksum(fname.getBytes)
        sum.toString
      }
      if (testFile.exists) {
        eprintf("%s\n", cksum(file))
        eprintf("%s\n", cksum(testFile))
        sys.error(s"collision: original file / intended rename already exists:\n${file.posx}\n${testFile.posx}")
      }
      val escapedFname = spath.replaceAll("([\\$])", "\\\\$1")
      printf("mv %s %s\n", q + escapedFname + q, q + fixed + q)
      renameNumber += 1
    case other =>
      sys.error(s"other[$other]")
    }
    if (renameNumber > 0 && verbose) {
      eprintf("processPath[%s]\n", file)
    }
    renameNumber
  }
  def shellExec2(str: String): List[String] = {
    import scala.sys.process.*
    val cmd = Seq(bashExe, "-c", str)
    cmd.lazyLines_!.toList
  }

  def reportDiscrepancy(bf: Path): String = {
//    val boge = probSubstrings
    val ff        = bf.stdpath
    var offending = List.empty[String]
    for (cc <- probSubstrings) {
      if (ff.contains(cc)) {
        offending ::= cc
      }
    }
    offending.distinct.reverse.mkString("|")
  }
}
