package vastblue

import vastblue.unifile.*
import vastblue.Platform.{SCALA_HOME, _pwd, here, hereDrive}
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
        val drivePrefix = Platform.here.take(2).mkString
        if (isWindows) {
          assert(drivePrefix.matches("[a-zA-Z]:"))
        } else {
          assert(drivePrefix.isEmpty)
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
}
