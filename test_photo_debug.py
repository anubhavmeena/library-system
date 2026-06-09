"""
Debug photo upload: capture browser console + make direct API call
"""
import os, sys, time, requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options

BASE_URL   = "http://localhost:3000"
API_BASE   = "http://localhost:8080"
MOBILE     = "9071356852"
OTP        = "123456"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PHOTO_PATH = os.path.join(SCRIPT_DIR, "myphoto.jpg")

# ── Get JWT via direct API calls ──────────────────────────────────────────────

def get_jwt():
    r1 = requests.post(f"{API_BASE}/api/auth/send-otp",
                       json={"contact": MOBILE, "contactType": "MOBILE"})
    print(f"  send-otp: {r1.status_code} {r1.text[:200]}")

    session_token = r1.json()["data"]["sessionToken"] if r1.ok else None

    r2 = requests.post(f"{API_BASE}/api/auth/verify-otp",
                       json={"contact": MOBILE, "otp": OTP})
    print(f"  verify-otp: {r2.status_code} {r2.text[:200]}")

    r3 = requests.post(f"{API_BASE}/api/auth/login",
                       json={"contact": MOBILE, "sessionToken": r2.json()["data"]["sessionToken"]})
    print(f"  login: {r3.status_code} {r3.text[:300]}")
    return r3.json()["data"]["token"]


print("\n=== DIRECT API TEST ===")
try:
    jwt = get_jwt()
    print(f"  JWT obtained: {jwt[:40]}...")

    with open(PHOTO_PATH, "rb") as f:
        r = requests.post(
            f"{API_BASE}/api/users/me/photo",
            headers={"Authorization": f"Bearer {jwt}"},
            files={"file": ("myphoto.jpg", f, "image/jpeg")}
        )
    print(f"  upload status: {r.status_code}")
    print(f"  upload body:   {r.text[:500]}")
except Exception as e:
    print(f"  ERROR: {e}")

# ── Selenium with console log capture ─────────────────────────────────────────

print("\n=== SELENIUM WITH CONSOLE CAPTURE ===")

opts = Options()
opts.set_capability("goog:loggingPrefs", {"browser": "ALL", "performance": "ALL"})
opts.add_argument("--window-size=1400,900")

driver = webdriver.Chrome(options=opts)
wait   = WebDriverWait(driver, 15)

def dump_console():
    logs = driver.get_log("browser")
    if logs:
        print("  -- browser console --")
        for l in logs:
            print(f"     [{l['level']}] {l['message']}")

try:
    # Login
    driver.get(f"{BASE_URL}/login")
    wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "input.input"))).send_keys(MOBILE)
    wait.until(EC.element_to_be_clickable((By.XPATH, "//button[contains(text(),'Send OTP')]"))).click()
    wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[maxlength='6']"))).send_keys(OTP)
    wait.until(EC.element_to_be_clickable((By.XPATH, "//button[contains(text(),'Sign In')]"))).click()
    wait.until(EC.url_contains("/student/dashboard"))
    print("  Logged in")
    dump_console()

    driver.get(f"{BASE_URL}/student/profile")
    wait.until(EC.presence_of_element_located((By.XPATH, "//h1[contains(text(),'My Profile')]")))
    print("  Profile page loaded")
    dump_console()

    # Intercept XHR to capture response
    driver.execute_script("""
        window._uploadResp = null;
        const origPost = window.fetch;
        const origXHROpen = XMLHttpRequest.prototype.open;
        const origXHRSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.open = function(m, u, ...a) {
            this._url = u;
            return origXHROpen.call(this, m, u, ...a);
        };
        XMLHttpRequest.prototype.send = function(body) {
            this.addEventListener('loadend', () => {
                if (this._url && this._url.includes('photo')) {
                    window._uploadResp = {
                        status: this.status,
                        body: this.responseText
                    };
                    console.log('PHOTO_UPLOAD_RESP: status=' + this.status + ' body=' + this.responseText.slice(0,300));
                }
            });
            return origXHRSend.call(this, body);
        };
    """)

    file_input = driver.find_element(By.CSS_SELECTOR, "input[type='file']")
    driver.execute_script("arguments[0].style.display = 'block';", file_input)
    file_input.send_keys(PHOTO_PATH)
    print(f"  Sent file: {PHOTO_PATH}")

    # Wait for spinner to come and go
    try:
        wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, ".animate-spin")))
        print("  Spinner appeared")
        wait.until(EC.invisibility_of_element_located((By.CSS_SELECTOR, ".animate-spin")))
        print("  Spinner gone")
    except Exception:
        print("  (spinner not observed)")

    time.sleep(1)
    dump_console()

    resp = driver.execute_script("return window._uploadResp;")
    print(f"  XHR intercept result: {resp}")

    img_present = len(driver.find_elements(By.CSS_SELECTOR, "img[alt='Profile']")) > 0
    print(f"  img[alt='Profile'] present: {img_present}")

    driver.save_screenshot(os.path.join(SCRIPT_DIR, "debug_after_upload.png"))

except Exception as e:
    import traceback
    traceback.print_exc()
finally:
    driver.quit()
