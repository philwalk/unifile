//#!/usr/bin/env -S scala -cp target/scala-3.3.3/classes
package vastblue.demo

object Hostname {
  def main(args: Array[String]): Unit =
    printf("%s\n", vastblue.unifile.hostname)
}
