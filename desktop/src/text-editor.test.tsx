// @vitest-environment jsdom
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TextEditorDialog } from './TextEditorDialog';

describe('TextEditorDialog', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders with initial passage text and book title', () => {
    const handleSave = vi.fn().mockResolvedValue(undefined);
    const handleClose = vi.fn();

    render(
      <TextEditorDialog
        bookTitle="Alice in Wonderland"
        passageIndex={4}
        initialText="Down the rabbit-hole."
        onSave={handleSave}
        onClose={handleClose}
      />,
    );

    expect(screen.getByText('Alice in Wonderland · Passage 5')).toBeInTheDocument();
    expect(screen.getByText('Edit extracted text')).toBeInTheDocument();
    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    expect(textarea.value).toBe('Down the rabbit-hole.');
  });

  it('calls onSave with updated text on submit', async () => {
    const handleSave = vi.fn().mockResolvedValue(undefined);
    const handleClose = vi.fn();

    render(
      <TextEditorDialog
        bookTitle="Alice in Wonderland"
        passageIndex={0}
        initialText="Old typo here."
        onSave={handleSave}
        onClose={handleClose}
      />,
    );

    const textarea = screen.getByRole('textbox');
    fireEvent.change(textarea, { target: { value: 'Corrected text.' } });
    fireEvent.click(screen.getByRole('button', { name: /save passage/i }));

    await waitFor(() => {
      expect(handleSave).toHaveBeenCalledWith('Corrected text.');
    });
  });

  it('disables save button and shows error on Ctrl+Enter if text is empty', async () => {
    const handleSave = vi.fn().mockResolvedValue(undefined);
    const handleClose = vi.fn();

    render(
      <TextEditorDialog
        bookTitle="Alice in Wonderland"
        passageIndex={0}
        initialText="Old text"
        onSave={handleSave}
        onClose={handleClose}
      />,
    );

    const textarea = screen.getByRole('textbox');
    fireEvent.change(textarea, { target: { value: '   ' } });

    // The primary save button is disabled when empty
    const saveButton = screen.getByRole('button', { name: /save passage/i });
    expect(saveButton).toBeDisabled();

    // Triggering Ctrl+Enter directly executes handleSave validation
    fireEvent.keyDown(textarea, { key: 'Enter', ctrlKey: true });

    await waitFor(() => {
      expect(screen.getByText('Passage text cannot be empty.')).toBeInTheDocument();
    });
    expect(handleSave).not.toHaveBeenCalled();
  });
});
