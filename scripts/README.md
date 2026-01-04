# リリーススクリプト

リリース作業を自動化するためのスクリプト集です。

## スクリプト一覧

### 1. `prepare-release.sh` - リリース準備スクリプト

リリースの準備を自動化します。

**機能:**
- ✅ バージョン番号の検証（セマンティックバージョニング形式）
- ✅ Gitの状態確認（未コミットの変更をチェック）
- ✅ 既存タグの確認（重複防止）
- ✅ CHANGELOG.mdの確認
- ✅ バージョン番号の自動更新（build.gradle, pom.xml）
- ✅ テストの自動実行（スキップ可能）
- ✅ Javadocの自動生成
- ✅ プロジェクトのビルド
- ✅ JARファイルの検証と情報表示
- ✅ リリースノートのプレビュー生成
- ✅ 自動コミットとタグ作成（オプション）

**使用方法:**
```bash
# 基本的な使用（テストを実行）
./scripts/prepare-release.sh 1.0.0

# テストをスキップ
./scripts/prepare-release.sh 1.0.0 skip-tests

# テストをスキップして自動コミット・タグ作成
./scripts/prepare-release.sh 1.0.0 skip-tests auto-commit
```

### 2. `generate-release-notes.sh` - リリースノート生成スクリプト

CHANGELOG.mdからリリースノートを生成します。

**機能:**
- ✅ CHANGELOG.mdから指定バージョンのセクションを抽出
- ✅ クリップボードへの自動コピー（macOS/Linux対応）
- ✅ カスタム出力ファイルの指定

**使用方法:**
```bash
# 標準出力と/tmp/release-notes-1.0.0.txtに保存
./scripts/generate-release-notes.sh 1.0.0

# カスタムファイルに保存
./scripts/generate-release-notes.sh 1.0.0 release-notes.txt
```

### 3. `verify-release.sh` - リリース検証スクリプト

リリースアーティファクトを検証します。

**機能:**
- ✅ JARファイルの存在確認
- ✅ マニフェスト内のバージョン情報の検証
- ✅ JARファイルの実行可能性チェック
- ✅ ファイルサイズの表示
- ✅ CHANGELOG.mdの確認
- ✅ Gitタグの確認
- ✅ macOSアーティファクト（.app, .dmg）の確認

**使用方法:**
```bash
./scripts/verify-release.sh 1.0.0
```

### 4. `create-github-release.sh` - GitHubリリース作成スクリプト

GitHub CLIを使用してリリースを作成します。

**機能:**
- ✅ GitHub CLIを使用したリリース作成
- ✅ リリースノートの自動生成と設定
- ✅ アセットファイルの自動アップロード（JAR, DMG）
- ✅ リリースURLの表示

**前提条件:**
- GitHub CLI (`gh`) がインストールされている必要があります
- GitHub CLIで認証済みである必要があります

**インストール:**
```bash
# macOS
brew install gh

# Linux
# https://github.com/cli/cli/blob/trunk/docs/install_linux.md を参照
```

**使用方法:**
```bash
# デフォルトリポジトリ（KosukeMinamoto/xTreLoc）を使用
./scripts/create-github-release.sh 1.0.0

# カスタムリポジトリを指定
./scripts/create-github-release.sh 1.0.0 owner/repo
```

## リリースワークフロー例

### 基本的なワークフロー

```bash
# 1. リリース準備（テスト実行、ビルド、バージョン更新）
./scripts/prepare-release.sh 1.0.0

# 2. 変更を確認
git diff

# 3. コミットとタグ作成
git commit -am "Bump version to 1.0.0"
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0

# 4. リリース検証
./scripts/verify-release.sh 1.0.0

# 5. GitHubリリース作成（GitHub CLI使用）
./scripts/create-github-release.sh 1.0.0
```

### 自動化されたワークフロー

```bash
# 1. リリース準備（自動コミット・タグ作成付き）
./scripts/prepare-release.sh 1.0.0 skip-tests auto-commit

# 2. タグをプッシュ
git push origin v1.0.0

# 3. GitHubリリース作成
./scripts/create-github-release.sh 1.0.0
```

## 追加された便利機能

### prepare-release.sh の新機能

1. **バージョン番号の検証**
   - セマンティックバージョニング形式（X.Y.Z）をチェック
   - 不正な形式の場合はエラーを表示

2. **既存タグの確認**
   - 同じバージョンのタグが既に存在する場合に警告

3. **CHANGELOG.mdの確認**
   - 指定バージョンのエントリが存在するかチェック

4. **テストの自動実行**
   - デフォルトでテストを実行
   - `skip-tests`オプションでスキップ可能

5. **Javadocの自動生成**
   - ビルド前にJavadocを生成

6. **JARファイルの検証**
   - ビルドされたJARファイルの存在確認
   - ファイルサイズの表示
   - 見つからない場合の自動検索

7. **リリースノートのプレビュー**
   - リリース準備完了時にリリースノートを自動生成

8. **自動コミット機能**
   - `auto-commit`オプションで自動的にコミットとタグ作成

### generate-release-notes.sh の新機能

1. **クリップボードへの自動コピー**
   - macOS: `pbcopy`を使用
   - Linux: `xclip`を使用

2. **カスタム出力ファイル**
   - 出力先を指定可能

### 新規スクリプト

1. **verify-release.sh**
   - リリース前の包括的な検証
   - すべてのアーティファクトの確認

2. **create-github-release.sh**
   - GitHub CLIを使用したリリース作成の自動化
   - アセットの自動アップロード

## トラブルシューティング

### テストが失敗する場合

```bash
# テストをスキップして続行
./scripts/prepare-release.sh 1.0.0 skip-tests
```

### GitHub CLIがインストールされていない場合

`create-github-release.sh`の代わりに、GitHubのWebインターフェースから手動でリリースを作成してください。

### バージョン番号の形式エラー

セマンティックバージョニング形式（例: `1.0.0`）を使用してください。
プレリリース版は `1.0.0-beta` のような形式も使用可能です。

