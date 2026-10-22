import { expect, test } from '@playwright/test';
test('four-tab preview is honest, navigable and fits desktop widths', async ({ page }) => {
  await page.goto('/');
  await expect(
    page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button'),
  ).toHaveCount(4);
  await expect(page.getByRole('heading', { name: 'A little time for you.' })).toBeVisible();
  await page.getByRole('button', { name: 'Library', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'My Library.' })).toBeVisible();
  await expect(page.getByText('Browser preview · read-only')).toBeVisible();
  await expect(page.getByText('Your shelf starts here')).toBeVisible();
  await page.getByRole('button', { name: 'Home', exact: true }).click();
  await page.getByRole('button', { name: /Let the words come to you/ }).click();
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
  await page.getByRole('button', { name: 'Import your first book' }).click();
  await expect(page.getByRole('alert')).toContainText('Open Vern in its desktop window');
});
