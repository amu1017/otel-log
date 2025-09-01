/*
 * OpenTelemetry + Log4j Appender 統合サンプル
 * 
 * このサンプルは以下を示します：
 * 1. OpenTelemetry SDK の初期化方法
 * 2. Log4j Appender の設定と統合
 * 3. 従来の Log4j ロガーを使用したログ出力
 * 4. トレースコンテキストとログの関連付け
 * 5. 構造化ログ（Map形式）の使用方法
 * 
 * 【重要】このサンプルではLogs APIを直接呼び出さず、
 * Log4j Appenderを通してOpenTelemetryにログデータを送信します。
 */

package com.example.otel;

// 標準Javaライブラリ
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Log4j2 ログ関連
import org.apache.logging.log4j.LogManager;      // ロガーファクトリー
import org.apache.logging.log4j.Logger;          // ログ出力インターフェース
import org.apache.logging.log4j.ThreadContext;   // スレッド固有のコンテキスト情報
import org.apache.logging.log4j.message.MapMessage; // 構造化ログメッセージ

// OpenTelemetry コア機能
import io.opentelemetry.api.OpenTelemetry;           // OpenTelemetryのメインAPI
import io.opentelemetry.api.trace.Span;              // トレースのスパン
import io.opentelemetry.api.trace.Tracer;            // トレーサー（トレース生成）
import io.opentelemetry.context.Scope;               // コンテキストスコープ

// OpenTelemetry SDK 関連
import io.opentelemetry.sdk.OpenTelemetrySdk;        // SDK実装
import io.opentelemetry.sdk.logs.SdkLoggerProvider;  // ログプロバイダー
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor; // バッチ処理プロセッサー
import io.opentelemetry.sdk.resources.Resource;      // リソース（アプリケーション識別）
import io.opentelemetry.sdk.trace.SdkTracerProvider; // トレースプロバイダー
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor; // スパン処理プロセッサー

// OpenTelemetry Exporter （OTLP形式での出力）
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;   // ログ用OTLPエクスポーター
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;       // トレース用OTLPエクスポーター

// セマンティック規約（標準的な属性名定義）
import io.opentelemetry.semconv.ServiceAttributes;   // サービス関連の標準属性

// Log4j Appender 統合
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;

/**
 * OpenTelemetry Log4j Appender 統合のデモンストレーション
 * 
 * このクラスは以下のコンポーネント間の関係を実際に動作させて示します：
 * 
 * [アプリケーションコード] 
 *        ↓ (Log4j Logger経由でログ出力)
 * [Log4j フレームワーク]
 *        ↓ (OpenTelemetry Appenderが変換)
 * [OpenTelemetry SDK]
 *        ↓ (OTLP Exporterで送信)
 * [外部テレメトリーシステム]
 */
public class LoggingExample {
    
    // Log4jのロガーインスタンス
    // このロガーが通常通りログを出力し、同時にOpenTelemetryにもデータを送信
    private static final Logger logger = LogManager.getLogger(LoggingExample.class);
    
    // OpenTelemetryのトレーサー
    // スパン（トレースの単位）を作成するために使用
    private static Tracer tracer;

    public static void main(String[] args) {
        /*
         * 1. OpenTelemetry SDK を初期化
         * 
         * これにより以下が設定されます：
         * - リソース情報（サービス名など）
         * - ログプロバイダー（ログ処理エンジン）
         * - トレースプロバイダー（トレース処理エンジン）
         * - エクスポーター（外部への送信設定）
         */
        OpenTelemetrySdk openTelemetry = initializeOpenTelemetry();
        
        /*
         * 2. Log4j Appender と OpenTelemetry を統合
         * 
         * この設定により、Log4jのログが自動的にOpenTelemetryに転送されます。
         * アプリケーションコードは通常のLog4jの使い方のままで、
         * 追加でOpenTelemetryのテレメトリーも生成されます。
         */
        OpenTelemetryAppender.install(openTelemetry);
        
        // トレーサーを初期化
        tracer = openTelemetry.getTracer("com.example.otel");
        
        /*
         * 3. 実際のアプリケーション処理でログ出力をテスト
         * 
         * 以下の各メソッドで異なるタイプのログ出力を試します：
         * - シンプルなログ
         * - 構造化ログ
         * - トレースコンテキスト付きログ
         * - エラーログ
         */
        logger.info("=== OpenTelemetry Log4j Appender サンプル開始 ===");
        
        // 各種ログ出力のデモンストレーション
        demonstrateBasicLogging();           // 基本的なログ出力
        demonstrateStructuredLogging();      // 構造化ログ（Map形式）
        demonstrateTraceContextLogging();    // トレースコンテキスト付きログ
        demonstrateErrorLogging();           // エラーログ
        
        logger.info("=== サンプル終了 ===");
        
        /*
         * 4. OpenTelemetry SDK のシャットダウン
         * 
         * バッファリングされたデータを確実に送信し、リソースを解放します。
         * 本番環境では JVM シャットダウンフックに登録することが推奨されます。
         */
        shutdownOpenTelemetry(openTelemetry);
    }

