#!/usr/bin/env -S scala
package vastblue.demo

import vastblue.unifile.*
import vastblue.file.Util.*

object NativePathString {
  def main(args: Array[String]): Unit = {
    val pwdabs = pwd.abs
    val strings = Seq(
      ".",
      pwdabs,
      s"$pwdabs/..",
      "./bin",
      "C:/bin",
      "Z:/bin",
      "../",
    )

    for (fnameStr <- strings) {
      showExtensions(fnameStr)
    }
  }
  def showExtensions(fnameString: String): Unit = {
    val p = fnameString.path
    printf("\n")
    printf("fnameString:     %s\n", fnameString)
    printf("p.toString:      %s\n", p)
    printf("p.abs:           %s\n", p.abs)
    printf("p.stdpath:       %-32s     // <-- depends on if path is on current working drive\n", p.stdpath)
    printf("p.posx:          %s\n", p.posx)
    printf("p.locl:          %s\n", p.locl)
    printf("p.dospath:       %s\n", p.dospath)
    printf("p.relpath:       %s\n", p.relpath)
    printf("p.relpath.posx:  %s\n", p.relpath.posx)
    printf("p.relativePath:  %s\n", p.relativePath)
  }
}
