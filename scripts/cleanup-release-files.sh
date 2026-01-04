#!/bin/bash
# Script to move release-unnecessary files to tmp/release-cleanup/

set -e

CLEANUP_DIR="tmp/release-cleanup"
MOVE_PERSONAL_DATA=${1:-false}

echo "=========================================="
echo "Cleaning up release-unnecessary files"
echo "=========================================="

# Create cleanup directory
mkdir -p "$CLEANUP_DIR"

# Move build artifacts (regeneratable)
echo ""
echo "Moving build artifacts..."
[ -d "bin" ] && mv bin "$CLEANUP_DIR/" && echo "✓ Moved bin/"
[ -d "build" ] && mv build "$CLEANUP_DIR/" && echo "✓ Moved build/"
[ -d ".gradle" ] && mv .gradle "$CLEANUP_DIR/" && echo "✓ Moved .gradle/"

# Move IDE settings
echo ""
echo "Moving IDE settings..."
[ -d ".vscode" ] && mv .vscode "$CLEANUP_DIR/" && echo "✓ Moved .vscode/"
[ -d ".idea" ] && mv .idea "$CLEANUP_DIR/" && echo "✓ Moved .idea/"

# Move temporary/development files
echo ""
echo "Moving temporary/development files..."
[ -f "map_trd.png" ] && mv map_trd.png "$CLEANUP_DIR/" && echo "✓ Moved map_trd.png"
[ -f "map_trd.py" ] && mv map_trd.py "$CLEANUP_DIR/" && echo "✓ Moved map_trd.py"
[ -f "test.py" ] && mv test.py "$CLEANUP_DIR/" && echo "✓ Moved test.py"
[ -f "CLEANUP_NOTES.md" ] && mv CLEANUP_NOTES.md "$CLEANUP_DIR/" && echo "✓ Moved CLEANUP_NOTES.md"

# Move personal data (optional, requires confirmation)
if [ "$MOVE_PERSONAL_DATA" = "move-personal" ]; then
    echo ""
    echo "Moving personal data (as requested)..."
    [ -d "tohoku2007" ] && mv tohoku2007 "$CLEANUP_DIR/" && echo "✓ Moved tohoku2007/"
    [ -d "demo2" ] && mv demo2 "$CLEANUP_DIR/" && echo "✓ Moved demo2/"
elif [ -d "tohoku2007" ] || [ -d "demo2" ]; then
    echo ""
    echo "Personal data directories found (tohoku2007/, demo2/)"
    echo "These are in .gitignore and won't be included in release."
    echo "To move them, run: $0 move-personal"
fi

# Create README if it doesn't exist
if [ ! -f "$CLEANUP_DIR/README.md" ]; then
    cat > "$CLEANUP_DIR/README.md" << 'EOF'
# リリース不要ファイルの移動先

このディレクトリには、リリースに不要なファイル・ディレクトリが移動されています。

## 移動されたファイル・ディレクトリ

### ビルド成果物（再生成可能）
- `bin/` - Eclipse IDEの出力ディレクトリ
- `build/` - Gradleのビルド成果物
- `.gradle/` - Gradleキャッシュ

### IDE設定ファイル
- `.vscode/` - VSCode設定ファイル
- `.idea/` - IntelliJ IDEA設定ファイル

### 個人データ・テストデータ
- `tohoku2007/` - 個人用データセット（.gitignoreに含まれている）
- `demo2/` - 追加のテストデータ（.gitignoreに含まれている）

### 一時ファイル・開発用ファイル
- `map_trd.png`, `map_trd.py` - 一時的なファイル
- `test.py` - テスト用スクリプト
- `CLEANUP_NOTES.md` - クリーンアップ用のメモ

## 復元方法

必要に応じて、以下のコマンドで元の場所に戻せます：

```bash
# ビルド成果物は再生成可能なので、通常は復元不要
# 必要に応じて以下を実行：
mv tmp/release-cleanup/bin ./
mv tmp/release-cleanup/build ./
mv tmp/release-cleanup/.gradle ./
mv tmp/release-cleanup/.vscode ./
```

## 注意

- これらのファイルは`.gitignore`に含まれているため、Gitで追跡されていません
- ビルド成果物は`./gradlew build`または`mvn clean package`で再生成できます
- 個人データは復元が必要な場合のみ移動してください
EOF
fi

echo ""
echo "=========================================="
echo "Cleanup complete!"
echo "=========================================="
echo "Moved files are in: $CLEANUP_DIR/"
echo ""
echo "Total size:"
du -sh "$CLEANUP_DIR" 2>/dev/null || echo "  (calculating...)"

