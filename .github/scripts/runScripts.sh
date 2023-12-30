#!/usr/bin/bash

unset SCALA_OPT # should independent of this when testing
for F in `ls jsrc/*.sc` ; do
  echo "# $F"
  $F
done 2>&1
