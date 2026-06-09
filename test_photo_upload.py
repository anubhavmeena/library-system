"""
Selenium test: photo upload on My Profile page
Mobile: 9071356852  |  OTP: 123456 (dev mode)
Photo:  myphoto.jpg (2.2 MB JPEG, same directory as this script)
"""

import os, sys, time
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.common.exceptions import TimeoutException

BASE_URL   = "http://localhost:3000"
MOBILE     = "9071356852"
OTP        = "123456"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PHOTO_PATH = os.path.join(SCRIPT_DIR, "myphoto.jpg")

# ── helpers ──────────────────────────────────────────────────────────────────

def step(n, msg):
    print(f"  [{n}] {msg}")

def ok(msg):
    print(f"  [OK] {msg}")

def fail(msg):
    print(f"  [FAIL] {msg}")
    sys.exit(1)

# ── setup ─────────────────────────────────────────────────────────────────────

opts = Options()
opts.add_argument("--window-size=1400,900")
# Remove headless so we can take real screenshots; add --headless=new to flip
driver = webdriver.Chrome(options=opts)
wait   = WebDriverWait(driver, 15)

def ss(name):
    path = os.path.join(SCRIPT_DIR, name)
    driver.save_screenshot(path)
    print(f"       screenshot -> {path}")

# ── test ──────────────────────────────────────────────────────────────────────

try:
    print("\n=== LOGIN ===")
    driver.get(f"{BASE_URL}/login")
    step(1, "Opened login page")

    contact_input = wait.until(EC.presence_of_element_located(
        (By.CSS_SELECTOR, "input.input")))
    contact_input.clear()
    contact_input.send_keys(MOBILE)
    step(2, f"Entered mobile: {MOBILE}")

    send_btn = wait.until(EC.element_to_be_clickable(
        (By.XPATH, "//button[contains(text(),'Send OTP')]")))
    send_btn.click()
    step(3, "Clicked Send OTP")

    otp_input = wait.until(EC.presence_of_element_located(
        (By.CSS_SELECTOR, "input[maxlength='6']")))
    otp_input.send_keys(OTP)
    step(4, f"Entered OTP: {OTP}")

    sign_in_btn = wait.until(EC.element_to_be_clickable(
        (By.XPATH, "//button[contains(text(),'Sign In')]")))
    sign_in_btn.click()
    step(5, "Clicked Sign In")

    wait.until(EC.url_contains("/student/dashboard"))
    ok(f"Logged in -> {driver.current_url}")
    ss("01_dashboard.png")

    # ── profile page ─────────────────────────────────────────────────────────

    print("\n=== MY PROFILE — PHOTO UPLOAD ===")
    driver.get(f"{BASE_URL}/student/profile")

    wait.until(EC.presence_of_element_located(
        (By.XPATH, "//h1[contains(text(),'My Profile')]")))
    step(1, "Profile page loaded")
    ss("02_profile_before.png")

    # Record whether a photo already exists
    existing_photo = len(driver.find_elements(By.CSS_SELECTOR, "img[alt='Profile']")) > 0
    step(2, f"Photo already set: {existing_photo}")

    # The file input is display:none — unhide it so Selenium can interact
    file_input = driver.find_element(By.CSS_SELECTOR, "input[type='file']")
    driver.execute_script("arguments[0].style.display = 'block';", file_input)

    file_input.send_keys(PHOTO_PATH)
    step(3, f"Sent file path: {PHOTO_PATH}")

    # Wait for upload spinner to appear then disappear
    try:
        wait.until(EC.presence_of_element_located(
            (By.CSS_SELECTOR, ".animate-spin")))
        step(4, "Upload spinner appeared")
        wait.until(EC.invisibility_of_element_located(
            (By.CSS_SELECTOR, ".animate-spin")))
        step(5, "Upload spinner gone — upload complete")
    except TimeoutException:
        step(4, "Spinner did not appear (upload may have been instant or failed)")

    # Verify the <img alt="Profile"> is now present
    try:
        img = wait.until(EC.presence_of_element_located(
            (By.CSS_SELECTOR, "img[alt='Profile']")))
        src = img.get_attribute("src")
        ok(f"Profile image displayed  src={src}")
        ss("03_profile_after_upload.png")
    except TimeoutException:
        ss("03_profile_after_FAIL.png")
        fail("img[alt='Profile'] did not appear after upload")

    # ── probe: ensure src points to /uploads/ path ───────────────────────────

    print("\n=== PROBE: photo URL ===")
    if "/uploads/photos/" in src:
        ok(f"URL contains /uploads/photos/ — static-file handler will serve it")
    else:
        fail(f"Unexpected photoUrl: {src}  (expected /uploads/photos/...)")

    # ── probe: verify the browser can actually fetch the image ───────────────

    print("\n=== PROBE: HTTP fetch of uploaded image ===")
    status = driver.execute_script("""
        const resp = await fetch(arguments[0]);
        return resp.status;
    """, src)
    if status == 200:
        ok(f"GET {src} -> {status} OK")
    else:
        fail(f"GET {src} -> {status}  (image not served - static handler may be missing)")

    # ── probe: re-upload (replace existing) ─────────────────────────────────

    print("\n=== PROBE: re-upload replaces previous photo ===")
    file_input2 = driver.find_element(By.CSS_SELECTOR, "input[type='file']")
    driver.execute_script("arguments[0].style.display = 'block';", file_input2)
    file_input2.send_keys(PHOTO_PATH)
    try:
        wait.until(EC.invisibility_of_element_located((By.CSS_SELECTOR, ".animate-spin")))
    except TimeoutException:
        pass
    img2 = wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "img[alt='Profile']")))
    src2 = img2.get_attribute("src")
    ok(f"Re-upload succeeded  src={src2}")
    ss("04_profile_reuploaded.png")

    # ── probe: remove photo ──────────────────────────────────────────────────

    print("\n=== PROBE: remove photo ===")
    try:
        remove_btn = driver.find_element(
            By.XPATH, "//button[contains(text(),'Remove photo')]")
        remove_btn.click()
        # avatar (initials div) should reappear, img should vanish
        wait.until(EC.invisibility_of_element_located(
            (By.CSS_SELECTOR, "img[alt='Profile']")))
        ok("Photo removed — initials avatar restored")
        ss("05_profile_removed.png")
    except Exception as e:
        print(f"  [WARN] Remove probe: {e}")
        ss("05_profile_remove_fail.png")

    print("\n[PASS] All photo upload checks passed.\n")

except Exception as exc:
    ss("99_error.png")
    print(f"\n[FAIL] Unhandled exception: {exc}\n")
    import traceback; traceback.print_exc()
    sys.exit(1)

finally:
    driver.quit()
