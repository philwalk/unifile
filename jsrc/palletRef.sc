#!/usr/bin/env -S scala-cli shebang

//> using scala "3.4.3"
//> using dep "org.vastblue::unifile:0.3.12"

import vastblue.unifile.*

// show system memory info
if (osType != "darwin") {
  // osx doesn't have /proc/meminfo
  for (line <- "/proc/meminfo".path.lines) {
    printf("%s\n", line)
  }
}
// list child directories of "."
val cwd: Path = ".".path
for ((p: Path) <- cwd.paths.filter { _.isDirectory }) {
  printf("%s\n", p.posx)
}
