# 以專案現有的 stock-analysis:1.2 為基底（Python 3.11 + fastapi/uvicorn/APScheduler/
# PyMySQL/requests/dotenv 皆已內建），只補上 claude CLI 所需的 Node.js 與缺少的套件。
FROM stock-analysis:1.2

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Taipei

# Node.js 20（claude CLI 執行環境）
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

RUN npm install -g @anthropic-ai/claude-code \
    && claude --version

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

CMD ["python3", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8085"]
