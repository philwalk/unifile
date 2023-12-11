#!/usr/bin/env -S scala -deprecation -cp target/scala-3.3.1/classes

import vastblue.pallet._
import vastblue.file.ProcfsPaths._

object ProcCmdline {
  var verbose = false
  def main(args: Array[String]): Unit = {
    for (arg <- args) {
      arg match {
      case "-v" =>
        verbose = true
      }
    }
    if (isLinux || isWinshell) {
      printf("script name: %s\n\n", scriptName)
      // find /proc/[0-9]+/cmdline files
      for ((procfile, cmdline) <- cmdlines) {
        if (verbose || cmdline.contains(scriptName)) {
          printf("%s\n", procfile)
          printf("%s\n\n", cmdline)
        }
      }
    } else {
      printf("procfs filesystem not supported in os [%s]\n", osType)
    }
  }
}
