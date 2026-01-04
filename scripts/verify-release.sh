#!/bin/bash
# Verify release artifacts and prepare release summary

set -e

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 1.0.0"
    exit 1
fi

echo "=========================================="
echo "Verifying release ${VERSION}"
echo "=========================================="

# Determine build directory
if [ -f "gradlew" ]; then
    BUILD_DIR="build/libs"
elif [ -f "pom.xml" ]; then
    BUILD_DIR="target"
else
    echo "Error: No build file found"
    exit 1
fi

# Check JAR files
GUI_JAR=$(find "$BUILD_DIR" -name "xTreLoc-GUI-${VERSION}.jar" 2>/dev/null | head -1)
CLI_JAR=$(find "$BUILD_DIR" -name "xTreLoc-CLI-${VERSION}.jar" 2>/dev/null | head -1)

if [ -z "$GUI_JAR" ]; then
    GUI_JAR=$(find "$BUILD_DIR" -name "xTreLoc-GUI-*.jar" | head -1)
fi
if [ -z "$CLI_JAR" ]; then
    CLI_JAR=$(find "$BUILD_DIR" -name "xTreLoc-CLI-*.jar" | head -1)
fi

echo ""
echo "Checking JAR files..."
if [ -f "$GUI_JAR" ]; then
    echo "✓ GUI JAR found: $GUI_JAR"
    echo "  Size: $(du -h "$GUI_JAR" | cut -f1)"
    
    # Check if JAR contains version in manifest
    if unzip -p "$GUI_JAR" META-INF/MANIFEST.MF 2>/dev/null | grep -q "Implementation-Version: ${VERSION}"; then
        echo "  ✓ Version in manifest: ${VERSION}"
    else
        echo "  ⚠ Version in manifest does not match ${VERSION}"
    fi
else
    echo "✗ GUI JAR not found"
fi

if [ -f "$CLI_JAR" ]; then
    echo "✓ CLI JAR found: $CLI_JAR"
    echo "  Size: $(du -h "$CLI_JAR" | cut -f1)"
    
    # Check if JAR contains version in manifest
    if unzip -p "$CLI_JAR" META-INF/MANIFEST.MF 2>/dev/null | grep -q "Implementation-Version: ${VERSION}"; then
        echo "  ✓ Version in manifest: ${VERSION}"
    else
        echo "  ⚠ Version in manifest does not match ${VERSION}"
    fi
else
    echo "✗ CLI JAR not found"
fi

# Check if JARs are executable
echo ""
echo "Checking JAR executability..."
if [ -f "$GUI_JAR" ]; then
    if java -jar "$GUI_JAR" --version &>/dev/null || java -jar "$GUI_JAR" -v &>/dev/null; then
        echo "✓ GUI JAR is executable"
    else
        echo "⚠ GUI JAR may not be executable (this is normal for GUI apps)"
    fi
fi

if [ -f "$CLI_JAR" ]; then
    if java -jar "$CLI_JAR" --version &>/dev/null || java -jar "$CLI_JAR" -v &>/dev/null; then
        echo "✓ CLI JAR is executable"
        VERSION_OUTPUT=$(java -jar "$CLI_JAR" --version 2>&1 || java -jar "$CLI_JAR" -v 2>&1 || echo "")
        if echo "$VERSION_OUTPUT" | grep -q "${VERSION}"; then
            echo "  ✓ Version output matches: ${VERSION}"
        fi
    else
        echo "✗ CLI JAR is not executable"
    fi
fi

# Check CHANGELOG
echo ""
echo "Checking CHANGELOG.md..."
if [ -f "CHANGELOG.md" ]; then
    if grep -q "## \[${VERSION}\]" CHANGELOG.md; then
        echo "✓ CHANGELOG.md contains entry for ${VERSION}"
    else
        echo "⚠ CHANGELOG.md does not contain entry for ${VERSION}"
    fi
else
    echo "⚠ CHANGELOG.md not found"
fi

# Check Git tag
echo ""
echo "Checking Git tag..."
if git rev-parse "v${VERSION}" >/dev/null 2>&1; then
    echo "✓ Git tag v${VERSION} exists"
    TAG_DATE=$(git log -1 --format=%ai "v${VERSION}")
    echo "  Tag date: $TAG_DATE"
else
    echo "⚠ Git tag v${VERSION} does not exist"
fi

# Check for macOS artifacts
echo ""
echo "Checking for macOS artifacts..."
if [ -d "build/dist" ]; then
    if [ -d "build/dist/xTreLoc.app" ]; then
        echo "✓ macOS .app bundle found"
        echo "  Location: build/dist/xTreLoc.app"
    fi
    DMG_FILE=$(find build/dist -name "xTreLoc-*.dmg" 2>/dev/null | head -1)
    if [ -n "$DMG_FILE" ]; then
        echo "✓ macOS .dmg file found"
        echo "  Location: $DMG_FILE"
        echo "  Size: $(du -h "$DMG_FILE" | cut -f1)"
    fi
fi

# Summary
echo ""
echo "=========================================="
echo "Verification Summary"
echo "=========================================="
echo "Version: ${VERSION}"
echo "GUI JAR: ${GUI_JAR:-Not found}"
echo "CLI JAR: ${CLI_JAR:-Not found}"
echo ""
echo "Ready for release:"
if [ -f "$GUI_JAR" ] && [ -f "$CLI_JAR" ]; then
    echo "✓ All required artifacts are present"
else
    echo "✗ Some artifacts are missing"
fi

