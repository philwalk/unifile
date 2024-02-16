#!/usr/bin/env -S scala @./atFile
//package vastblue.demo

import vastblue.unifile.*

object Dirlist {
  def main(args: Array[String]): Unit = {
    ".".path.paths.filter { _.isDirectory }.foreach { (p: Path) => printf("%s\n", p.posx) }
  }
}
