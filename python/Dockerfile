# OpenTelemetry Python ログ統合サンプルアプリケーション用 Dockerfile
#
# このDockerfileは、OpenTelemetry + Python logging統合のサンプルアプリケーションを
# Podman環境で実行するためのコンテナイメージを構築します。
#
# 【構築内容】
# 1. Python 3.11ベースのイメージ
# 2. pipを使ったアプリケーション依存関係インストール
# 3. 必要な環境変数の設定
# 4. ループバックアドレスでの外部OpenTelemetry Collector接続設定

# ベースイメージ: Python 3.11 slim
# Debian slimベースで軽量、セキュリティ更新が頻繁
FROM python:3.11-slim AS base

# システム更新とセキュリティパッチ適用
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y \
        --no-install-recommends \
        ca-certificates \
        curl && \
    rm -rf /var/lib/apt/lists/*

# ビルド用の一時的なステージ
FROM base AS build

# ビルドツールをインストール
RUN apt-get update && \
    apt-get install -y \
        --no-install-recommends \
        gcc \
        g++ \
        python3-dev && \
    rm -rf /var/lib/apt/lists/*

# 作業ディレクトリの設定
WORKDIR /app

# Python依存関係ファイルを先にコピー（キャッシュ最適化）
COPY requirements.txt pyproject.toml ./

# 依存関係のインストール
# --no-cache-dir: キャッシュを残さない（イメージサイズ削減）
# --user: ユーザー領域にインストール
RUN pip install --no-cache-dir --user -r requirements.txt

# ランタイム用の軽量イメージ
FROM base AS runtime

# セキュリティのため非rootユーザーでの実行
# アプリケーション専用ユーザー 'oteluser' を作成
RUN groupadd -r oteluser && \
    useradd -r -g oteluser -m -d /home/oteluser oteluser

# 作業ディレクトリの作成と権限設定
WORKDIR /app
RUN chown oteluser:oteluser /app

# ビルドステージからPython依存関係をコピー
COPY --from=build /root/.local /home/oteluser/.local

# ソースコードをコピー
COPY src/ ./src/
COPY logging.conf logging_config.yaml ./

# 環境変数の設定
ENV PYTHONPATH="/app/src"
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV PATH="/home/oteluser/.local/bin:$PATH"

# OpenTelemetry関連の環境変数
ENV OTEL_LOGS_EXPORTER=otlp
ENV OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=http://host.containers.internal:4317
ENV OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://host.containers.internal:4317
ENV OTEL_RESOURCE_ATTRIBUTES=service.name=otel-python-logging-example,service.version=1.0.0

# Python実行環境の最適化
ENV PYTHONOPTIMIZE=1

# ログディレクトリの作成
RUN mkdir -p /app/logs && \
    chown -R oteluser:oteluser /app

# 非rootユーザーに切り替え
USER oteluser

# ヘルスチェック用のエンドポイント
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD python -m example_otel.logging_example --health-check || exit 1

# アプリケーション実行のエントリーポイント
ENTRYPOINT ["python", "-m", "example_otel.logging_example"]

# デフォルトの実行コマンド（引数なしでメイン処理実行）
CMD []

# コンテナのメタデータ
LABEL maintainer="OpenTelemetry Python Logging Example"
LABEL version="1.0.0"
LABEL description="OpenTelemetry Python + logging integration example for Podman"
LABEL org.opencontainers.image.title="OpenTelemetry Python Logging Example"
LABEL org.opencontainers.image.description="Sample application demonstrating OpenTelemetry logging integration with Python"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.authors="OpenTelemetry Python Example Team"
LABEL org.opencontainers.image.licenses="MIT"

# 使用ポート（アプリケーションがサーバー機能を持つ場合に使用）
# このサンプルはクライアントアプリケーションなのでEXPOSE は不要ですが、
# 将来的な拡張を考慮して記載
# EXPOSE 8080

# ボリュームマウントポイント（ログファイル出力用）
# 外部にログファイルを出力したい場合に使用
VOLUME ["/app/logs"]

# セキュリティ設定
# non-rootユーザーでの実行を保証
USER oteluser