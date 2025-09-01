# OpenTelemetry ログ統合プロジェクト

## プロジェクト概要

このプロジェクトは、**OpenTelemetry SDK とログライブラリの統合**について学習・理解するためのサンプルプロジェクトです。Java版とPython版の両方を提供し、それぞれの言語における統合方法を比較学習できます。

### 調査対象
- **Java版**: OpenTelemetry Java SDK（1.53.0） + Log4j Appender（2.18.1-alpha）
- **Python版**: OpenTelemetry Python SDK（1.27.0） + 標準ライブラリlogging
- ライブラリ計装（ゼロコード計装ではなく）
- ログテレメトリーの実装パターンと比較
- 各言語でのインスタンス関係性とデータフロー

### 技術仕様

#### Java版
- **Java**: 17（Java 8以上対応）
- **OpenTelemetry SDK**: 1.53.0
- **OpenTelemetry Instrumentation**: 2.18.1-alpha
- **Log4j**: 2.21.1
- **Maven**: 3.x

#### Python版
- **Python**: 3.8以上（推奨3.11）
- **OpenTelemetry SDK**: 1.27.0
- **OpenTelemetry Instrumentation**: 0.48b0
- **標準ライブラリ**: logging
- **パッケージ管理**: pip / pyproject.toml

---

## ファイル構成

```
/home/wsl-user/projects/otel-log/
├── java/                                # Java版実装
│   ├── pom.xml                          # Maven設定（依存関係定義）
│   ├── Dockerfile                       # Podman/Docker用コンテナ定義
│   ├── podman-compose.yml              # Podman Compose設定
│   ├── src/main/
│   │   ├── java/com/example/otel/
│   │   │   └── LoggingExample.java      # メインサンプルプログラム
│   │   └── resources/
│   │       └── log4j2.xml               # Log4j設定（OpenTelemetry Appender含む）
│   └── OpenTelemetry-Log4j-説明資料.md  # Java版詳細技術解説
├── python/                              # Python版実装
│   ├── requirements.txt                 # Python依存関係定義
│   ├── pyproject.toml                   # Python プロジェクト設定
│   ├── Dockerfile                       # Podman/Docker用コンテナ定義
│   ├── podman-compose.yml              # Podman Compose設定
│   ├── logging.conf                     # Python logging設定（INI形式）
│   ├── logging_config.yaml              # Python logging設定（YAML形式）
│   ├── src/example_otel/
│   │   ├── __init__.py                  # パッケージ初期化
│   │   └── logging_example.py           # メインサンプルプログラム
│   └── OpenTelemetry-Python-説明資料.md # Python版詳細技術解説
├── generated-diagrams/                  # アーキテクチャ図（共有）
│   ├── otel-java-data-flow-en.png       # Javaデータフロー図
│   └── otel-java-instance-relationships-en.png # Javaインスタンス関係性図
└── CLAUDE.md                           # このファイル（プロジェクト全体説明）
```

---

## クイックスタート

### Java版

#### 1. 依存関係のインストール
```bash
cd java/
mvn clean compile
```

#### 2. サンプルプログラムの実行
```bash
mvn exec:java
```

### Python版

#### 1. 依存関係のインストール
```bash
cd python/
pip install -r requirements.txt
```

#### 2. サンプルプログラムの実行
```bash
python -m example_otel.logging_example
```

### OpenTelemetry Collector（オプション）
ログデータを実際に受信したい場合は、別途OpenTelemetry Collectorを起動してください：
```bash
# docker-compose.yml を作成して Collector を起動
docker-compose up
```

---

## Podman での実行方法

### 前提条件
- **既存のOpenTelemetry Collector**がlocalhostで稼働中
- Collectorのポート4317でOTLPレシーバーが待機
- podman-composeがインストール済み

### Java版 Podman実行手順

#### 1. コンテナイメージのビルドと実行
```bash
# Java版ディレクトリで実行
cd java/

# podman-composeでビルド・実行
podman-compose up --build
```

#### 2. バックグラウンド実行
```bash
# デタッチモードで実行（バックグラウンド）
podman-compose up -d --build
```

#### 3. ログの確認
```bash
# リアルタイムログ表示
podman-compose logs -f otel-java-app

# 特定時刻以降のログのみ表示
podman-compose logs --since="10m" otel-java-app
```

### Python版 Podman実行手順

#### 1. コンテナイメージのビルドと実行
```bash
# Python版ディレクトリで実行
cd python/

# podman-composeでビルド・実行
podman-compose up --build
```

#### 2. ログの確認
```bash
# リアルタイムログ表示
podman-compose logs -f otel-python-app
```

