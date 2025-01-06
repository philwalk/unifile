# unifile

+ Provides a `cygwin-compatible` version of `java.nio.Paths`
+ `Paths.get()` understands `posix` and `Windows` paths, returns a `java.nio.file.Path` object
+ Expressive, Platform-portable scala library.
+ Simplify system administration tasks.
+ no 3rd party libraries, 100% scala.

For this functionality, plus reading of `.csv`, expressive Date & Time functions, and more, see [Pallet](https://github.com/philwalk/unifile) instead (has 3rd party dependencies).

Write one version of code to run in Linux, Mac, or Windows posix shell environments.
JVM support for `java.nio.file.Path` objects that work everywhere.

<img alt="unifile image" width=200 src="images/plastic-pallet.png">

recognizes `posix` file paths in Windows, via customizable mount points in `C:/msys64/etc/fstab`.

* Supported Scala Versions
  * `scala 3.x`

* Tested Target environments
  * `Linux`
  * `Darwin/OSX`
  * `Windows`
    * `Cygwin64`
    * `Msys64`
    * `Mingw64`
    * `Git-bash`
    * `WSL Linux`

### Usage

To use `unifile` in an `SBT` project, add this dependency to `build.sbt`
```sbt
  "org.vastblue" % "unifile_3" % "0.3.9"
```
For `scala` or `scala-cli` scripts, see examples below.

## TL;DR
Simplicity and Universal Portability:
* Script as though you're running in a Linux environment.
* extends the range of scala scripting:
* read process command lines from `/proc/$PID/cmdline` files
## Requirements
In Windows, requires a posix shell:
  * [MSYS64](https://msys2.org)
  * [CYGWIN64](https://www.cygwin.com)
  * [Git Bash](https://www.atlassian.com/git/tutorials/git-bash)
  * [WSL](https://learn.microsoft.com/en-us/windows/wsl/install)

Example scripts require a recent version of coreutils:
  (e.g., `ubuntu`: 8.32-4.1ubuntu1, `osx`: stable 9.4)
to support the use of `#!/usr/bin/env -S scala` in script hash-bang lines.

### Concept
  * import vastblue.unifile.*
* Exposes `vastblue.file.Paths` instead of `java.nio.file.Paths`
  * `Paths.get` returns `java.nio.file.Path` objects
  * `Paths.get("/etc/fstab").toString` == `C:\msys64\etc\fstab` (for example)

Examples below illustrate some of the capabilities.

### Background
Most platforms other than `Windows` are unix-like, but with differing
conventions and various incompatibilities:
   * Linux / OSX `/usr/bin/env`, etc.

There are posix environments available in Windows, provided by `cygwin64`, `msys64`, `Git-bash`, etc.
However, the `Windows` jvm doesn't recognize the `posix` filesystem abstractions provided by these environments.

This library provides the missing piece.

  * In Windows, a custom `Paths.get()` applies `/etc/fstab` mounts before returning a `java.nio.file.Path`.
  * In other environments, it uses plain vanilla `java.nio.file.Paths.get()`
  * to display a `Path` or `java.io.File`, extension methods provide `posix` or `native` formats.

### Example script: display the native path and the number of lines in `/etc/fstab`
The following example might surprise Windows developers, since JVM languages don't normally support posix file paths that aren't also legal Windows paths.

```scala
#!/usr/bin/env -S scala-cli shebang

//> using scala "3.4.3"
//> using dep "org.vastblue::unifile::0.3.9"

import vastblue.unifile.*

// display the native path and lines.size of /etc/fstab
// mapped to "C:\msys64\etc\fstab" in Windows
val p = Paths.get("/etc/fstab")
printf("%s\n", p.posx)
printf("env: %-10s| %-22s | %d lines\n", uname("-o"), p.posx, p.lines.size)
```
### Output of the previous example script on various platforms:
```
Linux Mint # env: GNU/Linux | shellRoot: /           | /etc/fstab            | 21 lines
Darwin     # env: Darwin    | shellRoot: /           | /etc/fstab            | 0 lines
WSL Ubuntu # env: GNU/Linux | shellRoot: /           | /etc/fstab            | 6 lines
Cygwin64   # env: Cygwin    | shellRoot: C:/cygwin64 | C:/cygwin64/etc/fstab | 24 lines
Msys64     # env: Msys      | shellRoot: C:/msys64/  | C:/msys64/etc/fstab   | 22 lines
```
Note that on Darwin, there is no `/etc/fstab` file, so the `Path#lines` extension returns `Nil`.

### Setup
  * `Windows`: install one of the following:
    * [MSYS64](https://msys2.org)
    * [CYGWIN64](https://www.cygwin.com)
    * [WSL](https://learn.microsoft.com/en-us/windows/wsl/install)
    * [Git Bash](https://www.atlassian.com/git/tutorials/git-bash)
  * `Linux`: required packages:
    * `sudo apt install coreutils`
  * `Darwin/OSX`:
    * `brew install coreutils`

### Tips for Writing Portable Scala Scripts
Things that maximize the odds of your script running on another system:
  * use `scala 3`
  * use `posix` file paths by default
  * in `Windows`
    * represent paths internally with forward slashes and avoid drive letters
    * drive letter not needed for paths on the current working drive (often C:)
    * to access disks other than the working drive, mount them via `/etc/fstab`
    * `vastblue.Paths.get()` can parse both `posix` and `Windows` filesystem paths
  * The jvm provides `java.nio.File.separator` and `sys.props("line.separator")` for
  * OS-appropriate line-endings.   They safe to use on output.
  * But it's safer when parsing input text to be OS-agnostic:
    * strings should be split into lines with `"(\r)?\n"`
  * create `java.nio.file.Path` objects in either of two ways:
    * `vastblue.file.Paths.get("/etc/fstab")
    * `"/etc/fstab".path  // uses `vastblue.file.Paths.get()` 
  * if client needs glob expression command line arguments, `val argv = prepArgs(args.toSeq)`
    * this avoids exposure to the `Windows` jvm glob expansion bug, and
    * inserts `script` path or `main` method class as `argv(0)` (as in C/C++)
    * argv(0) script name available as input parameter affecting script behaviour

