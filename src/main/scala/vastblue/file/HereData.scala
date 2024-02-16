//#!/usr/bin/env -S scala
package vastblue.file

import vastblue.unifile.*
import scala.util.Using
import java.io.{BufferedReader, FileReader}

/*
 * Provides a view of the DATA section, if present at the bottom of a source file.
 * The data rows must be delimited within a tagged multi-line comment,
 * so if the following 4 lines were at the end of the file, they would
 * suffice.
 */

/* __DATA__
a b c d e
f g h i j
*/

// The __DATA__ or __END__ section is ignored unless at the end of the file.
// The above facsimile is not valid and will be ignored.

object HereData {
  private var hook = 0
//  def DATA = scriptData // similar to perl or ruby

  var verbose = false
  def main(args: Array[String]): Unit =
    verbose = args.contains("-v")
    printf("script is [%s]\n", scriptFname)
    printf("source from stack is [%s]\n", sourceFileFromStack)
    DATA.foreach { printf("[%s]\n",_) }

  def readLines(p: Path): Seq[String] = {
    if ( ! p.toFile.isFile ){
      Nil
    } else {
      Using.resource(new BufferedReader(new FileReader(p.toString))) { reader =>
        Iterator.continually(reader.readLine()).takeWhile(_ != null).toSeq
      }
    }
  }

  def scriptFname = Option(sys.props("script.path")) match {
    case None => sourceFileFromStack
    case Some(path) => path
  }

  // Find a source file below '.' matching the source name on the stack.
  // Caveats:
  //    correct source file must be below the current working directory.
  //    might find the wrong source file, if multiple are present.
  def sourceFileFromStack: String = findBelowCwd(stackPathSourceFilename)

  // Return the name of the source file by examining the stack.
  // assumes compile with debugging enable.
  def stackPathSourceFilename: String = {
    val result = new java.io.StringWriter()
    new RuntimeException("stack").printStackTrace(new java.io.PrintWriter(result))
    val stack = result.toString.split("[\r\n]+").toList
    if (verbose) for( s <- stack ){ System.err.printf("[%s]\n",s) }
    stack.filter { str => str.contains(".main(") }.map {
      // derive main class name from stack when main object is NOT declared in source
      _.replaceAll(".*[(]","")
      .replaceAll("[)].*","")
      .replaceAll(":[0-9].*","")
      .replaceAll("\\s+at\\s+","")
    }.distinct.take(1).mkString("")
  }

  def findBelowCwd(fname: String): String = {
    val p = Paths.get(fname)
    if (! java.nio.file.Files.isRegularFile(p)) {
      Paths.get(".").pathsTree.filter { _.name == fname }.find { f => f.isFile } match {
      case None => fname
      case Some(f) => f.toString.replace('\\', '/')
      }
    } else {
      fname
    }
  }

  def DATA: Seq[String] = {
    val fname = scriptFname
    fname match {
    case "" =>
      System.err.println("not defined: script.path property!")
      Nil
    case path =>
      def beginData(line: String) = "^/\\* *__(DATA|END)__".r.matches(line.trim)
      var data = List.empty[String]
      val scriptPath = Paths.get(scriptFname)
      val lines = readLines(scriptPath).reverse.dropWhile(_.trim.isEmpty)
      lines.toList match {
      case "*/" :: tail =>
        val datalines = tail.takeWhile((s: String) => !beginData(s)).reverse
        val residue = tail.drop(datalines.length)
        residue match {
        case s :: tail if beginData(s) =>
          data = datalines
        case _ =>
          hook += 1 // no data
        }
      case _ =>
        hook += 1 // no data
      }
      data
    }
  }
}

/* __DATA__
a=1
b=2
c=3
*/
