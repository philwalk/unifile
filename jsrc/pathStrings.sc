#!/usr/bin/env -S scala

import vastblue.pallet.*

object PathStrings {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      printf("usage: %s <filepath-1> [<filepath2> ...]\n", scriptPath.name)
    } else {
      val argv = prepArgs(args.toSeq)
      for (a <- argv) {
        printf("========== arg[%s]\n", a)
        printf("stdpath   [%s]\n", Paths.get(a).stdpath)
        printf("normpath  [%s]\n", Paths.get(a).norm)
        printf("dospath   [%s]\n", Paths.get(a).dospath)
      }
    }
  }
}
