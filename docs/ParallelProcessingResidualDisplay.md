# 並列処理時の残差推移表示の解決策

## 問題点

並列処理で複数の.datファイルを一括処理する際、各イベントごとに異なる残差推移が発生します。すべてのイベントを同時に表示すると混乱する可能性があります。

## 解決策の提案

### 案1: アクティブイベント追跡方式（推奨）

現在処理中のイベントのみを表示し、処理が完了したら次のイベントに切り替えます。

**メリット:**
- シンプルで理解しやすい
- リアルタイムで最新の処理状況を確認できる
- 実装が比較的簡単

**実装:**

```java
public class ResidualPlotPanel extends JPanel {
    private String currentEventName;
    private int currentEventIndex;
    private Map<String, XYSeries> eventSeriesMap;
    
    /**
     * 新しいイベントの処理を開始
     */
    public void startNewEvent(String eventName, int eventIndex) {
        SwingUtilities.invokeLater(() -> {
            // 前のイベントのシリーズを非表示にする
            if (currentEventName != null && eventSeriesMap.containsKey(currentEventName)) {
                XYSeries oldSeries = eventSeriesMap.get(currentEventName);
                dataset.removeSeries(oldSeries);
            }
            
            // 新しいイベントのシリーズを作成
            currentEventName = eventName;
            currentEventIndex = eventIndex;
            XYSeries newSeries = new XYSeries(eventName + " (Active)");
            eventSeriesMap.put(eventName, newSeries);
            dataset.addSeries(newSeries);
            
            // グラフタイトルを更新
            updateChartTitle();
            updateChart();
        });
    }
    
    /**
     * 現在のイベントに残差データを追加
     */
    public void addResidualPoint(int iteration, double residual) {
        if (currentEventName != null) {
            XYSeries series = eventSeriesMap.get(currentEventName);
            if (series != null) {
                series.add(iteration, residual);
                updateChart();
            }
        }
    }
}
```

### 案2: マルチシリーズ表示方式

複数のイベントを色分けして同時に表示します。

**メリット:**
- 複数のイベントの収束状況を比較できる
- 処理の進捗を全体的に把握できる

**デメリット:**
- イベント数が多いと見づらくなる
- 色分けの管理が複雑

**実装:**

```java
public class ResidualPlotPanel extends JPanel {
    private Map<String, XYSeries> eventSeriesMap;
    private Color[] eventColors;
    private int maxVisibleEvents = 5; // 最大表示イベント数
    
    /**
     * イベントごとの残差データを追加
     */
    public void addResidualPoint(String eventName, int iteration, double residual) {
        SwingUtilities.invokeLater(() -> {
            XYSeries series = eventSeriesMap.get(eventName);
            if (series == null) {
                // 新しいイベントのシリーズを作成
                series = new XYSeries(eventName);
                eventSeriesMap.put(eventName, series);
                
                // 色を割り当て
                int colorIndex = eventSeriesMap.size() % eventColors.length;
                XYPlot plot = chart.getXYPlot();
                XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
                renderer.setSeriesPaint(eventSeriesMap.size() - 1, eventColors[colorIndex]);
                
                dataset.addSeries(series);
                
                // 最大表示数を超えたら古いイベントを削除
                if (eventSeriesMap.size() > maxVisibleEvents) {
                    String oldestEvent = findOldestEvent();
                    removeEvent(oldestEvent);
                }
            }
            
            series.add(iteration, residual);
            updateChart();
        });
    }
    
    private String findOldestEvent() {
        // 最も古い（最後に更新された）イベントを見つける
        // 実装省略
        return null;
    }
}
```

### 案3: イベント選択方式

ドロップダウンやタブで表示するイベントを選択できます。

**メリット:**
- ユーザーが表示したいイベントを選択できる
- 複数のイベントを切り替えて比較できる

**デメリット:**
- UIがやや複雑になる

**実装:**

