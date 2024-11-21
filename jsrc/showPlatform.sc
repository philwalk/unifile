#!/usr/bin/env -S scala @./atFile
//package vastblue.demo

import vastblue.Platform.{cygpathExe, cygpathM, etcdir, exeSuffix, findAllInPath, winshellBinDirs}
import vastblue.unifile.*

object ShowPlatform {
  // display discovered aspects of the runtime environment
  def main(args: Array[String]): Unit = {
    for (root <- winshellBinDirs) {
      printf("available installed posix environment root: %s\n", root)
    }
    printf("whichExe     [%s]\n", whichExe)
    for (f <- findAllInPath("bash")) {
      printf("found: [%s]\n", f)
    }
    printf("scriptName   [%s]\n", scriptName)
    printf("scriptPath   [%s]\n", scriptPath)
    printf("osName       [%s]\n", osName)
    printf("osType       [%s]\n", osType)
    printf("shellRoot    [%s]\n", shellRoot)
    printf("unameLong    [%s]\n", unameLong)
    printf("unameShort   [%s]\n", unameShort)
    printf("javaHome     [%s]\n", javaHome)
    printf("exeSuffix    [%s]\n", exeSuffix)
    printf("isCygwin     [%s]\n", isCygwin)
    printf("isMsys       [%s]\n", isMsys)
    printf("isGitSdk     [%s]\n", isGitSdk)
    printf("bashPath     [%s]\n", bashPath)
    printf("bashExe      [%s]\n", bashExe)
    printf("cygpathExe   [%s]\n", cygpathExe)
    printf("whichExe     [%s]\n", whichExe)
    printf("hostname     [%s]\n", hostname)
    printf("etcdir       [%s]\n", etcdir)
    printf("cygdrive2root[%s]\n", cygdrive2root)

    val ls = where("ls")
    printf("which ls     [%s]\n", ls.path.stdpath)
    val lspath = cygpathM(ls)
    printf("cygpath ls   [%s]\n", lspath)
  }
  lazy val prognames = Seq(
    "bash",
    "cat",
    "find",
    "which",
    "uname",
    "ls",
    "tr",
    "ps",
  )
}
