#!/bin/bash
# Fyren GWT/WebGL Compilation Script
# Compiles Java → JavaScript for browser play (demo mode only, no networking)
#
# Prerequisites:
#   1. mvn compile (must run first — Java sources + dependencies resolved)
#   2. gwt-dev JAR available in Maven repo (auto-downloaded by Maven)
#
# Output: target/gwt-out/fyren/ — HTML + JS + assets

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Auto-detect Maven local repository
MVN_REPO=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout 2>/dev/null)
if [ -z "$MVN_REPO" ]; then
    # Fallback paths
    for d in "$HOME/.m2/repository" "/d/soft/repository" "/d/develp/apache-maven-3.9.11/maven-repository"; do
        if [ -d "$d" ]; then MVN_REPO="$d"; break; fi
    done
fi
echo "Maven repo: $MVN_REPO"

GDX_VER="1.12.1"
GWT_VER="2.8.2"

GDX="$MVN_REPO/com/badlogicgames/gdx/gdx/$GDX_VER/gdx-$GDX_VER.jar"
GDX_SRC="$MVN_REPO/com/badlogicgames/gdx/gdx/$GDX_VER/gdx-$GDX_VER-sources.jar"
GDX_GWT="$MVN_REPO/com/badlogicgames/gdx/gdx-backend-gwt/$GDX_VER/gdx-backend-gwt-$GDX_VER.jar"
GDX_GWT_SRC="$MVN_REPO/com/badlogicgames/gdx/gdx-backend-gwt/$GDX_VER/gdx-backend-gwt-$GDX_VER-sources.jar"
GWT_USER="$MVN_REPO/com/google/gwt/gwt-user/$GWT_VER/gwt-user-$GWT_VER.jar"
GWT_DEV="$MVN_REPO/com/google/gwt/gwt-dev/$GWT_VER/gwt-dev-$GWT_VER.jar"
JSINTEROP="$MVN_REPO/com/google/jsinterop/jsinterop-annotations/1.0.2/jsinterop-annotations-1.0.2.jar"
JSINTEROP_SRC="$MVN_REPO/com/google/jsinterop/jsinterop-annotations/1.0.2/jsinterop-annotations-1.0.2-sources.jar"
VALIDATION="$MVN_REPO/javax/validation/validation-api/1.0.0.GA/validation-api-1.0.0.GA.jar"
VALIDATION_SRC="$MVN_REPO/javax/validation/validation-api/1.0.0.GA/validation-api-1.0.0.GA-sources.jar"
ANT="$MVN_REPO/org/apache/ant/ant/1.9.6/ant-1.9.6.jar"
ANT_LAUNCHER="$MVN_REPO/org/apache/ant/ant-launcher/1.9.6/ant-launcher-1.9.6.jar"
COLT="$MVN_REPO/colt/colt/1.2.0/colt-1.2.0.jar"
ASM="$MVN_REPO/org/ow2/asm/asm/5.0.3/asm-5.0.3.jar"
ASM_UTIL="$MVN_REPO/org/ow2/asm/asm-util/5.0.3/asm-util-5.0.3.jar"
ASM_COMMONS="$MVN_REPO/org/ow2/asm/asm-commons/5.0.3/asm-commons-5.0.3.jar"
GSON="$MVN_REPO/com/google/code/gson/gson/2.6.2/gson-2.6.2.jar"
JSR305="$MVN_REPO/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar"
TAPESTRY="$MVN_REPO/tapestry/tapestry/4.0.2/tapestry-4.0.2.jar"

# Ensure GWT compiler is downloaded
if [ ! -f "$GWT_DEV" ]; then
    echo "Downloading gwt-dev..."
    mvn dependency:get -Dartifact=com.google.gwt:gwt-dev:$GWT_VER -q
fi

CLASSES="target/classes"
SRC="src/main/java"

echo "=== Fyren GWT Compilation ==="
echo "Output: target/gwt-out/"
echo ""

# Build classpath (all GWT compiler + dependencies)
CP="$GWT_DEV;$GWT_USER"
CP="$CP;$GDX_GWT;$GDX_GWT_SRC;$GDX;$GDX_SRC"
CP="$CP;$JSINTEROP;$JSINTEROP_SRC;$VALIDATION;$VALIDATION_SRC"
CP="$CP;$SRC;$CLASSES"
CP="$CP;$ANT;$ANT_LAUNCHER;$COLT;$ASM;$ASM_UTIL;$ASM_COMMONS"
CP="$CP;$GSON;$JSR305;$TAPESTRY"

echo "Classpath:"
echo "  gwt-dev: $GWT_DEV"
echo "  gwt-user: $GWT_USER"
echo "  gdx: $GDX"
echo "  gdx-backend-gwt: $GDX_GWT"
echo "  sources: $SRC + $CLASSES"
echo ""

# Run GWT compiler
# -war: output directory for generated JS/HTML
# -style: PRETTY for readable JS (use OBF for production)
# Module: com.Fyren.render.libgdx.FyrenGwt (defined in FyrenGwt.gwt.xml)
java -cp "$CP" \
    com.google.gwt.dev.Compiler \
    -war target/gwt-out \
    -style PRETTY \
    -logLevel INFO \
    com.Fyren.FyrenGwt

echo ""
echo "=== GWT Compilation Complete ==="
echo "Output: target/gwt-out/"
echo "Open:  target/gwt-out/index.html"
ls -lh target/gwt-out/fyren/ 2>/dev/null || echo "  (check target/gwt-out/ for output)"
