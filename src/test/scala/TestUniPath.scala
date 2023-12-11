package vastblue

import vastblue.MountMapper.*
import vastblue.file.Paths
import vastblue.file.Paths.*
import vastblue.Platform.*
import org.junit.Test
import vastblue.util.PathExtensions

class TestUniPath extends PathExtensions {
  val verbose = Option(System.getenv("VERBOSE_TESTS")).nonEmpty

  def testArgs = Seq.empty[String]
  @Test def test1(): Unit = {
    val wherebash = _where("bash")
    val test      = Paths.get(wherebash)
    printf("bash [%s]\n", test)
    val bashVersion: String = _exec(_where("bash"), "-version")
    printf("%s\n%s\n", test, bashVersion)
    printf("bashPath     [%s]\n", _bashPath)
    printf("shellRoot    [%s]\n", _shellRoot)
    printf("systemDrive: [%s]\n", driveRoot)
    printf("shellDrive   [%s]\n", shellDrive)
    printf("shellBaseDir [%s]\n", shellBaseDir)
    printf("osName       [%s]\n", _osName)
    printf("unamefull    [%s]\n", _unameLong)
    printf("unameshort   [%s]\n", _unameShort)
    printf("isCygwin     [%s]\n", _isCygwin)
    printf("isMsys64     [%s]\n", _isMsys)
    printf("isMingw64    [%s]\n", _isMingw)
    printf("isGitSdk64   [%s]\n", _isGitSdk)
    printf("isWinshell   [%s]\n", _isWinshell)
    printf("isLinux      [%s]\n", _isLinux)
    printf("bash in path [%s]\n", findInPath("bash").getOrElse(""))
    printf("/etc/fstab   [%s]\n", Paths.get("/etc/fstab"))
    // dependent on /etc/fstab, in winshell environment
    printf("javaHome     [%s]\n", _javaHome)

    printf("\n")
    printf("all bash in path:\n")
    val bashlist = findAllInPath("bash")
    for (path <- bashlist) {
      printf(" found at %-36s : ", s"[$path]")
      printf("--version: [%s]\n", _exec(path.toString, "--version").takeWhile(_ != '('))
    }
    for ((key, valu) <- reverseMountMap) {
      printf("mount %-22s -> %s\n", key, valu)
    }
    val bpExists: Boolean = _bashPath.exists
    val bpIsFile: Boolean = _bashPath.isFile
    assert(bpExists == bpIsFile, s"bash not found")
  }
}
