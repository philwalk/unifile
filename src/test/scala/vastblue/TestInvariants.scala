package vastblue

import vastblue.unifile.*
import vastblue.Platform.{SCALA_HOME, _pwd, here, hereDrive}
import org.scalatest.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

// PathExtensions
class TestInvariants extends AnyFunSpec with Matchers with BeforeAndAfter {
  describe ("invariants") {
    // verify test invariants
    describe ("working drive") {
      val hd = Platform.hereDrive
      printf("hd [%s]\n", hd.toString)
      val workingDrive: String = Platform.workingDrive.string
      printf("workingDrive [%s]\n", workingDrive)
      it (" should be correct for os") {
        if (isWindows) {
          assert(hd.equalsIgnoreCase(workingDrive))
          assert(hereDrive.matches("[a-zA-Z]:"))
        } else {
          assert(hereDrive.isEmpty)
        }
      }
    }

    describe("pwd") {
      it ("should be correct wrt rootDrive for os") {
        val workingDrive: String = Platform.workingDrive.string
        if (isWindows) {
          val currentWorkingDrive = Platform.here.take(2).mkString
          assert(currentWorkingDrive.matches("[a-zA-Z]:"))
          assert(workingDrive.equalsIgnoreCase(currentWorkingDrive))
        } else {
          assert(workingDrive.isEmpty)
        }
      }
    }
    describe("scalaHome") {
      it ("should be defined") {
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
