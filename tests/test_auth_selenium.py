"""
Selenium end-to-end tests for the register and login flows in dev mode.

Dev mode assumptions
--------------------
- App is running at http://localhost:3000
- SPRING_PROFILES_ACTIVE=dev  →  OTP is always accepted as '123456'
- No real Twilio credentials required

Run
---
    pip install selenium pytest
    pytest tests/test_auth_selenium.py -v

Add --headless flag to run without opening a browser window:
    pytest tests/test_auth_selenium.py -v --headless
"""

import time
import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE_URL = "http://localhost:3000"
DEV_OTP  = "123456"

# Unique 10-digit mobile per test run so register never collides on re-runs
TEST_MOBILE = f"7{str(int(time.time()))[-9:]}"


# ── Helpers ───────────────────────────────────────────────────────────────────

def visible(driver, css, timeout=10):
    """Return element once it is visible; raises TimeoutException on failure."""
    return WebDriverWait(driver, timeout).until(
        EC.visibility_of_element_located((By.CSS_SELECTOR, css))
    )


def clear_auth(driver):
    """Wipe localStorage so previous JWT/session state doesn't bleed between tests."""
    driver.execute_script("localStorage.clear()")
    time.sleep(0.3)


# ── Fixtures ──────────────────────────────────────────────────────────────────

def pytest_addoption(parser):
    parser.addoption("--headless", action="store_true", default=False,
                     help="Run Chrome in headless mode")


@pytest.fixture(scope="module")
def driver(request):
    opts = Options()
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")
    opts.add_argument("--window-size=1280,900")
    if request.config.getoption("--headless"):
        opts.add_argument("--headless=new")

    d = webdriver.Chrome(options=opts)
    d.implicitly_wait(5)
    yield d
    d.quit()


# ── Register flow ─────────────────────────────────────────────────────────────

class TestRegister:
    """
    Walks the 3-step registration flow for a brand-new mobile number.
      Step 0 → enter mobile, send OTP
      Step 1 → enter OTP (123456 in dev mode), verify
      Step 2 → fill profile, submit
    Expected result: redirect to /student/dashboard
    """

    def test_step0_enter_mobile_and_send_otp(self, driver):
        driver.get(f"{BASE_URL}/register")
        clear_auth(driver)
        driver.get(f"{BASE_URL}/register")

        contact = visible(driver, "input.input")
        contact.clear()
        contact.send_keys(TEST_MOBILE)

        visible(driver, "button.btn-primary").click()

        # Step 1 is shown when the OTP field (monospaced, tracking-widest) appears
        visible(driver, "input.tracking-widest")
        assert "register" in driver.current_url, "Should still be on /register after sending OTP"

    def test_step1_verify_otp(self, driver):
        otp_field = visible(driver, "input.tracking-widest")
        otp_field.clear()
        otp_field.send_keys(DEV_OTP)

        visible(driver, "button.btn-primary").click()

        # Step 2 is shown when the name input reappears (profile form)
        visible(driver, "input.input")
        assert "register" in driver.current_url, "Should still be on /register after OTP"

    def test_step2_complete_registration_and_redirect(self, driver):
        name_field = visible(driver, "input.input")
        name_field.clear()
        name_field.send_keys("Selenium Test User")

        visible(driver, "button.btn-primary").click()

        WebDriverWait(driver, 15).until(EC.url_contains("/student/dashboard"))
        assert "/student/dashboard" in driver.current_url, \
            f"Expected /student/dashboard, got {driver.current_url}"


# ── Login flow ────────────────────────────────────────────────────────────────

class TestLogin:
    """
    Walks the 2-step login flow using the mobile registered above.
      Step 1 → enter mobile, send OTP
      Step 2 → enter OTP (123456 in dev mode), sign in
    Expected result: redirect to /student/dashboard
    """

    def test_step1_enter_mobile_and_send_otp(self, driver):
        driver.get(f"{BASE_URL}/login")
        clear_auth(driver)
        driver.get(f"{BASE_URL}/login")

        contact = visible(driver, "input.input")
        contact.clear()
        contact.send_keys(TEST_MOBILE)

        visible(driver, "button.btn-primary").click()

        # OTP field appears
        visible(driver, "input.tracking-widest")
        assert "login" in driver.current_url, "Should still be on /login after sending OTP"

    def test_step2_enter_otp_and_sign_in(self, driver):
        otp_field = visible(driver, "input.tracking-widest")
        otp_field.clear()
        otp_field.send_keys(DEV_OTP)

        visible(driver, "button.btn-primary").click()

        WebDriverWait(driver, 15).until(EC.url_contains("/student/dashboard"))
        assert "/student/dashboard" in driver.current_url, \
            f"Expected /student/dashboard, got {driver.current_url}"
