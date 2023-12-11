#!/usr/bin/env -S scala-cli shebang

//> using scala "3.3.1"
//> using lib "org.vastblue::pallet::0.10.1"

import vastblue.pallet._

object FstabCli {
  def main(args: Array[String]): Unit = {
    // `posixroot` is the native path corresponding to "/"
    // display the native path and lines.size of /etc/fstab
    val p = Paths.get("/etc/fstab")
    // format: off
    printf("env: %-10s| posixroot: %-12s| %-22s| %d lines\n",
      uname("-o"), posixroot, p.norm, p.lines.size)
  }
}
FstabCli.main(args)
