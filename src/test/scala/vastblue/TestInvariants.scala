package vastblue

import vastblue.unifile.*
import vastblue.Platform._pwd

import org.scalatest.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

// PathExtensions
class PathSpec extends AnyFunSpec with Matchers with BeforeAndAfter {
  describe ("invariants") {
    // verify test invariants
    describe ("working drive") {
      it (" should be correct for os") {
        if (isWindows) {
          assert(hereDrive.matches("[a-zA-Z]:"))
        } else {
          assert(hereDrive.isEmpty)
        }
      }
    }
    describe("pwd") {
      it ("should be correct wrt rootDrive for os") {
        if (isWindows) {
          assert(here.take(2).matches("[a-zA-Z]:"))
        } else {
          assert(!here.take(2).mkString.contains(":"))
        }
      }
    }
    describe("scalaHome") {
      it ("show not be empty") {
        eprintf("SCALA_HOME [%s]\n" , SCALA_HOME) // might be empty
        eprintf("scalaHome  [%s]\n" , scalaHome)  // should never be empty
      }
      assert(scalaHome.nonEmpty)
    }
    describe("scala3Version") {
      eprintf("scala3Version[%s]\n" ,scala3Version)
      it ("should not be empty") {
        assert(scala3Version.nonEmpty)
      }
    }
  }
  lazy val SCALA_HOME = Option(System.getenv("SCALA_HOME")).getOrElse("") // might be empty
  lazy val here  = _pwd.toAbsolutePath.normalize.toString.toLowerCase.replace('\\', '/')
  lazy val uhere = here.replaceFirst("^[a-zA-Z]:", "")
  lazy val hereDrive = {
    val hd = here.replaceAll(":.*$", "")
    hd match {
    case drive if drive >= "a" && drive <= "z" =>
      s"$drive:"
    case _ =>
      if (isWindows) {
        System.err.println(s"internal error: _pwd[$_pwd], here[$here], uhere[$uhere]")
        sys.error("hereDrive error")
      }
      ""
    }
  }

}
