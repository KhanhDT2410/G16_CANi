import { useState, useEffect, useCallback } from 'react';
import { apiClient } from '../../../services/api';
import { showToast } from '../../../components/common/Toast';

const AdminSkillsTab = ({ onCountChange }) => {
  const [skillQueue, setSkillQueue] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadSkillQueue = useCallback(async () => {
    setLoading(true);
    try {
      const response = await apiClient.get('/v1/workers/skills/admin/queue');
      const data = response.data?.data || [];
      setSkillQueue(data);
      if (onCountChange) onCountChange(data.length);
    } catch (err) {
      setSkillQueue([]);
      if (onCountChange) onCountChange(0);
      showToast(err.response?.data?.message || 'Không thể tải hàng đợi kỹ năng từ máy chủ', 'error');
    } finally {
      setLoading(false);
    }
  }, [onCountChange]);

  useEffect(() => {
    loadSkillQueue();
  }, [loadSkillQueue]);

  const handleSkillReview = async (id, approved) => {
    const notes = approved ? 'Chứng chỉ hợp lệ, đã đối soát mắt.' : 'Chứng chỉ mờ hoặc không trùng khớp.';
    try {
      await apiClient.post(`/v1/workers/skills/admin/${id}/review`, null, {
        params: { approved, reviewNotes: notes },
      });

      setSkillQueue((prev) => {
        const next = prev.filter((item) => item.id !== id);
        if (onCountChange) onCountChange(next.length);
        return next;
      });

      showToast(
        approved
          ? 'Đã duyệt kỹ năng thành công! Worker được cộng +25 SP_verify.'
          : 'Đã từ chối yêu cầu xác thực kỹ năng.',
        approved ? 'success' : 'warning'
      );
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể xử lý yêu cầu thẩm định kỹ năng.', 'error');
    }
  };

  if (loading) {
    return (
      <div className="admin-card-list">
        <div style={{ padding: '1rem 0' }}>
          <div className="skeleton" style={{ height: '320px', borderRadius: '14px', marginBottom: '1rem' }} />
          <div className="skeleton" style={{ height: '320px', borderRadius: '14px' }} />
        </div>
      </div>
    );
  }

  if (skillQueue.length === 0) {
    return (
      <div className="admin-card-list">
        <div style={{ textAlign: 'center', padding: '3rem', background: '#fff', borderRadius: '0.75rem' }}>
          <p style={{ color: '#059669', fontWeight: 'bold' }}>
            Hiện tại không còn hồ sơ chứng chỉ kỹ năng nào đang chờ duyệt.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-card-list">
      {skillQueue.map((item) => (
        <div key={item.id} className="admin-verif-card animate-fade-in-up">
          <div className="admin-card-top">
            <div>
              <h3 style={{ margin: '0 0 0.25rem 0', fontSize: '1.2rem', fontWeight: 700 }}>
                {item.workerName || 'Nhân sự Worker'}
              </h3>
              <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>
                Kỹ năng thẩm định: <strong style={{ color: '#2563eb' }}>{item.skillName || item.jobType}</strong> | Cấp bởi: {item.institution || 'Cơ sở đào tạo'} ({item.issueDate || 'N/A'})
              </span>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#f59e0b' }}>
                Độ tin cậy OCR: {item.overallConfidence ? (item.overallConfidence > 1 ? item.overallConfidence : (item.overallConfidence * 100).toFixed(0)) : 0}%
              </div>
              <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>
                Tier 2: AI Document Review
              </div>
            </div>
          </div>

          <div style={{ background: '#fef3c7', padding: '0.75rem 1rem', borderRadius: '0.5rem', fontSize: '0.85rem', color: '#92400e', marginBottom: '1rem' }}>
            <strong>Lý do AI chuyển duyệt:</strong> {item.routingReason || 'Tài liệu cần Admin/CSKH đối soát thủ công.'}
          </div>

          <div style={{ display: 'flex', gap: '1.25rem', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
            <div style={{ flex: '0 0 320px', height: '200px', borderRadius: '0.5rem', overflow: 'hidden', border: '1px solid #e5e7eb', background: '#f3f4f6' }}>
              {item.documentUrl ? (
                <img
                  src={item.documentUrl}
                  alt="Chứng chỉ kỹ năng"
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
              ) : (
                <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#9ca3af' }}>
                  Không có hình ảnh đính kèm
                </div>
              )}
            </div>
            <div style={{ flex: 1, minWidth: '250px', fontSize: '0.9rem', color: '#374151', lineHeight: '1.6' }}>
              <p style={{ margin: '0 0 0.5rem 0' }}><strong>Đơn vị cấp bằng:</strong> {item.institution || 'Chưa xác định'}</p>
              <p style={{ margin: '0 0 0.5rem 0' }}><strong>Ngày phát hành:</strong> {item.issueDate || 'N/A'}</p>
              <p style={{ margin: '0 0 0.5rem 0' }}><strong>Dấu mộc / Chữ ký:</strong> {item.hasSealOrSignature ? 'Có xác nhận hợp lệ' : 'Chưa nhận diện được'}</p>
              <div style={{ marginTop: '0.75rem', padding: '0.5rem 0.75rem', background: '#ecfdf5', borderRadius: '0.375rem', color: '#065f46', fontSize: '0.85rem', fontWeight: 600 }}>
                Điểm cộng khi duyệt: +25 SP_verify (Kích hoạt Tier 2 AI-Verified)
              </div>
            </div>
          </div>

          <div className="admin-action-row">
            <button
              className="btn-admin-reject"
              onClick={() => handleSkillReview(item.id, false)}
            >
              Từ chối
            </button>
            <button
              className="btn-admin-approve"
              onClick={() => handleSkillReview(item.id, true)}
            >
              Phê duyệt (+25 SP)
            </button>
          </div>
        </div>
      ))}
    </div>
  );
};

export default AdminSkillsTab;
