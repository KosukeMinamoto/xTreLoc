#!/bin/bash
# Script to prepare a release

set -e

VERSION=$1
SKIP_TESTS=${2:-false}
AUTO_COMMIT=${3:-false}

if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version> [skip-tests] [auto-commit]"
    echo "Example: $0 1.0.0"
    echo "Example: $0 1.0.0 skip-tests"
    echo "Example: $0 1.0.0 skip-tests auto-commit"
    exit 1
fi

# Validate version format (semantic versioning)
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+)?$ ]]; then
    echo "Error: Invalid version format. Expected format: X.Y.Z or X.Y.Z-suffix"
    echo "Example: 1.0.0 or 1.0.0-beta"
    exit 1
fi

echo "=========================================="
echo "Preparing release ${VERSION}"
echo "=========================================="

# Check if we're on a clean git state
if [ -n "$(git status --porcelain)" ]; then
    echo "Warning: You have uncommitted changes. Please commit or stash them first."
    if [ "$AUTO_COMMIT" != "auto-commit" ]; then
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# Check if tag already exists
if git rev-parse "v${VERSION}" >/dev/null 2>&1; then
    echo "Error: Tag v${VERSION} already exists!"
    exit 1
fi

# Check if CHANGELOG.md has entry for this version
if [ -f "CHANGELOG.md" ]; then
    if ! grep -q "## \[${VERSION}\]" CHANGELOG.md; then
        echo "Warning: CHANGELOG.md does not contain entry for version ${VERSION}"
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# Update version in build.gradle
if [ -f "build.gradle" ]; then
    echo "Updating version in build.gradle..."
    sed -i.bak "s/version = '.*-SNAPSHOT'/version = '${VERSION}'/" build.gradle
    rm build.gradle.bak
fi

# Update version in pom.xml
if [ -f "pom.xml" ]; then
    echo "Updating version in pom.xml..."
    sed -i.bak "s/<version>.*-SNAPSHOT<\/version>/<version>${VERSION}<\/version>/" pom.xml
    rm pom.xml.bak
fi

# Run tests (unless skipped)
if [ "$SKIP_TESTS" != "skip-tests" ]; then
    echo ""
    echo "Running tests..."
    if [ -f "gradlew" ]; then
        ./gradlew test || {
            echo "Error: Tests failed. Use 'skip-tests' to skip tests."
            exit 1
        }
    elif [ -f "pom.xml" ]; then
        mvn test || {
            echo "Error: Tests failed. Use 'skip-tests' to skip tests."
            exit 1
        }
    fi
    echo "Tests passed!"
fi

# Generate Javadoc
echo ""
echo "Generating Javadoc..."
if [ -f "gradlew" ]; then
    ./gradlew javadoc || echo "Warning: Javadoc generation failed (continuing...)"
elif [ -f "pom.xml" ]; then
    mvn javadoc:javadoc || echo "Warning: Javadoc generation failed (continuing...)"
fi

# Build the project
echo ""
echo "Building project..."
if [ -f "gradlew" ]; then
    ./gradlew clean build cliJar
    BUILD_DIR="build/libs"
elif [ -f "pom.xml" ]; then
    mvn clean package
    BUILD_DIR="target"
else
    echo "Error: No build file found (build.gradle or pom.xml)"
    exit 1
fi

# Check if JAR files were created
GUI_JAR="${BUILD_DIR}/xTreLoc-GUI-${VERSION}.jar"
CLI_JAR="${BUILD_DIR}/xTreLoc-CLI-${VERSION}.jar"

if [ ! -f "$GUI_JAR" ]; then
    echo "Warning: GUI JAR not found at expected location: $GUI_JAR"
    # Try to find it
    GUI_JAR=$(find "$BUILD_DIR" -name "xTreLoc-GUI-*.jar" | head -1)
    if [ -z "$GUI_JAR" ]; then
        echo "Error: Could not find GUI JAR file"
        exit 1
    fi
    echo "Found GUI JAR at: $GUI_JAR"
fi

if [ ! -f "$CLI_JAR" ]; then
    echo "Warning: CLI JAR not found at expected location: $CLI_JAR"
    # Try to find it
    CLI_JAR=$(find "$BUILD_DIR" -name "xTreLoc-CLI-*.jar" | head -1)
    if [ -z "$CLI_JAR" ]; then
        echo "Error: Could not find CLI JAR file"
        exit 1
    fi
    echo "Found CLI JAR at: $CLI_JAR"
fi

# Display JAR file information
echo ""
echo "Built JAR files:"
echo "  GUI: $GUI_JAR ($(du -h "$GUI_JAR" | cut -f1))"
echo "  CLI: $CLI_JAR ($(du -h "$CLI_JAR" | cut -f1))"

# Generate release notes preview
echo ""
echo "Generating release notes preview..."
if [ -f "scripts/generate-release-notes.sh" ]; then
    bash scripts/generate-release-notes.sh "$VERSION"
fi

# Auto-commit if requested
if [ "$AUTO_COMMIT" = "auto-commit" ]; then
    echo ""
    echo "Auto-committing changes..."
    git add build.gradle pom.xml CHANGELOG.md 2>/dev/null || true
    git commit -m "Bump version to ${VERSION}" || echo "Warning: Commit failed or nothing to commit"
    
    echo "Creating tag..."
    git tag -a "v${VERSION}" -m "Release version ${VERSION}"
    
    echo ""
    echo "Tag created: v${VERSION}"
    echo "To push the tag, run: git push origin v${VERSION}"
    echo "To push commits and tag together, run: git push origin HEAD && git push origin v${VERSION}"
else
    echo ""
    echo "Release ${VERSION} prepared successfully!"
    echo ""
    echo "Next steps:"
    echo "1. Review the changes: git diff"
    echo "2. Commit the version changes: git commit -am \"Bump version to ${VERSION}\""
    echo "3. Create a tag: git tag -a v${VERSION} -m \"Release version ${VERSION}\""
    echo "4. Push the tag: git push origin v${VERSION}"
    echo "5. Create a GitHub release with the tag v${VERSION}"
    echo "6. Upload the built JAR files as release assets:"
    echo "   - $GUI_JAR"
    echo "   - $CLI_JAR"
fi

echo ""
echo "=========================================="
echo "Release preparation complete!"
echo "=========================================="

