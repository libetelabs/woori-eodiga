"""Pleos Virtual Console 페이지를 열어 둔다(열려 있어야 /vehicle/gear 같은 HTTP 조작이 먹는다).

사용: adb forward tcp:8088 tcp:8088 후 `python3 tools/vc_hold.py &`, 끝나면 kill.
열리면 "VC_READY" 를 한 줄 찍는다. 페이지를 열 때 기어는 P 로 초기화된다.
"""
import time
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page()
    page.goto("http://127.0.0.1:8088/")
    for _ in range(30):
        ready = page.evaluate("fetch('/vehicle/state').then(r => r.json()).then(j => !!(j.gear && j.gear.bridge_ready))")
        if ready:
            break
        time.sleep(1)
    print("VC_READY" if ready else "VC_NOT_READY", flush=True)
    while True:
        time.sleep(60)
