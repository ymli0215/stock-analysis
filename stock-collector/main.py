"""stock-collector：n8n flow 遷移後的 Python worker 入口。

只負責三件事：FastAPI 路由、排程註冊、呼叫 jobs——業務邏輯都在 jobs/ 與 services/。
新增 job 時：jobs/ 加模組 → JOBS 註冊 → _register_schedules 加排程（用 env flag 控制開關）。
"""

import logging
import threading
from datetime import datetime
from logging.handlers import RotatingFileHandler

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger
from fastapi import BackgroundTasks, FastAPI, HTTPException

import config
from jobs import stock_collect, stockserver_maintenance

config.LOG_DIR.mkdir(parents=True, exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    handlers=[
        logging.StreamHandler(),
        RotatingFileHandler(config.LOG_DIR / "worker.log", maxBytes=10 * 1024 * 1024, backupCount=3),
    ],
)
logger = logging.getLogger("worker")

JOBS = {
    "stock_collect": stock_collect.run,
    "sync_stock_data": stockserver_maintenance.sync_stock_data,
    "import_warrant": stockserver_maintenance.import_warrant,
}

last_results: dict[str, dict] = {}
_locks: dict[str, threading.Lock] = {name: threading.Lock() for name in JOBS}

app = FastAPI(title="stock-collector")
scheduler = BackgroundScheduler(timezone="Asia/Taipei")


def run_job(name: str) -> dict:
    fn = JOBS[name]
    lock = _locks[name]
    if not lock.acquire(blocking=False):
        logger.warning("job %s 仍在執行中，跳過本次觸發", name)
        return {"status": "skipped", "message": "前一次執行尚未結束"}
    started = datetime.now()
    try:
        result = fn()
        record = {"started": started.isoformat(), "status": "ok", "result": result}
    except Exception as e:
        record = {"started": started.isoformat(), "status": "error", "error": str(e)}
    finally:
        lock.release()
    record["finished"] = datetime.now().isoformat()
    last_results[name] = record
    return record


def _register_schedules() -> None:
    if config.STOCK_COLLECT_ENABLED:
        scheduler.add_job(
            run_job,
            IntervalTrigger(seconds=config.STOCK_COLLECT_INTERVAL_SECONDS),
            args=["stock_collect"],
            id="stock_collect",
            max_instances=1,
            coalesce=True,
        )
        logger.info("已排程 stock_collect：每 %s 秒", config.STOCK_COLLECT_INTERVAL_SECONDS)
    if config.SYNC_STOCK_DATA_ENABLED:
        scheduler.add_job(
            run_job,
            CronTrigger(day_of_week="sat", hour=6, minute=0),
            args=["sync_stock_data"],
            id="sync_stock_data",
            max_instances=1,
        )
        logger.info("已排程 sync_stock_data：週六 06:00")
    if config.IMPORT_WARRANT_ENABLED:
        scheduler.add_job(
            run_job,
            CronTrigger(day_of_week="mon-fri", hour=7, minute=0),
            args=["import_warrant"],
            id="import_warrant",
            max_instances=1,
        )
        logger.info("已排程 import_warrant：週一至五 07:00")


@app.on_event("startup")
def startup() -> None:
    _register_schedules()
    scheduler.start()
    logger.info(
        "stock-collector 已啟動，排程狀態：stock_collect=%s sync=%s warrant=%s",
        config.STOCK_COLLECT_ENABLED,
        config.SYNC_STOCK_DATA_ENABLED,
        config.IMPORT_WARRANT_ENABLED,
    )


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "healthy", "scheduled": [j.id for j in scheduler.get_jobs()]}


@app.get("/jobs")
def list_jobs() -> dict:
    return {
        "registered": list(JOBS.keys()),
        "scheduled": {j.id: str(j.next_run_time) for j in scheduler.get_jobs()},
        "last_results": last_results,
    }


@app.post("/jobs/{name}/run")
def trigger_job(name: str, background_tasks: BackgroundTasks, wait: bool = False):
    """手動觸發 job（測試/補跑用）。wait=true 同步等待結果。"""
    if name not in JOBS:
        raise HTTPException(status_code=404, detail=f"未知的 job：{name}")
    if wait:
        return run_job(name)
    background_tasks.add_task(run_job, name)
    return {"status": "accepted", "job": name}
