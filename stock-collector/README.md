# stock-collector

n8n flow 遷移後的 Python worker：輪詢 MySQL 佇列、claude CLI 分析、寫入 Obsidian 筆記、Telegram 通知。
架構原則：`main.py` 只做入口（FastAPI 路由 + 排程註冊），業務邏輯在 `jobs/`，共用能力在 `services/`。

## 已遷移的 n8n flow

| job | 原 n8n flow | 排程 | env 開關 |
|-----|------------|------|---------|
| `stock_collect` | 股票資訊收集分析 - SCHEDULE（vzqX5VmmeDkoovX6） | 每 120 秒 | `STOCK_COLLECT_ENABLED` |
| `sync_stock_data` | Sync Stock Data（208JRIcBB1BiXZj9） | 週六 06:00 | `SYNC_STOCK_DATA_ENABLED` |
| `import_warrant` | 每日更新上市櫃權證資料（pC2cOkmx1c4389IN） | 週一至五 07:00 | `IMPORT_WARRANT_ENABLED` |
| `vocus_collect` | Get Vocus-摩股雙週報（a0nASk4JksapafXd）＋ 邏輯投資（TQYmb4Ahut7FGAY1） | 每日 06:00、18:00 | `VOCUS_COLLECT_ENABLED` |

### vocus_collect 資料流（選項 A：爬蟲層正規化）

```
帳密登入 api.vocus.cc → 取 token → 逐沙龍抓文章清單（VOCUS_SALONS 設定驅動）
  → vocus_sync_log 去重 → 逐篇：
    取詳情（吸收 post/article 兩種型別差異）→ 下載圖片到 /obsidian/Assets（vocus 原生 UUID 檔名）
    → 組正規化 markdown（正文含 ![[Assets/..]] + 問答對話）
    → claude CLI 分析（知識索引與分析專家，callout 格式）
    → 最終筆記寫 /obsidian/01_Sources → Telegram 通知 → 寫入 vocus_sync_log
```

**兩種型別差異（已在 services/vocus.py 吸收）**：`post` 走 `/api/posts/{id}`（欄位平鋪、內文無圖、圖片在獨立陣列）；`article` 走 `/api/article/{id}`（資料包在 article 物件、內文 HTML 圖片內嵌）。留言端點用單數型別 `/api/comments/{post|article}/{id}`（注意與詳情端點的 posts 複數不同），回覆作者在父留言 `replyUser` 以 userId 對應。

**選項 A 的意義**：圖片在爬蟲階段用 token 下載完（付費內容下游拿不到 token），輸出的是已正規化 markdown。investanchors 之後 Python 化時套同一種輸出，下游 AI 分析就能對所有來源共用一套處理，不需為每個網站寫特例（定錨 n8n flow 的圖片 regex 寫死 `/uploads/image/file/`，vocus 資料丟進去會抓不到圖，正是要避免的反例）。

新增沙龍：改 `VOCUS_SALONS`（格式 `salonId:名稱,salonId:名稱`）即可，程式不動。首次全量大（邏輯投資 1050 篇），用 `VOCUS_MAX_PER_RUN` 分批消化。

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

由 `stock-analysis/docker-compose.yml` 的 `stock-collector` service 啟動，port `8085`（不影響既有的 8050/8090）。
image `stock-collector:1.0` 以專案現有的 `stock-analysis:1.2` 為基底、只補 Node.js + claude CLI，
由 Dockerfile **初次建立一次**；日常維運一律透過 docker compose（程式碼 volume 掛載、
依賴變更由啟動時 pip install 處理），不重建 image、不依賴任何 stock 專案以外的資源。
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
