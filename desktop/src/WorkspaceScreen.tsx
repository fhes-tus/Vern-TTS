import { Activity, type ReactNode } from 'react';
import type { Page } from './navigation';

export function WorkspaceScreen({
  page,
  current,
  visited,
  children,
}: {
  page: Page;
  current: Page;
  visited: Page[];
  children: ReactNode;
}) {
  if (!visited.includes(page)) return null;
  return <Activity mode={current === page ? 'visible' : 'hidden'}>{children}</Activity>;
}
