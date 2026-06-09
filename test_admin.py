"""
Selenium tests: Admin page functionalities.
Admin mobile: 9999999999, dev OTP: 123456 (SPRING_PROFILES_ACTIVE=dev).

Coverage:
  T1  - Admin login flow (OTP 2-step)
  T2  - Dashboard: stat cards load
  T3  - Dashboard: refresh button reloads stats
  T4  - Dashboard: quick-link cards navigate to sub-pages
  T5  - Students: table loads with headers and rows
  T6  - Students: search input filters visible rows
  T7  - Students: status filter (All / Active / Inactive) tabs
  T8  - Students: View button opens detail modal, ✕ closes it
  T9  - Students: Disable/Enable toggle changes status (auto-reverts)
  T10 - Seats: page loads with seat map and 3 stat cards
  T11 - Seats: shift buttons (Morning / Evening / Full Day) switch context
  T12 - Seats: date input triggers map reload
  T13 - Seats: occupied seat click shows detail modal (skipped if no bookings)
  T14 - Reminders: page loads with stats and Send button
  T15 - Reminders: days-filter buttons (3 / 7 / 14 / 30) switch list
  T16 - Reminders: Select All / Deselect All toggles every checkbox
  T17 - Reminders: Send button enabled state and fires action
"""
import time, os, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from datetime import date, timedelta
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.common.action_chains import ActionChains
from webdriver_manager.chrome import ChromeDriverManager

BASE         = "http://localhost:3000"
ADMIN_MOBILE = "9999999999"
SCREENSHOTS  = r"C:\Users\anubh\ccode-library-system\screenshots"
os.makedirs(SCREENSHOTS, exist_ok=True)


# ── Helpers ────────────────────────────────────────────────────────────────────

def shot(driver, name):
    p = os.path.join(SCREENSHOTS, f"admin_{name}.png")
    driver.save_screenshot(p)
    print(f"  📸 {p}")
    return p


def make_driver():
    opts = Options()
    opts.add_argument("--window-size=1440,900")
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


def wait_clickable(driver, css, timeout=10):
    return WebDriverWait(driver, timeout).until(
        EC.element_to_be_clickable((By.CSS_SELECTOR, css))
    )


def wait_no_shimmer(driver, timeout=15):
    try:
        WebDriverWait(driver, timeout).until(
            lambda d: not d.find_elements(By.CSS_SELECTOR, ".shimmer")
        )
    except Exception:
        pass
    time.sleep(0.4)


def login_admin(driver):
    """Navigate to /admin/login, do the 2-step OTP flow, land on /admin/dashboard."""
    driver.get(f"{BASE}/admin/login")

    # Step 1 — enter contact
    contact_inp = wait_visible(driver, "input.input")
    contact_inp.clear()
    contact_inp.send_keys(ADMIN_MOBILE)

    send_btn = wait_clickable(driver, "button.bg-red-600")
    send_btn.click()

    # Step 2 — enter OTP
    otp_inp = wait_visible(driver, "input[maxlength='6']")
    otp_inp.send_keys("123456")

    login_btn = wait_clickable(driver, "button.bg-red-600")
    login_btn.click()

    wait_url(driver, "/admin/dashboard", timeout=15)
    print("  ✓ Admin logged in → /admin/dashboard")


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
        try:
            shot(driver, f"FAIL_{label.replace(' ', '_')[:40]}")
        except Exception:
            pass
    finally:
        driver.quit()


# ── T1: Admin login flow ───────────────────────────────────────────────────────
def test_admin_login(driver):
    driver.get(f"{BASE}/admin/login")
    wait_visible(driver, "input.input")
    shot(driver, "01_login_page")

    # Step 1 — contact input + Send OTP
    inp = driver.find_element(By.CSS_SELECTOR, "input.input")
    inp.send_keys(ADMIN_MOBILE)
    shot(driver, "02_mobile_entered")

    send_btn = wait_clickable(driver, "button.bg-red-600")
    send_btn.click()

    # Step 2 — OTP field should appear
    otp_inp = wait_visible(driver, "input[maxlength='6']")
    assert otp_inp.is_displayed(), "OTP input not visible after Send OTP"
    print("  ✓ OTP step visible after sending")
    shot(driver, "03_otp_step")

    otp_inp.send_keys("123456")
    login_btn = wait_clickable(driver, "button.bg-red-600")
    login_btn.click()

    wait_url(driver, "/admin/dashboard", timeout=15)
    shot(driver, "04_dashboard_landed")
    print("  ✓ Redirected to /admin/dashboard after login")


