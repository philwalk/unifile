#!/usr/bin/env -S scala @./atFile
// hashbang line requires successful sbt compile
import vastblue.pallet.*

def main(args: Array[String]): Unit = {
  ".".path.paths.filter { _.isDirectory }.foreach { (p: Path) => printf("%s\n", p.norm) }
}
