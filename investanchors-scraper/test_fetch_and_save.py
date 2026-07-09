"""
テスト：記事 17827077851593 の取得 → API 保存の完整チェーン検証
"""
import json
import os
import sys
from pathlib import Path

import requests
from bs4 import BeautifulSoup
from dotenv import load_dotenv

load_dotenv()

EMAIL = os.getenv("INVESTANCHORS_EMAIL", "")
PASSWORD = os.getenv("INVESTANCHORS_PASSWORD", "")

ARTICLE_ID = "17827077851593"
ARTICLE_URL = f"https://investanchors.com/user/vip_contents/{ARTICLE_ID}"
BASE_URL = "https://investanchors.com"
LOGIN_PAGE = f"{BASE_URL}/user/register/new"
LOGIN_POST = f"{BASE_URL}/user/session"
API_URL = "https://stock.bignoodle.net/StockServer/stockPageRaw/save"

OUTPUT_DIR = Path("./output")
OUTPUT_FILE = OUTPUT_DIR / f"test_article_{ARTICLE_ID}.html"

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


def login(session: requests.Session) -> bool:
    print("\n── Step 1：登入 ──")
    resp = session.get(LOGIN_PAGE, timeout=30)
    print(f"  GET {LOGIN_PAGE} → {resp.status_code}")

    soup = BeautifulSoup(resp.text, "html.parser")
    token_input = soup.find("input", {"name": "authenticity_token"})
    if not token_input:
        print("[ERROR] 找不到 authenticity_token")
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
        print("[FAIL] 登入後被導回登入頁，帳號/密碼可能有誤")
        return False

    page_text = resp.text
    if "登出" in page_text or "sign_out" in page_text or "Log out" in page_text:
        print("  [OK] 登入成功（偵測到登出連結）")
        return True

    soup_after = BeautifulSoup(page_text, "html.parser")
    import re
    logout_link = soup_after.find("a", href=re.compile(r"sign_out|logout|session", re.I))
    if logout_link:
        print(f"  [OK] 登入成功（找到登出連結 href={logout_link['href']}）")
        return True

    print("  [WARN] 無法確定登入狀態，繼續執行")
    return True


def fetch_article(session: requests.Session) -> str | None:
    print(f"\n── Step 2：抓取文章 {ARTICLE_ID} ──")
    print(f"  URL：{ARTICLE_URL}")

    resp = session.get(
        ARTICLE_URL,
        headers={"Referer": f"{BASE_URL}/user/vip_contents/investanchors_index"},
        timeout=30,
    )
    print(f"  GET → {resp.status_code}")
    print(f"  Content-Type：{resp.headers.get('Content-Type', '(none)')}")
    print(f"  Content-Length：{len(resp.content):,} bytes")

    if resp.status_code != 200:
        print(f"  [ERROR] 非 200 回應，停止執行")
        return None

    # Step 3：驗證是否為完整 HTML
    print("\n── Step 3：驗證 HTML 格式 ──")
    content_start = resp.text.lstrip()[:50]
    print(f"  開頭內容：{repr(content_start)}")

    if not content_start.lower().startswith("<!doctype html"):
        print("  [FAIL] 不是完整 HTML！header 修正可能未生效")
        print("  實際送出的 Request Headers：")
        for k, v in resp.request.headers.items():
            print(f"    {k}: {v}")
        print("\n  完整回應前 500 字：")
        print(repr(resp.text[:500]))
        return None

    print("  [OK] 確認為完整 HTML（以 <!DOCTYPE html> 開頭）")

    # Step 4：覆蓋舊檔
    print("\n── Step 4：儲存檔案 ──")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT_FILE.write_bytes(resp.content)
    size_kb = len(resp.content) / 1024
    print(f"  [OK] 已覆蓋 → {OUTPUT_FILE.resolve()}")
    print(f"  檔案大小：{size_kb:.1f} KB（{len(resp.content):,} bytes）")

    return resp.text


def call_api(html_content: str) -> None:
    print(f"\n── Step 5：呼叫 API 儲存文章 ──")
    print(f"  POST {API_URL}")

    payload = {
        "url": ARTICLE_URL,
        "htmlContent": html_content,
        "source": "定錨",
    }

    try:
        resp = requests.post(
            API_URL,
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=30,
        )
    except requests.exceptions.RequestException as e:
        print(f"  [ERROR] 請求失敗：{e}")
        return

    print(f"  HTTP Status：{resp.status_code}")
    print(f"  Response Body：")

    try:
        body = resp.json()
        print(f"  {json.dumps(body, ensure_ascii=False, indent=4)}")
        if resp.status_code == 200 and body.get("status") == "ok":
            print("\n  [OK] API 回應正常（status: ok）")
        else:
            print(f"\n  [WARN] API 回應異常，請確認上方 status code 和 body")
    except Exception:
        print(f"  （非 JSON 格式）{resp.text[:500]}")


def main():
    print("=" * 60)
    print("  完整鏈路測試：抓取 17827077851593 → 儲存 → API")
    print("=" * 60)

    if not EMAIL:
        print("[ERROR] .env 中 INVESTANCHORS_EMAIL 未設定")
        sys.exit(1)
    if not PASSWORD:
        print("[ERROR] .env 中 INVESTANCHORS_PASSWORD 未設定")
        sys.exit(1)
    print(f"[OK] 帳號：{EMAIL}")

    session = requests.Session()
    session.headers.update(HEADERS)

    # Step 1
    if not login(session):
        print("\n[ABORT] 登入失敗")
        sys.exit(1)

    # Step 2–4
    html = fetch_article(session)
    if html is None:
        print("\n[ABORT] 文章取得失敗，不發送 API（請確認 header 修正）")
        sys.exit(1)

    # Step 5
    call_api(html)

    print("\n" + "=" * 60)
    print("  完成")
    print("=" * 60)


if __name__ == "__main__":
    main()
