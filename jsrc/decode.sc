#!/usr/bin/env -S scala -cp target/scala-3.3.1/classes

import vastblue.pallet._

/*
 * Attempt to decode input file bytes as `utf-8`.
 * If validation errors occur, assume `ISO_8859_1`.
 *
 * Report the encoding used.
 * In verbose mode, also print the content.
 */
object Decoder {
  var verbose = false
  var infiles = Seq.empty[Path]

  def usage(m: String = ""): Nothing = {
    if (m.nonEmpty) {
      printf("%s\n", m)
    }
    printf("usage: %s <file-path-1> [<file-2> ...]\n", sys.props("script.path"))
    sys.exit(1)
  }

  def main(args: Array[String]): Unit = {
    for (arg <- args.toSeq) {
      arg match {
      case "-v" =>
        verbose = true
      case f if Paths.get(f).toFile.isFile =>
        infiles :+= Paths.get(f)
      }
    }
    if (infiles.isEmpty) {
      usage()
    }
    for (p <- infiles) {
      val (charset, str) = p.charsetAndContent
      System.err.printf("charset: %s\n", charset)
      if (verbose) {
        printf("%s\n", str)
      }
    }
  }
}
