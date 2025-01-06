#!/usr/bin/env -S scala-cli shebang
//package vastblue.demo

//> using scala "3.4.3"
//> using dep "org.vastblue::unifile:0.3.9"

import vastblue.unifile.*

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
