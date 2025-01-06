#!/usr/bin/env -S scala-cli shebang
//package vastblue.demo

//> using scala "3.4.3"
//> using dep "org.vastblue::unifile:0.3.9"

import vastblue.unifile.*

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
