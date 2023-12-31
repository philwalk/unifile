package vastblue

import vastblue.unifile.*
import vastblue.Platform.{SCALA_HOME, _pwd, here, hereDrive}
import org.scalatest.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

// PathExtensions
class MainSpec extends AnyFunSpec with Matchers with BeforeAndAfter {
  describe ("MainArgs") {
    it ("should tolerate empty main.args") {
      val emptyArgs  = Array.ofDim[String](0)
      val argv       = prepArgv(emptyArgs.toSeq)
      val thisScript = argv.head
      var args       = argv.tail.toList
    }
  }
}

