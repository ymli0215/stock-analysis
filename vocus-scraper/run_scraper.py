"""
vocus.cc 文章批次抓取 → API 儲存

自 investanchors-scraper 複製的骨架，登入/列表/解析等站點細節
待使用者提供 vocus 的做法後調整（TODO 標記處）。
"""
import os
import re
import sys
import time
import random
from dataclasses import dataclass, field

import requests
from bs4 import BeautifulSoup
from dotenv import load_dotenv

load_dotenv()

EMAIL    = os.getenv("VOCUS_EMAIL", "")
PASSWORD = os.getenv("VOCUS_PASSWORD", "")

BASE_URL  = "https://vocus.cc"  # TODO: 待確認
LOGIN_PAGE = f"{BASE_URL}/"      # TODO: vocus 登入頁（登入方式待確認，可能非 Rails token 模式）
LOGIN_POST = f"{BASE_URL}/"      # TODO: 登入 POST endpoint
LIST_URL   = f"{BASE_URL}/"      # TODO: 文章列表頁
API_URL    = "https://stock.bignoodle.net/StockServer/stockPageRaw/save"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/125.0.0.0 Safari/537.36"
    ),
    "Accept": (
        "text/html,application/xhtml+xml,application/xml;"
        "q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
    ),
    "Accept-Language": "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7",
    # Accept-Encoding は requests に任せる（明示すると自動展開が無効になる）
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
}


@dataclass
class ArticleResult:
    article_id: str
    title: str
    url: str
    success: bool = False
    api_id: int | None = None
    failure_reason: str = ""


# ──────────────────────────────────────────────
# Step 1：登入
# ──────────────────────────────────────────────
def login(session: requests.Session) -> bool:
    print("\n── Step 1：登入 ──")
    resp = session.get(LOGIN_PAGE, timeout=30)
    print(f"  GET {LOGIN_PAGE} → {resp.status_code}")

    soup = BeautifulSoup(resp.text, "html.parser")
    token_input = soup.find("input", {"name": "authenticity_token"})
    if not token_input:
        print("  [ERROR] 找不到 authenticity_token，登入頁結構可能已改變")
        print(f"  回應前 300 字：{resp.text[:300]}")
        return False

    token = token_input.get("value", "")
    print(f"  authenticity_token = {token[:20]}…")

    payload = {
        "authenticity_token": token,
        "user[email]": EMAIL,
        "user[password]": PASSWORD,
    }
    resp = session.post(
        LOGIN_POST,
        data=payload,
        headers={"Referer": LOGIN_PAGE},
        timeout=30,
        allow_redirects=True,
    )
    print(f"  POST {LOGIN_POST} → {resp.status_code}（最終 URL：{resp.url}）")

    if any(kw in resp.url for kw in ["register", "sign_in", "login"]):
        print("  [FAIL] 登入後被導回登入相關頁面，帳號/密碼可能有誤")
        return False

    page_text = resp.text
    if "登出" in page_text or "sign_out" in page_text or "Log out" in page_text:
        print("  [OK] 登入成功（偵測到登出關鍵字）")
        return True

    soup_after = BeautifulSoup(page_text, "html.parser")
    logout_link = soup_after.find("a", href=re.compile(r"sign_out|logout|session", re.I))
    if logout_link:
        print(f"  [OK] 登入成功（找到登出連結 href={logout_link['href']}）")
        return True

    print("  [WARN] 無法明確判斷登入狀態，繼續執行")
    return True


# ──────────────────────────────────────────────
# Step 2：抓列表頁，解析未讀文章
# ──────────────────────────────────────────────
def get_unread_articles(session: requests.Session) -> list[dict]:
    print(f"\n── Step 2：抓列表頁 ──")
    resp = session.get(LIST_URL, headers={"Referer": BASE_URL}, timeout=30)
    print(f"  GET {LIST_URL} → {resp.status_code}")

    if resp.status_code != 200:
        print(f"  [ERROR] 非 200 回應")
        return []

    soup = BeautifulSoup(resp.text, "html.parser")
    active_rows = soup.find_all("tr", class_="active")
    print(f"  找到 {len(active_rows)} 個 <tr class=\"active\">")

    articles = []
    for tr in active_rows:
        link = tr.find("a", class_="td-a") or tr.find("a", href=re.compile(r"vip_contents/\d+"))
        if not link:
            continue
        href = link.get("href", "")
        title = link.get_text(strip=True)
        full_url = href if href.startswith("http") else f"{BASE_URL}{href}"

        match = re.search(r"/(\d+)(?:[/?]|$)", full_url)
        article_id = match.group(1) if match else "unknown"

        articles.append({"id": article_id, "title": title, "url": full_url})

    if not articles:
        print("  [WARN] 未在 <tr class=\"active\"> 中找到任何文章連結")
        if active_rows:
            print("  前 2 筆 tr HTML 供診斷：")
            for i, tr in enumerate(active_rows[:2]):
                print(f"  --- tr[{i}] ---\n{tr.prettify()[:400]}")
    return articles


