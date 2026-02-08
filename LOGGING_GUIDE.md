# xTreLoc ログシステムガイド

## 概要

xTreLoc では、統一されたログシステムを採用しています。すべてのログは自動的に以下に出力されます：

1. **ファイル**: `~/.xTreLoc/logs/` ディレクトリ
2. **UI/CLI**: 各モード（GUI/CLI/TUI）に応じた適切な出力先

## ログ出力の仕組み

### SolverLogger クラス

`com.treloc.xtreloc.util.SolverLogger` は、ソルバーおよび関連コンポーネントの統一ログ機構です。

```java
// 基本的な使用方法
SolverLogger.info("処理開始");
SolverLogger.warning("警告メッセージ");
SolverLogger.severe("エラーメッセージ");
SolverLogger.fine("詳細ログ");
```

### ログレベル

- **INFO**: 一般的な進行状況（ユーザーに表示）
- **WARNING**: 警告（ユーザーに表示）
- **SEVERE**: エラー（ユーザーに表示）
- **FINE**: デバッグ情報（ファイルのみ）

## 各モードでのログ出力

### GUI モード

- **UI表示**: "Execution log" パネルに表示
- **ファイル**: ログファイルに保存
- **詳細度**: INFO 以上のみ表示

GUI でのログ出力は、`XTreLocGUI` で自動的に統合されます。

### CLI モード

- **stdout**: 通常のメッセージ（INFO）
- **stderr**: 警告とエラー（WARNING, SEVERE）
- **ファイル**: ログファイルに保存
- **詳細度**: INFO 以上のみ表示

例：
```
$ java -jar xTreLoc.jar GRD config.json
Starting grid search for: 000101.000000.dat
Grid search completed for: 000101.000000.dat
```

### TUI モード

- **ログウィンドウ**: 別ウィンドウで表示（ユーザーが開く）
- **ファイル**: ログファイルに保存
- **メインUI**: シンプルなステータスメッセージのみ

TUI では、ログウィンドウを開くことで詳細ログを確認できます。

## ソルバー内でのログ出力

### 推奨される実装

ソルバー内では、以下のように `SolverLogger` を使用します：

```java
package com.treloc.xtreloc.solver;

import com.treloc.xtreloc.util.SolverLogger;

public class HypoGridSearch extends SolverBase {
    
    public void start(String datFile, String outFile) {
        String fileName = new java.io.File(datFile).getName();
        
        // 開始ログ
        SolverLogger.info("Starting grid search for: " + fileName);
        
        try {
            // 処理実行
            // ...
            
            // 詳細ログ（ファイルのみ）
            SolverLogger.fine(String.format("Result: %.3f %.3f %.3f", lat, lon, dep));
            
            // 完了ログ
            SolverLogger.info("Grid search completed for: " + fileName);
        } catch (Exception e) {
            // エラーログ
            SolverLogger.severe("Failed to process: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
```

### 推奨事項

- **info()**: ユーザーに見せたい主要なイベント（開始、完了）
- **warning()**: 計算に影響しない注意事項
- **severe()**: エラー（ユーザーに通知）
- **fine()**: 詳細なデバッグ情報（ファイル記録用）

## ログファイルの場所

- **Mac/Linux**: `~/.xTreLoc/logs/`
- **Windows**: `%APPDATA%\.xTreLoc\logs\`

ログファイルには、すべてのログレベルのメッセージが記録されます。

## トラブルシューティング

### ログが表示されない場合

1. **ファイルモード**: ログファイルが作成されているか確認
2. **CLI モード**: stderr/stdout が正しくリダイレクトされているか確認
3. **TUI モード**: ログウィンドウを開いているか確認（"Show Log Window" ボタン）
4. **GUI モード**: Execution log パネルが表示されているか確認

### ログが多すぎる場合

- TUI では自動的にリングバッファ（最大 1000 行）で管理
- CLI では必要に応じて ファイルをフィルタリング

## まとめ

統一されたログシステムにより：

1. **一貫性**: すべてのモードで同じ形式のログ
2. **保追跡可能性**: すべてのログがファイルに記録
3. **柔軟性**: 各モードに応じた最適な表示
4. **パフォーマンス**: 大量ログでも TUI が安定
