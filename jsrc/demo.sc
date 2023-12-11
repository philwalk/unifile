#!/usr/bin/env -S scala @./atFile
//package vastblue.demo

import vastblue.pallet._
import vastblue.MainArgs

object Demo {
  def main(args: Array[String]): Unit = {
    try {
      if (osType != "darwin") {
        // show system memory info
        for (line <- "/proc/meminfo".path.lines) {
          printf("%s\n", line)
        }
      }
      // list child directories of the current working directory
      val cwd: Path = ".".path
      for ((p: Path) <- cwd.paths.filter { _.isDirectory }) {
        printf("%s\n", p.norm)
      }

      val argv = MainArgs.prepArgs(args.toSeq)
      for ((arg, i) <- argv.zipWithIndex) {
        printf("%2d: [%s]\n", i, arg)
      }
    } catch {
      case e: Exception =>
        showLimitedStack(e)
        sys.exit(1)
    }
  }
}
