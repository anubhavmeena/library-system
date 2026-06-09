import sys, io, time
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager

opts = Options()
opts.add_argument("--window-size=1400,900")
opts.binary_location = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()), options=opts)
try:
    driver.get("http://localhost:3000/login")
    WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input")))
    driver.find_element(By.CSS_SELECTOR, "input").send_keys("9000000002")
    driver.find_element(By.XPATH, "//button[contains(text(),'Send OTP')]").click()
    WebDriverWait(driver, 10).until(
        EC.visibility_of_element_located((By.CSS_SELECTOR, "input[maxlength='6']"))
    ).send_keys("123456")
    driver.find_element(By.XPATH, "//button[contains(text(),'Sign In')]").click()
    WebDriverWait(driver, 15).until(lambda d: "/student/dashboard" in d.current_url)
    print("Logged in")

    driver.get("http://localhost:3000/student/profile")
    time.sleep(3)
    driver.save_screenshot(r"C:\Users\anubh\ccode-library-system\screenshots\profile_photo_fixed.png")
    print("Screenshot saved")

    imgs = driver.find_elements(By.CSS_SELECTOR, 'img[alt="Profile"]')
    if imgs:
        w = driver.execute_script("return arguments[0].naturalWidth", imgs[0])
        h = driver.execute_script("return arguments[0].naturalHeight", imgs[0])
        print(f"Profile img naturalWidth={w} naturalHeight={h}")
        assert w > 0 and h > 0, f"Image broken (naturalWidth={w})"
        print("PASS: photo displayed correctly")
    else:
        print("No img[alt=Profile] — showing initials fallback")
        sys.exit(1)
finally:
    driver.quit()
