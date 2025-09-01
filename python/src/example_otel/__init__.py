"""OpenTelemetry Python ログ統合サンプルパッケージ

このパッケージは、OpenTelemetry Python SDK を使用したログ統合の
サンプル実装を提供します。

主な機能:
- OpenTelemetry SDK の初期化と設定
- Python標準ライブラリ logging との統合
- トレースコンテキストとログの関連付け
- OTLP形式での外部システムへのログ送信
"""

__version__ = "1.0.0"
__author__ = "OpenTelemetry Python Example"
__email__ = "example@opentelemetry.io"

# パッケージレベルでの公開API
from .logging_example import main, LoggingExample

__all__ = ["main", "LoggingExample"]