package vastblue

object Info {
  import java.io.FileInputStream
  import java.util.jar.JarInputStream

  lazy val scalaRuntimeVersion: String = {
    val scala3LibJar = classOf[CanEqual[_, _]].getProtectionDomain.getCodeSource.getLocation.toURI.getPath
    val manifest     = new JarInputStream(new FileInputStream(scala3LibJar)).getManifest
    manifest.getMainAttributes.getValue("Implementation-Version")
  }
}
