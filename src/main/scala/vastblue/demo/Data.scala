#!/usr/bin/env scala
package vastblue.demo

import vastblue.file.HereData.*

object Data {
  def main(args: Array[String]): Unit = {
    for (s <- DATA) {
      printf("%s\n", s)
    }
  }
}
/* __DATA__
line 1
line 2
line 3
*/