```java
public class ResidualPlotPanel extends JPanel {
    private JComboBox<String> eventSelector;
    private Map<String, XYSeries> eventSeriesMap;
    private String selectedEvent;
    
    private void createEventSelector() {
        eventSelector = new JComboBox<>();
        eventSelector.addActionListener(e -> {
            String selected = (String) eventSelector.getSelectedItem();
            if (selected != null && !selected.equals(selectedEvent)) {
                switchToEvent(selected);
            }
        });
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(new JLabel("Event:"));
        controlPanel.add(eventSelector);
        // ... 他のコントロール ...
        add(controlPanel, BorderLayout.NORTH);
    }
    
    /**
     * 新しいイベントを登録
     */
    public void registerEvent(String eventName) {
        SwingUtilities.invokeLater(() -> {
            if (!eventSeriesMap.containsKey(eventName)) {
                XYSeries series = new XYSeries(eventName);
                eventSeriesMap.put(eventName, series);
                eventSelector.addItem(eventName);
                
                if (selectedEvent == null) {
                    selectedEvent = eventName;
                    eventSelector.setSelectedItem(eventName);
                    dataset.addSeries(series);
                }
            }
        });
    }
    
    /**
     * 表示イベントを切り替え
     */
    private void switchToEvent(String eventName) {
        // 現在のシリーズを非表示
        if (selectedEvent != null) {
            XYSeries oldSeries = eventSeriesMap.get(selectedEvent);
            dataset.removeSeries(oldSeries);
        }
        
        // 新しいシリーズを表示
        selectedEvent = eventName;
        XYSeries newSeries = eventSeriesMap.get(eventName);
        dataset.addSeries(newSeries);
        updateChart();
    }
}
```

### 案4: 最新イベント + 履歴表示方式（推奨）

最新のイベントを強調表示し、完了したイベントは薄い色で履歴として表示します。

**メリット:**
- 現在の処理状況が明確
- 過去のイベントも参照可能
- 視覚的に分かりやすい

**実装:**

```java
public class ResidualPlotPanel extends JPanel {
    private String activeEventName;
    private Map<String, EventSeriesInfo> eventSeriesMap;
    private int maxHistoryEvents = 3; // 履歴として表示する最大イベント数
    
    private static class EventSeriesInfo {
        XYSeries series;
        boolean isActive;
        long lastUpdateTime;
    }
    
    /**
     * 新しいイベントをアクティブにする
     */
    public void setActiveEvent(String eventName) {
        SwingUtilities.invokeLater(() -> {
            // 前のアクティブイベントを非アクティブ化
            if (activeEventName != null) {
                EventSeriesInfo oldInfo = eventSeriesMap.get(activeEventName);
                if (oldInfo != null) {
                    oldInfo.isActive = false;
                    // 色を薄くする
                    updateSeriesColor(activeEventName, false);
                }
            }
            
            // 新しいイベントをアクティブ化
            activeEventName = eventName;
            EventSeriesInfo info = eventSeriesMap.get(eventName);
            if (info == null) {
                info = new EventSeriesInfo();
                info.series = new XYSeries(eventName);
                info.isActive = true;
                eventSeriesMap.put(eventName, info);
                dataset.addSeries(info.series);
            } else {
                info.isActive = true;
                if (!dataset.getSeries().contains(info.series)) {
                    dataset.addSeries(info.series);
                }
            }
            
            updateSeriesColor(eventName, true);
            
            // 古い履歴イベントを削除
            cleanupOldEvents();
            
            updateChart();
        });
    }
    
    private void updateSeriesColor(String eventName, boolean isActive) {
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        
        int seriesIndex = -1;
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            if (dataset.getSeries(i).getKey().equals(eventName)) {
                seriesIndex = i;
                break;
            }
        }
        
        if (seriesIndex >= 0) {
            if (isActive) {
                renderer.setSeriesPaint(seriesIndex, new Color(50, 150, 200)); // 濃い青
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(2.5f));
            } else {
                renderer.setSeriesPaint(seriesIndex, new Color(200, 200, 200)); // 薄いグレー
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(1.0f, BasicStroke.CAP_ROUND, 
                    BasicStroke.JOIN_ROUND, 1.0f, new float[]{3.0f, 3.0f}, 0.0f));
            }
        }
    }
    
    private void cleanupOldEvents() {
        // 非アクティブなイベントを時系列でソート
        List<Map.Entry<String, EventSeriesInfo>> inactiveEvents = new ArrayList<>();
        for (Map.Entry<String, EventSeriesInfo> entry : eventSeriesMap.entrySet()) {
            if (!entry.getValue().isActive) {
                inactiveEvents.add(entry);
            }
        }
        
        // 古い順にソート
        inactiveEvents.sort((a, b) -> 
            Long.compare(a.getValue().lastUpdateTime, b.getValue().lastUpdateTime));
        
        // 最大履歴数を超えた分を削除
        while (inactiveEvents.size() > maxHistoryEvents) {
            Map.Entry<String, EventSeriesInfo> oldest = inactiveEvents.remove(0);
            dataset.removeSeries(oldest.getValue().series);
            eventSeriesMap.remove(oldest.getKey());
        }
    }
}
```

