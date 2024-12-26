//#!/usr/bin/env -S scala
//package vastblue.demo

//> using lib "org.vastblue::unifile:0.3.6"
import vastblue.unifile.*

object UnameGreeting {
  def main(args: Array[String]): Unit =
    printf("uname / osType / osName:\n%s\n", s"platform info: ${unameShort} / ${osType} / ${osName}")
    if (isLinux) {
      // uname is "Linux"
      printf("hello Linux\n")
    } else if (isDarwin) {
      // uname is "Darwin*"
      printf("hello Mac\n")
    } else if (isWinshell) {
      // isWinshell: Boolean = isMsys | isCygwin | isMingw | isGitSdk | isGitbash
      printf("hello %s\n", unameShort)
    } else if (envOrEmpty("MSYSTEM").nonEmpty) {
      printf("hello %s\n", envOrEmpty("MSYSTEM"))
    } else {
      assert(isWindows, s"unknown environment: ${unameLong} / ${osType} / ${osName}")
      printf("hello Windows\n")
    }
}
