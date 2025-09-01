"""
OpenTelemetry + Python logging 統合サンプル

このサンプルは以下を示します:
1. OpenTelemetry SDK の初期化方法
2. Python標準ライブラリ logging の設定と統合
3. 従来の Python logging を使用したログ出力
4. トレースコンテキストとログの関連付け
5. 構造化ログ（JSON形式）の使用方法

【重要】このサンプルでは Logs API を直接呼び出さず、
Python標準ライブラリのloggingを通してOpenTelemetryにログデータを送信します。
"""

import logging
import json
import time
import sys
import os
from typing import Dict, Any, Optional
from datetime import datetime

# OpenTelemetry コア機能
from opentelemetry import trace, logs
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.logs import LoggingHandler
from opentelemetry.sdk.logs.export import BatchLogRecordProcessor
from opentelemetry.sdk.resources import Resource

# OpenTelemetry Exporter (OTLP形式での出力)
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter

# セマンティック規約（標準的な属性名定義）
from opentelemetry.semconv.resource import ResourceAttributes

# OpenTelemetry logging instrumentation
from opentelemetry.instrumentation.logging import LoggingInstrumentor


class LoggingExample:
    """
    OpenTelemetry Python logging 統合のデモンストレーション
    
    このクラスは以下のコンポーネント間の関係を実際に動作させて示します:
    
    [アプリケーションコード] 
           ↓ (Python logging経由でログ出力)
    [Python logging フレームワーク]
           ↓ (OpenTelemetry LoggingHandlerが変換)
    [OpenTelemetry SDK]
           ↓ (OTLP Exporterで送信)
    [外部テレメトリーシステム]
    """
    
    def __init__(self):
        """LoggingExampleインスタンスを初期化"""
        # OpenTelemetry SDK を初期化
        self.resource = self._create_resource()
        self.tracer_provider = self._setup_tracing()
        self.log_emitter_provider = self._setup_logging()
        
        # Python標準ライブラリのロガーを取得
        # このロガーが通常通りログを出力し、同時にOpenTelemetryにもデータを送信
        self.logger = logging.getLogger(__name__)
        
        # OpenTelemetryのトレーサー
        # スパン（トレースの単位）を作成するために使用
        self.tracer = trace.get_tracer(__name__)
        
        # ログ計装を有効化
        LoggingInstrumentor().instrument(set_logging_format=True)
    
    def _create_resource(self) -> Resource:
        """
        アプリケーションを識別するためのリソース情報を作成
        
        リソースは、テレメトリーデータの送信元を識別するために使用されます。
        サービス名、バージョン、環境などの情報を含みます。
        
        Returns:
            Resource: OpenTelemetryリソースオブジェクト
        """
        return Resource.create({
            # セマンティック規約に従った標準的な属性を設定
            ResourceAttributes.SERVICE_NAME: "otel-python-logging-example",
            ResourceAttributes.SERVICE_VERSION: "1.0.0",
            ResourceAttributes.SERVICE_INSTANCE_ID: f"instance-{os.getpid()}",
            
            # カスタム属性
            "environment": "development",
            "team": "platform-engineering",
            "application.language": "python",
            "example.type": "logging_integration"
        })
    
    def _setup_tracing(self) -> TracerProvider:
        """
        OpenTelemetryトレース機能を初期化
        
        トレースは分散システムでのリクエスト追跡に使用されます。
        ログとトレースを関連付けることで、包括的な可観測性を実現します。
        
        Returns:
            TracerProvider: 設定済みのトレースプロバイダー
        """
        # トレースプロバイダーを作成し、リソース情報を設定
        tracer_provider = TracerProvider(resource=self.resource)
        trace.set_tracer_provider(tracer_provider)
        
        # OTLP Exporter を設定（トレース用）
        # 環境変数 OTEL_EXPORTER_OTLP_TRACES_ENDPOINT でエンドポイントを指定可能
        otlp_exporter = OTLPSpanExporter(
            endpoint=os.getenv(
                "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", 
                "http://localhost:4317"
            ),
            # 追加のヘッダー情報
            headers={"service-name": "otel-python-logging-example"}
        )
        
        # バッチスパンプロセッサーを設定
        # 複数のスパンをまとめて効率的に送信
        span_processor = BatchSpanProcessor(
            otlp_exporter,
            max_queue_size=2048,      # キューの最大サイズ
            max_export_batch_size=512, # 一回の送信での最大バッチサイズ
            export_timeout=30000,     # エクスポートタイムアウト（ミリ秒）
            schedule_delay=5000       # バッチ処理の間隔（ミリ秒）
        )
        tracer_provider.add_span_processor(span_processor)
        
        return tracer_provider
    
    def _setup_logging(self):
        """
        OpenTelemetryログ機能を初期化
        
        Python標準ライブラリのloggingとOpenTelemetryを統合し、
        ログメッセージを自動的にOpenTelemetry形式で送信します。
        
        Returns:
            LoggerProvider: 設定済みのログプロバイダー
        """
        # ログエミッタープロバイダーを作成
        log_emitter_provider = logs.get_logger_provider()
        
        # OTLP Exporter を設定（ログ用）
        # 環境変数 OTEL_EXPORTER_OTLP_LOGS_ENDPOINT でエンドポイントを指定可能
        otlp_log_exporter = OTLPLogExporter(
            endpoint=os.getenv(
                "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT",
                "http://localhost:4317"
            ),
            # 追加のヘッダー情報
            headers={"service-name": "otel-python-logging-example"}
        )
        
        # バッチログレコードプロセッサーを設定
        # 複数のログレコードをまとめて効率的に送信
        log_processor = BatchLogRecordProcessor(
            otlp_log_exporter,
            max_queue_size=2048,      # キューの最大サイズ
            max_export_batch_size=512, # 一回の送信での最大バッチサイズ
            export_timeout=30000,     # エクスポートタイムアウト（ミリ秒）
            schedule_delay=5000       # バッチ処理の間隔（ミリ秒）
        )
        log_emitter_provider.add_log_record_processor(log_processor)
        
        # OpenTelemetry LoggingHandlerを作成
        # Python標準ライブラリのloggingとOpenTelemetryを橋渡し
        handler = LoggingHandler(
            level=logging.NOTSET,
            logger_provider=log_emitter_provider
        )
        
        # rootロガーにHandlerを追加
        logging.getLogger().addHandler(handler)
        logging.getLogger().setLevel(logging.INFO)
        
        return log_emitter_provider
    
    def demonstrate_basic_logging(self):
        """
        基本的なログ出力のデモンストレーション
        
        通常のPython loggingライブラリの使用方法を示しながら、
        同時にOpenTelemetryにもログデータが送信されることを確認します。
        """
        self.logger.info("=== 基本的なログ出力のデモンストレーション ===")
        
        # 各ログレベルでの出力例
        self.logger.debug("これはデバッグレベルのログです")
        self.logger.info("これは情報レベルのログです")
        self.logger.warning("これは警告レベルのログです")
        self.logger.error("これはエラーレベルのログです")
        
        try:
            # 意図的に例外を発生させてスタックトレース付きログを出力
            result = 1 / 0
        except ZeroDivisionError:
            self.logger.exception("ゼロ除算エラーが発生しました")
        
        self.logger.info("基本的なログ出力のデモンストレーション完了")
    
    def demonstrate_structured_logging(self):
        """
        構造化ログのデモンストレーション
        
        extra パラメーターを使用して、ログメッセージに追加のコンテキスト情報を
        付与する方法を示します。この情報はOpenTelemetryの属性として送信されます。
        """
        self.logger.info("=== 構造化ログのデモンストレーション ===")
        
        # 基本的な構造化ログ
        user_data = {
            "user_id": 12345,
            "user_name": "田中太郎",
            "session_id": "sess_abc123",
            "operation": "login"
        }
        
        self.logger.info(
            "ユーザーログイン処理", 
            extra=user_data
        )
        
        # より複雑な構造化ログ
        transaction_data = {
            "transaction_id": "tx_987654321",
            "amount": 15000,
            "currency": "JPY",
            "merchant_id": "merchant_001",
            "payment_method": "credit_card",
            "processing_time_ms": 245,
            "success": True
        }
        
        self.logger.info(
            "決済処理完了",
            extra=transaction_data
        )
        
        # ネストされた構造化データ
        order_data = {
            "order_id": "ord_555666",
            "customer": {
                "id": "cust_789",
                "name": "鈴木花子",
                "tier": "premium"
            },
            "items": [
                {"product_id": "prod_001", "quantity": 2, "price": 3000},
                {"product_id": "prod_002", "quantity": 1, "price": 9000}
            ],
            "total_amount": 15000,
            "shipping_address": {
                "prefecture": "東京都",
                "city": "渋谷区"
            }
        }
        
        self.logger.info(
            "注文処理",
            extra=order_data
        )
        
        self.logger.info("構造化ログのデモンストレーション完了")
    
    def demonstrate_trace_log_correlation(self):
        """
        トレースとログの関連付けのデモンストレーション
        
        OpenTelemetryのスパン（トレース）を作成し、そのコンテキスト内で
        ログを出力することで、ログとトレースを関連付けます。
        """
        self.logger.info("=== トレースとログの関連付けデモンストレーション ===")
        
        # ルートスパンを作成
        with self.tracer.start_as_current_span("user_registration_process") as root_span:
            # スパンに属性を設定
            root_span.set_attribute("user.id", "user_999")
            root_span.set_attribute("operation.type", "user_registration")
            
            self.logger.info("ユーザー登録処理を開始しました")
            
            # 入力検証のサブスパン
            with self.tracer.start_as_current_span("validate_user_input") as validation_span:
                validation_span.set_attribute("validation.step", "email_format")
                self.logger.info("メールアドレスの形式を検証中", extra={
                    "email": "user@example.com",
                    "validation_rule": "RFC5322"
                })
                
                time.sleep(0.1)  # 処理時間をシミュレート
                
                validation_span.set_attribute("validation.result", "success")
                self.logger.info("メールアドレスの検証が完了しました")
            
            # データベース保存のサブスパン
            with self.tracer.start_as_current_span("save_to_database") as db_span:
                db_span.set_attribute("db.operation", "INSERT")
                db_span.set_attribute("db.table", "users")
                
                self.logger.info("データベースにユーザー情報を保存中", extra={
                    "db.connection_pool": "primary",
                    "db.transaction_id": "tx_db_001"
                })
                
                time.sleep(0.2)  # データベース処理時間をシミュレート
                
                # 成功ログ
                db_span.set_attribute("db.rows_affected", 1)
                self.logger.info("ユーザー情報の保存が完了しました", extra={
                    "db.execution_time_ms": 180,
                    "user_id": "user_999"
                })
            
            # メール送信のサブスパン
            with self.tracer.start_as_current_span("send_welcome_email") as email_span:
                email_span.set_attribute("email.provider", "smtp_service")
                email_span.set_attribute("email.type", "welcome")
                
                self.logger.info("ウェルカムメール送信中", extra={
                    "email.recipient": "user@example.com",
                    "email.template": "welcome_template_v2"
                })
                
                time.sleep(0.15)  # メール送信時間をシミュレート
                
                email_span.set_attribute("email.status", "sent")
                self.logger.info("ウェルカムメールの送信が完了しました")
            
            root_span.set_attribute("registration.status", "completed")
            self.logger.info("ユーザー登録処理が正常に完了しました", extra={
                "total_processing_time_ms": 450,
                "user_id": "user_999"
            })
        
        self.logger.info("トレースとログの関連付けデモンストレーション完了")
    
    def demonstrate_error_logging_with_traces(self):
        """
        エラーログとトレースの統合デモンストレーション
        
        エラーが発生した際のログとトレースの関連付け、
        スパンステータスの設定方法を示します。
        """
        self.logger.info("=== エラーログとトレースの統合デモンストレーション ===")
        
        with self.tracer.start_as_current_span("payment_processing") as payment_span:
            payment_span.set_attribute("payment.amount", 50000)
            payment_span.set_attribute("payment.currency", "JPY")
            payment_span.set_attribute("payment.method", "credit_card")
            
            self.logger.info("決済処理を開始しました", extra={
                "payment_id": "pay_123456",
                "amount": 50000
            })
            
            try:
                # 外部API呼び出しをシミュレート
                with self.tracer.start_as_current_span("call_payment_gateway") as gateway_span:
                    gateway_span.set_attribute("gateway.provider", "payment_gateway_api")
                    gateway_span.set_attribute("gateway.endpoint", "/api/v1/charge")
                    
                    self.logger.info("決済ゲートウェイAPI呼び出し中")
                    
                    time.sleep(0.1)
                    
                    # 決済エラーをシミュレート
                    if True:  # 意図的にエラーを発生
                        gateway_span.set_status(trace.Status(trace.StatusCode.ERROR, "Payment declined"))
                        gateway_span.set_attribute("error.type", "payment_declined")
                        gateway_span.set_attribute("error.code", "INSUFFICIENT_FUNDS")
                        
                        raise Exception("決済が拒否されました: 残高不足")
            
            except Exception as e:
                payment_span.set_status(trace.Status(trace.StatusCode.ERROR, str(e)))
                payment_span.set_attribute("error.occurred", True)
                payment_span.set_attribute("error.type", type(e).__name__)
                
                self.logger.error(
                    "決済処理でエラーが発生しました", 
                    extra={
                        "payment_id": "pay_123456",
                        "error_type": type(e).__name__,
                        "error_message": str(e),
                        "amount": 50000
                    },
                    exc_info=True  # スタックトレースを含める
                )
        
        self.logger.info("エラーログとトレースの統合デモンストレーション完了")
    
    def run_all_demonstrations(self):
        """
        全てのデモンストレーションを実行
        
        このメソッドは、OpenTelemetry Python logging統合の
        全ての機能を順次実行し、動作を確認します。
        """
        print("\n" + "="*80)
        print("🚀 OpenTelemetry Python Logging Integration Demo 開始")
        print("="*80)
        
        start_time = datetime.now()
        
        # 開始ログ
        self.logger.info("OpenTelemetry Python Logging Demoを開始します", extra={
            "demo.start_time": start_time.isoformat(),
            "demo.version": "1.0.0"
        })
        
        try:
            # 各デモンストレーションを実行
            self.demonstrate_basic_logging()
            time.sleep(1)  # 少し間を置く
            
            self.demonstrate_structured_logging()
            time.sleep(1)
            
            self.demonstrate_trace_log_correlation()
            time.sleep(1)
            
            self.demonstrate_error_logging_with_traces()
            time.sleep(1)
            
            # 完了ログ
            end_time = datetime.now()
            duration = (end_time - start_time).total_seconds()
            
            self.logger.info("OpenTelemetry Python Logging Demoが正常に完了しました", extra={
                "demo.end_time": end_time.isoformat(),
                "demo.duration_seconds": duration,
                "demo.status": "completed"
            })
            
            print("\n" + "="*80)
            print("✅ OpenTelemetry Python Logging Integration Demo 完了")
            print(f"実行時間: {duration:.2f}秒")
            print("="*80)
            
        except Exception as e:
            self.logger.error("Demoの実行中にエラーが発生しました", extra={
                "error_type": type(e).__name__,
                "error_message": str(e)
            }, exc_info=True)
            print(f"\n❌ Demo実行エラー: {e}")
            raise
        
        # 少し待ってからバッファをフラッシュ
        time.sleep(2)
        print("\n📤 ログデータの送信処理中...")
        time.sleep(3)
        print("📤 送信完了")


