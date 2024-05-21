//#!/usr/bin/env -S scala -deprecation
package vastblue.file

import java.io._
import java.nio.file.{Path, Paths}
import java.nio.file.attribute._
import java.nio.file._
import java.nio.file.FileVisitResult._
import java.nio.file.FileVisitOption._
import scala.io.Source

object TreeWalker {
  def main(args: Array[String]): Unit = {
    try {
      val dir      = Paths.get("F:/")
      val sfx      = ".mp4"
      var maxdepth = -1
      var tossDirs = Set(".git", "target") // ignore these
      walkTreeFast(dir, tossDirs, maxdepth = -1) { (p: Path) =>
        val name: String = p.toFile.getName
        val ok           = name.endsWith(sfx)
        if (ok) printf("%s\n", p.toAbsolutePath.toString.replace('\\', '/'))
        ok
      }
    } catch {
      case t: Throwable =>
        // t.printStackTrace()
        import vastblue.file.Util.*
        _showLimitedStack(t)
        sys.exit(1)
    }
  }

  // Walk directory tree:
  //   recovers from permission security exceptions
  //   much faster than Files.walk
  // Caveat: in Windows, pukes if directory name has trailing dot
  def walkTreeFast(dir: Path, tossDirs: Set[String] = Set.empty[String], maxdepth: Int = -1)(filt: Path => Boolean): (Long, Long) = {
    class TreeWalker(examine: Path => Boolean) extends SimpleFileVisitor[Path] {
      var (visited, matched)  = (0L, 0L)
      def stats: (Long, Long) = (visited, matched)

      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        visited += 1
        matched += (if examine(file) then 1 else 0)
        CONTINUE
      }

      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val name = dir.toFile.getName
        if (tossDirs.contains(name)) {
          SKIP_SUBTREE
        } else {
          CONTINUE
        }
      }

      override def visitFileFailed(p: Path, e: IOException): FileVisitResult = {
        System.err.println(e)
        CONTINUE
      }
    }

    val examiner = new TreeWalker(filt)
    Files.walkFileTree(dir, examiner)
    examiner.stats
  }
}
