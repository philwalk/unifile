#!/usr/bin/env -S scalaQ
//#!/usr/bin/env -S scala-cli shebang
//package vastblue.demo

//> using dep "org.vastblue::unifile:0.3.12"
import vastblue.unifile.*

//CmdInfo.main(args)
object CmdInfo {
  def main(args: Array[String]): Unit = {

    printf("[%s]\n", unifile.Info.scalaRuntimeVersion)
    printf("# args [%s]\n", args.toSeq.mkString("|"))
    for ((arg, i) <- args.zipWithIndex) {
      printf("A:  args(%d) == [%s]\n", i, arg)
    }
    val argv = prepArgv(args.toSeq)

    printf("\n# argv [%s]\n", argv.toSeq.mkString("|"))
    for ((arg, i) <- argv.zipWithIndex) {
      printf("B:  argv(%d) == [%s]\n", i, arg)
    }
  }
}
