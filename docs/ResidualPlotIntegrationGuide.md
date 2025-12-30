# 残差推移グラフの統合ガイド

## 概要

`ResidualPlotPanel`を`HypocenterLocationPanel`に統合し、STD、MCMC、TRDモードの残差推移をリアルタイムで可視化する方法です。

## 1. HypocenterLocationPanelへの統合

### 1.1 フィールドの追加

```java
public class HypocenterLocationPanel extends JPanel {
    // ... 既存のフィールド ...
    
    private ResidualPlotPanel residualPlotPanel;
    private ConvergenceCallback convergenceCallback;
}
```

### 1.2 パネルの作成と配置

```java
private void createMainLayout() {
    // ... 既存のレイアウト ...
    
    // 収束情報パネル（ログパネルの下に追加）
    residualPlotPanel = new ResidualPlotPanel();
    
    // レイアウト例
    JPanel rightPanel = new JPanel(new BorderLayout());
    rightPanel.add(createLogPanel(), BorderLayout.CENTER);
    rightPanel.add(residualPlotPanel, BorderLayout.SOUTH);
    
    // または、タブ形式
    JTabbedPane infoTabbedPane = new JTabbedPane();
    infoTabbedPane.addTab("Log", createLogPanel());
    infoTabbedPane.addTab("Residual Plot", residualPlotPanel);
}
```

### 1.3 コールバックの設定

```java
private void setupConvergenceCallback() {
    convergenceCallback = new ConvergenceCallback() {
        @Override
        public void onResidualUpdate(int iteration, double residual) {
            residualPlotPanel.addResidualPoint(iteration, residual);
        }
        
        @Override
        public void onLikelihoodUpdate(int sample, double logLikelihood) {
            residualPlotPanel.addLikelihoodPoint(sample, logLikelihood);
        }
        
        @Override
        public void onClusterResidualUpdate(int clusterId, int iteration, double residual) {
            residualPlotPanel.addResidualPoint(iteration, residual, clusterId);
        }
        
        @Override
        public void onIterationUpdate(int iteration, int evaluations, double residual, 
                                     double[] parameterChanges) {
            residualPlotPanel.addResidualPoint(iteration, residual);
        }
    };
}
```

## 2. 各ソルバーへの統合

### 2.1 STDモード（HypoStationPairDiff）

```java
// HypoStationPairDiff.java に追加
private ConvergenceCallback convergenceCallback;

public void setConvergenceCallback(ConvergenceCallback callback) {
    this.convergenceCallback = callback;
}

// start()メソッド内の反復ループで
for (int n = 0; n < 10; n++) {
    // ... 既存のコード ...
    
    if (convergenceCallback != null) {
        double[] paramChanges = new double[]{
            Math.abs(newLon - lon),
            Math.abs(newLat - lat),
            Math.abs(newDep - dep)
        };
        convergenceCallback.onIterationUpdate(
            n, nEval, res, paramChanges
        );
    }
    
    // ... 既存のコード ...
}
```

### 2.2 MCMCモード（HypoMCMC）

```java
// HypoMCMC.java に追加
private ConvergenceCallback convergenceCallback;

public void setConvergenceCallback(ConvergenceCallback callback) {
    this.convergenceCallback = callback;
}

// start()メソッド内のサンプリングループで
for (int i = 0; i < nSamples; i++) {
    // ... 既存のコード ...
    
    if (convergenceCallback != null) {
        // 残差の計算（簡易版）
        double residual = Math.sqrt(-currentLikelihood);
        convergenceCallback.onResidualUpdate(i, residual);
        convergenceCallback.onLikelihoodUpdate(i, currentLikelihood);
    }
    
    // ... 既存のコード ...
}
```

### 2.3 TRDモード（HypoTripleDiff）

```java
// HypoTripleDiff.java に追加
private ConvergenceCallback convergenceCallback;

public void setConvergenceCallback(ConvergenceCallback callback) {
    this.convergenceCallback = callback;
}

// start()メソッド内の反復ループで
for (int j = 0; j < iterNum; j++) {
    // ... 既存のコード ...
    
    // LSQR結果取得後
    ScipyLSQR.LSQRResult result = ScipyLSQR.lsqr(...);
    
    // 残差RMSの計算
    double residualRMS = calculateResidualRMS(d, G, result.x);
    
    if (convergenceCallback != null) {
        convergenceCallback.onClusterResidualUpdate(
            clusterId, j, residualRMS
        );
    }
    
    // ... 既存のコード ...
}

private double calculateResidualRMS(double[] d, Object G, double[] x) {
    // 簡易的な残差RMS計算
    if (G instanceof OpenMapRealMatrix) {
        OpenMapRealMatrix GMatrix = (OpenMapRealMatrix) G;
        double[] residual = new double[d.length];
        double[] Gx = GMatrix.operate(x);
        for (int i = 0; i < d.length; i++) {
            residual[i] = d[i] - Gx[i];
        }
        double sumSq = 0;
        for (double r : residual) {
            sumSq += r * r;
        }
        return Math.sqrt(sumSq / residual.length);
    } else if (G instanceof COOSparseMatrix) {
        COOSparseMatrix GMatrix = (COOSparseMatrix) G;
        double[] residual = new double[d.length];
        double[] Gx = GMatrix.operate(x);
        for (int i = 0; i < d.length; i++) {
            residual[i] = d[i] - Gx[i];
        }
        double sumSq = 0;
        for (double r : residual) {
            sumSq += r * r;
        }
        return Math.sqrt(sumSq / residual.length);
    }
    return 0.0;
}
```

