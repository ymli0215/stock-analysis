# ⚠️ 本 Dockerfile 僅供「初次建立 image」使用（docker build -t stock-collector:1.0 .），
# 之後不再使用：日常維運一律透過 docker-compose.yml（程式碼 volume 掛載、
# 依賴變更由容器啟動時 pip install -r requirements.txt 處理），不重建 image。
#
# 基底為專案現有的 stock-analysis:1.2（Python 3.11 + fastapi/uvicorn/APScheduler/
# PyMySQL/requests/dotenv 皆已內建），只補上 claude CLI 所需的 Node.js。
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