### 共通操作

#### コンテナの停止・削除
```bash
# 停止
podman-compose down

# ボリュームも含めて完全削除
podman-compose down -v
```

### Podman固有の設定

#### ネットワーク設定オプション

**オプション1: host ネットワーク使用（推奨）**
```yaml
# podman-compose.yml での設定
network_mode: host
```
- 既存のCollectorに直接localhost経由でアクセス
- 最もシンプルで確実な方法

**オプション2: カスタムネットワーク**
```yaml
networks:
  otel-network:
    driver: bridge
```
- より分離されたネットワーク環境
- `host.containers.internal` でホストにアクセス

#### 環境変数のカスタマイズ
```bash
# 環境変数ファイルを使用
echo "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=http://localhost:4317" > .env
podman-compose --env-file .env up
```

### Podman デバッグコマンド

```bash
# コンテナ内部に入ってデバッグ
podman-compose exec otel-java-app bash

# コンテナの詳細情報確認
podman inspect otel-java-log4j-example

# Podmanネットワークの確認
podman network ls
podman network inspect bridge
```

---

## 学習のポイント

### 🔍 重要な学習項目

#### Java版（Log4j Appender）
1. **Log4j Appenderの仕組み**
   - `log4j2.xml` での設定方法
   - `OpenTelemetryAppender.install()` による統合

2. **OpenTelemetry Java SDK の初期化**
   - `Resource` による サービス識別
   - `BatchLogRecordProcessor` によるバッチ処理
   - `OtlpGrpcLogRecordExporter` による外部送信

3. **トレースとログの関連付け**
   - `Span.makeCurrent()` によるコンテキスト設定
   - 自動的な trace_id, span_id の付与仕組み

4. **構造化ログ**
   - `MapMessage` による属性付きログ
   - `ThreadContext` による スレッド固有情報

#### Python版（標準ライブラリlogging）
1. **LoggingHandlerの仕組み**
   - Python標準ライブラリのloggingとの統合
   - `LoggingInstrumentor` による自動計装

2. **OpenTelemetry Python SDK の初期化**
   - `Resource` によるサービス識別
   - `BatchLogRecordProcessor` によるバッチ処理
   - `OTLPLogExporter` による外部送信

3. **トレースとログの関連付け**
   - `tracer.start_as_current_span()` によるコンテキスト設定
   - 自動的な trace_id, span_id の付与仕組み

4. **構造化ログ**
   - `extra` パラメーターによる属性付きログ
   - 辞書形式での構造化データ送信

### 📚 参考ドキュメント

- **`java/OpenTelemetry-Log4j-説明資料.md`**: Java版技術的詳細とインスタンス関係性の完全解説
  - 🎨 **包括的なMermaid図**: 11種類の図でアーキテクチャからトラブルシューティングまで完全可視化
  - フローチャート、シーケンス図、クラス図、状態遷移図、円グラフを駆使した技術解説
- **`python/OpenTelemetry-Python-説明資料.md`**: Python版技術的詳細とインスタンス関係性の完全解説
  - 🎨 **包括的なMermaid図**: 11種類の図でPython固有の実装詳細を視覚化
  - GIL、例外処理、循環参照回避などPython特有の考慮事項も図解
- **`java/src/main/java/com/example/otel/LoggingExample.java`**: Java実装例とベストプラクティス
- **`python/src/example_otel/logging_example.py`**: Python実装例とベストプラクティス
- **図表**: データフローとアーキテクチャの視覚的理解

---

## 開発コマンド

### Java版 コマンド

#### Maven コマンド
```bash
cd java/

# 依存関係の確認
mvn dependency:tree

# コンパイルのみ
mvn compile

# テスト実行（テストがある場合）
mvn test

# プロジェクトのクリーンアップ
mvn clean
```

#### デバッグ実行
```bash
# デバッグ情報を有効にして実行
mvn exec:java -Dlog4j2.debug=true
```

### Python版 コマンド

#### pip コマンド
```bash
cd python/

# 依存関係のインストール
pip install -r requirements.txt

# 開発用依存関係も含めてインストール
pip install -e .

# 依存関係の確認
pip list
```

#### デバッグ実行
```bash
# デバッグ情報を有効にして実行
OTEL_LOG_LEVEL=debug python -m example_otel.logging_example

# 詳細なPythonログも有効にして実行
python -u -m example_otel.logging_example
```

---

## トラブルシューティング

### よくある問題

#### 1. ログがOpenTelemetryに送信されない
**チェックポイント**:
- `OpenTelemetryAppender.install(sdk)` が実行されているか
- `log4j2.xml` で `AppenderRef` が設定されているか
- OpenTelemetry SDK が適切に初期化されているか

