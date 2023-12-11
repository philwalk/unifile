#!/usr/bin/env -S scala-cli shebang

//> using scala "3.3.1"
//> using lib "org.vastblue::pallet::0.10.1"

import vastblue.pallet.*

lazy val bashPath = where("bash").path

def main(args: Array[String]): Unit =
  printf("userhome: [%s]\n", userhome)
  import scala.sys.process.*
  val progname = if (isWindows) {
    "where.exe"
  } else {
    "which"
  }
  val whereBash = Seq(progname, "bash").lazyLines_!.take(1).mkString
  printf("first bash in path:\n%s\n", whereBash)
  printf("%s\n", bashPath)
  printf("%s\n", bashPath.realpath)
  printf("%s\n", bashPath.toRealPath())
  printf("posixroot: %s\n", posixroot)
  printf("sys root:  %s\n", where("bash").norm.replaceAll("(/usr)?/bin/bash.*", ""))

main(args)
