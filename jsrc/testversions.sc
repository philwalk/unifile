#!/usr/bin/env -S scala @./atFile

import vastblue.pallet._
import scala.sys.process._

def main(args: Array[String]): Unit =
  // show runtime scala VERSION
  val scalaHome = sys.props("scala.home")
  val version   = Paths.get(s"$scalaHome/VERSION").contentAsString.trim
  printf("%s\n", version)

  // uname
  printf("%s\n", "uname".!!.trim)
  // uname -a
  printf("%s\n", Seq("uname", "-a").lazyLines_!.toList.mkString(""))
