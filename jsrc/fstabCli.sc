#!/usr/bin/env -S scala-cli shebang
//package vastblue.demo

//> using scala "3.4.3"
//> using dep "org.vastblue::unifile:0.3.9"

// display native path and lines.size of /etc/fstab
// format: off

import vastblue.unifile.*

val p = Paths.get("/etc/fstab")
printf("env: %-10s| shellRoot: %-12s| %-22s| %d lines\n", uname("-o"), shellRoot, p.posx, p.lines.size)
