#!/usr/bin/env -S scala

import vastblue.pallet._

object Fstab {
  def main(args: Array[String]): Unit = {
    // `posixroot` is the native path corresponding to "/"
    // display the native path and lines.size of /etc/fstab
    val p = Paths.get("/etc/fstab")
    // format: off
    printf("env: %-10s| posixroot: %-12s| %-22s| %d lines\n",
      uname("-o"), posixroot, p.norm, p.lines.size)
  }
}