### 案5: タブ形式表示

各イベントごとにタブを作成し、切り替えて表示します。

**メリット:**
- 各イベントの詳細を個別に確認できる
- UIが整理される

**デメリット:**
- タブ数が多くなると管理が大変
- 実装が複雑

## 推奨実装（案4の拡張版）

最新イベントを強調表示し、完了したイベントを履歴として表示する方式を推奨します。

### 統合方法

```java
// HypocenterLocationPanel.java の並列処理部分

Future<Void> future = executor.submit(() -> {
    String eventName = finalDatFile.getName();
    
    // イベント処理開始を通知
    SwingUtilities.invokeLater(() -> {
        residualPlotPanel.setActiveEvent(eventName);
    });
    
    // ソルバーにコールバックを設定
    ConvergenceCallback callback = new ConvergenceCallback() {
        @Override
        public void onResidualUpdate(int iteration, double residual) {
            residualPlotPanel.addResidualPoint(eventName, iteration, residual);
        }
    };
    
    if ("STD".equals(mode)) {
        HypoStationPairDiff solver = new HypoStationPairDiff(config);
        solver.setConvergenceCallback(callback);
        solver.start(inputPath, outputPath);
    } else if ("MCMC".equals(mode)) {
        HypoMCMC solver = new HypoMCMC(config);
        solver.setConvergenceCallback(callback);
        solver.start(inputPath, outputPath);
    }
    
    // 処理完了を通知
    SwingUtilities.invokeLater(() -> {
        residualPlotPanel.markEventCompleted(eventName);
    });
    
    return null;
});
```

### ResidualPlotPanelの拡張

```java
public void addResidualPoint(String eventName, int iteration, double residual) {
    SwingUtilities.invokeLater(() -> {
        EventSeriesInfo info = eventSeriesMap.get(eventName);
        if (info == null) {
            // 新しいイベントを登録
            registerEvent(eventName);
            info = eventSeriesMap.get(eventName);
        }
        
        info.series.add(iteration, residual);
        info.lastUpdateTime = System.currentTimeMillis();
        
        // アクティブイベントの場合は強調表示
        if (eventName.equals(activeEventName)) {
            updateChart();
        }
    });
}

public void markEventCompleted(String eventName) {
    SwingUtilities.invokeLater(() -> {
        EventSeriesInfo info = eventSeriesMap.get(eventName);
        if (info != null) {
            info.isActive = false;
            updateSeriesColor(eventName, false);
            
            // 次のアクティブイベントを探す
            if (eventName.equals(activeEventName)) {
                findNextActiveEvent();
            }
        }
    });
}
```

## 表示例

```
┌─────────────────────────────────────┐
│ 📈 Residual Convergence Plot         │
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
│ Active: event_003.dat              │
│ History: event_001.dat (completed) │
│          event_002.dat (completed) │
│                                     │
│ [Auto Scale ✓] [Clear] [Export]   │
└─────────────────────────────────────┘
```

## 実装の優先順位

1. **Phase 1**: 案4（最新イベント + 履歴表示）を実装 ✓ (完了)
2. **Phase 2**: イベント選択機能を追加（オプション）
3. **Phase 3**: 統計情報の表示（平均残差、最小残差など）

## 実装完了

`ResidualPlotPanel`に以下の機能を追加しました：

- **複数イベント対応**: `addResidualPoint(String eventName, int iteration, double residual)`
- **アクティブイベント設定**: `setActiveEvent(String eventName)`
- **イベント完了マーク**: `markEventCompleted(String eventName)`
- **自動履歴管理**: 完了したイベントを最大3つまで履歴として表示
- **視覚的区別**: アクティブイベントは濃い青、完了イベントは薄いグレーで表示

## 使用例

```java
// 並列処理中
Future<Void> future = executor.submit(() -> {
    String eventName = datFile.getName();
    
    // イベント開始
    residualPlotPanel.setActiveEvent(eventName);
    
    // コールバック設定
    ConvergenceCallback callback = (iter, res) -> {
        residualPlotPanel.addResidualPoint(eventName, iter, res);
    };
    
    solver.setConvergenceCallback(callback);
    solver.start(inputPath, outputPath);
    
    // イベント完了
    residualPlotPanel.markEventCompleted(eventName);
    
    return null;
});
```

