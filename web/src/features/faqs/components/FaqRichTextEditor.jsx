import { useRef, useEffect } from 'react';

/**
 * Trình soạn thảo Rich Text Editor với thanh công cụ định dạng (Paragraph, B, I, Link, Lists, Table, Quote, Image, Undo/Redo)
 * Chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqRichTextEditor = ({ value, onChange, placeholder, disabled }) => {
  const editorRef = useRef(null);

  useEffect(() => {
    if (editorRef.current && editorRef.current.innerHTML !== (value || '')) {
      editorRef.current.innerHTML = value || '';
    }
  }, [value]);

  const handleInput = () => {
    if (editorRef.current && onChange) {
      onChange(editorRef.current.innerHTML);
    }
  };

  const exec = (command, val = null) => {
    if (disabled) return;
    document.execCommand(command, false, val);
    handleInput();
  };

  const handleInsertLink = () => {
    if (disabled) return;
    const url = window.prompt('Nhập đường dẫn URL liên kết:');
    if (url) exec('createLink', url);
  };

  const handleInsertImage = () => {
    if (disabled) return;
    const url = window.prompt('Nhập đường dẫn hình ảnh (URL):');
    if (url) exec('insertImage', url);
  };

  const handleInsertTable = () => {
    if (disabled) return;
    const tableHtml = `
      <table style="width:100%; border-collapse:collapse; margin:12px 0;" border="1">
        <thead><tr><th style="padding:8px; border:1px solid #cbd5e1; background:#f8fafc;">Tiêu đề 1</th><th style="padding:8px; border:1px solid #cbd5e1; background:#f8fafc;">Tiêu đề 2</th></tr></thead>
        <tbody><tr><td style="padding:8px; border:1px solid #cbd5e1;">Nội dung 1</td><td style="padding:8px; border:1px solid #cbd5e1;">Nội dung 2</td></tr></tbody>
      </table><p><br></p>
    `;
    exec('insertHTML', tableHtml);
  };

  const handleBlockquote = () => {
    if (disabled) return;
    exec('formatBlock', 'blockquote');
  };

  return (
    <div className={`faq-rich-editor ${disabled ? 'is-disabled' : ''}`}>
      {/* Thanh công cụ WYSIWYG */}
      <div className="faq-editor-toolbar" role="toolbar">
        <select
          className="faq-toolbar-select"
          onChange={(e) => exec('formatBlock', e.target.value)}
          defaultValue="p"
          disabled={disabled}
        >
          <option value="p">Paragraph</option>
          <option value="h2">Heading 2</option>
          <option value="h3">Heading 3</option>
          <option value="h4">Heading 4</option>
        </select>

        <span className="faq-toolbar-divider" />

        <button type="button" title="In đậm (Bold)" onClick={() => exec('bold')} disabled={disabled}>
          <strong>B</strong>
        </button>
        <button type="button" title="In nghiêng (Italic)" onClick={() => exec('italic')} disabled={disabled}>
          <em>I</em>
        </button>
        <button type="button" title="Chèn liên kết (Link)" onClick={handleInsertLink} disabled={disabled}>
          🔗
        </button>
        <button type="button" title="Danh sách chấm (Bullet list)" onClick={() => exec('insertUnorderedList')} disabled={disabled}>
          •≡
        </button>
        <button type="button" title="Danh sách số (Numbered list)" onClick={() => exec('insertOrderedList')} disabled={disabled}>
          1≡
        </button>
        <button type="button" title="Chèn bảng (Table)" onClick={handleInsertTable} disabled={disabled}>
          ▦
        </button>
        <button type="button" title="Trích dẫn (Quote)" onClick={handleBlockquote} disabled={disabled}>
          ❝
        </button>
        <button type="button" title="Chèn ảnh (Image)" onClick={handleInsertImage} disabled={disabled}>
          🖼
        </button>

        <span className="faq-toolbar-divider" />

        <button type="button" title="Hoàn tác (Undo)" onClick={() => exec('undo')} disabled={disabled}>
          ↺
        </button>
        <button type="button" title="Làm lại (Redo)" onClick={() => exec('redo')} disabled={disabled}>
          ↻
        </button>
      </div>

      {/* Vùng soạn thảo nội dung */}
      <div
        ref={editorRef}
        className="faq-editor-content"
        contentEditable={!disabled}
        onInput={handleInput}
        data-placeholder={placeholder || 'Nhập nội dung chi tiết bài viết...'}
      />
    </div>
  );
};

export default FaqRichTextEditor;

