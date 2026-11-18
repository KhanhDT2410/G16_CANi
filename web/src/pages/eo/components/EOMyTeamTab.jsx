import React, { useState, useEffect } from 'react';
import { myTeamService } from '../../../services/myTeamService';
import { blockListService } from '../../../services/blockListService';
import { showToast } from '../../../components/common/Toast';

export default function EOMyTeamTab() {
  const [team, setTeam] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [favoritedOnly, setFavoritedOnly] = useState(false);

  // Modal thêm nhân sự mới
  const [showAddModal, setShowAddModal] = useState(false);
  const [addForm, setAddForm] = useState({ phone: '', internalNote: '', isFavorited: true });
  const [submittingAdd, setSubmittingAdd] = useState(false);

  // State chỉnh sửa ghi chú inline
  const [editingNoteId, setEditingNoteId] = useState(null);
  const [tempNote, setTempNote] = useState('');

  const fetchTeam = async () => {
    setLoading(true);
    try {
      const data = await myTeamService.getMyTeam(search, favoritedOnly);
      setTeam(Array.isArray(data) ? data : []);
    } catch (err) {
      console.warn('Lỗi tải danh sách My Team:', err);
      setTeam([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTeam();
  }, [favoritedOnly]);

  const handleSearchSubmit = (e) => {
    e?.preventDefault();
    fetchTeam();
  };

  const handleToggleFavorite = async (worker) => {
    const nextState = !worker.isFavorited;
    try {
      await myTeamService.updateMyTeamMember(worker.workerId, {
        isFavorited: nextState,
        internalNote: worker.internalNote,
      });
      setTeam((prev) =>
        prev.map((w) => (w.workerId === worker.workerId ? { ...w, isFavorited: nextState } : w))
      );
      showToast(nextState ? `Đã thêm ${worker.fullName} vào danh sách yêu thích!` : `Đã bỏ yêu thích ${worker.fullName}`, 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi cập nhật trạng thái yêu thích', 'error');
    }
  };

  const handleSaveNote = async (workerId) => {
    try {
      await myTeamService.updateMyTeamMember(workerId, {
        internalNote: tempNote,
      });
      setTeam((prev) =>
        prev.map((w) => (w.workerId === workerId ? { ...w, internalNote: tempNote } : w))
      );
      setEditingNoteId(null);
      showToast('Đã lưu ghi chú nội bộ thành công!', 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi lưu ghi chú', 'error');
    }
  };

  const handleRemoveMember = async (worker) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa "${worker.fullName}" khỏi danh sách nhân sự quen thuộc?`)) return;
    try {
      await myTeamService.removeWorkerFromMyTeam(worker.workerId);
      setTeam((prev) => prev.filter((w) => w.workerId !== worker.workerId));
      showToast(`Đã xóa ${worker.fullName} khỏi danh sách nhân sự quen thuộc.`, 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi xóa nhân sự', 'error');
    }
  };

  const handleBlockWorker = async (worker) => {
    const reason = window.prompt(
      `Bạn có chắc muốn CHẶN "${worker.fullName}" không?\n\nNgười này sẽ bị xóa khỏi danh sách nhân sự quen thuộc và không thể nhận bất kỳ ca làm nào của bạn nữa.\nNhập lý do:`,
      'Không đạt chất lượng công việc'
    );
    if (reason === null) return;

    try {
      // Backend My Team có user_id hoặc block qua workerId
      await myTeamService.updateMyTeamMember(worker.workerId, { isBlacklisted: true });
      setTeam((prev) => prev.filter((w) => w.workerId !== worker.workerId));
      showToast(`Đã đưa ${worker.fullName} vào Blacklist!`, 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi chặn nhân sự', 'error');
    }
  };

  const handleAddSubmit = async (e) => {
    e.preventDefault();
    if (!addForm.phone.trim()) {
      showToast('Vui lòng nhập số điện thoại nhân sự', 'warning');
      return;
    }
    setSubmittingAdd(true);
    try {
      await myTeamService.addWorkerToMyTeam(addForm);
      showToast('Đã thêm nhân sự vào danh sách quen thuộc thành công!', 'success');
      setShowAddModal(false);
      setAddForm({ phone: '', internalNote: '', isFavorited: true });
      fetchTeam();
    } catch (err) {
      showToast(err.response?.data?.message || 'Không tìm thấy nhân sự hoặc có lỗi xảy ra', 'error');
    } finally {
      setSubmittingAdd(false);
    }
  };

  return (
    <div className="eo-my-team-container animate-fade-in-up" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* 1. HEADER BANNER */}
      <div className="eo-widget-card" style={{ padding: '1.25rem 1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
                Nhân Sự Quen Thuộc
              </h3>
              <span style={{
                background: '#eff6ff',
                color: '#1d4ed8',
                padding: '0.2rem 0.6rem',
                borderRadius: '999px',
                fontSize: '0.75rem',
                fontWeight: 700
              }}>
                {team.length} nhân sự
              </span>
            </div>
            <p style={{ margin: '0.35rem 0 0', fontSize: '0.85rem', color: '#64748b' }}>
              Quản lý danh sách nhân sự quen thuộc từng cộng tác chung, lưu ghi chú đánh giá nội bộ và ưu tiên điều động cho sự kiện sắp tới.
            </p>
          </div>

          <button
            type="button"
            className="eo-btn-primary"
            style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem', padding: '0.55rem 1.25rem' }}
            onClick={() => setShowAddModal(true)}
          >
            <span>+</span> Thêm Nhân Sự Quen Thuộc
          </button>
        </div>
      </div>

      {/* 2. FILTER & SEARCH TOOLBAR */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '0.5rem', flex: '1 1 300px', maxWidth: '420px' }}>
          <input
            type="text"
            placeholder="Tìm theo tên nhân sự, số điện thoại..."
            className="eo-form-input"
            style={{ padding: '0.5rem 0.85rem', fontSize: '0.85rem' }}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <button type="submit" className="eo-btn-secondary" style={{ padding: '0.5rem 1rem', fontSize: '0.85rem' }}>
            Tìm kiếm
          </button>
        </form>

        <label style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '0.85rem', fontWeight: 600, color: '#334155' }}>
          <input
            type="checkbox"
            checked={favoritedOnly}
            onChange={(e) => setFavoritedOnly(e.target.checked)}
            style={{ width: 16, height: 16, cursor: 'pointer' }}
          />
          Chỉ xem nhân sự Yêu thích
        </label>
      </div>

      {/* 3. WORKER GRID LIST */}
      {loading ? (
        <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }}>
          Đang tải danh sách nhân sự quen thuộc...
        </div>
      ) : team.length === 0 ? (
        <div style={{
          padding: '3rem 1.5rem',
          textAlign: 'center',
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          border: '1.5px dashed #cbd5e1',
          color: '#64748b',
        }}>
          <p style={{ margin: '0 0 0.5rem', fontWeight: 700, fontSize: '1rem', color: '#1e293b' }}>
            Chưa có nhân sự nào trong danh sách quen thuộc.
          </p>
          <p style={{ margin: '0 0 1.25rem', fontSize: '0.85rem' }}>
            Bằng cách nhập số điện thoại của nhân sự, bạn có thể lưu vào danh sách quen thuộc để gắn thẻ phân nhóm và ưu tiên gửi lời mời điều phối cho các sự kiện tiếp theo.
          </p>
          <button
            type="button"
            className="eo-btn-primary"
            style={{ fontSize: '0.85rem', padding: '0.5rem 1.2rem' }}
            onClick={() => setShowAddModal(true)}
          >
            + Thêm Nhân Sự Bằng Số Điện Thoại
          </button>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 340px), 1fr))', gap: '1rem' }}>
          {team.map((w) => {
            const isEditing = editingNoteId === w.workerId;
            return (
              <div
                key={w.eoWorkerId || w.workerId}
                className="eo-widget-card"
                style={{
                  padding: '1.25rem',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.85rem',
                  position: 'relative',
                  border: w.isFavorited ? '1.5px solid #fde047' : '1px solid #e2e8f0',
                  boxShadow: w.isFavorited ? '0 2px 8px rgba(250, 204, 21, 0.15)' : 'none',
                }}
              >
                {/* Header card: Avatar + Info + Star */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.75rem' }}>
                  <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                    {w.avatarUrl ? (
                      <img
                        src={w.avatarUrl}
                        alt={w.fullName}
                        style={{ width: 44, height: 44, borderRadius: '50%', objectFit: 'cover' }}
                      />
                    ) : (
                      <div style={{
                        width: 44,
                        height: 44,
                        borderRadius: '50%',
                        backgroundColor: '#eff6ff',
                        color: '#1d4ed8',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 800,
                        fontSize: '1rem',
                      }}>
                        {(w.fullName || 'W').charAt(0).toUpperCase()}
                      </div>
                    )}

                    <div>
                      <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: '#0f172a' }}>
                        {w.fullName}
                      </h4>
                      <div style={{ fontSize: '0.775rem', color: '#64748b', marginTop: '0.15rem' }}>
                        SĐT: <strong>{w.phone || 'Chưa cập nhật'}</strong>
                      </div>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => handleToggleFavorite(w)}
                    style={{
                      background: 'none',
                      border: 'none',
                      fontSize: '1.35rem',
                      cursor: 'pointer',
                      padding: 0,
                      lineHeight: 1,
                      color: w.isFavorited ? '#eab308' : '#cbd5e1',
                    }}
                    title={w.isFavorited ? 'Bỏ yêu thích' : 'Đánh dấu yêu thích'}
                  >
                    ★
                  </button>
                </div>

                {/* Metrics row: Hạng, Số ca làm chung, Đánh giá */}
                <div style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  padding: '0.5rem 0.75rem',
                  backgroundColor: '#f8fafc',
                  borderRadius: '8px',
                  fontSize: '0.775rem',
                }}>
                  <div>
                    <span style={{ color: '#64748b' }}>Hạng:</span>{' '}
                    <strong style={{ color: '#0f172a' }}>{w.skillTier || 'STARTER'}</strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Đã làm chung:</span>{' '}
                    <strong style={{ color: '#10b981' }}>{w.totalShiftsTogether || 0} ca</strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Điểm uy tín:</span>{' '}
                    <strong style={{ color: '#0066FF' }}>{w.reliabilityScore || 100}/100</strong>
                  </div>
                </div>

                {/* Ghi chú nội bộ (Internal Note) */}
                <div style={{ fontSize: '0.8rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.25rem' }}>
                    <span style={{ fontWeight: 600, color: '#475569', fontSize: '0.75rem' }}>Ghi chú nội bộ của bạn:</span>
                    {!isEditing && (
                      <button
                        type="button"
                        onClick={() => { setEditingNoteId(w.workerId); setTempNote(w.internalNote || ''); }}
                        style={{ background: 'none', border: 'none', color: '#0066FF', fontSize: '0.725rem', cursor: 'pointer', fontWeight: 600 }}
                      >
                        {w.internalNote ? 'Sửa ✎' : '+ Thêm ghi chú'}
                      </button>
                    )}
                  </div>

                  {isEditing ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                      <textarea
                        rows={2}
                        className="eo-form-input"
                        style={{ fontSize: '0.8rem', padding: '0.4rem' }}
                        value={tempNote}
                        onChange={(e) => setTempNote(e.target.value)}
                        placeholder="Nhập ghi chú thái độ, điểm mạnh..."
                      />
                      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.4rem' }}>
                        <button
                          type="button"
                          className="eo-btn-secondary"
                          style={{ padding: '0.25rem 0.6rem', fontSize: '0.75rem' }}
                          onClick={() => setEditingNoteId(null)}
                        >
                          Hủy
                        </button>
                        <button
                          type="button"
                          className="eo-btn-primary"
                          style={{ padding: '0.25rem 0.6rem', fontSize: '0.75rem' }}
                          onClick={() => handleSaveNote(w.workerId)}
                        >
                          Lưu
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div style={{
                      padding: '0.4rem 0.65rem',
                      backgroundColor: '#ffffff',
                      border: '1px dashed #cbd5e1',
                      borderRadius: '6px',
                      color: w.internalNote ? '#334155' : '#94a3b8',
                      fontStyle: w.internalNote ? 'normal' : 'italic',
                      lineHeight: 1.35,
                    }}>
                      {w.internalNote || 'Chưa có ghi chú nội bộ cho nhân sự này.'}
                    </div>
                  )}
                </div>

                {/* Footer action buttons */}
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', borderTop: '1px solid #f1f5f9', paddingTop: '0.65rem' }}>
                  <button
                    type="button"
                    onClick={() => handleBlockWorker(w)}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '0.25rem',
                      padding: '0.35rem 0.65rem',
                      background: '#fff1f2',
                      color: '#e11d48',
                      border: '1px solid #fecdd3',
                      borderRadius: '6px',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                    title="Chặn nhân sự không cho nhận ca trong tương lai"
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
                      <circle cx="12" cy="12" r="10" />
                      <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
                    </svg>
                    Chặn
                  </button>

                  <button
                    type="button"
                    onClick={() => handleRemoveMember(w)}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '0.25rem',
                      padding: '0.35rem 0.65rem',
                      background: '#f8fafc',
                      color: '#64748b',
                      border: '1px solid #cbd5e1',
                      borderRadius: '6px',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                    title="Xóa khỏi danh sách quen thuộc"
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
                      <polyline points="3 6 5 6 21 6" />
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    </svg>
                    Xóa
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* MODAL THÊM NHÂN SỰ BẰNG SĐT */}
      {showAddModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: '1rem',
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            width: '100%',
            maxWidth: '460px',
            padding: '1.5rem',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          }}>
            <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.15rem', fontWeight: 800, color: '#0f172a' }}>
              Thêm Nhân Sự Quen Thuộc
            </h3>
            <p style={{ margin: '0 0 1.25rem', fontSize: '0.825rem', color: '#64748b' }}>
              Nhập số điện thoại của nhân sự đã đăng ký trên CANi để lưu vào danh sách ưu tiên của bạn.
            </p>

            <form onSubmit={handleAddSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.825rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Số điện thoại nhân sự <span style={{ color: '#dc2626' }}>*</span>
                </label>
                <input
                  type="text"
                  placeholder="Ví dụ: 0912345678"
                  className="eo-form-input"
                  style={{ width: '100%', padding: '0.55rem 0.85rem' }}
                  value={addForm.phone}
                  onChange={(e) => setAddForm({ ...addForm, phone: e.target.value })}
                  required
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.825rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Ghi chú nội bộ ban đầu
                </label>
                <textarea
                  rows={3}
                  placeholder="Ví dụ: Setup sân khấu tốt, từng làm sự kiện SECC..."
                  className="eo-form-input"
                  style={{ width: '100%', padding: '0.55rem 0.85rem', fontSize: '0.85rem' }}
                  value={addForm.internalNote}
                  onChange={(e) => setAddForm({ ...addForm, internalNote: e.target.value })}
                />
              </div>

              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '0.85rem', fontWeight: 600, color: '#334155' }}>
                <input
                  type="checkbox"
                  checked={addForm.isFavorited}
                  onChange={(e) => setAddForm({ ...addForm, isFavorited: e.target.checked })}
                  style={{ width: 16, height: 16, cursor: 'pointer' }}
                />
                Đánh dấu Yêu thích ngay lập tức
              </label>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  className="eo-btn-secondary"
                  onClick={() => setShowAddModal(false)}
                  disabled={submittingAdd}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="eo-btn-primary"
                  disabled={submittingAdd}
                >
                  {submittingAdd ? 'Đang thêm...' : 'Thêm Vào Danh Sách'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
