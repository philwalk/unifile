#!/usr/bin/env -S scala
//package vastblue

import vastblue.pallet._
import vastblue.file.ProcfsPaths._

object ProcTest {
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
      dumpProcTree(proc)
    } else {
      printf("/proc filesystem not supported in os [%s]\n", uname)
    }
  }

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
