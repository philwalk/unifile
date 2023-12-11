package vastblue.util

import java.io.{File => JFile}
import java.nio.file.Path
import java.nio.file.{Files => JFiles, Paths => JPaths}
import scala.collection.mutable.{Map => MutMap}
import scala.jdk.CollectionConverters.*
import java.nio.charset.Charset
import vastblue.file.Paths
import vastblue.Platform.*

/*
 * Supported environments:
 * Unix / Linux / OSX, Windows shell environments (CYGWIN64, MINGW64,
 * MSYS2, GIT-BASH, etc.).
 */
trait PathExtensions {
  private var hook = 0
  def Paths = vastblue.file.Paths
  
  extension(s: String) {
    def posx: String = s.replace('\\', '/')
    def jpath: Path = JPaths.get(s)
    def path: Path = Paths.get(s)
    def toPath: Path = Paths.get(s)
    def toFile: JFile = Paths.get(s).toFile
  }
  extension(p: Path) {
    def posx: String = p.toString.posx
    def abs: String = p.toAbsolutePath.posx
    def stdpath: String = _stdpath(p)
    def dospath: String = posx.replace('/', '\\')
    def localpath: String = posx.replace('/', JFile.separatorChar)
    def isDirectory: Boolean = p.toFile.isDirectory
    def isFile: Boolean = p.toFile.isFile
    def exists: Boolean = p.toFile.exists
    def getParent: String = p.toFile.getParent
    def parentFile: JFile = p.toFile.getParentFile
    def relpath: Path = relativize(p)
    def relativePath: String = relpath.toString.posx
    def lines = readLines(p)
    def files: Seq[JFile] = p.toFile.listFiles.toSeq
    def paths: Seq[Path] = p.toFile.listFiles.map { _.toPath }.toSeq
    def noDrive: String = p.posx match {
    case s if s.take(2).endsWith(":") => s.drop(2)
    case s                            => s
    }
    def delete() = p.toFile.delete()
    def isSymbolicLink: Boolean = JFiles.isSymbolicLink(p)
    def realPath: Path = toRealPath(p)
    def byteArray: Array[Byte] = JFiles.readAllBytes(p)
    def contentAsString(charset: Charset = DefaultCharset): String = {
      val posx = p.posx
      if (posx.startsWith("/proc")) {
        _exec("cat", posx)
      } else {
        new String(byteArray, charset)
      }
    }
  }
  
  extension(f: JFile) {
    def posx: String = f.getAbsolutePath.posx
    def abs: String = f.toPath.toAbsolutePath.posx
    def isDirectory: Boolean = f.isDirectory
    def isFile: Boolean = f.isFile
    def exists: Boolean = f.exists
    def getParent: String = f.getParent
    def parentFile: JFile = f.getParentFile
    def byteArray: Array[Byte] = JFiles.readAllBytes(f.toPath)
    def files: Seq[JFile] = f.listFiles.toSeq
    def paths: Seq[Path] = f.listFiles.toSeq.map { _.toPath }
  }
}
