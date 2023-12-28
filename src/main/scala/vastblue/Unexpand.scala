package vastblue

import java.nio.file.{Paths => JPaths}

object Unexpand {
  private var hook = 0

  def unexpandArgs(argv: Seq[String], argz: Seq[String]): Seq[String] = {
    if (argz.exists(hasSpace(_))) {
      hook += 1
    }
    val argzHasGlob: Boolean  = argz.exists(isGlob(_))
    val argvHasSpace: Boolean = argv.exists(hasSpace(_))
    if (!argzHasGlob) {
      argv
    } else if (!argvHasSpace) {
      argz
    } else {
      // both argv-spaces and argz-globs present
      var state = Unexpand(argv, argz)
      unexpand(state)
      state.resultLeft ++ state.resultRite
    }
  }

  // argv: a.*       , "a b"
  // argz: a.sc, b.sc, a, b
  var mergeDepth = 0
  def unexpand(state: Unexpand): Unit = {
    val prevstate = state.indices.toList
    state.update()
    val newstate = state.indices.toList
    if (mergeDepth > 3) {
      hook += 1
    }
    if (state.notMerged && prevstate != newstate) {
      mergeDepth += 1
      unexpand(state)
      mergeDepth -= 1
    }
  }
  def isGlob(s: String): Boolean = {
    s.exists {
      case '*' | '?' => true
      case _         => false
    }
  }

  def hasSpace(s: String): Boolean = {
    s.exists {
      case ' ' | '\t' | '\n' | '\r' => true
      case _                        => false
    }
  }
}

case class Unexpand(argv: Seq[String], argz: Seq[String], var indices: Array[Int] = Array.ofDim[Int](4)) {
  import Unexpand._
  if (this.isNil) {
    this.reset()
  }
  def lvi = indices(0)
  def lzi = indices(1)
  def rvi = indices(2)
  def rzi = indices(3)

  def lvi_+=(n: Int): Unit = indices(0) += n
  def lzi_+=(n: Int): Unit = indices(1) += n
  def rvi_-=(n: Int): Unit = indices(2) -= n
  def rzi_-=(n: Int): Unit = indices(3) -= n

  def lvi_=(n: Int): Unit = indices(0) = n
  def lzi_=(n: Int): Unit = indices(1) = n
  def rvi_=(n: Int): Unit = indices(2) = n
  def rzi_=(n: Int): Unit = indices(3) = n

  def isNil: Boolean = !indices.exists(_ != 0)

  var resultLeft = Vector.empty[String]
  var resultRite = Vector.empty[String]
  def reset(): Unit = {
    lvi = 0
    lzi = 0

    rvi = argv.size - 1
    rzi = argz.size - 1

    resultLeft = Vector.empty[String]
    resultRite = Vector.empty[String]
  }
  def trimLeft(str: String, vIncr: Int = 1, zIncr: Int = 1): Unit = {
    if (isGlob(str) || hasSpace(str)) {
      hook += 1
    }
    if (lvi <= rvi) {    // <= gives trimLeft priority over the middle
      resultLeft :+= str // append
      lvi += vIncr
    } else {
      hook += 1
    }
    if (lzi <= rzi) {
      lzi += zIncr
    } else {
      hook += 1
    }
    hook += 1
  }
  def trimRite(str: String, vIncr: Int = 1, zIncr: Int = 1): Unit = {
    if (isGlob(str) || hasSpace(str)) {
      hook += 1
    }
    if (rvi >= lvi) {
      resultRite +:= str // prepend
      rvi -= vIncr
    } else {
      hook += 1
    }
    if (rzi > lzi) {
      rzi -= zIncr
    } else {
      hook += 1
    }
    hook += 1
  }

  def skipLeadingMatches(): Unit = {
    var av = argv(lvi)
    var az = argz(lzi)
    if (av == az) {
      trimLeft(av)
    } else if (hasSpace(av)) {
      // need to combine consecutive argz to match av
      val avsubs = av.split(" ")

      az = argz.take(avsubs.size).mkString(" ")
      if (av == az) {
        trimLeft(av, 1, avsubs.size)
      }
    } else if (isGlob(az)) {
      if (lvi < rvi) {
        val regex = glob2regex(az)
        if (av.matches(regex)) {
          trimLeft(az, 1, 0)
          av = argv(lvi)
        }
        while (av.matches(regex)) {
          lvi += 1 // only decrement lvi
          av = argv(lvi)
        }
      } else {
        hook += 1
      }
    }
  }

  def skipTrailingMatches(): Unit = {
    var av = argv(rvi)
    var az = argz(rzi)
    if (av == az) {
      trimRite(av)
    } else if (hasSpace(av)) {
      // need to combine consecutive argz to match av
      val avsubs = av.split(" ")
      val zli    = rzi - avsubs.size + 1

      az = argz.drop(zli).take(avsubs.size).mkString(" ")
      if (av == az) {
        trimRite(av, 1, avsubs.size)
      }
    } else if (isGlob(az)) {
      if (lvi < rvi) {
        val regex = glob2regex(az)
        if (av.matches(regex)) {
          trimRite(az, 1, 0)
          av = argv(rvi)
          while (av.matches(regex)) {
            rvi -= 1
            av = argv(rvi)
          }
        }
      } else {
        hook += 1
      }
    }
  }

  def glob2regex(glob: String): String = {
    val regex = glob.replaceAll("[.]", "\\.").replaceAll("[*]", ".*").replaceAll("[?]", ".")
    ".*" + regex
  }

  def update(): Unit = {
    skipLeadingMatches()
    skipTrailingMatches()
  }
  def notMerged: Boolean = {
    val Array(lv, lz, rv, rz) = indices
    lv < rv || lz < rz
  }
}