    /**
     * OpenTelemetry SDK を初期化する
     * 
     * @return 設定済みの OpenTelemetrySdk インスタンス
     * 
     * 【初期化フロー】
     * 1. リソース情報の定義（サービス名など）
     * 2. ログエクスポーターの作成（OTLP形式）
     * 3. ログプロセッサーの作成（バッチ処理）
     * 4. ログプロバイダーの構築
     * 5. 同様にトレース機能も設定
     * 6. 全体をOpenTelemetrySdkに統合
     */
    private static OpenTelemetrySdk initializeOpenTelemetry() {
        logger.info("OpenTelemetry SDK を初期化中...");
        
        /*
         * リソース定義
         * 
         * リソースは「このテレメトリーデータがどのサービスから来たか」を
         * 識別するための情報です。最低限 service.name は設定が必要です。
         */
        Resource resource = Resource.getDefault()
            .merge(Resource.builder()
                .put(ServiceAttributes.SERVICE_NAME, "otel-log4j-example")      // サービス名
                .put(ServiceAttributes.SERVICE_VERSION, "1.0.0")               // バージョン
                .put(ServiceAttributes.SERVICE_NAMESPACE, "example.com")       // 名前空間
                .build());

        /*
         * ログエクスポーターの設定
         * 
         * OtlpGrpcLogRecordExporter は収集したログレコードを
         * OTLP (OpenTelemetry Protocol) 形式で外部に送信します。
         * 
         * 【注意】実際の環境では setEndpoint() でCollectorのURLを指定します
         */
        OtlpGrpcLogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
            .setEndpoint("http://localhost:4317")  // OTLP Collector のエンドポイント
            .setTimeout(Duration.ofSeconds(5))      // タイムアウト設定
            .build();

        /*
         * ログプロセッサーの設定
         * 
         * BatchLogRecordProcessor はログレコードを効率的にバッチ処理します。
         * 一つずつ送信するのではなく、まとめて送信することでパフォーマンスを向上させます。
         */
        BatchLogRecordProcessor logProcessor = BatchLogRecordProcessor.builder(logExporter)
            .setMaxExportBatchSize(512)                        // 1回の送信での最大レコード数
            .setExportTimeout(Duration.ofSeconds(2))           // 送信タイムアウト
            .setScheduleDelay(Duration.ofMilliseconds(500))    // 送信間隔
            .build();

        /*
         * ログプロバイダーの構築
         * 
         * SdkLoggerProvider はOpenTelemetryのログ機能の中核です。
         * リソース情報とプロセッサーを組み合わせて、ログ処理パイプラインを構築します。
         */
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)          // リソース情報を関連付け
            .addLogRecordProcessor(logProcessor) // ログプロセッサーを追加
            .build();

        /*
         * トレースエクスポーターとプロバイダーの設定
         * 
         * ログと同様に、トレースも設定します。
         * これにより、ログとトレースが関連付けられたテレメトリーが生成されます。
         */
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://localhost:4317")
            .setTimeout(Duration.ofSeconds(5))
            .build();

        BatchSpanProcessor spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setMaxExportBatchSize(512)
            .setExportTimeout(Duration.ofSeconds(2))
            .setScheduleDelay(Duration.ofMilliseconds(500))
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(spanProcessor)
            .build();

