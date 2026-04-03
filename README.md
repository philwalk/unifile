# unifile

> **DEPRECATED** — This project is superseded by [`github.com/philwalk/uni`](https://github.com/philwalk/uni).
> New projects should use `uni` directly. See the [Migration Guide](#migration-guide-vastbluepallet--uni) below.

---

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
  "org.vastblue" % "unifile_3" % "0.4.1"
For `scala 3.5+` or `scala-cli` scripts:
  "//> using dep org.vastblue:unifile_3:0.4.1"

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
//> using dep "org.vastblue::unifile:0.4.1"

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

---

# Migration Guide: vastblue.pallet → uni

## 1. Imports

**Before:**
```scala
import vastblue.pallet.*
import vastblue.util.DataTypes.*
```

**After:**
```scala
import uni.*
import uni.time.*
import uni.data.*
import java.time.LocalDateTime  // only needed if using uni 0.11.2; fixed in 0.11.3
```

Note: `uni.time.*` exports `DateTime` (type alias for `LocalDateTime`).
In uni **0.11.2**, `LocalDateTime` itself was NOT exported — workaround was `import java.time.LocalDateTime`.
**Fixed in 0.11.3**: `import uni.time.*` now exports `LocalDateTime` directly; no extra import needed.

---

## 2. CSV Parsing

**Before:** custom `QuickCsv.parseCsvLine(...)` or `vast.file.QuickCsv`

**After:** `uni.io.FastCsv.parseCsvLine(str)`

```scala
import uni.io.FastCsv
val cols: Seq[String] = FastCsv.parseCsvLine(rowCsv)

// or inline:
val colrows = lines.map { uni.io.FastCsv.parseCsvLine(_) }
```

---

## 3. Big / BigDecimal Constructors

**Before:** `Big(str)` or `Big(value)` (opaque type constructor — not accessible outside object Big)

**After:** `big(str)` (lowercase function, only accepts String in uni)

```scala
big("0")          // OK
big(v.toString)   // OK — convert Any to String first
// Big(v)         // ERROR — opaque type constructor not accessible
```

**Named constants:**
| pallet              | uni                    | Notes                                  |
|---------------------|------------------------|----------------------------------------|
| `BigZero`           | `Big.zero`             | zero value                             |
| `Hundred`           | `hundred`              | the value 100 as Big                   |
| `badNum`            | `BigNaN`               | sentinel for non-numeric / parse error |
| `BigNaN`            | `BigNaN`               | same sentinel (name kept in uni)       |

**Checking for NaN/bad value:**
```scala
// pallet:
if (v == badNum) ...
if (v == BigNaN) ...

// uni — same sentinel, just use BigNaN:
if (v == BigNaN) ...
```

**setScale — no direct method on opaque Big, must go through BigDecimal:**
```scala
import scala.math.BigDecimal.RoundingMode
big(BigDecimal(n.toString).setScale(prec, RoundingMode.HALF_UP).toString)
// n is a Big; .toString converts it; result is a new Big rounded to prec decimal places
```

---

## 4. Missing Operator: Int * Big

`uni.data.Big` does not have a left-hand `Int *` extension operator.

**Workaround:** swap operands, or use Double on left side:
```scala
// 100 * expPct   // ERROR: no implicit for Int * Big
expPct * 100      // OK — Big has right-hand * for numeric
expPct * 100.0    // OK — use Double
```

---

## 5. Pattern Matching on Big (opaque type erasure)

**Before:**
```scala
case b: Big =>   // compiles but gives unchecked warning — erases to BigDecimal at runtime
```

**After:**
```scala
case b: BigDecimal =>   // correct match; cast back if needed
  b.asInstanceOf[Big]   // zero-cost cast since Big is opaque over BigDecimal
```

---

## 6. Path API

| pallet              | uni                  | Notes                                           |
|---------------------|----------------------|-------------------------------------------------|
| `s.file` → JFile    | `s.path` → Path      | JFile has no uni extension methods; use Path    |
| `f.toPath.xxx`      | use Path directly    | if you have a JFile, call `.toPath` first       |
| `p.name`            | `p.last`             | `.name` deprecated — gives last path component  |
| —                   | `p.posx`             | POSIX-style string, e.g. `/f/weekly/foo.csv`    |
| —                   | `p.stdpath`          | standardized string (forward slashes on Windows)|
| `p.contentAsString` | `p.contentAsString`  | still available via `import uni.*` (not missing)|
| `p.trimmedLines`    | `p.trimmedLines`     | still available via `import uni.*` (not missing)|

**Examples:**
```scala
// String → Path
val p: Path = "/f/weekly/foo.csv".path

// JFile → Path (to get uni extension methods)
val jf: java.io.File = someJFile
val p: Path = jf.toPath       // now p has .lines, .trimmedLines, .posx, etc.

// Last path component (filename)
val name: String = p.last     // was p.name

// String representations
val posix: String  = p.posx       // /f/weekly/foo.csv
val std: String    = p.stdpath    // forward-slash normalized

// renameTo — takes Path, NOT String
p.renameTo(destDir, destPath, overwrite = true)   // all args are Path
// NOT: p.renameTo("/some/path")  — that's a type error, not a missing method
```

---

## 7. Lines / trimmedLines — Iterator vs Seq

`Path.lines` in uni returns `Iterator[String]`; pallet returned `Seq[String]`.
An `Iterator` can only be consumed once — if you need to traverse more than once, call `.toSeq`.

**Add `.toSeq` or `.toList` where a Seq is needed:**
```scala
// pallet returned Seq — code like this compiled fine:
val rows: Seq[String] = somePath.lines

// uni returns Iterator — must convert:
val rows: Seq[String] = somePath.lines.toSeq
val rows: Seq[String] = somePath.trimmedLines.toSeq

// safe to chain before converting:
val data = somePath.lines.filter(_.nonEmpty).toSeq
val data = somePath.trimmedLines.drop(1).toSeq   // skip header
```

Note: in uni 0.11.3, `trimmedLines` itself calls `.toSeq` internally so its return type
is `Seq[String]` — but `lines` still returns `Iterator[String]`, so always convert `lines`.

---

## 8. Date Parsing

| pallet                  | uni                                         |
|-------------------------|---------------------------------------------|
| `dateParser(str)`       | `parseDate(str)`                            |
| `daysBetween(a, b)`     | `ChronoUnit.DAYS.between(a, b)`             |

**Examples:**
```scala
import java.time.temporal.ChronoUnit

// parse a date string to LocalDate
val d: LocalDate = parseDate("2026-03-27")   // was dateParser("2026-03-27")

// days between two LocalDate values
val n: Long = ChronoUnit.DAYS.between(startDate, endDate)
// NOTE: argument order is (earlier, later) for a positive result
// pallet's daysBetween may have had reversed argument order — verify when migrating
```

---

## 9. showLimitedStack (NOT missing — present in uni)

`showLimitedStack(e: Throwable)` IS available via `import uni.*` (defined in `uni/PathsUtils.scala`).
Do NOT replace with `e.printStackTrace()` — showLimitedStack filters the stack to relevant frames.

Also available: `showMinimalStack(e: Throwable)` — even more condensed output.

```scala
import uni.*

try {
  doSomething()
} catch {
  case e: Exception =>
    showLimitedStack(e)   // preferred — filters stack frames
    // NOT: e.printStackTrace()
}
```

---

## 10. csvColnamesAndRows / csvRows

`csvColnamesAndRows` may not exist in uni — check before using.
**Workaround:** use `p.csvRows` and split head/tail manually:
```scala
val allRows: Seq[Seq[String]] = path.csvRows
val header: Seq[String]       = allRows.head     // column names
val data:   Seq[Seq[String]]  = allRows.tail     // data rows

// uni replacement:
val colnames = path.csvRows.head
val rows     = path.csvRows.tail
// or more efficiently (single read):
val allRows  = path.csvRows
val colnames = allRows.head
val rows     = allRows.tail
```

---

## 11. Dependency Directives (scala-cli .sc files)

**Before:**
```scala
//> using dep org.vastblue::pallet:0.11.1
```

**After:**
```scala
//> using dep org.vastblue:uni_3:0.12.0
```

---

## 12. showUsage Pattern

No change in semantics, but confirm which variant is in scope:
- `showUsage(m, "<argspec>")` — uni pattern
- `_usage(m, Seq(...))` — also available

---

## Known Confirmed Non-Issues (do not add workarounds)

- `Path.contentAsString` — available via `import uni.*`
- `Path.trimmedLines` — available via `import uni.*`
- `Path.renameTo(Path, Path, Boolean)` — takes Path not String (type mismatch, not missing)
- `showLimitedStack(Throwable)` — available via `import uni.*`
