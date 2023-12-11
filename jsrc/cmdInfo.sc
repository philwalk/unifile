#!/usr/bin/env -S scala

import vastblue.pallet._

def main(args: Array[String]): Unit = {
  printf("# args [%s]\n", args.toSeq.mkString("|"))
  for ((arg, i) <- args.zipWithIndex) {
    printf("A:  args(%d) == [%s]\n", i, arg)
  }
  val argv = prepArgs(args.toSeq)

  printf("\n# argv [%s]\n", argv.toSeq.mkString("|"))
  for ((arg, i) <- argv.zipWithIndex) {
    printf("B:  argv(%d) == [%s]\n", i, arg)
  }
}
