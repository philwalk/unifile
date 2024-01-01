//#!/usr/bin/env -S scala
package vastblue.demo

import vastblue.unifile.*
import vastblue.file.MountMapper.*

object ShowMountMap {
  def main(args: Array[String]): Unit = {
    if (!isDarwin) {
      val cygdrivePrefix = reverseMountMap.get("cygdrive").getOrElse("not-found")
      printf("cygdrivePrefix: [%s]\n", cygdrivePrefix)
      for ((k, v) <- mountMap.toList.sortBy { case (k, v) => k.length }.reverse) {
        printf("%-22s: %s\n", k, v)
      }
    }
  }
}
