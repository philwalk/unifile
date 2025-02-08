#!/usr/bin/env -S scalaQ

object Info {
  def main(args: Array[String]): Unit = {
    printf("[%s]\n", scalaRuntimeVersion)
  }

  lazy val scalaRuntimeVersion: String = {
    val scala3Lib: String = classOf[CanEqual[?, ?]].getProtectionDomain.getCodeSource.getLocation.toURI.getPath.replace('\\', '/').replaceFirst("/([A-Za-z]:)", "$1")
    val codeSrc = java.nio.file.Paths.get(scala3Lib).toFile
    if (codeSrc.isFile && codeSrc.getName.endsWith(".jar")) {
      import java.io.FileInputStream
      import java.util.jar.JarInputStream
      val manifest     = new JarInputStream(new FileInputStream(codeSrc)).getManifest
      manifest.getMainAttributes.getValue("Implementation-Version")
    } else {
      val verby = Option(System.getenv("VERBY")).nonEmpty
      if (verby) printf("not a jar: scala3Lib[%s]\n", codeSrc.toString.replace('\\', '/'))
      val first = scala3Lib.split("[:/]").reverse.dropWhile( !_.matches(".*[0-9][.].*") ).take(1).mkString
      val byName = first.dropWhile( (c: Char) => c < '0' || c > '9') // drop until 1st digit
      byName
    }
  }
}
