//#!/usr/bin/env -S scala3
package vastblue.util

import vastblue.unifile.*

/**
* Generic Command line arguments parser.
* See CommandLineDemo class below for usage example.
*/
object ArgsUtil {
  type Big = BigDecimal
  def big(str: String): Big = {
    val stripped = str.replaceAll("[^-.0-9]", "").trim
    BigDecimal(stripped)
  }

  lazy val debugArgs: Boolean = System.getenv("DEBUG_EACHARG") match {
  case null => false
  case _    => true
  }

  var pusage: String => Nothing = defaultUsage
  var pargs: Seq[String]        = Seq.empty[String]
  var pindex: Int               = 0
  var skips: Int                = 0

  def defaultUsage(msg: String = ""): Nothing = _usage(msg, Seq("[options]"))

  def parg = thisArg

  def thisArg: String = {
    pargs(pindex)
  }

  def noMoreArgs: Boolean  = peekNext == ""
  def hasMoreArgs: Boolean = !noMoreArgs

  def indexNext: Int  = pindex + skips
  def argNext: String = pargs(indexNext)

  def nextArg: String = {
    peekNext
  }

  def consumeArgs(n: Int): Seq[String] = {
    if (pargs.size < n) {
      pusage(s"error: request for ${n} args failed, only have ${pargs.size} available : ${pargs}")
    }
    var list = List.empty[String]
    while (list.size < n) {
      list ::= consumeNext
    }
    list.reverse
  }

  /**
  * argument following current arg, with error checking.
  */
  def consumeNext: String = {
    skips += 1
    if (debugArgs) {
      printf("consumeNext: skips[%s],pindex[%s],pargs[%s],thisArg[%s],argNext[%s]\n", skips, pindex, pargs, thisArg, argNext)
    }
    indexNext match {
    case idx if idx < pargs.size =>
      pargs(idx)
    case _ =>
      pusage(s"error: ${parg} not followed by at least ${skips} parameter(s)")
    }
  }
  lazy val NumericPattern = """([-+]?\d[\d\.]*)""".r
  def consumeBigDecimal: Big = {
    val str = consumeNext
    str match {
    case NumericPattern(_) =>
      big(str)
    case _ =>
      _usage(s"not numeric: [$str]", Seq.empty[String])
    }
  }
  def consumeDouble: Double = {
    consumeBigDecimal.toDouble
  }
  def consumeLong: Long = {
    consumeBigDecimal.toLong
  }
  def consumeInt: Int = {
    consumeBigDecimal.toInt
  }

  def peekNext: String = {
    pindex + skips + 1 match {
    case idx if idx < pargs.size =>
      pargs(idx)
    case _ =>
      ""
    }
  }

  def eachArg(args: Seq[String], usage: (String) => Nothing = defaultUsage)(custom: String => Unit): Unit = {
    pusage = usage
    pargs = args.toList
    skips = 0
    for ((arg, i) <- args.zipWithIndex) {
      if (debugArgs) System.err.printf("%d:[%s]\n".format(i, arg))
      pindex = i
      skips match {
      case 0 =>
        custom(arg)
      case _ =>
        if (debugArgs) {
          printf("skip consumed arg [%d:%s]\n", i, arg)
        }
        skips -= 1
      }
    }
  }
  def usageShortname: String = {
    if (scriptName.nonEmpty) {
      scriptName.path.stdpath // drop drive letter, if pwd on working drive
    } else if (scriptPath.isFile) {
      scriptPath.name
    } else {
      val s = propOrElse("sun.java.command", scriptName)
      s match {
      case "" =>
        vastblue.Script.getClassName(this.getClass).replaceAll("[$].*$", "").trim
      case s if s.matches(".*[.]sc[al]+( .*)$") =>
        s.split(" ").dropWhile(!_.contains(".sc")).take(1).mkString("")
      case s =>
        s.replaceAll("[$].*$", "").trim
      }
    }
  }

  def _usage: (String, Seq[String]) => Nothing = Usage.usage
  object Usage {
    def usage(msg: String, info: Seq[String]): Nothing = {
      if (msg != "") System.err.printf("%s\n", msg)
      val shortname = usageShortname

      System.err.printf("usage: %s %s\n", shortname.posx, info.mkString("\n"))
      System.getenv("WSLTEST") match {
      case "19" =>
        sys.exit(0)
      case _ =>
        sys.exit(2)
      }
    }
  }
  def main(args: Array[String]): Unit =
    _usage("only a Northern song", Nil)
}
