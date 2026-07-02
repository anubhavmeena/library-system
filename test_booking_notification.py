#!/usr/bin/env python3
"""
Visual Selenium test: Create a new student, book a seat via admin cash
membership, and verify that booking notifications are sent to the
student and the admin.

Credentials are live (Meta WhatsApp + SendGrid configured), so the test
verifies that the notification attempt was made — log lines for each
channel confirm that the service called the external API.

OTP Strategy:
  1. Click "Send OTP" → backend sends real OTP to phone via Meta WhatsApp
     AND stores a random OTP in Redis.
  2. Immediately overwrite Redis key `otp:{contact}` with "123456"
     using redis-py (before the user enters the OTP in the browser).
  3. Enter "123456" → backend checks Redis → finds "123456" → success.

Requirements (all installed):
  selenium==4.45.0, webdriver-manager, requests, redis
"""

import os
import sys
import time
import signal
import subprocess
import requests
import redis as redis_lib
from pathlib import Path

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.firefox.options import Options as FirefoxOptions
from selenium.webdriver.firefox.service import Service as FirefoxService
from webdriver_manager.firefox import GeckoDriverManager

# ── Configuration ─────────────────────────────────────────────────────────────

FRONTEND_URL   = "http://localhost:3000"
BACKEND_URL    = "http://localhost:8080"
BACKEND_DIR    = Path(__file__).parent / "rust-backend"
BACKEND_BIN    = BACKEND_DIR / "target" / "release" / "library-backend"
BACKEND_ENV    = BACKEND_DIR / ".env"
LOG_FILE       = Path("/tmp/test_backend.log")
SCREENSHOTS    = Path("/tmp/booking_test_screenshots")

ADMIN_CONTACT  = "9071356842"
OTP            = "123456"

TEST_STUDENT_NAME  = "Selenium Test Student"
TEST_STUDENT_PHONE = "9988776655"

PASS_SYM = "\033[92m✓\033[0m"
FAIL_SYM = "\033[91m✗\033[0m"
INFO_SYM = "\033[94m→\033[0m"


def log(symbol, msg):
    print(f"  {symbol} {msg}")


def screenshot(driver, name):
    SCREENSHOTS.mkdir(exist_ok=True)
    path = SCREENSHOTS / f"{name}.png"
    driver.save_screenshot(str(path))
    log(INFO_SYM, f"Screenshot: {path.name}")


def wait_click(driver, by, value, timeout=20):
    el = WebDriverWait(driver, timeout).until(
        EC.element_to_be_clickable((by, value)))
    el.click()
    return el


def assert_notification(log_text, pattern, label):
    if pattern in log_text:
        log(PASS_SYM, f"Notification confirmed — {label}")
    else:
        log(FAIL_SYM, f"Notification MISSING — {label}")
        log(INFO_SYM, f"Pattern: '{pattern}'")
        sys.exit(1)


# ── Redis OTP injection ───────────────────────────────────────────────────────

def inject_otp(contact, value="123456", ttl=300):
    """Override the OTP stored in Redis with a known value."""
    r = redis_lib.Redis(host="localhost", port=6379, decode_responses=True)
    r.setex(f"otp:{contact}", ttl, value)
    r.delete(f"otp:cooldown:{contact}")
    log(INFO_SYM, f"OTP injected in Redis: otp:{contact} = {value}")


# ── Backend management ────────────────────────────────────────────────────────

def kill_backend():
    try:
        result = subprocess.run(
            ["pgrep", "-f", "library-backend"], capture_output=True, text=True)
        for pid in result.stdout.strip().split("\n"):
            pid = pid.strip()
            if pid:
                os.kill(int(pid), signal.SIGTERM)
        time.sleep(1)
    except Exception:
        pass


def load_env_file(path: Path) -> dict:
    env = {}
    if path.exists():
        for line in path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, _, v = line.partition("=")
                env[k.strip()] = v.strip()
    return env


