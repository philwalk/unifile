//#!/usr/bin/env -S scala -deprecation
package vastblue.demo

import vastblue.Platform.*
import vastblue.file.ProcfsPaths.*

object ProcCmdline {
  var verbose = false
  def main(args: Array[String]): Unit = {
    for (arg <- args) {
      arg match {
      case "-v" =>
        verbose = true
      case arg =>
        printf("unrecognized arg [%s]\n", arg)
        printf("usage: %s <arg1> [<arg2> ...]\n", scriptName)
        sys.exit(1)
      }
    }
    if (_isLinux || _isWinshell) {
      printf("script name: %s\n\n", scriptName)
      // find /proc/[0-9]+/cmdline files
      for ((procfile, cmdline) <- cmdlines) {
        if (verbose || cmdline.contains(scriptName)) {
          printf("%s\n", procfile)
          printf("%s\n\n", cmdline)
        }
      }
    } else {
      printf("procfs filesystem not supported in os [%s]\n", _osType)
    }
  }
}
