import { chromium } from 'playwright';
import path from 'path';

const BASE_URL = 'http://localhost:4174';
const PIC_DIR = path.resolve('doc/picture/readme');

async function shot(page, p, filename) {
    try {
        console.log(`Navigating to ${p}...`);
        await page.goto(`${BASE_URL}${p}`, { waitUntil: 'networkidle', timeout: 30000 });
        await page.screenshot({ path: path.join(PIC_DIR, filename), fullPage: true });
        console.log(`Saved: ${filename}`);
    } catch(e) {
        console.log(`Failed to screenshot ${p}:`, e.message);
    }
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
    await page.waitForLoadState('networkidle');
    
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
    await page.click('.form-actions button');
    await page.waitForLoadState('networkidle');

    await shot(page, '/teacher/knowledge', 'teacher-knowledge.png');
    await shot(page, '/teacher/plans', 'teacher-plans.png');
    await shot(page, '/teacher/analytics', 'teacher-analytics.png');
    
    // Teacher suggestions and Sandbox
    await shot(page, '/teacher/suggestions', 'teacher-suggestions.png');
    console.log("Triggering Sandbox in teacher suggestions...");
    try {
      await page.waitForSelector('button:has-text("一键生成干预沙盘推演")', { timeout: 3000 });
      await page.click('button:has-text("一键生成干预沙盘推演")');
      await page.waitForSelector('.int-topic', { timeout: 60000 });
      await page.screenshot({ path: path.join(PIC_DIR, 'teacher-suggestions-sandbox.png'), fullPage: true });
      console.log(`Saved: teacher-suggestions-sandbox.png`);
    } catch(e) { console.log('Sandbox btn not found or failed', e); }

    // Logout
    await page.evaluate(() => localStorage.clear());

    // 4. Student login
    await page.goto(`${BASE_URL}/login`);
    await page.waitForSelector('input[name="username"]');
    await page.fill('input[name="username"]', 'student01');
    await page.fill('input[name="password"]', '12345678');
    await page.click('.form-actions button');
    await page.waitForLoadState('networkidle');

    await shot(page, '/student/chat', 'student-chat.png');
    await shot(page, '/student/exercise', 'student-exercise.png');
    await shot(page, '/student/exercise/records', 'student-records.png');
    
    // Student wrong book and Socratic Probe
    await shot(page, '/student/wrong-book', 'student-wrong-book.png');
    console.log("Triggering Socratic Probe in wrong book...");
    try {
      await page.waitForSelector('button:has-text("苏格拉底追问")', { timeout: 3000 });
      await page.click('button:has-text("苏格拉底追问")');
      await page.waitForSelector('.socratic-modal', { timeout: 30000 });
      await page.screenshot({ path: path.join(PIC_DIR, 'student-wrong-book-socratic.png'), fullPage: true });
      console.log(`Saved: student-wrong-book-socratic.png`);
    } catch(e) { console.log('Socratic btn not found or failed', e); }

    await shot(page, '/student/ai-questions', 'student-ai-questions.png');
    
    // Profile features the Knowledge Topology
    await shot(page, '/student/profile', 'student-profile.png');

  } catch (err) {
    console.error(err);
  } finally {
    await browser.close();
    console.log('All screenshots completed.');
  }
})();
