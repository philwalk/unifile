#!/usr/bin/env -S scala-cli shebang
//#!/usr/bin/env -S scalaQ

//> using dep org.vastblue::unifile:0.4.1

import vastblue.Script.* // guessMainClass

// guess name of script by examining live stack trace at runtime
object Scriptname {
  def sunJavaCommand = sys.props("sun.java.command")
  def main(args: Array[String]): Unit =
    val stackHeadFilename: String = new Exception().getStackTrace.head.getFileName
    printf("stackHeadFilename[%s]\n", stackHeadFilename)
    printf("mainProgramPath[%s]\n", mainProgramPath)
    printf("guessMainClass[%s]\n", guessMainClass)
    printf("sunJavaCommand[%s]\n", sunJavaCommand)
    val program = stackHeadFilename.trim match {
    case "" => guessMainClass
    case str => str
    }
    printf("[%s]\n", program)
    sys.props.foreach { s =>
      if (s.toString.contains(program)){
        printf("%s\n", s)
      }
    }
}
