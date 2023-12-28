//#!/usr/bin/env -S scala3
package vastblue.math

import scala.util.control.Breaks.*

// drop in replacement for gnu cksum
// gnuCksum(bytes: Array[Byte]): (Long, Long)
object Cksum {
  // drop-in replacement for gnu cksum, according to
  // https://crypto.stanford.edu/~blynn/c2go/cksum.html
  def gnuCksum(bytes: Array[Byte]): (Long, Long) = {
    gnuCksum(bytes.iterator)
  }
  def gnuCksum(bytes: Iterator[Byte]): (Long, Long) = {
    val p = 0x04c11db7
    var r = 0

    val mask = (1 << 31)

    def f(ci: Int): Unit = {
      var c = ci
      if (c < 0) {
        c += 0x100
      }
      var i = 7
      while (i >= 0) {
        val msb = r & mask
        r <<= 1
        if (msb != 0) r = r ^ p
        i -= 1
      }
      r ^= c
    }

    var len = 0L
    for (b <- bytes) {
      len += 1
      f(b.toInt)
    }

    var n = len
    // simulate do-while:
    breakable {
      while (true) {
        f(n.toInt & 0xff)
        n >>>= 8 // right shift zero fill
        if (n == 0) {
          break()
        }
      }
    }
    f(0)
    f(0)
    f(0)
    f(0)

    // simulate unsigned int with Long
    val unsignedSum: Long = ~r match {
    case num if num < 0 =>
      num.toLong + 0x100000000L
    case num =>
      num.toLong
    }
    (unsignedSum, len)
  }
  def myCksum(str: String): Long = {
    val bytes         = str.getBytes
    val (sum, length) = gnuCksum(bytes.iterator)
    sum
  }
}
