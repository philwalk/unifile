#!/usr/bin/env -S scala
package vastblue

import vastblue.pallet._

object Wait {
  def main(args: Array[String]): Unit = {
    val seconds: Int = if (args.isEmpty || args.head.startsWith("-")) 1 else args.head.toInt
    printf("waiting %d seconds ...", seconds)
    Thread.sleep(seconds * 1000L)
    printf("\n")
  }
}