#### 2. 文字化けが発生する
**対処法**:
```bash
# UTF-8エンコーディングで実行
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
mvn exec:java
```

#### 3. 外部システムへの送信エラー
**確認事項**:
- エンドポイントURL（`http://localhost:4317`）が正しいか
- OpenTelemetry Collector が起動しているか
- ネットワーク接続に問題がないか

---

## 学習進行の推奨順序

### Java版学習順序
1. **📖 概念理解**: `java/OpenTelemetry-Log4j-説明資料.md` を読む
   - 🎨 11種類のMermaid図でアーキテクチャから実装詳細まで視覚的に理解
2. **🎯 コード理解**: `java/src/main/java/com/example/otel/LoggingExample.java` のコメントを追いながらコードを読む
3. **🔧 設定理解**: `java/src/main/resources/log4j2.xml` と `java/pom.xml` の設定を確認
4. **▶️ 実行確認**: `mvn exec:java` で実際に動作させる
5. **🎨 アーキテクチャ**: バッチ処理フロー、エラーハンドリング、OTLPエクスポートの図解で深く理解

### Python版学習順序
1. **📖 概念理解**: `python/OpenTelemetry-Python-説明資料.md` を読む
   - 🎨 11種類のMermaid図でPython固有の実装パターンを視覚的に理解
2. **🎯 コード理解**: `python/src/example_otel/logging_example.py` のコメントを追いながらコードを読む
3. **🔧 設定理解**: `python/requirements.txt` と `python/pyproject.toml` の設定を確認
4. **▶️ 実行確認**: `python -m example_otel.logging_example` で実際に動作させる
5. **🎨 アーキテクチャ**: GIL、例外処理、トラブルシューティングの図解でPython特有の課題を理解

### 比較学習
1. **🔄 実装比較**: Java版とPython版の実装方法の違いを理解
2. **📊 パフォーマンス**: 両方の言語でのパフォーマンス特性を比較
3. **🛠️ 統合方法**: Log4j AppenderとPython LoggingHandlerの違いを理解

---

## 関連リソース

### 公式ドキュメント

#### Java関連
- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/languages/java/)
- [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
- [Log4j Appender README](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/log4j/log4j-appender-2.17/library/README.md)

#### Python関連
- [OpenTelemetry Python Documentation](https://opentelemetry.io/docs/languages/python/)
- [OpenTelemetry Python Repository](https://github.com/open-telemetry/opentelemetry-python)
- [OpenTelemetry Python Contrib](https://github.com/open-telemetry/opentelemetry-python-contrib)

### パッケージリポジトリ

#### Maven Repository (Java)
- [OpenTelemetry Log4j Appender](https://mvnrepository.com/artifact/io.opentelemetry.instrumentation/opentelemetry-log4j-appender-2.17)
- [OpenTelemetry SDK](https://mvnrepository.com/artifact/io.opentelemetry/opentelemetry-sdk)

#### PyPI Repository (Python)
- [OpenTelemetry SDK](https://pypi.org/project/opentelemetry-sdk/)
- [OpenTelemetry Instrumentation Logging](https://pypi.org/project/opentelemetry-instrumentation-logging/)

---

## プロジェクトの特徴

✅ **多言語対応**: Java版とPython版の両方を提供し、言語間の違いを比較学習可能  
✅ **完全なサンプル**: 実際に動作する完全なコード例  
✅ **詳細コメント**: 初心者向けの丁寧な説明  
✅ **最新バージョン**: 2025年最新の安定版を使用  
✅ **ベストプラクティス**: 推奨される実装パターンに準拠  
✅ **包括的な設定**: Dockerコンテナ、Podman Compose、設定ファイルを完備  
✅ **視覚的理解**: **22種類のMermaid図**でデータフロー・インスタンス関係性・エラーハンドリングを完全可視化  
✅ **技術解説充実**: アーキテクチャから実装詳細、トラブルシューティングまで包括的にカバー  

このプロジェクトを通して、以下を実践的に学習できます：

### Java版の特徴
- OpenTelemetryとLog4jの統合による可観測性の実装
- Log4j Appenderを使った既存コードの無変更統合
- Maven エコシステムでの依存関係管理

### Python版の特徴
- OpenTelemetryとPython標準ライブラリloggingの統合
- LoggingHandlerを使った既存コードの無変更統合
- pipエコシステムでの依存関係管理

### 比較学習の価値
- 異なる言語での同じ概念の実装方法を比較
- ログライブラリのエコシステムの違いを理解
- パフォーマンス特性と実装パターンの違いを学習