import { expect, test } from '@playwright/test';
import { writeFile } from 'node:fs/promises';
test('four-tab preview is honest, navigable and fits desktop widths', async ({ page }) => {
  await page.goto('/');
  await expect(
    page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button'),
  ).toHaveCount(4);
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible();
  const snapshot = await page.evaluate(() => {
    const html = document.documentElement.cloneNode(true) as HTMLElement;
    html.querySelectorAll('script, style, link').forEach((node) => node.remove());
    html.querySelectorAll('[hidden], [style]').forEach((node) => {
      if (node.hasAttribute('hidden') || (node as HTMLElement).style.display === 'none')
        node.remove();
    });
    html.querySelectorAll('img[src]').forEach((node) => {
      const src = node.getAttribute('src')!;
      if (src.startsWith('/') && !src.startsWith('//')) node.setAttribute('src', '/public' + src);
    });
    const css = document.createElement('style');
    css.textContent = Array.from(document.styleSheets)
      .flatMap((sheet) => Array.from(sheet.cssRules, (rule) => rule.cssText))
      .join('\n')
      .replaceAll('/fonts/', '/public/fonts/');
    html.querySelector('head')!.append(css);
    return '<!doctype html>\n' + html.outerHTML;
  });
  await writeFile('.artifacts/msi-validation-20261102/concise-home-live.html', snapshot);
  for (const width of [1440, 900, 390]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    await page.screenshot({
      path: `.artifacts/msi-validation-20261102/concise-home-${width}.png`,
      fullPage: true,
    });
  }
  await page.setViewportSize({ width: 1365, height: 767 });
  await page.getByRole('button', { name: 'Library', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Library.' })).toBeVisible();
  await expect(page.getByText('Browser preview · read-only')).toBeVisible();
  await expect(page.getByText('Your shelf starts here')).toBeVisible();
  await page.getByRole('button', { name: 'Home', exact: true }).click();
  await page.getByRole('button', { name: 'Listening queue', exact: true }).click();
  await page.getByRole('button', { name: 'Voice Studio', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Heart', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Download Studio' })).toBeDisabled();
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await expect(page.getByLabel('Reader typeface')).toHaveValue('Bitter');
  await page.getByRole('button', { name: 'Library', exact: true }).click();
  await page.screenshot({ path: '.artifacts/library-preview.png', fullPage: true });
  await page.setViewportSize({ width: 900, height: 650 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(900);
});
test('browser cannot claim native imports worked', async ({ page }) => {
  await page.goto('/');
  await page
    .locator('.home-hero')
    .getByRole('button', { name: 'Import book', exact: true })
    .click();
  await expect(page.getByRole('alert')).toContainText('Open Vern in its desktop window');
});