# ──────────────────────────────────────────────
# Step 3a/3b：抓單篇文章並驗證格式
# ──────────────────────────────────────────────
def fetch_article_html(session: requests.Session, article: dict) -> str | None:
    url = article["url"]
    resp = session.get(url, headers={"Referer": LIST_URL}, timeout=30)

    if resp.status_code != 200:
        print(f"    HTTP {resp.status_code}，跳過")
        return None

    content_start = resp.text.lstrip()[:20]
    if not content_start.lower().startswith("<!doctype html"):
        print(f"    [WARN] 回應不是完整 HTML（開頭：{repr(content_start[:60])}），跳過")
        print(f"    實際送出的 Request Headers：")
        for k, v in resp.request.headers.items():
            print(f"      {k}: {v}")
        return None

    size_kb = len(resp.content) / 1024
    print(f"    HTML 驗證 OK（{size_kb:.1f} KB）")
    return resp.text


# ──────────────────────────────────────────────
# Step 3c：呼叫 API 儲存
# ──────────────────────────────────────────────
def save_to_api(article: dict, html: str) -> tuple[bool, int | None, str]:
    payload = {
        "url": article["url"],
        "htmlContent": html,
        "source": "vocus",  # TODO: 確認 source 名稱（下游依此過濾）
    }
    try:
        resp = requests.post(API_URL, json=payload, timeout=30)
    except requests.exceptions.RequestException as e:
        return False, None, f"連線失敗：{e}"

    if resp.status_code != 200:
        return False, None, f"HTTP {resp.status_code}：{resp.text[:200]}"

    try:
        body = resp.json()
    except Exception:
        return False, None, f"回應非 JSON：{resp.text[:200]}"

    if body.get("status") != "ok":
        return False, None, f"status 非 ok：{body}"

    return True, body.get("id"), ""


# ──────────────────────────────────────────────
# 主流程
# ──────────────────────────────────────────────
def main():
    print("=" * 60)
    print("  vocus 文章批次抓取 → API 儲存")
    print("=" * 60)

    if not EMAIL:
        print("[ERROR] .env 中 VOCUS_EMAIL 未設定")
        sys.exit(1)
    if not PASSWORD:
        print("[ERROR] .env 中 VOCUS_PASSWORD 未設定")
        sys.exit(1)
    print(f"[OK] 帳號：{EMAIL}")

    session = requests.Session()
    session.headers.update(HEADERS)

    # Step 1：登入
    if not login(session):
        print("\n[ABORT] 登入失敗，終止執行")
        sys.exit(1)

    # Step 2：取得未讀清單
    articles = get_unread_articles(session)
    if not articles:
        print("\n[INFO] 沒有未讀文章，結束。")
        sys.exit(0)

    print(f"\n── 發現 {len(articles)} 篇未讀文章 ──")
    for i, a in enumerate(articles, 1):
        print(f"  {i:>3}. [{a['id']}] {a['title']}")

    # Step 3：逐篇處理
    print(f"\n── Step 3：逐篇抓取並儲存 ──")
    results: list[ArticleResult] = []

    for i, a in enumerate(articles, 1):
        print(f"\n  [{i}/{len(articles)}] {a['title']}")
        print(f"    URL：{a['url']}")
        result = ArticleResult(article_id=a["id"], title=a["title"], url=a["url"])

        # 3a/3b：抓取並驗證
        html = fetch_article_html(session, a)
        if html is None:
            result.failure_reason = "HTML 格式錯誤或請求失敗"
            results.append(result)
            continue

        # 3c：呼叫 API
        ok, api_id, err = save_to_api(a, html)
        if ok:
            result.success = True
            result.api_id = api_id
            print(f"    API 儲存 OK → id={api_id}")
        else:
            result.failure_reason = err
            print(f"    API 儲存失敗：{err}")

        results.append(result)

        # 3e：避免請求過密
        if i < len(articles):
            print(f"    等待 10s…")
            time.sleep(10)

    # Step 4：總結
    success = [r for r in results if r.success]
    failed  = [r for r in results if not r.success]

    print("\n" + "=" * 60)
    print("  執行總結")
    print("=" * 60)
    print(f"  處理文章：{len(results)} 篇")
    print(f"  成功：{len(success)} 篇")
    print(f"  失敗：{len(failed)} 篇")

    if success:
        print("\n  ✔ 成功清單：")
        for r in success:
            print(f"    [{r.article_id}] {r.title}  → API id={r.api_id}")

    if failed:
        print("\n  ✘ 失敗清單：")
        for r in failed:
            print(f"    [{r.article_id}] {r.title}")
            print(f"      原因：{r.failure_reason}")

    print("=" * 60)


if __name__ == "__main__":
    main()
