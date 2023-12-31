package vastblue.file

import vastblue.Platform.*
import vastblue.unifile.posx

import java.io.File as JFile
import java.nio.file.Path as JPath
import java.nio.file.{Files as JFiles, Paths as JPaths}
import scala.collection.immutable.ListMap
import scala.util.control.Breaks.*
import java.io.{BufferedReader, FileReader}
import scala.util.Using
import scala.sys.process.*
import scala.jdk.CollectionConverters.*

object ProcfsPaths {
  private var hook = 0
  def hasProcfs: Boolean = _isLinux || _isWinshell

  def rootSeg(s: String): String = {
    val posix = posx(s)
    if (!posix.startsWith("/")) "" else posix.drop(1).split("/").head
  }
  def pathSegs(s: String): List[String] = {
    val posix = posx(s)
    if (posix.startsWith("/")) {
      posix.drop(1).split("/").toList
    } else {
      "." :: posix.drop(1).split("/").toList
    }
  }

  def isProcfs(s: String): Boolean = hasProcfs && rootSeg(s) == "proc"

//  val procFiles = Seq(
//    "/proc/cpuinfo",
//    "/proc/devices",
//    "/proc/filesystems",
//    "/proc/loadavg",
//    "/proc/meminfo",
//    "/proc/misc",
//    "/proc/partitions",
//    "/proc/stat",
//    "/proc/swaps",
//    "/proc/uptime",
//    "/proc/version",
//  )

  // List dirs && files below a procfs directory.
  // conventions:
  //  list dirs first, then files
  //  append trailing slash to dirs
  //
  // Input and output are strings.
  def procfsSubfiles(procdir: String): Seq[String] = {
    if ((_isWinshell || _isLinux) && procdir.startsWith("/proc")) {
      val dcmd = Seq(_findExe, procdir, "-maxdepth", "1", "-type", "d")
      val dirs: Seq[String] = {
        for {
          dir <- dcmd.lazyLines_!.toSeq
          if dir != procdir
          if !procReject.contains(dir)
        } yield s"$dir/"
      }.toIndexedSeq
      val files = Seq(_findExe, procdir, "-maxdepth", "1", "-type", "f").lazyLines_!.toSeq
      dirs ++ files
    } else {
      Nil
    }
  }
  lazy val procReject = Set(
    "/proc/registry",
    "/proc/registry32",
    "/proc/registry64",
    "/proc/sys",
  )

  def procfsExists(parent: String, basename: String, isDir: Boolean): Boolean = {
    val ftype = if (isDir) "d" else "f"
    val cmd   = Seq(_findExe, parent, "-maxdepth", "1", "-type", ftype, "-name", basename)
    cmd.lazyLines_!.nonEmpty
  }

  // useful for converting /proc/12345/cmdline into command line
  def catv(filepath: String): Array[String] = {
    val cmd = Seq(_bashExe, "-c", s"[ -e $filepath ] && ${_catExe} -v $filepath")
    cmd.lazyLines_!.mkString.split("\\^@")
  }

  def cmdlines: Seq[(String, String)] = {
    val cmd = Seq(_findExe, "/proc/", "-maxdepth", "2", "-type", "f", "-name", "cmdline")
    val results = {
      for {
        pf <- cmd.lazyLines_!
        cmdline = catv(pf).map { s => s"'$s'" }.mkString(" ")
      } yield (pf, cmdline)
    }.toList
    results
  }

  object Procfs {
    def apply(path: String): Procfs = {
      assert(path.startsWith("/"))
      new Procfs(path)
    }
  }
  class Procfs(val filepath: String) {
    val segs: List[String] = pathSegs(filepath)
    val basename           = segs.last.takeWhile(_ != '/')     // drop trailing slash
    val directorySyntax    = filepath.length > basename.length // true if has a trailing slash

    def parent: String  = "/" + segs.init.mkString("/")
    def exists: Boolean = procfsExists(parent, basename, directorySyntax)
    def isFile: Boolean = exists && !directorySyntax
    def isDir: Boolean  = exists && directorySyntax
    def isDirectory     = isDir

    // format: off
    def subfiles: Seq[Procfs] = if (isDir) procfsSubfiles(filepath).map { Procfs(_) } else Nil

    override def toString = filepath
  }
}
