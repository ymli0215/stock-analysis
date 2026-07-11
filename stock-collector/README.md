# stock-collector

n8n flow 遷移後的 Python worker：輪詢 MySQL 佇列、claude CLI 分析、寫入 Obsidian 筆記、Telegram 通知。
架構原則：`main.py` 只做入口（FastAPI 路由 + 排程註冊），業務邏輯在 `jobs/`，共用能力在 `services/`。

## 已遷移的 n8n flow

| job | 原 n8n flow | 排程 | env 開關 |
|-----|------------|------|---------|
| `stock_collect` | 股票資訊收集分析 - SCHEDULE（vzqX5VmmeDkoovX6） | 每 120 秒 | `STOCK_COLLECT_ENABLED` |
| `sync_stock_data` | Sync Stock Data（208JRIcBB1BiXZj9） | 週六 06:00 | `SYNC_STOCK_DATA_ENABLED` |
| `import_warrant` | 每日更新上市櫃權證資料（pC2cOkmx1c4389IN） | 週一至五 07:00 | `IMPORT_WARRANT_ENABLED` |

完整的 flow 盤點與遷移計畫見 `/mnt/d/Environments/ubuntu-n8n/N8N_FLOWS_INVENTORY.md`。

## 資料流（stock_collect）

```
Telegram bot A → StockServer /webhook/stockinfo → MySQL stocks_raw_data (status=0)
    ↓ 使用者傳 "done" 結批
worker 每 120s 輪詢 → 鎖定(status=1) → 合併文字/展開URL(r.jina.ai) → 下載附件
    → claude CLI 分析（圖片給本地路徑） → 筆記寫 /obsidian/00_Inbox、附件寫 /obsidian/Assets
    → Telegram 通知 → 標記完成(status=2)
```

status 語義：`0` 待處理 / `1` 處理中（失敗會停在這裡並發 TG 告警，人工改回 0 重跑）/ `2` 完成。

## 部署

由 `stock-analysis/docker-compose.yml` 的 `stock-collector` service 啟動（claude-agent-base image）。
設定放 `.env`（不進版控，範本見 `.env.example`）。claude CLI 認證走 `ANTHROPIC_AUTH_TOKEN` + `ANTHROPIC_BASE_URL` 環境變數。

```bash
# 健康檢查
curl http://localhost:8085/healthz
# 查看排程與最近執行結果
curl http://localhost:8085/jobs
# 手動觸發（測試/補跑）
curl -X POST "http://localhost:8085/jobs/stock_collect/run?wait=true"
```

## 注意事項

- **切換原則**：env 開關預設全關。停用對應 n8n flow 後才打開，避免雙跑（尤其 `sync_stock_data` 會觸發 StockServer 爬蟲，雙跑會重複爬取）。
- `stocks_raw_data` 的 `telegram_file_id` 綁定 bot A（`/webhook/stockinfo` 那隻），下載附件必須用同一隻 bot 的 token。
- `sync_stock_data` / `import_warrant` 會觸發爬蟲，勿手動高頻觸發、勿當 healthcheck。
