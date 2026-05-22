#!/bin/bash
# ============================================================
#  build.sh  —  Compile and run Library Loan Management System
#  Requires: Java 8+  and  derby.jar in the lib/ folder
# ============================================================

MAIN_CLASS="com.library.ui.MainApp"
SRC_DIR="src/main/java"
OUT_DIR="out"
LIB="lib/derby.jar"

echo ""
echo "============================================================"
echo "  Library Loan Management System — Build Script"
echo "============================================================"

# Check Java
if ! command -v java &>/dev/null; then
    echo "ERROR: java not found. Install JDK 8+ and add to PATH."
    exit 1
fi

# Check Derby jar
if [ ! -f "$LIB" ]; then
    echo "ERROR: $LIB not found."
    echo ""
    echo "Download Apache Derby from:"
    echo "  https://db.apache.org/derby/derby_downloads.html"
    echo "Copy  db-derby-XX.XX.X.X-bin/lib/derby.jar  into lib/"
    exit 1
fi

mkdir -p "$OUT_DIR" output

echo ""
echo "[1/2] Compiling sources..."
find "$SRC_DIR" -name "*.java" > sources.txt
javac -d "$OUT_DIR" -cp "$LIB" @sources.txt
STATUS=$?
rm -f sources.txt
if [ $STATUS -ne 0 ]; then
    echo "COMPILATION FAILED."
    exit 1
fi
echo "      Done."

echo ""
echo "[2/2] Running application..."
echo ""
java -cp "$OUT_DIR:$LIB" "$MAIN_CLASS"