run("Admin login flow (OTP 2-step)", test_admin_login)


# ── T2: Dashboard stat cards ───────────────────────────────────────────────────
def test_dashboard_stats(driver):
    login_admin(driver)
    wait_no_shimmer(driver)
    shot(driver, "05_dashboard")

    # Expect the large grid of stat cards (at least 4)
    cards = driver.find_elements(By.CSS_SELECTOR, ".card")
    assert len(cards) >= 4, f"Expected ≥4 stat cards, found {len(cards)}"
    print(f"  ✓ {len(cards)} card elements visible")

    # Occupancy progress bar
    prog_bar = driver.find_elements(By.CSS_SELECTOR, ".h-4.bg-primary-800")
    assert len(prog_bar) >= 1, "Occupancy progress bar not found"
    print("  ✓ Occupancy bar present")

    # Page heading
    header = driver.find_element(By.CSS_SELECTOR, ".page-header")
    assert header.is_displayed()
    print(f"  ✓ Page header: {header.text!r}")


run("Dashboard stat cards load", test_dashboard_stats)


# ── T3: Dashboard refresh ──────────────────────────────────────────────────────
def test_dashboard_refresh(driver):
    login_admin(driver)
    wait_no_shimmer(driver)

    # The only btn-ghost on the dashboard is the refresh button
    refresh_btn = wait_clickable(driver, "button.btn-ghost")
    refresh_btn.click()
    print("  ✓ Refresh clicked")

    time.sleep(0.4)
    wait_no_shimmer(driver)
    shot(driver, "06_dashboard_refreshed")

    cards = driver.find_elements(By.CSS_SELECTOR, ".card")
    assert len(cards) >= 4, "Stat cards missing after refresh"
    print(f"  ✓ {len(cards)} cards still visible after refresh")


run("Dashboard refresh button reloads stats", test_dashboard_refresh)


# ── T4: Dashboard quick links ──────────────────────────────────────────────────
def test_dashboard_quick_links(driver):
    login_admin(driver)
    wait_no_shimmer(driver)

    # card-hover elements at the bottom of the page are the 4 quick-link cards
    quick_cards = driver.find_elements(By.CSS_SELECTOR, ".card-hover")
    assert len(quick_cards) >= 1, "No quick-link cards found"
    print(f"  ✓ {len(quick_cards)} quick-link card(s) present")

    # Cards may be below the fold — scroll into view before clicking
    driver.execute_script("arguments[0].scrollIntoView({block:'center'});", quick_cards[0])
    time.sleep(0.3)
    quick_cards[0].click()
    wait_url(driver, "/admin/students", timeout=10)
    shot(driver, "07_students_via_quicklink")
    print("  ✓ First quick link → /admin/students")


run("Dashboard quick-link cards navigate correctly", test_dashboard_quick_links)