def start_backend() -> tuple:
    log_fh = open(LOG_FILE, "w")
    env = os.environ.copy()
    for k, v in load_env_file(BACKEND_ENV).items():
        env.setdefault(k, v)
    env["RUST_LOG"] = "library_backend=info,tower_http=warn"

    proc = subprocess.Popen(
        [str(BACKEND_BIN)],
        stdout=log_fh, stderr=subprocess.STDOUT,
        env=env, cwd=str(BACKEND_DIR),
    )
    log(INFO_SYM, f"Backend PID {proc.pid} → log: {LOG_FILE}")
    return proc, log_fh


def wait_backend_ready(timeout=20):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            r = requests.get(f"{BACKEND_URL}/api/plans", timeout=2)
            if r.status_code == 200:
                log(PASS_SYM, "Backend ready")
                return
        except Exception:
            pass
        time.sleep(0.5)
    raise RuntimeError("Backend did not start in time")


# ── Admin token (via API) ─────────────────────────────────────────────────────

def get_admin_token_api() -> str:
    """
    Obtain admin JWT without the browser:
      1. Inject OTP into Redis with a known value.
      2. POST /api/auth/admin/login with that value.
    """
    inject_otp(ADMIN_CONTACT)
    r = requests.post(f"{BACKEND_URL}/api/auth/admin/login",
                      json={"contact": ADMIN_CONTACT, "otp": OTP})
    if r.status_code != 200:
        raise RuntimeError(f"admin/login failed: {r.status_code} {r.text}")
    token = r.json()["data"]["token"]
    log(PASS_SYM, f"Admin token via API (…{token[-8:]})")
    return token


# ── API helpers ───────────────────────────────────────────────────────────────

def get_first_plan(token: str) -> dict:
    r = requests.get(f"{BACKEND_URL}/api/plans",
                     headers={"Authorization": f"Bearer {token}"})
    plans = r.json()["data"]
    assert plans, "No plans found"
    return plans[0]


def delete_test_student(token: str, phone: str):
    headers = {"Authorization": f"Bearer {token}"}
    r = requests.get(f"{BACKEND_URL}/api/admin/students?page=0&size=200",
                     headers=headers)
    if r.status_code != 200:
        return
    students = r.json().get("data", {}).get("students", [])
    for s in students:
        if s.get("mobile") == phone:
            requests.delete(
                f"{BACKEND_URL}/api/admin/students/{s['id']}", headers=headers)
            log(INFO_SYM, f"Removed pre-existing test student ({phone})")


# ── Selenium visual test ──────────────────────────────────────────────────────

