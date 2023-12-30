//#!/usr/bin/env -S scala
package vastblue.demo

import vastblue.unifile.*

object Fstab {
  def main(args: Array[String]): Unit = {
    // `shellRoot` is the native path corresponding to "/"
    // display the native path and lines.size of /etc/fstab
    val p = Paths.get("/etc/fstab")
    // format: off
    printf("env: %-10s| shellRoot: %-12s| %-22s| %d lines\n",
      uname("-o"), shellRoot, p.posx, p.lines.size)
  }
}
