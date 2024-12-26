#!/usr/bin/env -S scala -cp target/scala-3.4.2/classes
//package vastblue.demo

import vastblue.unifile.*
import vastblue.file.ProcfsPaths.*

object ProcTests {
  var verbose = false
  def main(args: Array[String]): Unit = {
    for (arg <- args) {
      arg match {
      case "-v" =>
        verbose = true
      }
    }

    if (isLinux || isWinshell) {
      val dir      = "/proc".path
      val proc     = Procfs("/proc")
      val subfiles = proc.subfiles.toIndexedSeq
      if (verbose) {
        for (f <- subfiles) {
          printf("%s\n", f)
        }
      }
      for (p <- procFiles) {
        printf("# %s\n%s\n", p, p.firstline)
      }
    } else {
      printf("/proc filesystem not supported in os [%s]\n", uname)
    }
  }

  lazy val procFiles = Seq(
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
  ).map { _.path }

  def dumpProcTree(procDir: Procfs, depth: Int = 0): Unit = {
    for (sub <- procDir.subfiles) {
      val isDir = sub.isDir
      val spath = sub.filepath
      if (sub.filepath.contains("/cmdline")) {
        val cmdline = catv(sub.filepath).map { s => s"'$s'" }
        printf("%s: [%s]\n", sub.filepath, cmdline.mkString(" "))
      } else if (verbose) {
        printf("%s\n", sub)
      }
      if ((isDir && depth < 2) && (verbose || sub.filepath.matches("/proc/[0-9]+/"))) {
        dumpProcTree(sub, depth + 1)
      }
    }
  }
}
