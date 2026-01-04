#!/bin/bash
# Generate release notes from CHANGELOG.md for GitHub release

VERSION=$1
OUTPUT_FILE=${2:-/tmp/release-notes-${VERSION}.txt}

if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version> [output-file]"
    echo "Example: $0 1.0.0"
    echo "Example: $0 1.0.0 release-notes.txt"
    exit 1
fi

# Extract version section from CHANGELOG.md
if [ -f "CHANGELOG.md" ]; then
    # Find the section for the specified version
    awk "/^## \[${VERSION}\]/,/^## \[/" CHANGELOG.md | sed '$d' > "$OUTPUT_FILE"
    
    if [ -s "$OUTPUT_FILE" ]; then
        echo "Release notes for version ${VERSION}:"
        echo "=========================================="
        cat "$OUTPUT_FILE"
        echo "=========================================="
        echo ""
        echo "Release notes saved to: $OUTPUT_FILE"
        echo ""
        echo "You can copy this content to GitHub release notes."
        
        # Try to copy to clipboard if available
        if command -v pbcopy &> /dev/null; then
            cat "$OUTPUT_FILE" | pbcopy
            echo "Release notes copied to clipboard (macOS)"
        elif command -v xclip &> /dev/null; then
            cat "$OUTPUT_FILE" | xclip -selection clipboard
            echo "Release notes copied to clipboard (Linux)"
        fi
    else
        echo "Warning: No release notes found for version ${VERSION}"
        echo "Please update CHANGELOG.md"
        exit 1
    fi
else
    echo "Error: CHANGELOG.md not found"
    exit 1
fi

