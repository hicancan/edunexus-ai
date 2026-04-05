import { chromium } from 'playwright';
import path from 'path';

const BASE_URL = 'http://127.0.0.1:4174';
const PIC_DIR = path.resolve('doc/picture/readme');

async function shot(page, p, filename) {
    console.log(`Navigating to ${p}...`);
    await page.goto(`${BASE_URL}${p}`);
    await page.waitForTimeout(3000); // Wait for ECharts and requests to settle
    await page.screenshot({ path: path.join(PIC_DIR, filename), fullPage: true });
    console.log(`Saved: ${filename}`);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  try {
    // 1. Unauthenticated screens
    await shot(page, '/login', 'login.png');
    await shot(page, '/register', 'register.png');
    
    // We can't directly hit 403 or 404 sometimes if they are soft routes, but let's try
    await shot(page, '/403', 'forbidden.png');
    await shot(page, '/404', 'not-found.png');

    // 2. Admin login
    await page.goto(`${BASE_URL}/login`);
    await page.waitForSelector('input[name="username"]');
    await page.fill('input[name="username"]', 'admin');
    await page.fill('input[name="password"]', '12345678');
    await page.click('.form-actions button');
    await page.waitForTimeout(3000);
    
    await shot(page, '/admin/dashboard', 'admin-dashboard.png');
    await shot(page, '/admin/users', 'admin-users.png');
    await shot(page, '/admin/resources', 'admin-resources.png');
    await shot(page, '/admin/audits', 'admin-audits.png');

    // Logout
    await page.evaluate(() => localStorage.clear());
    
    // 3. Teacher login
    await page.goto(`${BASE_URL}/login`);
    await page.waitForSelector('input[name="username"]');
    await page.fill('input[name="username"]', 'teacher01');
    await page.fill('input[name="password"]', '12345678');
    await page.click('button[attr-type="submit"]');
    await page.waitForTimeout(3000);

    await shot(page, '/teacher/knowledge', 'teacher-knowledge.png');
    await shot(page, '/teacher/plans', 'teacher-plans.png');
    await shot(page, '/teacher/analytics', 'teacher-analytics.png');
    await shot(page, '/teacher/suggestions', 'teacher-suggestions.png');

    // Logout
    await page.evaluate(() => localStorage.clear());

    // 4. Student login
    await page.goto(`${BASE_URL}/login`);
    await page.waitForSelector('input[name="username"]');
    await page.fill('input[name="username"]', 'student01');
    await page.fill('input[name="password"]', '12345678');
    await page.click('button[attr-type="submit"]');
    await page.waitForTimeout(3000);

    await shot(page, '/student/chat', 'student-chat.png');
    await shot(page, '/student/exercise', 'student-exercise.png');
    await shot(page, '/student/exercise/records', 'student-records.png');
    await shot(page, '/student/wrong-book', 'student-wrong-book.png');
    await shot(page, '/student/ai-questions', 'student-ai-questions.png');
    await shot(page, '/student/profile', 'student-profile.png');

  } catch (err) {
    console.error(err);
  } finally {
    await browser.close();
    console.log('All screenshots completed.');
  }
})();
