#!/bin/sh
# Compile and start the Online Notice Board.  Usage: ./run.sh [port]
cd "$(dirname "$0")" || exit 1
mkdir -p bin
javac -encoding UTF-8 -d bin src/noticeboard/*.java || exit 1
java -cp bin noticeboard.Main "$@"