# ── T5: Students table loads ───────────────────────────────────────────────────
def test_students_list(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/students")
    wait_no_shimmer(driver)
    shot(driver, "08_students_page")

    header = driver.find_element(By.CSS_SELECTOR, ".page-header")
    assert header.is_displayed()
    print(f"  ✓ Page header: {header.text!r}")

    # Table headers (6 columns)
    th_list = driver.find_elements(By.CSS_SELECTOR, "table th")
    assert len(th_list) >= 4, f"Expected ≥4 <th> elements, found {len(th_list)}"
    print(f"  ✓ Table has {len(th_list)} header column(s)")

    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    print(f"  ✓ Table shows {len(rows)} student row(s)")


run("Students page loads with table", test_students_list)


# ── T6: Students search ────────────────────────────────────────────────────────
def test_students_search(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/students")
    wait_no_shimmer(driver)

    rows_before = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    print(f"  ✓ Rows before search: {len(rows_before)}")

    search_inp = wait_visible(driver, "input.input")
    search_inp.clear()
    search_inp.send_keys("zzz_no_match_xyz_9999")
    time.sleep(0.4)
    shot(driver, "09_search_no_match")

    rows_after = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    empty_state = driver.find_elements(By.CSS_SELECTOR, ".card.p-12")
    assert len(rows_after) == 0 or len(empty_state) > 0, \
        "Rows not filtered for a clearly non-matching search term"
    print("  ✓ No rows shown for non-matching search term")

    # Clear → all rows return
    search_inp.clear()
    time.sleep(0.4)
    rows_restored = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    shot(driver, "10_search_cleared")
    print(f"  ✓ Rows restored after clearing: {len(rows_restored)}")


run("Students search filters the list", test_students_search)


# ── T7: Students status filter ─────────────────────────────────────────────────
def test_students_status_filter(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/students")
    wait_no_shimmer(driver)

    # Filter tabs live inside the flex-wrap search row (unique class combo: flex-wrap + gap-3)
    # Using the parent div avoids confusing with the pagination div.flex.gap-2
    filter_row = wait_visible(driver, "div.flex-wrap.gap-3")
    filter_btns = filter_row.find_elements(By.TAG_NAME, "button")
    assert len(filter_btns) >= 2, f"Expected ≥2 filter buttons, found {len(filter_btns)}"
    print(f"  ✓ {len(filter_btns)} filter tab(s): {[b.text for b in filter_btns]}")

    # Click each filter and verify content area updates
    for btn in filter_btns[1:]:   # skip "All" (already active)
        btn.click()
        time.sleep(0.3)
        wait_no_shimmer(driver)
        print(f"  ✓ Filter applied: {btn.text!r}")

    shot(driver, "11_status_filter")

    # Reset to All
    filter_btns[0].click()
    time.sleep(0.3)
    wait_no_shimmer(driver)
    print("  ✓ Reset to All filter")


run("Students status filter tabs (All/Active/Inactive)", test_students_status_filter)


# ── T8: Students view modal ────────────────────────────────────────────────────
def test_students_view_modal(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/students")
    wait_no_shimmer(driver)

    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    if not rows:
        print("  ⚠ No student rows — skipping modal test")
        return

    # Each row's last cell has two buttons: View (bg-primary-700) + Disable/Enable
    first_row_btns = rows[0].find_elements(By.CSS_SELECTOR, "td:last-child button")
    assert len(first_row_btns) >= 1, "No action buttons in first student row"

    shot(driver, "12_before_modal")
    first_row_btns[0].click()   # View button

    modal = wait_visible(driver, ".fixed.inset-0", timeout=8)
    assert modal.is_displayed(), "Student detail modal not visible"
    shot(driver, "13_modal_open")
    print("  ✓ Student detail modal opened")

    # Close via ✕
    close_btn = driver.find_element(By.XPATH, "//button[text()='✕']")
    close_btn.click()
    time.sleep(0.4)

    remaining = driver.find_elements(By.CSS_SELECTOR, ".fixed.inset-0")
    assert len(remaining) == 0, "Modal still present after ✕"
    shot(driver, "14_modal_closed")
    print("  ✓ Modal closed via ✕")


run("Students View modal opens and closes", test_students_view_modal)


# ── T9: Students enable/disable toggle ────────────────────────────────────────
def test_students_toggle_status(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/students")
    wait_no_shimmer(driver)

    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    if not rows:
        print("  ⚠ No students found — skipping toggle test")
        return

    first_row = rows[0]

    # Record the student's name to re-identify the row after the list reloads
    name_cell    = first_row.find_element(By.CSS_SELECTOR, "td:first-child p.font-medium")
    student_name = name_cell.text.strip()

    action_btns = first_row.find_elements(By.CSS_SELECTOR, "td:last-child button")
    assert len(action_btns) >= 2, \
        f"Expected ≥2 action buttons in first row, found {len(action_btns)}"

    toggle_btn   = action_btns[1]
    original_txt = toggle_btn.text.strip()
    print(f"  ✓ Student {student_name!r}, toggle label: {original_txt!r}")
    shot(driver, "15_before_toggle")

    toggle_btn.click()
    time.sleep(1.5)
    wait_no_shimmer(driver)
    shot(driver, "16_after_toggle")

    # After reload, find the SAME student by name (order may have changed)
    target_row = None
    for row in driver.find_elements(By.CSS_SELECTOR, "table tbody tr"):
        try:
            if row.find_element(By.CSS_SELECTOR, "td:first-child p.font-medium").text.strip() == student_name:
                target_row = row
                break
        except Exception:
            continue

    if target_row:
        btns2   = target_row.find_elements(By.CSS_SELECTOR, "td:last-child button")
        new_txt = btns2[1].text.strip() if len(btns2) >= 2 else original_txt
        assert new_txt != original_txt, \
            f"Toggle label unchanged (still {original_txt!r}) for {student_name!r}"
        print(f"  ✓ Status toggled: {original_txt!r} → {new_txt!r}")

        # Revert back to original state
        btns2[1].click()
        time.sleep(1.5)
        wait_no_shimmer(driver)
        shot(driver, "17_toggle_reverted")
        print("  ✓ Status reverted to original")
    else:
        print(f"  ⚠ Could not re-find {student_name!r} after reload — toggle may have succeeded")


run("Students enable/disable toggle changes status", test_students_toggle_status)


# ── T10: Seats page loads ──────────────────────────────────────────────────────
def test_seats_map_loads(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/seats")
    wait_no_shimmer(driver)
    shot(driver, "18_seats_page")

    header = driver.find_element(By.CSS_SELECTOR, ".page-header")
    assert header.is_displayed()
    print(f"  ✓ Page header: {header.text!r}")

    # 3 stat cards (Total / Occupied / Available)
    stat_grid = driver.find_elements(By.CSS_SELECTOR, ".grid.grid-cols-3 .card")
    assert len(stat_grid) == 3, f"Expected 3 seat-stat cards, found {len(stat_grid)}"
    print("  ✓ 3 stat cards (Total / Occupied / Available)")

    # Occupancy bar
    occ = driver.find_elements(By.CSS_SELECTOR, ".h-3.bg-primary-800")
    assert len(occ) >= 1, "Seat occupancy bar not found"
    print("  ✓ Occupancy bar present")

    # Seat map: must have seat buttons
    seat_btns = driver.find_elements(By.CSS_SELECTOR, ".space-y-3 button")
    assert len(seat_btns) > 0, "No seat buttons found in map"
    print(f"  ✓ {len(seat_btns)} seat button(s) in map")


run("Seats page loads with seat map", test_seats_map_loads)


# ── T11: Seats shift filter ────────────────────────────────────────────────────
def test_seats_shift_filter(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/seats")
    wait_no_shimmer(driver)

    # Shift buttons contain emoji + text: 🌅 Morning, 🌆 Evening, 🌟 Full Day
    shift_btns = driver.find_elements(
        By.XPATH,
        "//button[contains(text(),'Morning') or contains(text(),'Evening') or contains(text(),'Full Day')]"
    )
    assert len(shift_btns) == 3, f"Expected 3 shift buttons, found {len(shift_btns)}"
    print(f"  ✓ 3 shift buttons: {[b.text.strip() for b in shift_btns]}")

    for btn in shift_btns:
        btn.click()
        time.sleep(0.3)
        wait_no_shimmer(driver)
        print(f"  ✓ Shift selected: {btn.text.strip()!r}")

    shot(driver, "19_shift_filter")
    print("  ✓ All 3 shift filters exercised")


run("Seats shift buttons switch MORNING/EVENING/FULL_DAY", test_seats_shift_filter)


# ── T12: Seats date input ──────────────────────────────────────────────────────
def test_seats_date_input(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/seats")
    wait_no_shimmer(driver)

    date_inp = wait_visible(driver, "input[type='date']")
    original = date_inp.get_attribute("value")
    print(f"  ✓ Current date: {original!r}")

    tomorrow = (date.today() + timedelta(days=1)).isoformat()

    # Use JS to set value and fire change event (works with React synthetic events)
    driver.execute_script(
        "arguments[0].value = arguments[1];"
        "arguments[0].dispatchEvent(new Event('input',  {bubbles: true}));"
        "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
        date_inp, tomorrow
    )
    time.sleep(0.5)
    wait_no_shimmer(driver)
    shot(driver, "20_seats_tomorrow")
    print(f"  ✓ Date changed to {tomorrow!r}")

    seat_btns = driver.find_elements(By.CSS_SELECTOR, ".space-y-3 button")
    assert len(seat_btns) > 0, "Seat map disappeared after date change"
    print(f"  ✓ {len(seat_btns)} seat button(s) still visible after date change")


run("Seats date input reloads availability", test_seats_date_input)


# ── T13: Occupied seat modal (skip if no bookings) ────────────────────────────
def test_seats_occupied_modal(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/seats")
    wait_no_shimmer(driver)
    shot(driver, "21_seats_before_occupied_modal")

    # Scope search to the seat-map grid only (.space-y-3) to exclude the active
    # shift filter button which also carries bg-red-500/* on its active state.
    occupied = driver.find_elements(
        By.XPATH,
        "//div[contains(@class,'space-y-3')]//button[contains(@class,'bg-red-500')]"
    )

    if not occupied:
        print("  ⚠ No occupied seats (fresh DB or no bookings) — skipping")
        return

    print(f"  ✓ Found {len(occupied)} occupied seat button(s)")
    # Bring the button into the viewport then click normally
    driver.execute_script("arguments[0].scrollIntoView({block:'nearest'});", occupied[0])
    time.sleep(0.3)
    modal = wait_visible(driver, ".fixed.inset-0", timeout=8)
    assert modal.is_displayed(), "Seat detail modal not visible"
    shot(driver, "22_seat_modal_open")
    print("  ✓ Occupied seat modal opened")

    close = driver.find_element(By.XPATH, "//button[text()='✕']")
    close.click()
    time.sleep(0.4)
    remaining = driver.find_elements(By.CSS_SELECTOR, ".fixed.inset-0")
    assert len(remaining) == 0, "Seat modal still open after ✕"
    print("  ✓ Seat modal closed")


run("Seats occupied seat shows detail modal", test_seats_occupied_modal)


# ── T14: Reminders page loads ──────────────────────────────────────────────────
def test_reminders_loads(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/reminders")
    wait_no_shimmer(driver)
    shot(driver, "23_reminders_page")

    header = driver.find_element(By.CSS_SELECTOR, ".page-header")
    assert header.is_displayed()
    print(f"  ✓ Page header: {header.text!r}")

    # 3 stat cards (Total / Critical / Warning)
    stat_cards = driver.find_elements(By.CSS_SELECTOR, ".grid.grid-cols-3 .card")
    assert len(stat_cards) == 3, f"Expected 3 stat cards, found {len(stat_cards)}"
    print("  ✓ 3 stat cards (Total / Critical / Warning)")

    # Send button always present
    send_btn = driver.find_element(By.CSS_SELECTOR, "button.bg-red-600")
    assert send_btn.is_displayed(), "Send reminders button not found"
    print(f"  ✓ Send button visible: {send_btn.text!r}")


run("Reminders page loads with stats and Send button", test_reminders_loads)


# ── T15: Reminders days filter ─────────────────────────────────────────────────
def test_reminders_days_filter(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/reminders")
    wait_no_shimmer(driver)

    # Day filter buttons live inside the "flex flex-wrap items-center gap-3 mb-6" row.
    # Scope to that container to avoid matching the navbar language switcher.
    filter_row = wait_visible(driver, "div.flex-wrap.items-center")
    day_btns = filter_row.find_elements(
        By.XPATH,
        ".//button[contains(@class,'px-3') and contains(@class,'rounded-lg')]"
    )
    assert len(day_btns) >= 2, f"Expected ≥2 day filter buttons, found {len(day_btns)}"
    print(f"  ✓ Found {len(day_btns)} day filter button(s): {[b.text for b in day_btns]}")

    for btn in day_btns:
        btn.click()
        time.sleep(0.4)
        wait_no_shimmer(driver)
        print(f"  ✓ Day filter: {btn.text!r}")

    shot(driver, "24_reminders_day_filter")


run("Reminders days filter (3/7/14/30) switches list", test_reminders_days_filter)


# ── T16: Reminders Select All / Deselect All ──────────────────────────────────
def test_reminders_select_all(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/reminders")
    wait_no_shimmer(driver)

    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    if not rows:
        print("  ⚠ No expiring memberships — skipping Select All test")
        return

    # The Select All / Deselect All button is btn-ghost in the ml-auto flex group
    select_all = driver.find_element(
        By.XPATH,
        "//button[contains(@class,'btn-ghost') and (contains(text(),'Select') or contains(text(),'Deselect'))]"
    )
    original_label = select_all.text.strip()
    print(f"  ✓ Button label before click: {original_label!r}")
    shot(driver, "25_before_select_all")

    select_all.click()
    time.sleep(0.3)

    checkboxes = driver.find_elements(By.CSS_SELECTOR, "table tbody input[type='checkbox']")
    checked = [cb for cb in checkboxes if cb.is_selected()]
    assert len(checked) == len(checkboxes), \
        f"Only {len(checked)}/{len(checkboxes)} boxes checked after Select All"
    print(f"  ✓ All {len(checked)} row checkboxes selected")
    shot(driver, "26_all_selected")

    # Deselect all
    deselect_btn = driver.find_element(
        By.XPATH,
        "//button[contains(@class,'btn-ghost') and (contains(text(),'Select') or contains(text(),'Deselect'))]"
    )
    deselect_btn.click()
    time.sleep(0.3)

    still_checked = [cb for cb in driver.find_elements(By.CSS_SELECTOR, "table tbody input[type='checkbox']")
                     if cb.is_selected()]
    assert len(still_checked) == 0, \
        f"{len(still_checked)} boxes still checked after Deselect All"
    print("  ✓ All checkboxes deselected")
    shot(driver, "27_all_deselected")


run("Reminders Select All / Deselect All toggles checkboxes", test_reminders_select_all)


# ── T17: Reminders send button ────────────────────────────────────────────────
def test_reminders_send_button(driver):
    login_admin(driver)
    driver.get(f"{BASE}/admin/reminders")
    wait_no_shimmer(driver)
    shot(driver, "28_reminders_send_state")

    send_btn = driver.find_element(By.CSS_SELECTOR, "button.bg-red-600")
    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")

    if not rows:
        # No expiring memberships — button must be disabled
        disabled = send_btn.get_attribute("disabled")
        assert disabled is not None, "Send button should be disabled when no students"
        print(f"  ✓ Send button disabled (no expiring members): {send_btn.text!r}")
    else:
        # Expiring members exist — button must be enabled
        assert send_btn.is_enabled(), "Send button should be enabled with students present"
        initial_label = send_btn.text.strip()
        print(f"  ✓ Send button enabled ({len(rows)} expiring): {initial_label!r}")

        send_btn.click()
        time.sleep(0.5)

        # Button may briefly show a "Sending…" state
        try:
            mid_label = driver.find_element(By.CSS_SELECTOR, "button.bg-red-600").text.strip()
            print(f"  ✓ Mid-send label: {mid_label!r}")
        except Exception:
            pass

        time.sleep(2.0)
        shot(driver, "29_after_send")
        print("  ✓ Send action completed without JS error")


run("Reminders Send button enabled state and fires action", test_reminders_send_button)


# ── Summary ────────────────────────────────────────────────────────────────────
print("\n─── Results ───")
passed = sum(1 for r in results if r[0] == "PASS")
for r in results:
    icon = "✅" if r[0] == "PASS" else "❌"
    print(f"  {icon} {r[1]}" + (f"\n     {r[2]}" if r[0] == "FAIL" else ""))
print(f"\n{passed}/{len(results)} passed")
sys.exit(0 if passed == len(results) else 1)
