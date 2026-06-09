import time, os, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager

BASE = "http://localhost:3000"
MOBILE = "9000000002"   # registered in previous session
FRESH  = "9000000099"   # unregistered — for register probe
SCREENSHOTS = r"C:\Users\anubh\ccode-library-system\screenshots"
os.makedirs(SCREENSHOTS, exist_ok=True)

def shot(driver, name):
    p = os.path.join(SCREENSHOTS, f"{name}.png")
    driver.save_screenshot(p)
    print(f"  📸 {p}")
    return p

def make_driver():
    opts = Options()
    opts.add_argument("--window-size=1280,900")
    opts.binary_location = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
    svc = Service(ChromeDriverManager().install())
    return webdriver.Chrome(service=svc, options=opts)

def wait_url(driver, fragment, timeout=15):
    WebDriverWait(driver, timeout).until(lambda d: fragment in d.current_url)

def wait_elem(driver, css, timeout=10):
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((By.CSS_SELECTOR, css))
    )

def wait_visible(driver, css, timeout=10):
    return WebDriverWait(driver, timeout).until(
        EC.visibility_of_element_located((By.CSS_SELECTOR, css))
    )

results = []

def run(label, fn):
    driver = make_driver()
    try:
        fn(driver)
        results.append(("PASS", label))
        print(f"✅ {label}")
    except Exception as e:
        results.append(("FAIL", label, str(e)))
        print(f"❌ {label}: {e}")
        try: shot(driver, f"FAIL_{label.replace(' ','_')}")
        except: pass
    finally:
        driver.quit()

# ── Test 1: happy-path login ──────────────────────────────────────────────────
def test_login(driver):
    driver.get(f"{BASE}/login")
    wait_elem(driver, "input")
    shot(driver, "01_login_page")

    # Enter mobile and send OTP
    inp = driver.find_element(By.CSS_SELECTOR, "input")
    inp.clear(); inp.send_keys(MOBILE)
    driver.find_element(By.XPATH, "//button[contains(text(),'Send OTP')]").click()

    # Step 2: OTP input appears
    otp_inp = wait_visible(driver, "input[maxlength='6']")
    shot(driver, "02_otp_step")
    otp_inp.send_keys("123456")
    shot(driver, "03_otp_entered")

    driver.find_element(By.XPATH, "//button[contains(text(),'Sign In')]").click()

    # Should land on /student/dashboard
    wait_url(driver, "/student/dashboard", timeout=15)
    shot(driver, "04_dashboard")

    url = driver.current_url
    assert "/student/dashboard" in url, f"Expected dashboard, got {url}"

run("happy-path login → dashboard", test_login)

# ── Test 2: wrong OTP shows error ────────────────────────────────────────────
def test_wrong_otp(driver):
    driver.get(f"{BASE}/login")
    wait_elem(driver, "input")

    inp = driver.find_element(By.CSS_SELECTOR, "input")
    inp.clear(); inp.send_keys(MOBILE)
    driver.find_element(By.XPATH, "//button[contains(text(),'Send OTP')]").click()

    otp_inp = wait_visible(driver, "input[maxlength='6']")
    otp_inp.send_keys("000000")
    driver.find_element(By.XPATH, "//button[contains(text(),'Sign In')]").click()

    # Should stay on /login and show an error toast / message
    time.sleep(2)
    shot(driver, "05_wrong_otp_error")
    assert "/login" in driver.current_url, "Should stay on login page"

run("wrong OTP stays on login", test_wrong_otp)

# ── Test 3: empty mobile blocked before OTP send ─────────────────────────────
def test_empty_mobile(driver):
    driver.get(f"{BASE}/login")
    wait_elem(driver, "input")
    # Click Send OTP without entering anything
    driver.find_element(By.XPATH, "//button[contains(text(),'Send OTP')]").click()
    time.sleep(1)
    shot(driver, "06_empty_mobile")
    # Should still be on step 1 (no OTP input visible)
    otp_inputs = driver.find_elements(By.CSS_SELECTOR, "input[maxlength='6']")
    assert len(otp_inputs) == 0, "Should not advance to OTP step with empty mobile"

run("empty mobile blocked", test_empty_mobile)

# ── Test 4: unregistered mobile gets error after correct OTP ─────────────────
def test_unregistered(driver):
    driver.get(f"{BASE}/login")
    wait_elem(driver, "input")

    inp = driver.find_element(By.CSS_SELECTOR, "input")
    inp.clear(); inp.send_keys(FRESH)
    driver.find_element(By.XPATH, "//button[contains(text(),'Send OTP')]").click()

    otp_inp = wait_visible(driver, "input[maxlength='6']")
    otp_inp.send_keys("123456")
    driver.find_element(By.XPATH, "//button[contains(text(),'Sign In')]").click()

    time.sleep(2)
    shot(driver, "07_unregistered_error")
    assert "/login" in driver.current_url, "Unregistered user should stay on login"

run("unregistered mobile stays on login", test_unregistered)

# ── Summary ───────────────────────────────────────────────────────────────────
print("\n─── Results ───")
passed = sum(1 for r in results if r[0] == "PASS")
for r in results:
    icon = "✅" if r[0] == "PASS" else "❌"
    print(f"  {icon} {r[1]}" + (f"\n     {r[2]}" if r[0] == "FAIL" else ""))
print(f"\n{passed}/{len(results)} passed")
sys.exit(0 if passed == len(results) else 1)
