#!/usr/bin/env -S scala

import vastblue.script.legalMainClass

// test code to skip java portion of "sun.command.line" property
// especially legalMainClass(s)
def main(args: Array[String]): Unit =
  for ((arg, i) <- dummyArgs.zipWithIndex) {
    val legal = legalMainClass(arg)
    if (legal) {
      printf(" %2d: [%s] : [%s]\n", i, legal, arg)
    }
  }
  val scriptProp = "jsrc/globArg.sc"
  val scriptArgs = dummyArgs.dropWhile(s => !s.endsWith(scriptProp) && !legalMainClass(s))
  printf("\n")
  for (arg <- scriptArgs) {
    printf(" [%s]\n", arg)
  }

lazy val dummyArgs: Array[String] =
  """C:\opt\jdk\bin\java.exe^@-Dscala.home=C:/opt/scala^@-classpath^@C:/opt/scala/lib/scala-library-2.13.10.jar;C:/opt/scala/lib/scala3-library_3-3.3.1.jar;C:/opt/scala/lib/scala-asm-9.5.0-scala-1.jar;C:/opt/scala/lib/compiler-interface-1.3.5.jar;C:/opt/scala/lib/scala3-interfaces-3.3.1.jar;C:/opt/scala/lib/scala3-compiler_3-3.3.1.jar;C:/opt/scala/lib/tasty-core_3-3.3.1.jar;C:/opt/scala/lib/scala3-staging_3-3.3.1.jar;C:/opt/scala/lib/scala3-tasty-inspector_3-3.3.1.jar;C:/opt/scala/lib/jline-reader-3.19.0.jar;C:/opt/scala/lib/jline-terminal-3.19.0.jar;C:/opt/scala/lib/jline-terminal-jna-3.19.0.jar;C:/opt/scala/lib/jna-5.3.1.jar;;^@dotty.tools.MainGenericRunner^@-classpath^@C:/opt/scala/lib/scala-library-2.13.10.jar;C:/opt/scala/lib/scala3-library_3-3.3.1.jar;C:/opt/scala/lib/scala-asm-9.5.0-scala-1.jar;C:/opt/scala/lib/compiler-interface-1.3.5.jar;C:/opt/scala/lib/scala3-interfaces-3.3.1.jar;C:/opt/scala/lib/scala3-compiler_3-3.3.1.jar;C:/opt/scala/lib/tasty-core_3-3.3.1.jar;C:/opt/scala/lib/scala3-staging_3-3.3.1.jar;C:/opt/scala/lib/scala3-tasty-inspector_3-3.3.1.jar;C:/opt/scala/lib/jline-reader-3.19.0.jar;C:/opt/scala/lib/jline-terminal-3.19.0.jar;C:/opt/scala/lib/jline-terminal-jna-3.19.0.jar;C:/opt/scala/lib/jna-5.3.1.jar;;^@-cp^@target/scala-3.3.1/classes^@jsrc/globArg.sc^@*.sc"""
    .split("\\^@")
