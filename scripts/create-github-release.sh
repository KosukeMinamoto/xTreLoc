#!/bin/bash
# Create a GitHub release using GitHub CLI (gh) or API

set -e

VERSION=$1
REPO=${2:-"KosukeMinamoto/xTreLoc"}

if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version> [repo]"
    echo "Example: $0 1.0.0"
    echo "Example: $0 1.0.0 KosukeMinamoto/xTreLoc"
    exit 1
fi

TAG="v${VERSION}"

# Check if GitHub CLI is installed
if command -v gh &> /dev/null; then
    echo "Using GitHub CLI (gh)..."
    
    # Check if tag exists
    if ! git rev-parse "$TAG" >/dev/null 2>&1; then
        echo "Error: Tag $TAG does not exist locally"
        exit 1
    fi
    
    # Generate release notes
    RELEASE_NOTES_FILE="/tmp/release-notes-${VERSION}.txt"
    if [ -f "scripts/generate-release-notes.sh" ]; then
        bash scripts/generate-release-notes.sh "$VERSION" "$RELEASE_NOTES_FILE"
    fi
    
    # Create release
    if [ -f "$RELEASE_NOTES_FILE" ]; then
        gh release create "$TAG" \
            --title "Release ${VERSION}" \
            --notes-file "$RELEASE_NOTES_FILE" \
            --repo "$REPO"
    else
        gh release create "$TAG" \
            --title "Release ${VERSION}" \
            --notes "Release version ${VERSION}" \
            --repo "$REPO"
    fi
    
    # Upload assets
    echo ""
    echo "Uploading release assets..."
    
    if [ -f "build/libs/xTreLoc-GUI-${VERSION}.jar" ]; then
        gh release upload "$TAG" "build/libs/xTreLoc-GUI-${VERSION}.jar" --repo "$REPO"
        echo "✓ Uploaded GUI JAR"
    fi
    
    if [ -f "build/libs/xTreLoc-CLI-${VERSION}.jar" ]; then
        gh release upload "$TAG" "build/libs/xTreLoc-CLI-${VERSION}.jar" --repo "$REPO"
        echo "✓ Uploaded CLI JAR"
    fi
    
    if [ -f "target/xTreLoc-GUI-${VERSION}.jar" ]; then
        gh release upload "$TAG" "target/xTreLoc-GUI-${VERSION}.jar" --repo "$REPO"
        echo "✓ Uploaded GUI JAR"
    fi
    
    if [ -f "target/xTreLoc-CLI-${VERSION}.jar" ]; then
        gh release upload "$TAG" "target/xTreLoc-CLI-${VERSION}.jar" --repo "$REPO"
        echo "✓ Uploaded CLI JAR"
    fi
    
    # Upload DMG if exists
    DMG_FILE=$(find build/dist -name "xTreLoc-*.dmg" 2>/dev/null | head -1)
    if [ -n "$DMG_FILE" ]; then
        gh release upload "$TAG" "$DMG_FILE" --repo "$REPO"
        echo "✓ Uploaded DMG"
    fi
    
    echo ""
    echo "Release created successfully!"
    echo "View at: https://github.com/${REPO}/releases/tag/${TAG}"
    
else
    echo "GitHub CLI (gh) is not installed."
    echo ""
    echo "To install GitHub CLI:"
    echo "  macOS: brew install gh"
    echo "  Linux: See https://github.com/cli/cli/blob/trunk/docs/install_linux.md"
    echo ""
    echo "Alternatively, you can create the release manually at:"
    echo "  https://github.com/${REPO}/releases/new"
    echo ""
    echo "Tag to use: ${TAG}"
    exit 1
fi

