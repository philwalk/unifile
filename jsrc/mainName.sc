#!/usr/bin/env -S scala @./atFile -deprecation
//package vastblue.demo

import vastblue.pallet.*

object MainName {
  def main(args: Array[String]): Unit = {
    val argv = prepArgs(args.toSeq)
    printf("scriptName: [%s]\n", argv.head)
    printf("scriptArgs: [%s]\n", argv.tail.mkString("|"))
    printf("thisProc: %s\n", thisProc)
    for ((a, i) <- argv.zipWithIndex) {
      printf(" %2d: [%s]\n", i, a)
    }
  }
}