def run_selenium(admin_token: str, plan: dict):
    log(INFO_SYM, "Launching Firefox (non-headless)…")
    service = FirefoxService(GeckoDriverManager().install())
    driver = webdriver.Firefox(service=service, options=FirefoxOptions())
    driver.set_window_size(1400, 900)
    wait = WebDriverWait(driver, 20)

    try:
        # ── 1. Admin login via browser ─────────────────────────────────────────
        print("\n[1] Admin login via browser")
        driver.get(f"{FRONTEND_URL}/admin/login")
        time.sleep(1.5)
        screenshot(driver, "01_login_page")

        contact_input = wait.until(EC.presence_of_element_located(
            (By.CSS_SELECTOR, "input.input")))
        contact_input.clear()
        contact_input.send_keys(ADMIN_CONTACT)

        # Send OTP — backend will store a random OTP in Redis
        wait_click(driver, By.XPATH,
                   "//button[contains(text(),'Send OTP') or contains(text(),'Sending')]")
        time.sleep(2)  # wait for backend response

        # Overwrite the random OTP with "123456" BEFORE entering it
        inject_otp(ADMIN_CONTACT)
        screenshot(driver, "02_otp_input_visible")

        otp_field = wait.until(EC.presence_of_element_located(
            (By.CSS_SELECTOR, "input[maxlength='6']")))
        otp_field.clear()
        otp_field.send_keys(OTP)
        screenshot(driver, "03_otp_entered")

        wait_click(driver, By.XPATH,
                   "//button[contains(text(),'Login') or contains(text(),'Verify')]")
        time.sleep(2.5)
        screenshot(driver, "04_after_login")

        assert "/admin" in driver.current_url, \
            f"Login failed — URL: {driver.current_url}"
        log(PASS_SYM, f"Logged in — {driver.current_url}")

        # ── 2. Create test student via import form ─────────────────────────────
        print("\n[2] Create test student")
        driver.get(f"{FRONTEND_URL}/admin/import")
        time.sleep(1.5)
        screenshot(driver, "05_import_page")

        name_el = driver.find_element(By.XPATH,
            "//input[contains(@placeholder,'Rahul') or contains(@placeholder,'Name') "
            "or contains(@placeholder,'name')]")
        name_el.clear()
        name_el.send_keys(TEST_STUDENT_NAME)

        phone_el = driver.find_element(By.XPATH,
            "//input[contains(@placeholder,'10-digit') or contains(@placeholder,'mobile') "
            "or contains(@placeholder,'Phone') or contains(@placeholder,'phone')]")
        phone_el.clear()
        phone_el.send_keys(TEST_STUDENT_PHONE)
        screenshot(driver, "06_student_form")

        wait_click(driver, By.XPATH,
                   "//button[contains(text(),'Add Student') or contains(text(),'Adding')]")
        time.sleep(2)
        screenshot(driver, "07_student_created")
        log(PASS_SYM, f"Student '{TEST_STUDENT_NAME}' / {TEST_STUDENT_PHONE} created")

        # ── 3. Create cash membership (4-step wizard) ─────────────────────────
        print("\n[3] Create cash membership (4-step wizard)")
        driver.get(f"{FRONTEND_URL}/admin/memberships/new")
        time.sleep(2)
        screenshot(driver, "08_new_membership_step1")

        # Step 1 — search and select student
        search = wait.until(EC.presence_of_element_located(
            (By.XPATH,
             "//input[contains(@placeholder,'Search') or contains(@placeholder,'search')]")))
        search.clear()
        search.send_keys(TEST_STUDENT_NAME)
        time.sleep(1.5)
        screenshot(driver, "09_student_searched")

        student_btn = wait.until(EC.element_to_be_clickable(
            (By.XPATH,
             f"//button[.//p[contains(text(),'{TEST_STUDENT_NAME}')] "
             f"or contains(.,'{TEST_STUDENT_NAME}')]")))
        student_btn.click()
        time.sleep(0.8)

        # "New Booking" prompt (only if student already has a membership)
        try:
            nb = WebDriverWait(driver, 3).until(EC.element_to_be_clickable(
                (By.XPATH,
                 "//button[contains(text(),'New Booking') or contains(text(),'new booking')]")))
            nb.click()
            time.sleep(0.8)
        except Exception:
            pass
        screenshot(driver, "10_student_selected")
        log(PASS_SYM, "Student selected")

        # Step 2 — select plan
        plan_name = plan["name"]
        plan_type = plan.get("planType", "")
        plan_btn = wait.until(EC.element_to_be_clickable(
            (By.XPATH,
             f"//button[.//span[contains(text(),'{plan_name}')] "
             f"or .//p[contains(text(),'{plan_name}')] "
             f"or contains(.,'{plan_name}')]")))
        plan_btn.click()
        time.sleep(0.5)
        log(PASS_SYM, f"Plan: {plan_name}")

        # Shift (skip for FULL_DAY)
        if plan_type != "FULL_DAY":
            try:
                shift_btn = WebDriverWait(driver, 3).until(EC.element_to_be_clickable(
                    (By.XPATH,
                     "//button[contains(text(),'Morning') or @value='MORNING']")))
                shift_btn.click()
                time.sleep(0.5)
                log(PASS_SYM, "Shift: Morning")
            except Exception:
                pass
        screenshot(driver, "11_plan_shift")

        wait_click(driver, By.XPATH,
                   "//button[contains(text(),'Continue') or contains(text(),'Next')]")
        time.sleep(2)
        screenshot(driver, "12_seat_grid")

        # Step 3 — pick first available seat
        available = wait.until(EC.element_to_be_clickable(
            (By.XPATH,
             "//button[not(@disabled) and ("
             "contains(@class,'emerald') or contains(@class,'green'))]")))
        seat_label = (
            available.get_attribute("title") or available.text or "?"
        ).strip()
        available.click()
        time.sleep(0.5)
        screenshot(driver, "13_seat_selected")
        log(PASS_SYM, f"Seat: {seat_label}")

        wait_click(driver, By.XPATH,
                   "//button[contains(text(),'Continue') or contains(text(),'Next')]")
        time.sleep(1)
        screenshot(driver, "14_review_step")

        # Step 4 — check confirmation and submit
        checkbox = wait.until(EC.presence_of_element_located(
            (By.XPATH, "//input[@type='checkbox']")))
        if not checkbox.is_selected():
            driver.execute_script("arguments[0].click();", checkbox)
        time.sleep(0.3)
        screenshot(driver, "15_checkbox_checked")

        create_btn = wait.until(EC.element_to_be_clickable(
            (By.XPATH,
             "//button[contains(text(),'Create Membership') or contains(text(),'Creating')]")))
        create_btn.click()
        log(PASS_SYM, "Membership creation submitted")
        time.sleep(5)  # allow async tokio notification tasks to fire
        screenshot(driver, "16_submitted")

        # UI success check
        try:
            WebDriverWait(driver, 5).until(EC.presence_of_element_located(
                (By.XPATH,
                 "//*[contains(text(),'success') or contains(text(),'Success') "
                 "or contains(text(),'created') or contains(text(),'Created')]")))
            log(PASS_SYM, "Success message visible in UI")
        except Exception:
            log(INFO_SYM, "No explicit success toast observed — will verify logs")
        screenshot(driver, "17_final_ui")

    finally:
        time.sleep(3)
        driver.quit()


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    print("=" * 62)
    print("  Booking Notification Test")
    print("  Rust Backend · Visual Selenium · Firefox")
    print("=" * 62)
    SCREENSHOTS.mkdir(exist_ok=True)

    print("\n[SETUP] Restarting backend with fresh log capture…")
    kill_backend()
    backend_proc, log_fh = start_backend()
    wait_backend_ready()

    admin_token = None
    try:
        admin_token = get_admin_token_api()
        delete_test_student(admin_token, TEST_STUDENT_PHONE)

        plan = get_first_plan(admin_token)
        log(INFO_SYM, f"Plan: {plan['name']} ₹{plan['price']} ({plan['planType']})")

        run_selenium(admin_token, plan)

    finally:
        time.sleep(3)
        log_fh.flush()
        log_content = LOG_FILE.read_text()
        backend_proc.terminate()
        log_fh.close()

        # ── Verify notification log lines ──────────────────────────────────────
        print("\n[VERIFY] Checking backend logs for notifications…")

        # WhatsApp to student — Meta API attempted with student's phone
        assert_notification(log_content,
            TEST_STUDENT_PHONE,
            f"WhatsApp/notification to student ({TEST_STUDENT_PHONE})")

        # WhatsApp to admin
        assert_notification(log_content,
            ADMIN_CONTACT,
            f"Notification to admin ({ADMIN_CONTACT})")

        # Email to admin
        assert_notification(log_content,
            "admin@targetzone.co.in",
            "Email to admin (admin@targetzone.co.in)")

        # Student name appears in booking notification body
        assert_notification(log_content,
            TEST_STUDENT_NAME,
            "Student name in notification")

        # Cleanup
        if admin_token:
            delete_test_student(admin_token, TEST_STUDENT_PHONE)

        # Print notification-related lines for review
        notif_lines = [
            l for l in log_content.splitlines()
            if any(x in l for x in [
                "Meta WhatsApp", "SendGrid", "DEV WhatsApp", "DEV Email",
                "Twilio", "notification", TEST_STUDENT_PHONE, ADMIN_CONTACT,
                "booking", "Booking", TEST_STUDENT_NAME,
            ])
        ]
        print(f"\n── Notification log ({len(notif_lines)} lines) ──")
        for line in notif_lines[-50:]:
            print(f"  {line}")

    print("\n" + "=" * 62)
    print(f"  {PASS_SYM} All checks passed!")
    print(f"  Screenshots → {SCREENSHOTS}/")
    print("=" * 62)


if __name__ == "__main__":
    main()
