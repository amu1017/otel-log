# OpenTelemetry Java Log4j Appender サンプルアプリケーション用 Dockerfile
#
# このDockerfileは、OpenTelemetry + Log4j統合のサンプルアプリケーションを
# Podman環境で実行するためのコンテナイメージを構築します。
#
# 【構築内容】
# 1. OpenJDK 17ベースのイメージ
# 2. Mavenを使ったアプリケーションビルド
# 3. 必要な環境変数の設定
# 4. ループバックアドレスでの外部OpenTelemetry Collector接続設定

# ベースイメージ: OpenJDK 17 + Maven
# Alpine Linuxベースで軽量、セキュリティ更新が頻繁
FROM maven:3.9.5-openjdk-17-slim AS build

# 作業ディレクトリの設定
WORKDIR /app

# Maven設定ファイルを先にコピー（依存関係キャッシュの最適化）
# pom.xmlが変更されない限り、依存関係のダウンロードをスキップできる
COPY pom.xml .

# 依存関係の事前ダウンロード
# この段階で依存関係をキャッシュすることで、ソースコード変更時の再ビルドが高速化
RUN mvn dependency:go-offline -B

# ソースコードをコピー
COPY src ./src

# アプリケーションのビルド
# -B: バッチモード（インタラクティブな入力を無効）
# -T 1C: 1CPU当たり1スレッドでビルド並列化
RUN mvn package -B -T 1C

# ランタイム用の軽量イメージ（マルチステージビルド）
FROM openjdk:17-jre-slim AS runtime

# セキュリティのため非rootユーザーでの実行
# アプリケーション専用ユーザー 'oteluser' を作成
RUN groupadd -r oteluser && useradd -r -g oteluser oteluser

# 作業ディレクトリの作成と権限設定
WORKDIR /app
RUN chown oteluser:oteluser /app

# ビルド成果物をランタイムイメージにコピー
COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/dependency ./dependency

# 必要なライブラリJARファイルを /app/lib にコピー
# Maven dependency:copy-dependencies で取得したJARファイルを利用
RUN mkdir -p /app/lib
COPY --from=build /root/.m2/repository /tmp/maven-repo

# 必要な依存関係JARファイルを抽出してlibディレクトリに配置
RUN find /tmp/maven-repo -name "*.jar" -path "*/io/opentelemetry/*" -exec cp {} /app/lib/ \; && \
    find /tmp/maven-repo -name "*.jar" -path "*/org/apache/logging/log4j/*" -exec cp {} /app/lib/ \; && \
    rm -rf /tmp/maven-repo

# 環境変数の設定
ENV JAVA_OPTS="-server -Xms256m -Xmx512m"
ENV OTEL_LOGS_EXPORTER=otlp
ENV OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=http://host.containers.internal:4317
ENV OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://host.containers.internal:4317
ENV OTEL_RESOURCE_ATTRIBUTES=service.name=otel-log4j-example,service.version=1.0.0

# JVMの文字エンコーディングをUTF-8に設定（文字化け防止）
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo"

# ファイル権限を oteluser に変更
RUN chown -R oteluser:oteluser /app

# 非rootユーザーに切り替え
USER oteluser

# ヘルスチェック用のエンドポイント
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD java -cp "/app/classes:/app/lib/*" com.example.otel.LoggingExample --health-check || exit 1

# アプリケーション実行のエントリーポイント
ENTRYPOINT ["java", "-cp", "/app/classes:/app/lib/*", "com.example.otel.LoggingExample"]

# デフォルトの実行コマンド（引数なしでメイン処理実行）
CMD []

# コンテナのメタデータ
LABEL maintainer="OpenTelemetry Log4j Example"
LABEL version="1.0.0"
LABEL description="OpenTelemetry Java + Log4j Appender integration example for Podman"

# 使用ポート（アプリケーションがサーバー機能を持つ場合に使用）
# このサンプルはクライアントアプリケーションなのでEXPOSE は不要ですが、
# 将来的な拡張を考慮して記載
# EXPOSE 8080

# ボリュームマウントポイント（ログファイル出力用）
# 外部にログファイルを出力したい場合に使用
VOLUME ["/app/logs"]