#!/usr/bin/env -S scala
//package vastblue.demo

import vastblue.unifile.*

object PathStrings {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      printf("usage: %s <filepath-1> [<filepath2> ...]\n", scriptPath.name)
    } else {
      val argv = prepArgv(args.toSeq)
      for (a <- argv) {
        printf("========== arg[%s]\n", a)
        printf("stdpath   [%s]\n", Paths.get(a).stdpath)
        printf("posxpath  [%s]\n", Paths.get(a).posx)
        printf("dospath   [%s]\n", Paths.get(a).dospath)
      }
    }
  }
}