## 3. GUIでの使用例

```java
// HypocenterLocationPanel.java の executeLocation()メソッド内

// モードに応じてグラフを初期化
String selectedMode = (String) modeCombo.getSelectedItem();
residualPlotPanel.setMode(selectedMode);
residualPlotPanel.clearData();

// ソルバー作成時にコールバックを設定
if ("STD".equals(selectedMode)) {
    HypoStationPairDiff solver = new HypoStationPairDiff(config);
    solver.setConvergenceCallback(convergenceCallback);
    solver.start(inputPath, outputPath);
} else if ("MCMC".equals(selectedMode)) {
    HypoMCMC solver = new HypoMCMC(config);
    solver.setConvergenceCallback(convergenceCallback);
    solver.start(inputPath, outputPath);
} else if ("TRD".equals(selectedMode)) {
    HypoTripleDiff solver = new HypoTripleDiff(config);
    solver.setConvergenceCallback(convergenceCallback);
    solver.start(inputPath, outputPath);
}
```

## 4. 表示例

### STDモード
```
┌─────────────────────────────────────┐
│ 📈 Residual Convergence Plot        │
│                                     │
│  Residual (s)                      │
│   0.10 │                           │
│   0.08 │     ╱╲                    │
│   0.06 │    ╱  ╲                   │
│   0.04 │   ╱    ╲                  │
│   0.02 │  ╱      ╲                 │
│   0.00 └───────────────             │
│        0  2  4  6  8 10            │
│            Iteration                │
│                                     │
│ [Auto Scale ✓] [Clear] [Export]    │
└─────────────────────────────────────┘
```

### MCMCモード
```
┌─────────────────────────────────────┐
│ 📈 Residual & Log-Likelihood        │
│                                     │
│  Residual (s) / Log-L              │
│   0.10 │                           │
│   0.08 │     ╱╲                    │
│   0.06 │    ╱  ╲                   │
│   0.04 │   ╱    ╲                  │
│   0.02 │  ╱      ╲                 │
│   0.00 └───────────────             │
│        0  200 400 600 800 1000      │
│            Sample                   │
│                                     │
│ Legend:                             │
│  ─── Residual                       │
│  ─── Log-Likelihood                 │
│                                     │
│ [Auto Scale ✓] [Clear] [Export]    │
└─────────────────────────────────────┘
```

### TRDモード
```
┌─────────────────────────────────────┐
│ 📈 Residual Convergence (Per Cluster)│
│                                     │
│  Residual (s)                      │
│   0.10 │                           │
│   0.08 │     ╱╲                    │
│   0.06 │    ╱  ╲                   │
│   0.04 │   ╱    ╲                  │
│   0.02 │  ╱      ╲                 │
│   0.00 └───────────────             │
│        0  2  4  6  8 10            │
│            Iteration                │
│                                     │
│ Legend:                             │
│  ─── Overall Residual               │
│  ─ ─ ─ Per Cluster                  │
│                                     │
│ [Auto Scale ✓] [Clear] [Export]    │
└─────────────────────────────────────┘
```

## 5. 実装のステップ

1. **ResidualPlotPanelの作成** ✓ (完了)
2. **ConvergenceCallbackインターフェースの作成** ✓ (完了)
3. **HypocenterLocationPanelへの統合**
   - パネルの追加
   - コールバックの設定
4. **各ソルバーへの統合**
   - STDモード: 反復ごとに残差を報告
   - MCMCモード: サンプルごとに残差と尤度を報告
   - TRDモード: クラスター・反復ごとに残差を報告
5. **テストと調整**
   - 更新頻度の調整
   - 表示範囲の最適化
   - パフォーマンスの確認

## 6. 注意事項

- **スレッドセーフティ**: すべてのUI更新は`SwingUtilities.invokeLater()`で実行
- **パフォーマンス**: 大量のデータポイントは自動的に制限（デフォルト1000点）
- **メモリ管理**: 古いデータポイントは自動的に削除
- **更新頻度**: 必要に応じて更新頻度を制限（例: 10回に1回のみ更新）

