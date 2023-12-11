#!/usr/bin/env -S scala

import vastblue.pallet.*
import vastblue.file.Util.*

def main(args: Array[String]): Unit =
  printf("%s\n", nativePathString("./bin".path))
  printf("%s\n", nativePathString("./bin".path.relpath))
  printf("%s\n", "./bin".path.relpath.norm)
  printf("%s\n", "./bin".path.relativePath)
  printf("%s\n", "./bin".path.stdpath)
  printf("%s\n", "./bin".path.norm)
