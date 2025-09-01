# OpenTelemetry Python ログ統合サンプル

このプロジェクトは、OpenTelemetry Python SDK と標準ライブラリ logging の統合を実装したサンプルです。

## 概要

Python標準ライブラリのloggingモジュールを使用した既存のコードを変更せずに、OpenTelemetryのテレメトリー機能を追加する方法を学習できます。

## 主な機能

- ✅ **既存コード互換**: 既存のPython loggingコードをそのまま使用可能
- ✅ **自動トレース関連付け**: スパンコンテキストが自動的にログに付与
- ✅ **構造化ログサポート**: extraパラメーターが自動的に属性として変換
- ✅ **高性能**: バッチ処理による効率的なデータ送信

## クイックスタート

### 1. 依存関係のインストール

```bash
pip install -r requirements.txt
```

### 2. サンプル実行

```bash
python -m example_otel.logging_example
```

### 3. Podman/Docker実行

```bash
podman-compose up --build
```

## アーキテクチャ

```
[アプリケーションコード]
        ↓ logger.info() 呼び出し
[Python logging フレームワーク]
        ↓ 並列処理で両方に送信
[StreamHandler]          [OpenTelemetry LoggingHandler]
        ↓                         ↓ ログレコードを変換
[標準出力]                [OpenTelemetry SDK]
                                 ↓ OTLP形式で送信
                          [外部テレメトリーシステム]
```

## 設定

### 環境変数

- `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT`: ログエクスポート先 (デフォルト: http://localhost:4317)
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`: トレースエクスポート先 (デフォルト: http://localhost:4317)
- `OTEL_LOG_LEVEL`: OpenTelemetryのログレベル (デフォルト: INFO)

### ログ設定

- `logging.conf`: INI形式の設定ファイル
- `logging_config.yaml`: YAML形式の設定ファイル

## 学習リソース

- [OpenTelemetry-Python-説明資料.md](./OpenTelemetry-Python-説明資料.md): 詳細な技術解説
- [src/example_otel/logging_example.py](./src/example_otel/logging_example.py): サンプル実装コード

## 技術仕様

- **Python**: 3.8以上（推奨3.11）
- **OpenTelemetry SDK**: 1.27.0
- **OpenTelemetry Instrumentation**: 0.48b0