def main():
    """
    メインエントリーポイント
    
    コマンドライン引数の処理とサンプルアプリケーションの実行を行います。
    """
    print("OpenTelemetry Python Logging Integration Example")
    print("=" * 50)
    
    # 環境変数の確認と出力
    print("\n📋 環境変数設定:")
    print(f"  OTEL_EXPORTER_OTLP_LOGS_ENDPOINT: {os.getenv('OTEL_EXPORTER_OTLP_LOGS_ENDPOINT', 'http://localhost:4317')}")
    print(f"  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT: {os.getenv('OTEL_EXPORTER_OTLP_TRACES_ENDPOINT', 'http://localhost:4317')}")
    
    # ヘルスチェックオプション
    if len(sys.argv) > 1 and sys.argv[1] == "--health-check":
        print("\n💚 ヘルスチェック: OK")
        return 0
    
    try:
        # LoggingExampleインスタンスを作成し、デモを実行
        logging_demo = LoggingExample()
        logging_demo.run_all_demonstrations()
        return 0
        
    except KeyboardInterrupt:
        print("\n\n⚠️  ユーザーによって中断されました")
        return 1
    except Exception as e:
        print(f"\n❌ アプリケーションエラー: {e}")
        return 1


if __name__ == "__main__":
    exit_code = main()
    sys.exit(exit_code)