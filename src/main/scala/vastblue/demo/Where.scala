//#!/usr/bin/env -S scala
package vastblue.demo

import vastblue.unifile.*

object Where {
  def main(args: Array[String]): Unit = {
    import scala.sys.process._
    val name = if (isWindows) {
      "where.exe"
    } else {
      "which"
    }
    val whereExe = Seq(name, name).lazyLines_!.take(1).toList.mkString("").replace('\\', '/')
    printf("path of [%s] is [%s]\n", name, whereExe)
  }
}
