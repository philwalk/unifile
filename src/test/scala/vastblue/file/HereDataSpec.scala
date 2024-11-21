package vastblue.file

import vastblue.unifile.*
import org.scalatest.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class HereDataSpec extends AnyFunSpec with Matchers with BeforeAndAfter {
  lazy val verbose = Option(System.getenv("VERBOSE_TESTS")).nonEmpty
  var hook: Int    = 0

  before {
    java.nio.file.Files.writeString(testFile1, testScriptText)
    java.nio.file.Files.writeString(testFile2, testScriptText.replace("__DATA__", "__END__"))
    java.nio.file.Files.writeString(testFile3, testScriptCounterexample)
  }

  describe("Script DATA") {
    it("should correctly read source lines following the __DATA__ marker") {
      sys.props("script.path") = testFile1.posx
      val scriptdata = vastblue.file.HereData.DATA
      val lines      = dataLines
      assert(scriptdata == lines, s"scriptData[$scriptdata]")
    }
    it("should correctly read source lines following the __END__ marker") {
      sys.props("script.path") = testFile2.posx
      val scriptdata = vastblue.file.HereData.DATA
      assert(scriptdata == dataLines, s"scriptData[$scriptdata]")
    }
    it("should ignore sections with non-spaces following the quoted text") {
      sys.props("script.path") = testFile3.posx
      val scriptdata = vastblue.file.HereData.DATA
      assert(scriptdata.isEmpty, s"scriptData[$scriptdata]")
    }
  }

  lazy val TMP: String     = sys.props("java.io.tmpdir")
  lazy val testFile1: Path = Paths.get(s"${TMP}/dataScript1.sc")
  lazy val testFile2: Path = Paths.get(s"${TMP}/dataScript2.sc")
  lazy val testFile3: Path = Paths.get(s"${TMP}/dataScript3.sc")

  lazy val testScriptText = s"""
    |#!/usr/bin/env scala -S scala
    |def main(args: Array[String]): Unit =
    |  for (s <- DATA) {
    |    printf("%s\n", s)
    |  }
    |
    |/* __DATA__
    |${dataLines.mkString("\n")}
    |*/
    """.trim.stripMargin

  lazy val testScriptCounterexample = s"""
    |#!/usr/bin/env scala -S scala
    |def main(args: Array[String]): Unit = {
    |  for (s <- DATA) {
    |    printf("%s\n", s)
    |  }
    |
    |/* __DATA__
    |${dataLines.mkString("\n")}
    |*/
    |}
    """.trim.stripMargin

  lazy val dataLines: Seq[String] = Seq(
    "line 1",
    "line 2",
    "line 3",
  )
}
