import { resolveAssetUrl, isImageUrl, getFileExtension, getFileName } from '../utils/resolveAssetUrl';

export const TicketAttachmentsView = ({ attachments }) => {
  if (!attachments || !attachments.length) return null;

  return (
    <div className="ticket-attachments-view-section">
      <div className="ticket-attachments-view-title">
        Tệp & Hình Ảnh Đính Kèm ({attachments.length})
      </div>
      <div className="ticket-attachments-view-grid">
        {attachments.map((rawUrl, idx) => {
          const fileUrl = typeof rawUrl === 'string' ? rawUrl : rawUrl?.url;
          if (!fileUrl) return null;
          const fileName = (typeof rawUrl === 'object' && rawUrl.name) ? rawUrl.name : getFileName(fileUrl);
          const isImg = isImageUrl(fileUrl);
          const ext = getFileExtension(fileUrl);
          const fullHref = resolveAssetUrl(fileUrl);

          return (
            <div key={idx} className="ticket-attachment-view-card">
              {isImg ? (
                <div className="ticket-attachment-img-wrapper">
                  <a href={fullHref} target="_blank" rel="noopener noreferrer" title="Bấm để xem ảnh kích thước gốc">
                    <img
                      src={fullHref}
                      alt={fileName}
                      className="ticket-attachment-view-thumb"
                      loading="lazy"
                    />
                  </a>
                </div>
              ) : (
                <div className="ticket-attachment-doc-wrapper">
                  <span className="ticket-attachment-doc-badge">{ext}</span>
                </div>
              )}
              <div className="ticket-attachment-view-info">
                <span className="ticket-attachment-view-name" title={fileName}>
                  {fileName}
                </span>
                <a
                  href={fullHref}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="ticket-attachment-view-link"
                >
                  {isImg ? 'Xem ảnh' : 'Tải về / Xem tệp'}
                </a>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default TicketAttachmentsView;