        /*
         * 最終的な OpenTelemetrySdk インスタンスの構築
         * 
         * 設定した各プロバイダーを統合して、完全なOpenTelemetryインスタンスを作成します。
         * このインスタンスがアプリケーション全体のテレメトリー機能を提供します。
         */
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setLoggerProvider(loggerProvider)    // ログ機能を統合
            .setTracerProvider(tracerProvider)    // トレース機能を統合
            .buildAndRegisterGlobal();            // グローバルに登録して自動取得可能にする

        logger.info("OpenTelemetry SDK 初期化完了");
        return sdk;
    }

    /**
     * 基本的なログ出力のデモンストレーション
     * 
     * 通常のLog4jの使い方でログを出力します。
     * 内部的には OpenTelemetry Appender が動作して、
     * ログデータがOpenTelemetry形式でも送信されます。
     */
    private static void demonstrateBasicLogging() {
        logger.info("--- 基本的なログ出力のデモ ---");
        
        /*
         * 各レベルでのログ出力
         * 
         * Log4jの標準的なログレベルを使用します。
         * OpenTelemetry Appender は各ログレベルを適切な
         * OpenTelemetry の Severity レベルにマップします。
         */
        logger.trace("TRACE レベルのログ: 詳細なデバッグ情報");
        logger.debug("DEBUG レベルのログ: デバッグ情報"); 
        logger.info("INFO レベルのログ: 一般的な情報");
        logger.warn("WARN レベルのログ: 警告メッセージ");
        logger.error("ERROR レベルのログ: エラーメッセージ");
        
        /*
         * パラメーター付きログメッセージ
         * 
         * Log4jの {} プレースホルダーを使用した効率的なログ出力。
         * 文字列結合よりも高性能で、OpenTelemetryでも適切に処理されます。
         */
        String userName = "田中太郎";
        int userId = 12345;
        logger.info("ユーザーログイン: name={}, id={}", userName, userId);
    }

    /**
     * 構造化ログのデモンストレーション
     * 
     * Log4j の MapMessage を使用して構造化されたログを出力します。
     * このデータは OpenTelemetry で structured attributes として扱われ、
     * より高度な検索やフィルタリングが可能になります。
     */
    private static void demonstrateStructuredLogging() {
        logger.info("--- 構造化ログのデモ ---");
        
        /*
         * ThreadContext の使用
         * 
         * ThreadContext は現在のスレッド固有のコンテキスト情報を保存します。
         * OpenTelemetry Appender は captureContextDataAttributes="*" 設定により
         * これらの情報を自動的にログ属性として取り込みます。
         */
        ThreadContext.put("user_id", "user_12345");
        ThreadContext.put("session_id", "sess_abcd1234");
        ThreadContext.put("request_id", "req_xyz789");
        
        /*
         * MapMessage を使った構造化ログ
         * 
         * MapMessage はキー・バリューペアの形でログデータを構造化できます。
         * OpenTelemetry では各キーが attribute として扱われます。
         */
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "user_action");
        logData.put("action", "file_upload");
        logData.put("file_size_bytes", 1048576);  // 1MB
        logData.put("file_type", "image/jpeg");
        logData.put("processing_time_ms", 245);
        
        // MapMessage としてログ出力
        // captureMapMessageAttributes="true" 設定により、
        // 各キーがOpenTelemetryの属性として自動的に抽出されます
        MapMessage mapMessage = new MapMessage(logData);
        logger.info(mapMessage);
        
        // ThreadContext をクリア（メモリリークを防ぐため）
        ThreadContext.clearAll();
    }

    /**
     * トレースコンテキスト付きログのデモンストレーション
     * 
     * OpenTelemetry のトレース機能と組み合わせることで、
     * ログがどのリクエスト処理の一部なのかを関連付けできます。
     * これにより、分散システムでのデバッグが格段に容易になります。
     */
    private static void demonstrateTraceContextLogging() {
        logger.info("--- トレースコンテキスト付きログのデモ ---");
        
        /*
         * スパンの作成と開始
         * 
         * Span は処理の単位を表します。データベースアクセス、API呼び出し、
         * 業務処理など、個別の処理をスパンとして追跡できます。
         */
        Span span = tracer.spanBuilder("user_registration_process")
            .setSpanKind(io.opentelemetry.api.trace.SpanKind.INTERNAL) // 内部処理
            .setAttribute("user_type", "premium")  // スパンに属性を追加
            .setAttribute("registration_method", "email")
            .startSpan();
        
        /*
         * スパンをアクティブなコンテキストに設定
         * 
         * try-with-resources文を使用してスコープを管理します。
         * このブロック内で出力されるログは、自動的にトレースコンテキスト
         * （trace_id, span_id）が関連付けられます。
         */
        try (Scope scope = span.makeCurrent()) {
            
            // スパン開始のログ
            logger.info("ユーザー登録処理を開始");
            
            // 模擬的な処理ステップをログ出力
            simulateUserValidation();    // ユーザー検証
            simulateEmailVerification(); // メール確認
            simulateDatabaseSave();      // データベース保存
            
            logger.info("ユーザー登録処理が正常に完了");
            
        } catch (Exception e) {
            /*
             * エラー処理
             * 
             * スパンにエラー状態を設定し、ログにも記録します。
             * OpenTelemetry では、ログとスパンの両方でエラー情報が
             * 適切に関連付けられて記録されます。
             */
            span.recordException(e);  // スパンにException情報を記録
            logger.error("ユーザー登録処理でエラーが発生: {}", e.getMessage(), e);
        } finally {
            /*
             * スパンの終了
             * 
             * スパンを明示的に終了することで、処理時間や最終状態が
             * 記録されます。これは必須の処理です。
             */
            span.end();
        }
    }

    /**
     * ユーザー検証処理のシミュレーション
     * 
     * 実際の業務処理を模擬的に実装。
     * この処理内でのログは、親スパンのコンテキスト内で記録されます。
     */
    private static void simulateUserValidation() {
        /*
         * 子スパンの作成
         * 
         * 大きな処理を細分化して追跡したい場合、子スパンを作成します。
         * 親スパンとの関係性は自動的に管理されます。
         */
        Span validationSpan = tracer.spanBuilder("user_validation")
            .setSpanKind(io.opentelemetry.api.trace.SpanKind.INTERNAL)
            .startSpan();
            
        try (Scope scope = validationSpan.makeCurrent()) {
            
            logger.debug("ユーザー検証を開始");
            
            // 処理時間をシミュレート
            try {
                Thread.sleep(100); // 100msの処理時間
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 検証結果のログ（構造化）
            Map<String, Object> validationResult = new HashMap<>();
            validationResult.put("validation_type", "email_format");
            validationResult.put("validation_status", "passed");
            validationResult.put("validation_duration_ms", 98);
            
            logger.info(new MapMessage(validationResult));
            
            /*
             * スパンに追加情報を設定
             * 
             * 処理結果や重要な値をスパンの属性として記録できます。
             * これにより、トレースビューアーで詳細な情報が確認できます。
             */
            validationSpan.setAttribute("validation.result", "success");
            validationSpan.setAttribute("validation.email_domain", "example.com");
            
        } finally {
            validationSpan.end();
        }
    }

    /**
     * メール確認処理のシミュレーション
     */
    private static void simulateEmailVerification() {
        Span emailSpan = tracer.spanBuilder("email_verification")
            .setSpanKind(io.opentelemetry.api.trace.SpanKind.CLIENT) // 外部サービス呼び出し
            .startSpan();
            
        try (Scope scope = emailSpan.makeCurrent()) {
            
            logger.info("メール確認処理を開始");
            
            // 外部API呼び出しをシミュレート
            try {
                Thread.sleep(300); // 300msの外部API処理時間
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // API呼び出し結果のログ
            logger.info("メール送信API呼び出し完了: response_code={}, latency_ms={}", 200, 287);
            
            emailSpan.setAttribute("email.provider", "sendgrid");
            emailSpan.setAttribute("email.template_id", "user_verification_v2");
            emailSpan.setAttribute("http.status_code", 200);
            
        } finally {
            emailSpan.end();
        }
    }

    /**
     * データベース保存処理のシミュレーション
     */
    private static void simulateDatabaseSave() {
        Span dbSpan = tracer.spanBuilder("database_save")
            .setSpanKind(io.opentelemetry.api.trace.SpanKind.CLIENT) // DB呼び出し
            .startSpan();
            
        try (Scope scope = dbSpan.makeCurrent()) {
            
            logger.info("データベース保存処理を開始");
            
            // データベース処理をシミュレート  
            try {
                Thread.sleep(150); // 150msのDB処理時間
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // データベース操作のログ
            Map<String, Object> dbOperation = new HashMap<>();
            dbOperation.put("db_operation", "INSERT");
            dbOperation.put("table_name", "users");
            dbOperation.put("affected_rows", 1);
            dbOperation.put("execution_time_ms", 142);
            
            logger.info(new MapMessage(dbOperation));
            
            /*
             * データベース関連の標準属性
             * 
             * OpenTelemetry には、データベース操作用の標準的な属性名が
             * 定義されています。これに従うことで、統一された形式で
             * テレメトリーデータを記録できます。
             */
            dbSpan.setAttribute("db.system", "postgresql");
            dbSpan.setAttribute("db.name", "user_management");
            dbSpan.setAttribute("db.operation", "INSERT");
            dbSpan.setAttribute("db.sql.table", "users");
            
        } finally {
            dbSpan.end();
        }
    }

    /**
     * エラーログのデモンストレーション
     * 
     * 例外やエラー状況でのログ出力方法を示します。
     * OpenTelemetry では、エラーログとスパンのエラー状態が
     * 適切に関連付けられます。
     */
    private static void demonstrateErrorLogging() {
        logger.info("--- エラーログのデモ ---");
        
        Span errorSpan = tracer.spanBuilder("error_simulation")
            .startSpan();
            
        try (Scope scope = errorSpan.makeCurrent()) {
            
            logger.info("エラーシナリオのテストを開始");
            
            try {
                // 意図的に例外を発生させる
                throw new RuntimeException("模擬的なビジネスロジックエラー: データ整合性チェック失敗");
                
            } catch (RuntimeException e) {
                /*
                 * エラーログの出力
                 * 
                 * Log4j の error() メソッドで例外を記録します。
                 * OpenTelemetry Appender は例外情報も適切に
                 * テレメトリーデータに変換します。
                 */
                logger.error("業務処理でエラーが発生しました", e);
                
                /*
                 * スパンにもエラー情報を記録
                 * 
                 * recordException() により、スパンにも例外情報が記録され、
                 * トレースとログの両方でエラー情報が関連付けられます。
                 */
                errorSpan.recordException(e);
                errorSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, 
                    "データ整合性チェック失敗");
                
                /*
                 * エラー詳細の構造化ログ
                 * 
                 * エラーの詳細情報を構造化された形式で記録します。
                 * これにより、ログ分析システムでの検索・集計が容易になります。
                 */
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("error_type", "business_logic_error");
                errorDetails.put("error_code", "DATA_CONSISTENCY_FAILURE");
                errorDetails.put("error_severity", "high");
                errorDetails.put("affected_user_count", 1);
                errorDetails.put("retry_possible", false);
                
                logger.error(new MapMessage(errorDetails));
            }
            
        } finally {
            errorSpan.end();
        }
    }

    /**
     * OpenTelemetry SDK のシャットダウン処理
     * 
     * @param openTelemetry シャットダウン対象のSDKインスタンス
     * 
     * 適切なシャットダウンを行うことで：
     * 1. バッファリングされたデータが確実に送信される
     * 2. リソース（スレッド、ネットワーク接続など）が適切に解放される
     * 3. データの欠損を防ぐ
     */
    private static void shutdownOpenTelemetry(OpenTelemetrySdk openTelemetry) {
        logger.info("OpenTelemetry SDK をシャットダウン中...");
        
        try {
            /*
             * graceful shutdown
             * 
             * close() メソッドは内部的に以下を実行します：
             * 1. 新しいデータの受け入れ停止
             * 2. バッファリング中のデータを全て送信
             * 3. プロセッサーとエクスポーターのクリーンアップ
             * 4. スレッドプールのシャットダウン
             */
            openTelemetry.close();
            logger.info("OpenTelemetry SDK のシャットダウン完了");
            
        } catch (Exception e) {
            logger.error("OpenTelemetry SDK シャットダウン中にエラーが発生", e);
        }
    }
}