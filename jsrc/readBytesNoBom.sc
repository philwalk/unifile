#!/usr/bin/env -S scala

import java.io.{File, FileInputStream}

object NoBom {
  // utf8 BOM: 0xEF, 0xBB, 0xBF
  def cleanBytes(f: File): Array[Byte] = {
    var fis   = new FileInputStream(f)
    val count = fis.available()
    val arr   = new Array[Byte](count)
    fis.read(arr)
    fis.close()
    arr.take(3) match {
    case Array(-17, -69, -65) => // utf-8 BOM as signed bytes
      arr.drop(3)
    case _ =>
      arr
    }
  }
  def bytesNoBom(f: File): Array[Byte] = {
    val arr = java.nio.file.Files.readAllBytes(f.toPath)
    arr.take(3) match {
    case Array(-17, -69, -65) => arr.drop(3)
    case bytes                => bytes
    }
  }

  def main(args: Array[String]): Unit = {
    for (arg <- args) {
      val f = new File(arg)
      if (f.isFile) {
        // val bytes = cleanBytes(f)
        val bytes = bytesNoBom(f)
        printf("%s has %d bytes\n", arg, bytes.length)
        for (b <- bytes.take(3)) { printf(" %02X", b) }
        printf("\n")
      }
    }
  }
}
