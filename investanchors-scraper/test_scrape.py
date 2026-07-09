import os
import re
import sys
from pathlib import Path

import requests
from bs4 import BeautifulSoup
from dotenv import load_dotenv

load_dotenv()

EMAIL = os.getenv("INVESTANCHORS_EMAIL", "")
PASSWORD = os.getenv("INVESTANCHORS_PASSWORD", "")

BASE_URL = "https://investanchors.com"
LOGIN_PAGE = f"{BASE_URL}/user/register/new"
LOGIN_POST = f"{BASE_URL}/user/session"
LIST_URL = f"{BASE_URL}/user/vip_contents/investanchors_index"
OUTPUT_DIR = Path("./output")

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/125.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "zh-TW,zh;q=0.9,en-US;q=0.8",
}


# ──────────────────────────────────────────────
# 前置檢查
# ──────────────────────────────────────────────
def preflight_check():
    if not EMAIL:
        print("[ERROR] .env 中 INVESTANCHORS_EMAIL 為空，請先填入。")
        sys.exit(1)
    if not PASSWORD:
        print("[ERROR] .env 中 INVESTANCHORS_PASSWORD 為空，請先填入密碼再執行。")
        sys.exit(1)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"[OK] 帳號：{EMAIL}")
    print(f"[OK] 輸出目錄：{OUTPUT_DIR.resolve()}")


# ──────────────────────────────────────────────
# Step 1：登入
# ──────────────────────────────────────────────
def login(session: requests.Session) -> bool:
    print("\n── Step 1：取得登入頁面與 authenticity_token ──")
    resp = session.get(LOGIN_PAGE, headers=HEADERS, timeout=30)
    print(f"  GET {LOGIN_PAGE} → {resp.status_code}")

    soup = BeautifulSoup(resp.text, "html.parser")
    token_input = soup.find("input", {"name": "authenticity_token"})
    if not token_input:
        print("[ERROR] 找不到 authenticity_token，登入頁面結構可能已改變。")
        print(f"  回應前 500 字元：\n{resp.text[:500]}")
        return False

    token = token_input.get("value", "")
    print(f"  authenticity_token = {token[:20]}…（已截斷）")

    print("\n── Step 1b：POST 登入 ──")
    payload = {
        "authenticity_token": token,
        "user[email]": EMAIL,
        "user[password]": PASSWORD,
    }
    resp = session.post(
        LOGIN_POST,
        data=payload,
        headers={**HEADERS, "Referer": LOGIN_PAGE},
        timeout=30,
        allow_redirects=True,
    )
    print(f"  POST {LOGIN_POST} → {resp.status_code}（最終網址：{resp.url}）")

    # 判斷是否登入成功：最終頁面不應再出現登入/註冊相關字樣
    failure_keywords = ["登入", "Sign in", "sign_in", "register/new", "Login"]
    page_text = resp.text

    # 若被導回登入頁，或頁面仍含登入連結視為失敗
    if any(kw in resp.url for kw in ["register", "sign_in", "login"]):
        print("[FAIL] 登入後被導回登入相關頁面，帳號或密碼可能有誤。")
        print(f"  最終網址：{resp.url}")
        return False

    soup_after = BeautifulSoup(page_text, "html.parser")
    # 找登入/退出導覽連結來確認登入狀態
    logout_link = soup_after.find("a", href=re.compile(r"sign_out|logout|session", re.I))
    login_link = soup_after.find("a", href=re.compile(r"sign_in|register/new|login", re.I))

    if logout_link:
        print(f"  [OK] 找到登出連結 → 確認登入成功（href={logout_link['href']}）")
        return True
    if login_link:
        print(f"  [FAIL] 頁面仍含登入連結 → 登入失敗（href={login_link['href']}）")
        print("  可能原因：密碼錯誤、帳號不存在、或網站有 CAPTCHA 機制。")
        print(f"  回應前 800 字元：\n{page_text[:800]}")
        return False

    # fallback：用文字關鍵字判斷
    if "登出" in page_text or "sign_out" in page_text or "Log out" in page_text:
        print("  [OK] 頁面含登出關鍵字 → 確認登入成功")
        return True

    print("  [WARN] 無法明確判斷登入狀態，繼續執行，請自行確認輸出 HTML。")
    print(f"  最終網址：{resp.url}")
    return True


