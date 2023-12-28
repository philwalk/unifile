package vastblue

import java.nio.file.{Path, Paths => JPaths}
import vastblue.Platform.cygdrive

// DriveRoot Strings must match "" or "[A-Z]:"
// The `toPath` method resolves path to root of disk,
// rather than the one returned by `JPaths.get("C:")`.
object DriveRoot {
  opaque type DriveRoot = String

  // empty string or uppercase "[A-Z]:"
  def apply(s: String): DriveRoot = {
    require(s.isEmpty || s.length == 2, s"bad DriveRoot String [$s]")
    val str: String = s match {
    case dl if dl.matches("^[a-zA-Z]:") => dl.toUpperCase
    case dl if dl.matches("^[a-zA-Z]")  => s"$dl:".toUpperCase
    case _                              => ""
    }
    str
  }

  extension (dl: DriveRoot) {
    def letter: String   = dl.substring(0, 1).toLowerCase
    def string: String   = dl
    def isEmpty: Boolean = string.isEmpty
    def isDrive: Boolean = !isEmpty
    def toPath: Path     = JPaths.get(dl.string + "/")
    def workingDir: Path = JPaths.get(dl.string).toAbsolutePath

    def posix: String = if (cygdrive.endsWith("/")) {
      s"$cygdrive${letter}"
    } else {
      s"$cygdrive/$letter" // cygdrive like either "/" or "/cygdrive"
    }
  }
}
