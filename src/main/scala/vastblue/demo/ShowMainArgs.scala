//#!/usr/bin/env -S scala @./atFile
package vastblue.demo

import vastblue.unifile.*

object ShowMainArgs {
  def main(args: Array[String]): Unit = {
    for ((arg, i) <- args.zipWithIndex) {
      printf("args(%2d): [%s]\n", i, arg)
    }
    val argv = prepArgv(args.toSeq)
    for ((arg, i) <- argv.zipWithIndex) {
      printf("argv(%2d): [%s]\n", i, arg)
    }
  }
}
