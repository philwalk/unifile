#!/usr/bin/env -S scala @./atFile

import vastblue.file._
import vastblue.file.{EzPath, PathUnx, PathWin}

def main(args: Array[String]): Unit = {
  var paths = if (args.nonEmpty) {
    args.toSeq
  } else {
    Seq("/opt/ue", "f:/opt/ue")
  }
  for (a <- paths) {
    printf("u: %s : %s\n", a, PathUnx(a).toString)
    printf("w: %s : %s\n", a, PathWin(a).toString)
    printf("e: %s : %s\n", a, EzPath(a).toString)
  }
}
