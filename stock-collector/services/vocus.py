"""Vocus API client：登入、清單、文章詳情正規化、留言、圖片下載。

vocus 兩種文章型別結構不同，本模組把差異吸收掉，對外一律回傳正規化 dict：
- post   ：/api/posts/{id}，欄位平鋪；content 為純文字 HTML（無圖），圖片在獨立 images 陣列
- article：/api/article/{id}，資料包在 article 物件；content 為完整 HTML、圖片內嵌（代理 URL）

正規化輸出（fetch_detail）：
{
  "article_id", "creator_id", "title", "author", "salon_name",
  "publish_at", "url", "tags": [...],
  "markdown": "<正文 markdown，圖片為 ![[Assets/檔名]]>",
  "images": [{"url", "filename"}...],   # 已抽出、待下載
  "comments": [{...}],                    # 見 fetch_comments
}
"""

import logging
import re
from urllib.parse import unquote, urlparse

import requests
from bs4 import BeautifulSoup

import config

logger = logging.getLogger(__name__)

API = "https://api.vocus.cc/api"
_HEADERS = {"User-Agent": "Mozilla/5.0", "Referer": "https://vocus.cc/"}


class VocusError(RuntimeError):
    pass


def login() -> str:
    """以帳密登入取得 Bearer token（form-encoded）。"""
    if not config.VOCUS_EMAIL or not config.VOCUS_PASSWORD:
        raise VocusError("VOCUS_EMAIL / VOCUS_PASSWORD 未設定")
    r = requests.post(
        f"{API}/users/login",
        data={"email": config.VOCUS_EMAIL, "password": config.VOCUS_PASSWORD},
        headers=_HEADERS,
        timeout=30,
    )
    r.raise_for_status()
    token = r.json().get("token")
    if not token:
        raise VocusError("登入回應沒有 token")
    return token


