#!/usr/bin/env -S scala-cli shebang
//package vastblue.demo

//> using dep org.vastblue::unifile:0.4.2

import vastblue.unifile.*
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
        printf("%s\n", p.posx)
      }

      val argv = MainArgs.prepArgv(args.toSeq)
      for ((arg, i) <- argv.zipWithIndex) {
        printf("%2d: [%s]\n", i, arg)
      }
      sys.error("show-limited-stack")
    } catch {
      case e: Exception =>
        showLimitedStack(e)
        sys.exit(1)
    }
  }
}
