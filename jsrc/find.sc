#!/usr/bin/env -S scala @${HOME}/.scala3cp
//package vastblue.demo

// shebang line error on OSX/Darwin due to non-gnu /usr/bin/env
// portable way to set classpath:
// export SCALA_OPTS="@/Users/username/.scala3cp -save"
// .scala3cp contains '-cp <path-to-pallet.jar>'

import vastblue.pallet._

// partial implementation of the gnu find utility
object Find {
  def main(args: Array[String]): Unit = {
    try {
      val parms = parseArgs(args.toSeq)

      for (dir <- parms.paths) {
        for (f <- walkTree(dir.toFile, maxdepth = parms.maxdepth)) {
          val p = f.toPath
          if (parms.matches(p)) {
            printf("%s\n", p.relpath.posixpath)
          }
        }
      }
    } catch {
      case t: Throwable =>
        showLimitedStack(t)
        sys.exit(1)
    }
  }

  def usage(m: String = ""): Nothing = {
    if (m.nonEmpty) {
      printf("%s\n", m)
    }
    printf("Usage: %s [options] [path...] [expression]\n", scriptName)
    def usageText =
      """|<path> [<path> ...]
         | [-maxdepth <N>]
         | -type [fdl]
         | [-name | -iname] <filename-glob>
         |
         |Default path is the current directory.
         |
         |Normal options (always true, specified before other expressions):
         |      -maxdepth LEVELS
         |""".stripMargin

    printf("%s\n", usageText)
    sys.exit(1)
  }

  /**
   * prepArgs returns `argv`, equivalent to C language main arguments vector.
   * jvm main#args and `argv.tail` identical if no `glob` args are passed.
   * `argv` always delivers unexpanded glob arguments, unlike main#args.
   */

  def parseArgs(_args: Seq[String]): CmdParams = {
    val cmdParms = new CmdParams()

    val argv       = prepArgs(_args) // derive C-style argv
    val thisScript = argv.head
    var args       = argv.tail.toList

    while (args.nonEmpty) {
      args match {
      case Nil => // disable warning
      case "-v" :: tail =>
        args = tail
        cmdParms.verbose = true

      case "-maxdepth" :: dep :: tail =>
        args = tail
        if (dep.matches("[0-9]+")) {
          cmdParms.maxdepth = dep.toInt
        } else {
          usage(s"-maxdepth followed by a non-integer: [$dep]")
        }

      case "-type" :: typ :: tail =>
        args = tail
        typ match {
        case "f" | "d" | "l" =>
          cmdParms.ftype = typ
        case _ =>
          usage(s"-type [$typ] not supported")
        }

      case "-name" :: nam :: tail =>
        args = tail
        if (cmdParms.verbose) printf("nam[%s]\n", nam)
        cmdParms.glob = nam

      case "-iname" :: nam :: tail =>
        args = tail
        if (cmdParms.verbose) printf("nam[%s]\n", nam)
        cmdParms.glob = nam
        cmdParms.nocase = true

      case "-z" :: tail =>
        args = tail
        cmdParms.debug = true

      case arg :: _ if arg.startsWith("-") =>
        usage(s"unknown predicate '$arg'")

      case sdir :: tail =>
        args = tail
        if (cmdParms.verbose) printf("sdir[%s]\n", sdir)
        if (sdir.contains("*") || !sdir.path.exists) {
          usage(s"not found: $sdir")
        }
        cmdParms.appendDir(sdir)
      }
    }
    if (cmdParms.debug) {
      printf("%s\n", cmdParms)
      sys.exit(1)
    }
    cmdParms.validate // might exit with usage message
    cmdParms
  }

  // command line interface parameters
  case class CmdParams(
      var _dirs: Vector[String] = Vector.empty[String],
      var glob: String = "",
      var ftype: String = "",
      var maxdepth: Int = -1,
      var nocase: Boolean = false,
      var verbose: Boolean = false,
      var debug: Boolean = false,
  ) {
    override def toString = Seq(
      s"dirs     [${dirs.mkString("|")}]",
      s"glob     [$glob]",
      s"ftype    [$ftype]",
      s"maxdepth [$maxdepth]",
      s"nocase   [$nocase]",
      s"verbose  [$verbose]",
      s"debug    [$debug]",
    ).mkString("\n")
    def dirs: Seq[String] = _dirs
    def appendDir(s: String): Unit = {
      _dirs :+= s
    }
    val validFtypes = Seq("f", "d", "l")
    def validate: Unit = {
      if (dirs.isEmpty) {
        appendDir(".") // by default, find searches "."
      }
      if (ftype.nonEmpty && !validFtypes.contains(ftype)) {
        usage(s"unknown argument to -type: $ftype")
      }
      val badpaths = paths.filter { (p: Path) =>
        !p.exists
      }
      if (badpaths.nonEmpty) {
        for ((path, dir) <- badpaths zip dirs) {
          printf("find: '%s': No such file or directory", dir)
        }
        usage()
      }
    }
    lazy val paths = dirs.map { Paths.get(_) }

    import java.nio.file.{FileSystems, PathMatcher}
    lazy val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
    def nameMatch(p: Path): Boolean = {
      matcher.matches(p.getFileName)
    }
    def typeMatch(p: Path): Boolean = {
      ftype match {
      case ""  => true
      case "f" => p.isFile
      case "d" => p.isDirectory
      case "l" => p.isSymbolicLink
      case _   => false // should never happen, ftype was validated
      }
    }
    def matches(p: Path): Boolean = {
      val nameflag = glob.isEmpty || nameMatch(p)
      val typeflag = ftype.isEmpty || typeMatch(p)
      nameflag && typeflag
    }
  }
}