# ──────────────────────────────────────────────
# Step 2：抓列表頁，找未讀文章
# ──────────────────────────────────────────────
def get_unread_articles(session: requests.Session) -> list[dict]:
    print(f"\n── Step 2：抓列表頁 ──")
    resp = session.get(LIST_URL, headers=HEADERS, timeout=30)
    print(f"  GET {LIST_URL} → {resp.status_code}")

    soup = BeautifulSoup(resp.text, "html.parser")

    # 找所有 <tr class="active">（未讀）
    active_rows = soup.find_all("tr", class_="active")
    print(f"  找到 {len(active_rows)} 個 <tr class=\"active\">（未讀文章）")

    articles = []
    for tr in active_rows:
        link = tr.find("a", class_="td-a")
        if not link:
            # 有些 tr 可能用其他結構，嘗試找任何 a 標籤
            link = tr.find("a", href=re.compile(r"vip_contents"))
        if not link:
            continue
        href = link.get("href", "")
        title = link.get_text(strip=True)
        # href 可能是相對路徑
        full_url = href if href.startswith("http") else f"{BASE_URL}{href}"
        articles.append({"title": title, "url": full_url, "href": href})

    if not articles:
        print("  [WARN] 在 <tr class=\"active\"> 中找不到任何 <a class=\"td-a\"> 連結。")
        print("  嘗試 dump 前 3 個 active tr 的 HTML 供診斷：")
        for i, tr in enumerate(active_rows[:3]):
            print(f"  --- tr[{i}] ---\n{tr.prettify()[:400]}\n")
        return []

    print(f"\n  未讀文章清單（共 {len(articles)} 篇）：")
    for i, a in enumerate(articles, 1):
        print(f"  {i:>3}. {a['title']}")
        print(f"       {a['url']}")

    return articles


# ──────────────────────────────────────────────
# Step 3：抓第一篇未讀文章的完整 HTML
# ──────────────────────────────────────────────
def fetch_first_article(session: requests.Session, article: dict) -> Path | None:
    url = article["url"]
    print(f"\n── Step 3：抓第一篇文章 ──")
    print(f"  標題：{article['title']}")
    print(f"  網址：{url}")

    # 從 URL 取 article id（取最後一段數字）
    match = re.search(r"/(\d+)(?:[/?]|$)", url)
    article_id = match.group(1) if match else "unknown"

    resp = session.get(url, headers={"Referer": LIST_URL}, timeout=30)
    print(f"  GET {url} → {resp.status_code}")

    if resp.status_code != 200:
        print(f"  [ERROR] 非 200 回應，無法取得文章內容。")
        return None

    content_start = resp.text.lstrip()[:20]
    if not content_start.lower().startswith("<!doctype html"):
        print("  [WARN] 回應不是完整 HTML！開頭內容：")
        print(f"  {repr(resp.text[:300])}")
        print("\n  實際送出的 Request Headers：")
        for k, v in resp.request.headers.items():
            print(f"    {k}: {v}")
        return None

    output_path = OUTPUT_DIR / f"test_article_{article_id}.html"
    output_path.write_bytes(resp.content)

    size_kb = len(resp.content) / 1024
    print(f"  [OK] 已儲存 → {output_path.resolve()}")
    print(f"  檔案大小：{size_kb:.1f} KB（{len(resp.content):,} bytes）")
    return output_path


# ──────────────────────────────────────────────
# 主流程
# ──────────────────────────────────────────────
def main():
    print("=" * 55)
    print("  investanchors.com 登入 & 文章抓取測試")
    print("=" * 55)

    preflight_check()

    session = requests.Session()
    session.headers.update(HEADERS)

    # Step 1
    logged_in = login(session)
    if not logged_in:
        print("\n[ABORT] 登入失敗，終止執行。請確認密碼是否正確。")
        sys.exit(1)

    # Step 2
    articles = get_unread_articles(session)
    if not articles:
        print("\n[ABORT] 找不到未讀文章，無法繼續。")
        sys.exit(1)

    # Step 3
    saved_path = fetch_first_article(session, articles[0])

    # 摘要
    print("\n" + "=" * 55)
    print("  執行摘要")
    print("=" * 55)
    print(f"  登入成功：{'是' if logged_in else '否'}")
    print(f"  未讀文章數：{len(articles)} 篇")
    for i, a in enumerate(articles, 1):
        print(f"    {i}. {a['title']}")
    if saved_path:
        print(f"  第一篇 HTML 已存檔：{saved_path.resolve()}")
        print(f"  → 用瀏覽器開啟確認：file:///{saved_path.resolve()}")
    else:
        print("  第一篇 HTML 存檔：失敗")
    print("=" * 55)


if __name__ == "__main__":
    main()
