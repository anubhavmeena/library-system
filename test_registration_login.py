"""
Selenium test: new user registration + login in dev mode.
Dev mode OTP is always 123456.
"""

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.keys import Keys
import time

BASE_URL = "http://localhost:3000"
MOBILE   = "9071356852"
OTP      = "123456"
NAME     = "Test User"

def make_driver():
    opts = Options()
    opts.add_argument("--start-maximized")
    driver = webdriver.Chrome(options=opts)
    return driver

def wait_for(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((by, value))
    )

def wait_clickable(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(
        EC.element_to_be_clickable((by, value))
    )

def wait_url(driver, fragment, timeout=15):
    WebDriverWait(driver, timeout).until(EC.url_contains(fragment))

# ── Registration ──────────────────────────────────────────────────────────────

def test_registration(driver):
    print("\n=== REGISTRATION ===")
    driver.get(f"{BASE_URL}/register")

    # Step 0 — enter mobile and send OTP
    inp = wait_for(driver, By.CSS_SELECTOR, "input.input")
    inp.clear()
    inp.send_keys(MOBILE)

    wait_clickable(driver, By.XPATH, "//button[contains(., 'Send OTP')]").click()
    print(f"  [1] Sent OTP to {MOBILE}")

    # Step 1 — wait for OTP input (confirm UI transitioned) and verify
    otp_inp = wait_for(driver, By.CSS_SELECTOR, "input.input[maxlength='6']")
    otp_inp.clear()
    otp_inp.send_keys(OTP)

    wait_clickable(driver, By.XPATH, "//button[contains(., 'Verify OTP')]").click()
    print(f"  [2] Submitted OTP: {OTP}")

    # If user already exists the page redirects to /login
    time.sleep(2)
    if "/login" in driver.current_url:
        print("  [!] Mobile already registered - skipping profile step, proceeding to login test.")
        return False

    # Step 2 — fill profile
    def js_set(el, value):
        """Set a React-controlled input value via native setter + dispatching change event."""
        driver.execute_script("""
            var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            setter.call(arguments[0], arguments[1]);
            arguments[0].dispatchEvent(new Event('input', {bubbles:true}));
            arguments[0].dispatchEvent(new Event('change', {bubbles:true}));
        """, el, value)

    name_inp = wait_clickable(driver, By.XPATH,
        "//label[contains(text(),'Full Name')]/following-sibling::input")
    name_inp.click()
    name_inp.send_keys(Keys.CONTROL + "a")
    name_inp.send_keys(NAME)

    dob_inp = driver.find_element(By.CSS_SELECTOR, "input[type='date']")
    js_set(dob_inp, "2000-06-15")

    btn = wait_clickable(driver, By.XPATH, "//button[contains(., 'Complete Registration')]")
    driver.execute_script("arguments[0].scrollIntoView(true);", btn)
    time.sleep(1)
    btn.click()
    print(f"  [3] Completed profile as '{NAME}' (dob: 2000-06-15)")

    wait_url(driver, "/student/dashboard")
    print(f"  [OK] Registration SUCCESS -> {driver.current_url}")
    return True

# ── Login ─────────────────────────────────────────────────────────────────────

def test_login(driver):
    print("\n=== LOGIN ===")
    driver.get(f"{BASE_URL}/login")

    # Step 1 — enter mobile and send OTP
    inp = wait_for(driver, By.CSS_SELECTOR, "input.input")
    inp.clear()
    inp.send_keys(MOBILE)

    wait_clickable(driver, By.XPATH, "//button[contains(., 'Send OTP')]").click()
    print(f"  [1] Sent OTP to {MOBILE}")

    # Step 2 — enter OTP and sign in
    otp_inp = wait_for(driver, By.CSS_SELECTOR, "input.input[maxlength='6']")
    otp_inp.clear()
    otp_inp.send_keys(OTP)

    wait_clickable(driver, By.XPATH, "//button[contains(., 'Sign In')]").click()
    print(f"  [2] Submitted OTP: {OTP}")

    wait_url(driver, "/student/dashboard")
    print(f"  [OK] Login SUCCESS -> {driver.current_url}")

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    driver = make_driver()
    try:
        test_registration(driver)
        time.sleep(1)
        test_login(driver)
        print("\n[PASS] All tests passed!")
    except Exception as exc:
        print(f"\n[FAIL] Test failed: {exc}")
        import traceback
        traceback.print_exc()
    finally:
        time.sleep(3)
        driver.quit()

if __name__ == "__main__":
    main()
