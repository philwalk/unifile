# unifile

+ `vastblue.file.Paths.get()` returns a `java.nio.file.Path` object
+ extensions to `java.nio.Paths` for `cygwin`, `msys2`, `git-bash` and other Windows posix-shell environments.
+ Expressive, Platform-portable scala library.
+ Simplify system administration tasks.
+ no 3rd party libraries, 100% scala.

For `unifile` functionality, reading of `.csv`, Date & Time functions, and more, see [Pallet](https://github.com/philwalk/pallet) (has 3rd party dependencies).

Write portable code that runs on Linux, Mac, or Windows.
Converting path Strings to `java.nio.file.Path` objects.

<img alt="unifile image" width=200 src="images/plastic-pallet.png">

Recognizes `posix` file paths in Windows, via customizable mount points in `C:/msys64/etc/fstab`.

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
  "org.vastblue" % "unifile_3" % "0.3.12"
For `scala 3.5+` or `scala-cli` scripts:
  "//> using dep org.vastblue:unifile_3:0.3.12"

## TL;DR
Simplicity and Universal Portability:
* Script as though you're running in a Linux environment.
* extend the range of your programs to include `cygwin` and other Windows shell environments.
* read process command lines from `/proc/$PID/cmdline` files
## Requirements
In Windows, requires a posix shell:
  * [MSYS64](https://msys2.org)
  * [CYGWIN64](https://www.cygwin.com)
  * [Git Bash](https://www.atlassian.com/git/tutorials/git-bash)
  * [WSL](https://learn.microsoft.com/en-us/windows/wsl/install)

Example scripts require a recent version of coreutils:
  (e.g., `ubuntu`: 8.32-4.1ubuntu1, `osx`: stable 9.4)
to support the use of `-S` in `#!/usr/bin/env -S scala-cli shebang` in hash-bang lines.

### Concept
  * import vastblue.unifile.*
* Provides `vastblue.file.Paths` for converting path strings to `java.nio.file.Paths`
  * `Paths.get` returns `java.nio.file.Path` objects
  * `Paths.get("/etc/fstab").toString` == `/etc/fstab` in most environments
  * `Paths.get("/etc/fstab").toString` == `C:\msys64\etc\fstab` (in MSYS64, for example)
  * `Paths.get("/etc/fstab").posx`     == `C:/msys64/etc/fstab`

Examples below illustrate some of the capabilities.

### Background
Most platforms other than `Windows` are unix-like, but with differing conventions and
various incompatibilities:
   * Linux / OSX `/usr/bin/env`, etc.

Windows shell environments are provided by `cygwin64`, `msys64`, `Git-bash`, etc.
However, the `Windows` jvm doesn't recognize the filesystem abstractions of these environments.

This library provides the missing piece.

  * In Windows, a custom `Paths.get()` applies `/etc/fstab` mounts before returning a `java.nio.file.Path`
  * In other environments, it uses plain vanilla `java.nio.file.Paths.get()`
  * extension methods on `java.nio.file.Path` and `java.io.File` simplify writing portable code

### Example script: display the native path and the number of lines in `/etc/fstab`
The following example might surprise Windows developers, since JVM languages don't normally support posix file paths that aren't also legal Windows paths.

```scala
#!/usr/bin/env -S scala-cli shebang

//> using scala "3.4.3"
//> using dep "org.vastblue::unifile:0.3.12"

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
Most portability issues concern the differences between Windows jvms and most others.
Things that maximize the odds of your script running everywhere:
  * prefer `scala 3`
  * always use forward slashes in literal path Strings except when displaying output
  * in `Windows`
    * represent paths internally with forward slashes
    * minimize reference to drive letters
      * drive letter not needed for paths on the current working drive (often C:)
      * to access disks other than the working drive, mount them via `/etc/fstab`
      * `vastblue.unifile.Paths.get()` parses both `posix` and `Windows` filesystem paths
  * Avoid using `java.nio.File.separator` or `sys.props("line.separator")` to parse input
  * (they are safe to use on output.)
  * When parsing input text, be OS-agnostic:
    * split Strings with internal newlines using `"(\r)?\n"`
  * create `java.nio.file.Path` objects in either of two ways:
    * `vastblue.file.Paths.get("/etc/fstab")
    * `"/etc/fstab".path  // uses `vastblue.file.Paths.get()` 