class VocusClient:
    def __init__(self, token: str | None = None):
        self.s = requests.Session()
        self.s.headers.update(_HEADERS)
        self.token = token or login()
        self.s.headers["Authorization"] = f"Bearer {self.token}"

    # ---- 清單 ----
    def list_articles(self, salon_id: str, page: int = 1, num: int = 30) -> tuple[list[dict], int]:
        r = self.s.get(
            f"{API}/contents",
            params={"salonId": salon_id, "num": num, "page": page, "order": "desc", "sort": "publishAt"},
            timeout=30,
        )
        r.raise_for_status()
        d = r.json()
        return d.get("contents", []), d.get("count", 0)

    def iter_all_articles(self, salon_id: str, max_pages: int = 100):
        page = 1
        while page <= max_pages:
            items, _ = self.list_articles(salon_id, page=page)
            if not items:
                break
            yield from items
            page += 1

    # ---- 詳情 ----
    @staticmethod
    def _endpoint_type(t: str) -> str:
        return "posts" if t == "post" else t  # n8n create_url 邏輯：post→posts

    def fetch_detail(self, article_meta: dict, salon_name: str) -> dict:
        t = article_meta["type"]
        cid = article_meta["contentId"]
        r = self.s.get(f"{API}/{self._endpoint_type(t)}/{cid}", timeout=40)
        r.raise_for_status()
        raw = r.json()
        art = raw.get("article", raw)  # article 型別包一層，post 平鋪

        content_html = art.get("content", "") or ""
        if t == "post":
            markdown, images = self._normalize_post(content_html, art.get("images", []) or [])
        else:
            markdown, images = self._normalize_article(content_html)

        return {
            "article_id": cid,
            "creator_id": art.get("userId") or article_meta.get("creatorId", ""),
            "title": art.get("title", "無標題"),
            "author": (art.get("user") or {}).get("fullname", "") if isinstance(art.get("user"), dict) else "",
            "salon_name": salon_name,
            "type": t,
            "publish_at": art.get("publishAt") or art.get("lastPublishAt") or art.get("createdAt", ""),
            "url": f"https://vocus.cc/{'post' if t == 'post' else 'article'}/{cid}",
            "tags": [tg.get("title") if isinstance(tg, dict) else tg for tg in (art.get("tags") or [])],
            "abstract": art.get("abstract", ""),
            "markdown": markdown,
            "images": images,
            "comment_count": art.get("commentCount", 0),
        }

    # ---- 圖片 URL 正規化 ----
    @staticmethod
    def _real_image_url(src: str) -> str:
        """把 vocus 代理 URL 還原成 images.vocus.cc 原圖 URL。

        代理格式：
        - https://resize-image.vocus.cc/resize?...url=<urlencoded images.vocus.cc URL>
        - https://img.vocus.cc/HASH/w:800/f:webp/plain/https://images.vocus.cc/UUID.ext
        """
        if "resize-image.vocus.cc" in src and "url=" in src:
            return unquote(src.split("url=", 1)[1].split("&", 1)[0])
        if "img.vocus.cc" in src and "/plain/" in src:
            return src.split("/plain/", 1)[1]
        return src

    @staticmethod
    def _image_filename(real_url: str) -> str:
        name = urlparse(real_url).path.split("/")[-1] or "image"
        return re.sub(r"[^\w.\-]", "_", name)

    def _register_image(self, src: str, images: list[dict], seen: set) -> str:
        real = self._real_image_url(src)
        fname = self._image_filename(real)
        if real not in seen:
            seen.add(real)
            images.append({"url": real, "filename": fname})
        return f"![[Assets/{fname}]]"

    # ---- post：HTML 段落 + 獨立 images 陣列 ----
    def _normalize_post(self, content_html: str, image_urls: list) -> tuple[str, list[dict]]:
        images: list[dict] = []
        seen: set = set()
        md = self._html_to_markdown(content_html, images, seen)
        # post 圖片不在內文，統一附在文末
        for u in image_urls:
            embed = self._register_image(u, images, seen)
            md += f"\n\n{embed}"
        return md.strip(), images

    # ---- article：完整 HTML、圖片內嵌 ----
    def _normalize_article(self, content_html: str) -> tuple[str, list[dict]]:
        images: list[dict] = []
        seen: set = set()
        md = self._html_to_markdown(content_html, images, seen)
        return md.strip(), images

    # ---- 輕量 HTML→Markdown（vocus content 為 Lexical 產生，標籤可預期）----
    def _html_to_markdown(self, html: str, images: list[dict], seen: set) -> str:
        if not html:
            return ""
        soup = BeautifulSoup(html, "lxml")
        # 移除樣式/腳本
        for tag in soup(["style", "script", "head"]):
            tag.decompose()

        # 圖片 → Obsidian 內嵌，並登記待下載
        for img in soup.find_all("img"):
            src = img.get("src") or ""
            if not src:
                img.decompose()
                continue
            img.replace_with(self._register_image(src, images, seen))

        lines: list[str] = []

        def render(node) -> str:
            from bs4 import NavigableString
            if isinstance(node, NavigableString):
                return str(node)
            name = node.name
            if name in ("h1", "h2", "h3", "h4", "h5", "h6"):
                return "\n" + "#" * int(name[1]) + " " + node.get_text(strip=True) + "\n"
            if name == "a":
                text = node.get_text(strip=True)
                href = node.get("href", "")
                return f"[{text}]({href})" if href else text
            if name == "br":
                return "\n"
            if name == "li":
                return "- " + "".join(render(c) for c in node.children).strip() + "\n"
            if name in ("strong", "b"):
                return "**" + "".join(render(c) for c in node.children) + "**"
            if name in ("em", "i"):
                return "*" + "".join(render(c) for c in node.children) + "*"
            if name in ("p", "div"):
                inner = "".join(render(c) for c in node.children).strip()
                return "\n" + inner + "\n" if inner else ""
            return "".join(render(c) for c in node.children)

        body = soup.body or soup
        text = "".join(render(c) for c in body.children)
        # 收斂多餘空行
        text = re.sub(r"\n{3,}", "\n\n", text)
        return text.strip()

    # ---- 留言（含巢狀回覆），翻頁直到空 ----
    # 注意：留言端點用「原始」type（post/article，單數），與詳情端點的 posts（複數）不同。
    def fetch_comments(self, article_type: str, content_id: str, max_pages: int = 20) -> list[dict]:
        out: list[dict] = []
        for page in range(1, max_pages + 1):
            r = self.s.get(
                f"{API}/comments/{article_type}/{content_id}",
                params={"num": 30, "page": page},
                timeout=30,
            )
            if r.status_code != 200:
                break
            d = r.json()
            lst = d if isinstance(d, list) else d.get("comments", [])
            if not lst:
                break
            for c in lst:
                if not isinstance(c, dict):
                    continue
                user = c.get("user")
                author = user[0].get("fullname") if isinstance(user, list) and user else (
                    user.get("fullname") if isinstance(user, dict) else "匿名"
                )
                # 回覆的作者資訊不在 msgs[].user（為 None），而在父留言的 replyUser 陣列，
                # 以 userId 對應；先建 userId→fullname 對照表。
                uid_map = {}
                for u in c.get("replyUser") or []:
                    if isinstance(u, dict) and u.get("_id"):
                        uid_map[u["_id"]] = u.get("fullname", "匿名")
                replies = []
                for rp in c.get("msgs", []) or []:
                    rauthor = uid_map.get(rp.get("userId"), "匿名")
                    replies.append({"author": rauthor, "text": rp.get("msg") or rp.get("content") or ""})
                out.append({
                    "author": author,
                    "text": c.get("msg") or c.get("content") or "",
                    "like": c.get("likeCount", 0),
                    "replies": replies,
                })
            if len(lst) < 30:
                break
        return out
