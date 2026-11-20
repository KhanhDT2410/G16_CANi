import React, { useState, useEffect, useCallback } from 'react';
import { myTeamService } from '../../../services/myTeamService';
import { blockListService } from '../../../services/blockListService';
import { showToast } from '../../../components/common/Toast';
import { showConfirm, showPrompt } from '../../../components/common/PromptModal';
import { WorkerProfileModal } from './worker-profile';
import { useWorkerProfile } from '../hooks/useWorkerProfile';

export default function EOMyTeamTab() {
  const [team, setTeam] = useState([]);
  const [collaborators, setCollaborators] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [favoritedOnly, setFavoritedOnly] = useState(false);
  const workerProfile = useWorkerProfile();

  // Modal thêm nhân sự bằng SĐT (tùy chọn bổ sung)
  const [showAddModal, setShowAddModal] = useState(false);
  const [addForm, setAddForm] = useState({ phone: '', internalNote: '', isFavorited: true });
  const [submittingAdd, setSubmittingAdd] = useState(false);

  // State chỉnh sửa ghi chú inline
  const [editingNoteId, setEditingNoteId] = useState(null);
  const [tempNote, setTempNote] = useState('');

  const fetchAllData = useCallback(async () => {
    setLoading(true);
    try {
      const [teamData, collabData] = await Promise.all([
        myTeamService.getMyTeam(search, favoritedOnly),
        myTeamService.getPastCollaborators(search),
      ]);
      setTeam(Array.isArray(teamData) ? teamData : []);
      setCollaborators(Array.isArray(collabData) ? collabData : []);
    } catch (err) {
      console.warn('Lỗi tải danh sách nhân sự quen thuộc:', err);
      setTeam([]);
      setCollaborators([]);
    } finally {
      setLoading(false);
    }
  }, [search, favoritedOnly]);

  useEffect(() => {
    fetchAllData();
  }, [favoritedOnly, fetchAllData]);

  const handleSearchSubmit = (e) => {
    e?.preventDefault();
    fetchAllData();
  };

  const handleQuickSaveCollaborator = async (worker) => {
    try {
      await myTeamService.addWorkerToMyTeam({
        workerId: worker.workerId,
        internalNote: worker.lastJobType ? `Từng làm vị trí: ${worker.lastJobType}` : '',
        isFavorited: true,
      });
      showToast(`Đã lưu ${worker.fullName} vào danh sách nhân sự quen thuộc.`, 'success');
      fetchAllData();
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi lưu nhân sự', 'error');
    }
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
      showToast(
        nextState ? `Đã đánh dấu yêu thích ${worker.fullName}.` : `Đã bỏ yêu thích ${worker.fullName}.`,
        'success'
      );
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
      showToast('Đã lưu ghi chú nội bộ.', 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi lưu ghi chú', 'error');
    }
  };

  const handleRemoveMember = async (worker) => {
    const confirmed = await showConfirm({
      title: 'Bỏ lưu nhân sự quen thuộc',
      description: `Bạn có chắc chắn muốn bỏ lưu "${worker.fullName}" khỏi danh sách quen thuộc?`,
      type: 'warning',
      confirmText: 'Bỏ lưu',
    });
    if (!confirmed) return;
    try {
      await myTeamService.removeWorkerFromMyTeam(worker.workerId);
      setTeam((prev) => prev.filter((w) => w.workerId !== worker.workerId));
      showToast(`Đã bỏ lưu ${worker.fullName}.`, 'success');
      fetchAllData();
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi bỏ lưu nhân sự', 'error');
    }
  };

  const handleBlockWorker = async (worker) => {
    const reason = await showPrompt({
      title: `Chặn nhân sự "${worker.fullName}"`,
      description: 'Người này sẽ không thể nhìn thấy hoặc nhận bất kỳ ca làm nào của bạn nữa.',
      type: 'danger',
      confirmText: 'Xác nhận chặn',
      inputLabel: 'Lý do chặn',
      initialValue: 'Không đạt yêu cầu công việc',
      isMultiline: true,
      allowEmpty: true,
    });
    if (reason === null) return;

    try {
      if (worker.userId) {
        await blockListService.blockUser(worker.userId, reason);
      }
      if (worker.isSavedInMyTeam !== false) {
        await myTeamService.updateMyTeamMember(worker.workerId, { isBlacklisted: true }).catch(() => {});
      }
      showToast(`Đã đưa ${worker.fullName} vào danh sách chặn.`, 'success');
      fetchAllData();
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
      showToast('Đã thêm nhân sự vào danh sách quen thuộc.', 'success');
      setShowAddModal(false);
      setAddForm({ phone: '', internalNote: '', isFavorited: true });
      fetchAllData();
    } catch (err) {
      showToast(err.response?.data?.message || 'Không tìm thấy nhân sự hoặc có lỗi xảy ra', 'error');
    } finally {
      setSubmittingAdd(false);
    }
  };

  // Lọc những người đã từng làm cùng nhưng chưa được lưu vào danh sách quen thuộc
  const savedWorkerIds = new Set(team.map((t) => t.workerId));
  const unsavedCollaborators = collaborators.filter((c) => !c.isSavedInMyTeam && !savedWorkerIds.has(c.workerId));

  const formatTierLabel = (tier) => {
    switch (tier) {
      case 'EXPERT':
        return 'Chuyên gia';
      case 'PRO':
        return 'Thành thạo';
      case 'CAPABLE':
        return 'Có kinh nghiệm';
      default:
        return 'Cơ bản';
    }
  };

  return (
    <div className="eo-my-team-container animate-fade-in-up" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* 1. HEADER */}
      <div className="eo-widget-card" style={{ padding: '1.25rem 1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
                Nhân Sự Quen Thuộc
              </h3>
              <span
                style={{
                  background: '#eff6ff',
                  color: '#1d4ed8',
                  padding: '0.2rem 0.65rem',
                  borderRadius: '999px',
                  fontSize: '0.75rem',
                  fontWeight: 700,
                }}
              >
                {team.length} đã lưu
              </span>
            </div>
            <p style={{ margin: '0.35rem 0 0', fontSize: '0.85rem', color: '#64748b' }}>
              Tổng hợp toàn bộ nhân sự đã từng tham gia các sự kiện của bạn để lưu ghi chú đánh giá và ưu tiên mời cho các ca sau.
            </p>
          </div>

          <button
            type="button"
            className="eo-btn-secondary"
            style={{ fontSize: '0.84rem', padding: '0.5rem 1rem' }}
            onClick={() => setShowAddModal(true)}
          >
            Thêm bằng số điện thoại
          </button>
        </div>
      </div>

      {/* 2. THANH TÌM KIẾM & LỌC */}
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
          Chỉ xem nhân sự yêu thích
        </label>
      </div>

      {loading ? (
        <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }}>
          Đang tải danh sách nhân sự...
        </div>
      ) : (
        <>
          {/* KHU VỰC 1: DANH SÁCH NHÂN SỰ ĐÃ LƯU */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
            <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 800, color: '#0f172a' }}>
              Danh sách nhân sự đã lưu ({team.length})
            </h4>

            {team.length === 0 ? (
              <div
                style={{
                  padding: '2rem 1.5rem',
                  textAlign: 'center',
                  backgroundColor: '#ffffff',
                  borderRadius: '14px',
                  border: '1.5px dashed #cbd5e1',
                  color: '#64748b',
                }}
              >
                <p style={{ margin: '0 0 0.35rem', fontWeight: 700, fontSize: '0.95rem', color: '#1e293b' }}>
                  Chưa có nhân sự nào được lưu cố định.
                </p>
                <p style={{ margin: 0, fontSize: '0.84rem' }}>
                  {unsavedCollaborators.length > 0
                    ? 'Hãy xem mục Nhân sự đã từng làm việc cùng ở bên dưới và nhấn Lưu vào danh sách quen thuộc.'
                    : 'Khi có nhân sự tham gia các sự kiện của bạn, họ sẽ tự động xuất hiện ở mục Từng làm việc cùng để bạn lưu lại.'}
                </p>
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
                        border: w.isFavorited ? '1.5px solid #f59e0b' : '1px solid #e2e8f0',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.75rem' }}>
                        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                          <div
                            onClick={() => workerProfile.openProfile(w.workerId)}
                            style={{ cursor: 'pointer' }}
                            title="Xem hồ sơ nhân sự"
                          >
                            {w.avatarUrl ? (
                              <img
                                src={w.avatarUrl}
                                alt={w.fullName}
                                style={{ width: 44, height: 44, borderRadius: '50%', objectFit: 'cover' }}
                              />
                            ) : (
                              <div
                                style={{
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
                                }}
                              >
                                {(w.fullName || 'N').charAt(0).toUpperCase()}
                              </div>
                            )}
                          </div>

                          <div
                            onClick={() => workerProfile.openProfile(w.workerId)}
                            style={{ cursor: 'pointer' }}
                          >
                            <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: '#0066FF' }}>
                              {w.fullName}
                            </h4>
                            <div style={{ fontSize: '0.78rem', color: '#64748b', marginTop: '0.15rem' }}>
                              SĐT: <strong>{w.phone || 'Chưa cập nhật'}</strong>
                            </div>
                          </div>
                        </div>

                        <button
                          type="button"
                          onClick={() => handleToggleFavorite(w)}
                          style={{
                            background: w.isFavorited ? '#fef3c7' : '#f1f5f9',
                            border: w.isFavorited ? '1px solid #fcd34d' : '1px solid #cbd5e1',
                            borderRadius: '6px',
                            padding: '4px 8px',
                            fontSize: '0.74rem',
                            fontWeight: 700,
                            cursor: 'pointer',
                            color: w.isFavorited ? '#b45309' : '#475569',
                          }}
                        >
                          {w.isFavorited ? 'Yêu thích' : 'Đánh dấu yêu thích'}
                        </button>
                      </div>

                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          padding: '0.5rem 0.75rem',
                          backgroundColor: '#f8fafc',
                          borderRadius: '8px',
                          fontSize: '0.78rem',
                        }}
                      >
                        <div>
                          <span style={{ color: '#64748b' }}>Cấp độ:</span>{' '}
                          <strong style={{ color: '#0f172a' }}>{formatTierLabel(w.skillTier)}</strong>
                        </div>
                        <div>
                          <span style={{ color: '#64748b' }}>Làm chung:</span>{' '}
                          <strong style={{ color: '#059669' }}>{w.totalShiftsTogether || 0} ca</strong>
                        </div>
                        <div>
                          <span style={{ color: '#64748b' }}>Uy tín:</span>{' '}
                          <strong style={{ color: '#0066FF' }}>{w.reliabilityScore || 100}/100</strong>
                        </div>
                      </div>

                      <div style={{ fontSize: '0.8rem' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.25rem' }}>
                          <span style={{ fontWeight: 600, color: '#475569', fontSize: '0.75rem' }}>Ghi chú nội bộ:</span>
                          {!isEditing && (
                            <button
                              type="button"
                              onClick={() => { setEditingNoteId(w.workerId); setTempNote(w.internalNote || ''); }}
                              style={{ background: 'none', border: 'none', color: '#0066FF', fontSize: '0.74rem', cursor: 'pointer', fontWeight: 600 }}
                            >
                              {w.internalNote ? 'Sửa ghi chú' : 'Thêm ghi chú'}
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
                          <div
                            style={{
                              padding: '0.45rem 0.65rem',
                              backgroundColor: '#ffffff',
                              border: '1px dashed #cbd5e1',
                              borderRadius: '6px',
                              color: w.internalNote ? '#334155' : '#94a3b8',
                              fontStyle: w.internalNote ? 'normal' : 'italic',
                            }}
                          >
                            {w.internalNote || 'Chưa có ghi chú nội bộ.'}
                          </div>
                        )}
                      </div>

                      <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '0.5rem', borderTop: '1px solid #f1f5f9', paddingTop: '0.65rem' }}>
                        <button
                          type="button"
                          onClick={() => {
                            const targetUserId = w.userId || w.workerId;
                            window.dispatchEvent(
                              new CustomEvent('cani_open_direct_chat', {
                                detail: { targetUserId },
                              })
                            );
                          }}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '0.25rem',
                            padding: '0.35rem 0.65rem',
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            background: '#eff6ff',
                            color: '#2563eb',
                            border: '1px solid #bfdbfe',
                            borderRadius: '6px',
                            cursor: 'pointer',
                          }}
                          title="Mở khung nhắn tin trực tiếp với nhân sự này"
                        >
                          💬 Nhắn Tin
                        </button>
                        <button
                          type="button"
                          onClick={() => workerProfile.openProfile(w.workerId)}
                          className="eo-btn-secondary"
                          style={{ padding: '0.35rem 0.65rem', fontSize: '0.75rem', fontWeight: 600 }}
                        >
                          Xem Hồ Sơ
                        </button>
                        <button
                          type="button"
                          onClick={() => handleBlockWorker(w)}
                          style={{
                            padding: '0.35rem 0.65rem',
                            background: '#fff1f2',
                            color: '#e11d48',
                            border: '1px solid #fecdd3',
                            borderRadius: '6px',
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                          }}
                        >
                          Chặn
                        </button>
                        <button
                          type="button"
                          onClick={() => handleRemoveMember(w)}
                          style={{
                            padding: '0.35rem 0.65rem',
                            background: '#f8fafc',
                            color: '#64748b',
                            border: '1px solid #cbd5e1',
                            borderRadius: '6px',
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                          }}
                        >
                          Bỏ lưu
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* KHU VỰC 2: NHÂN SỰ TỪNG LÀM VIỆC CÙNG TRONG CÁC SỰ KIỆN TRƯỚC */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem', marginTop: '0.5rem' }}>
            <div>
              <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 800, color: '#0f172a' }}>
                Nhân sự đã từng làm việc cùng ({unsavedCollaborators.length})
              </h4>
              <p style={{ margin: '0.2rem 0 0', fontSize: '0.82rem', color: '#64748b' }}>
                Hệ thống tự động ghi nhận các nhân sự từng nhận ca trong sự kiện của bạn để bạn lưu lại làm việc lâu dài.
              </p>
            </div>

            {unsavedCollaborators.length === 0 ? (
              <div
                style={{
                  padding: '1.75rem',
                  textAlign: 'center',
                  backgroundColor: '#f8fafc',
                  borderRadius: '12px',
                  border: '1px solid #e2e8f0',
                  color: '#64748b',
                  fontSize: '0.84rem',
                }}
              >
                {collaborators.length > 0
                  ? 'Tất cả nhân sự từng làm việc cùng đã được bạn lưu vào danh sách quen thuộc ở trên.'
                  : 'Chưa ghi nhận nhân sự nào đã nhận ca hoặc hoàn thành công việc trong sự kiện của bạn.'}
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 340px), 1fr))', gap: '1rem' }}>
                {unsavedCollaborators.map((c) => (
                  <div
                    key={c.workerId}
                    className="eo-widget-card"
                    style={{
                      padding: '1.15rem',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.75rem',
                      border: '1px solid #e2e8f0',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem' }}>
                      <div>
                        <h4
                          onClick={() => workerProfile.openProfile(c.workerId)}
                          style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: '#0066FF', cursor: 'pointer' }}
                        >
                          {c.fullName}
                        </h4>
                        <div style={{ fontSize: '0.78rem', color: '#64748b', marginTop: 2 }}>
                          SĐT: <strong>{c.phone || 'Chưa cập nhật'}</strong>
                        </div>
                      </div>
                      <span
                        style={{
                          background: '#f0fdf4',
                          color: '#047857',
                          border: '1px solid #bbf7d0',
                          borderRadius: '6px',
                          padding: '3px 8px',
                          fontSize: '0.74rem',
                          fontWeight: 700,
                        }}
                      >
                        Đã làm {c.totalShiftsTogether || 1} ca
                      </span>
                    </div>

                    <div style={{ fontSize: '0.78rem', color: '#475569', background: '#f8fafc', padding: '0.5rem 0.75rem', borderRadius: '8px' }}>
                      <div>Vị trí gần nhất: <strong>{c.lastJobType || 'Nhân sự sự kiện'}</strong></div>
                      <div style={{ marginTop: 2 }}>
                        Uy tín: <strong>{c.reliabilityScore || 100}/100</strong> • Đánh giá: <strong>{c.ratingAvg || 5.0}</strong>
                      </div>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', borderTop: '1px solid #f1f5f9', paddingTop: '0.6rem' }}>
                      <button
                        type="button"
                        onClick={() => {
                          const targetUserId = c.userId || c.workerId;
                          window.dispatchEvent(
                            new CustomEvent('cani_open_direct_chat', {
                              detail: { targetUserId },
                            })
                          );
                        }}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '0.25rem',
                          padding: '0.38rem 0.75rem',
                          fontSize: '0.76rem',
                          fontWeight: 600,
                          background: '#eff6ff',
                          color: '#2563eb',
                          border: '1px solid #bfdbfe',
                          borderRadius: '6px',
                          cursor: 'pointer',
                        }}
                        title="Mở khung nhắn tin trực tiếp với nhân sự này"
                      >
                        💬 Nhắn Tin
                      </button>
                      <button
                        type="button"
                        onClick={() => workerProfile.openProfile(c.workerId)}
                        className="eo-btn-secondary"
                        style={{ padding: '0.38rem 0.75rem', fontSize: '0.76rem', fontWeight: 600 }}
                      >
                        Xem Hồ Sơ
                      </button>
                      <button
                        type="button"
                        onClick={() => handleBlockWorker(c)}
                        style={{
                          padding: '0.38rem 0.75rem',
                          background: '#fff1f2',
                          color: '#e11d48',
                          border: '1px solid #fecdd3',
                          borderRadius: '6px',
                          fontSize: '0.76rem',
                          fontWeight: 600,
                          cursor: 'pointer',
                        }}
                      >
                        Chặn
                      </button>
                      <button
                        type="button"
                        onClick={() => handleQuickSaveCollaborator(c)}
                        className="eo-btn-primary"
                        style={{ padding: '0.38rem 0.85rem', fontSize: '0.76rem', fontWeight: 700 }}
                      >
                        Lưu vào danh sách quen thuộc
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}

      {/* MODAL THÊM NHÂN SỰ BẰNG SĐT */}
      {showAddModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '1rem',
          }}
        >
          <div className="eo-widget-card" style={{ width: '100%', maxWidth: '420px', padding: '1.5rem', backgroundColor: '#ffffff' }}>
            <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.15rem', fontWeight: 800 }}>Thêm Nhân Sự Quen Thuộc</h3>
            <p style={{ margin: '0 0 1.25rem', fontSize: '0.825rem', color: '#64748b' }}>
              Nhập số điện thoại của nhân sự đã đăng ký trên hệ thống để lưu trực tiếp.
            </p>
            <form onSubmit={handleAddSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label className="eo-form-label" style={{ display: 'block', marginBottom: '0.35rem', fontSize: '0.85rem', fontWeight: 600 }}>
                  Số điện thoại nhân sự *
                </label>
                <input
                  type="text"
                  required
                  className="eo-form-input"
                  placeholder="Ví dụ: 0987654321"
                  value={addForm.phone}
                  onChange={(e) => setAddForm({ ...addForm, phone: e.target.value })}
                />
              </div>
              <div>
                <label className="eo-form-label" style={{ display: 'block', marginBottom: '0.35rem', fontSize: '0.85rem', fontWeight: 600 }}>
                  Ghi chú nội bộ
                </label>
                <textarea
                  rows={2}
                  className="eo-form-input"
                  placeholder="Nhận xét về thái độ, năng lực..."
                  value={addForm.internalNote}
                  onChange={(e) => setAddForm({ ...addForm, internalNote: e.target.value })}
                />
              </div>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '0.85rem' }}>
                <input
                  type="checkbox"
                  checked={addForm.isFavorited}
                  onChange={(e) => setAddForm({ ...addForm, isFavorited: e.target.checked })}
                />
                Đánh dấu là nhân sự yêu thích
              </label>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  className="eo-btn-secondary"
                  onClick={() => setShowAddModal(false)}
                  disabled={submittingAdd}
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  className="eo-btn-primary"
                  disabled={submittingAdd}
                >
                  {submittingAdd ? 'Đang lưu...' : 'Lưu vào danh sách'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <WorkerProfileModal
        isOpen={workerProfile.isOpen}
        loading={workerProfile.loading}
        error={workerProfile.error}
        profile={workerProfile.profile}
        onClose={workerProfile.closeProfile}
        onRetry={workerProfile.retry}
      />
    </div>
  );
}
