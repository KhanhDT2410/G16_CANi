import { useState, useEffect, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from '../../../components/common/Toast';
import {
  DISPUTE_OUTCOMES,
  DISPUTE_OUTCOME_LABELS,
  DISPUTE_STATUS_CONFIG,
  DISPUTE_RULES,
} from '../types/dispute.constants';
import { disputeApi } from '../api/disputeApi';
import { disputeRuleApi } from '../../admin-dispute-rules/api/disputeRuleApi';

const DisputeDecisionModal = ({ dispute, onClose, onSubmit }) => {
  const isEscalated = dispute?.status === 'ESCALATED';
  const [outcome, setOutcome] = useState(
    isEscalated ? DISPUTE_OUTCOMES.UPHOLD : DISPUTE_OUTCOMES.WORKER_FAULT
  );
  const [selectedBehaviorCode, setSelectedBehaviorCode] = useState('');
  const [selectedRepeatIndex, setSelectedRepeatIndex] = useState(1);
  const [rationale, setRationale] = useState('');
  const [penaltyWaiverReason, setPenaltyWaiverReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Quy tắc từ API và Lịch sử vi phạm của bên bị xử lý
  const [dbRules, setDbRules] = useState([]);
  const [violationHistory, setViolationHistory] = useState(null);
  const [hasNoShowSameShift, setHasNoShowSameShift] = useState(false);

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);

  // Tải danh mục quy tắc từ API (với fallback DISPUTE_RULES)
  useEffect(() => {
    let isMounted = true;
    disputeRuleApi
      .getRules({ enabled: true })
      .then((data) => {
        if (isMounted && Array.isArray(data) && data.length > 0) {
          setDbRules(data);
        }
      })
      .catch(() => {
        // Sử dụng DISPUTE_RULES dự phòng
      });
    return () => {
      isMounted = false;
    };
  }, []);

  const allRules = useMemo(
    () => (dbRules.length > 0 ? dbRules : DISPUTE_RULES),
    [dbRules]
  );

  const resolveLoserUserId = () => {
    if (!dispute) return null;
    if (outcome === DISPUTE_OUTCOMES.WORKER_FAULT) {
      if (dispute.complainantRole === 'WORKER') return dispute.complainantId;
      if (dispute.respondentRole === 'WORKER') return dispute.respondentId;
      return dispute.respondentId || dispute.complainantId;
    }
    if (outcome === DISPUTE_OUTCOMES.EO_FAULT) {
      if (dispute.complainantRole === 'EVENT_ORGANIZER') return dispute.complainantId;
      if (dispute.respondentRole === 'EVENT_ORGANIZER') return dispute.respondentId;
      return dispute.complainantId || dispute.respondentId;
    }
    if (outcome === DISPUTE_OUTCOMES.UPHOLD) {
      return dispute.complainantId;
    }
    return null;
  };

  // Nạp lịch sử vi phạm để tính tái phạm và kiểm tra miễn trừ phạt trùng lặp
  useEffect(() => {
    const loserId = resolveLoserUserId();
    if (!loserId) {
      setViolationHistory(null);
      setHasNoShowSameShift(false);
      return;
    }

    let isMounted = true;
    disputeApi
      .getViolationHistory(loserId, 90)
      .then((hist) => {
        if (!isMounted) return;
        setViolationHistory(hist);

        // Kiểm tra miễn trừ phạt trùng lặp nếu ca này đã có bản ghi No-show
        if (outcome === DISPUTE_OUTCOMES.WORKER_FAULT && dispute?.assignmentId) {
          const matchingNoShow = hist?.reliabilityLogs?.some(
            (l) =>
              l.referenceType === 'NO_SHOW' &&
              String(l.referenceId) === String(dispute.assignmentId)
          );
          if (matchingNoShow) {
            setHasNoShowSameShift(true);
            setPenaltyWaiverReason(
              'Đã xử lý phạt vắng mặt không phép trước đó, áp dụng miễn trừ để tránh xử phạt trùng lặp.'
            );
          } else {
            setHasNoShowSameShift(false);
          }
        } else {
          setHasNoShowSameShift(false);
        }
      })
      .catch(() => {
        if (isMounted) {
          setViolationHistory(null);
          setHasNoShowSameShift(false);
        }
      });
  }, [outcome, dispute?.id, dispute?.assignmentId]);

  // Danh sách các hành vi vi phạm tương ứng với phán quyết đang chọn
  const availableBehaviors = useMemo(() => {
    let list = allRules;
    if (outcome === DISPUTE_OUTCOMES.WORKER_FAULT) {
      list = allRules.filter(
        (r) => r.violatingParty === 'WORKER' || r.violatingParty === 'BOTH'
      );
    } else if (outcome === DISPUTE_OUTCOMES.EO_FAULT) {
      list = allRules.filter(
        (r) => r.violatingParty === 'EO' || r.violatingParty === 'BOTH'
      );
    } else if (outcome === DISPUTE_OUTCOMES.UPHOLD) {
      list = allRules.filter(
        (r) => r.faultOutcome === DISPUTE_OUTCOMES.UPHOLD || r.violatingParty === 'BOTH'
      );
    }

      const map = new Map();
      list.forEach((r) => {
        if (!map.has(r.code)) {
          map.set(r.code, r);
        }
      });
      return Array.from(map.values());
    }, [allRules, outcome]);

    // Đồng bộ hành vi vi phạm khi danh sách thay đổi hoặc ban đầu
    useEffect(() => {
      if (availableBehaviors.length === 0) {
        setSelectedBehaviorCode('');
        return;
      }
      const currentValid = availableBehaviors.some((b) => b.code === selectedBehaviorCode);
      if (!currentValid) {
        // Ưu tiên hành vi cùng loại tranh chấp với đơn
        const preferred =
          availableBehaviors.find((b) => b.ruleType === dispute?.type) ||
          availableBehaviors[0];
        setSelectedBehaviorCode(preferred?.code || '');
      }
    }, [availableBehaviors, dispute?.type, selectedBehaviorCode]);

    // Các mức xử lý (Lần 1, Lần 2, Lần 3) của hành vi đang chọn
    const currentBehaviorRules = useMemo(() => {
      if (!selectedBehaviorCode) return [];
      return allRules
        .filter((r) => r.code === selectedBehaviorCode)
        .sort((a, b) => a.repeatIndex - b.repeatIndex);
    }, [allRules, selectedBehaviorCode]);

    // Tự động tính toán mức đề xuất theo lịch sử vi phạm
    const suggestedRepeatIndex = useMemo(() => {
      if (!currentBehaviorRules.length) return 1;
      const prevCount = Number(violationHistory?.totalViolations || 0);
      const target = prevCount + 1;
      const maxIdx = Math.max(...currentBehaviorRules.map((r) => r.repeatIndex));
      return Math.min(target, maxIdx);
    }, [currentBehaviorRules, violationHistory]);

    // Đồng bộ mức đề xuất khi hành vi hoặc lịch sử thay đổi
    useEffect(() => {
      setSelectedRepeatIndex(suggestedRepeatIndex);
    }, [suggestedRepeatIndex]);

    // Quy tắc chế tài hiện hành
    const activeRule = useMemo(() => {
      if (!currentBehaviorRules.length) return null;
      return (
        currentBehaviorRules.find((r) => r.repeatIndex === selectedRepeatIndex) ||
        currentBehaviorRules[0]
      );
    }, [currentBehaviorRules, selectedRepeatIndex]);

  // Khi người dùng chủ động chọn hành vi vi phạm
  const handleSelectBehavior = (code) => {
    setSelectedBehaviorCode(code);
    const rule = allRules.find((r) => r.code === code);
    if (rule && rule.faultOutcome && rule.faultOutcome !== outcome) {
      setOutcome(rule.faultOutcome);
    }
  };

  const handleApplySanction = (rule) => {
    if (!rule) return;
      let text = `Căn cứ Quy chế xử lý đối với hành vi: "${rule.behavior}".\n- Mức áp dụng: ${rule.sanctionText}`;
      if (rule.adminActionText && !rule.adminActionText.includes('Không cần can thiệp')) {
        text += `\n- Đề xuất Quản trị viên: ${rule.adminActionText}`;
      }

      setRationale((prev) => {
        const trimmed = prev.trim();
        if (!trimmed || trimmed.length < 50) {
          return text;
        }
          if (!trimmed.includes(rule.behavior)) {
            return trimmed + '\n\n' + text;
          }
          return prev;
        });

      if (rule.defaultWaiverReason && !penaltyWaiverReason) {
        setPenaltyWaiverReason(rule.defaultWaiverReason);
      }
      showToast('Đã điền nội dung chế tài chuẩn vào lý do phán quyết', 'info');
    };

    const rationaleLen = rationale.trim().length;
    const isRationaleValid = rationaleLen >= 100 && rationaleLen <= 5000;

    const handleSubmit = async (e) => {
      e.preventDefault();
      if (!isRationaleValid) {
        showToast('Lý do quyết định phải từ 100 đến 5000 ký tự.', 'warning');
        return;
      }

      setSubmitting(true);
      try {
        await onSubmit(dispute.id, {
          outcome,
          rationale: rationale.trim(),
          penaltyWaiverReason: penaltyWaiverReason.trim() || null,
        });
        onClose();
      } catch {
        // Toast lỗi đã được hook xử lý
      } finally {
        setSubmitting(false);
      }
    };

    const statusLabel =
      DISPUTE_STATUS_CONFIG[dispute?.status]?.label || dispute?.status || 'Đang xử lý';

    return createPortal(
      <div className="dispute-modal-overlay" onClick={onClose}>
        <div className="dispute-modal" onClick={(e) => e.stopPropagation()}>
          <div className="dispute-modal-header">
            <div>
              <h3>
                {isEscalated
                  ? 'Phán Quyết Cuối Cùng Của Quản Trị Viên'
                  : 'Ban Hành Quyết Định Giải Quyết'}
              </h3>
              <span className="dispute-modal-subtitle">
                Mã đơn: <code>{dispute.id}</code> • Trạng thái: <strong>{statusLabel}</strong>
              </span>
            </div>
            <button type="button" className="dispute-modal-close" onClick={onClose}>
              &times;
            </button>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="dispute-modal-body">
              {/* Lựa chọn 1: Kết quả phán quyết */}
              <div className="dispute-field-group">
                <label>Kết quả phán quyết *</label>
                <select
                  className="dispute-select"
                  value={outcome}
                  onChange={(e) => setOutcome(e.target.value)}
                >
                  {Object.entries(DISPUTE_OUTCOMES).map(([k, val]) => (
                    <option key={k} value={val}>
                      {DISPUTE_OUTCOME_LABELS[val] || val}
                    </option>
                  ))}
                </select>
              </div>

                {/* Lựa chọn 2: Căn cứ hành vi vi phạm theo quy chế */ }
            {availableBehaviors.length > 0 && (
                <div className="dispute-rule-picker-group">
                  <label>Căn cứ hành vi vi phạm theo quy chế *</label>
                  <select
                    className="dispute-select"
                    value={selectedBehaviorCode}
                    onChange={(e) => handleSelectBehavior(e.target.value)}
                  >
                    {availableBehaviors.map((b) => (
                      <option key={b.code} value={b.code}>
                        {b.behavior}
                      </option>
                    ))}
                  </select>

                  {/* Chọn mức vi phạm (Lần 1, Lần 2, Lần 3) */}
                  {currentBehaviorRules.length > 1 && (
                    <div className="dispute-level-selector">
                      <span className="dispute-level-label">Mức xử lý:</span>
                      {currentBehaviorRules.map((r) => {
                        const isSuggested = r.repeatIndex === suggestedRepeatIndex;
                        const isActive = r.repeatIndex === selectedRepeatIndex;
                        return (
                          <button
                            key={r.repeatIndex}
                            type="button"
                            className={`dispute-level-btn ${isActive ? 'active' : ''} ${isSuggested ? 'suggested' : ''
                              }`}
                            onClick={() => setSelectedRepeatIndex(r.repeatIndex)}
                          >
                            Lần {r.repeatIndex}
                            {r.repeatWindowDays ? ` (${r.repeatWindowDays} ngày)` : ''}
                            {isSuggested && (
                              <span className="dispute-level-badge-suggested">Đề xuất</span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}

              {/* Banner hiển thị chi tiết quy tắc và chế tài xử lý */}
              {activeRule && (
                <div className="dispute-rule-banner">
                  <div className="dispute-rule-banner-header">
                    <span className="dispute-rule-banner-tag">
                      {activeRule.sanctionForm || 'Quy tắc xử lý'}
                    </span>
                    <button
                      type="button"
                      className="btn-apply-sanction"
                      onClick={() => handleApplySanction(activeRule)}
                    >
                      Áp dụng vào lý do quyết định
                    </button>
                  </div>

                  <div className="dispute-rule-banner-content">
                    <p className="dispute-rule-banner-text">
                      <strong>
                        Mức áp dụng: Lần {activeRule.repeatIndex}
                        {activeRule.repeatWindowDays
                          ? ` (trong thời hạn ${activeRule.repeatWindowDays} ngày)`
                          : ' (Vi phạm lần đầu)'}
                        :
                      </strong>{' '}
                      {activeRule.sanctionText}
                    </p>
                      {
                        activeRule.adminActionText && (
                          <p className="dispute-rule-admin-action">
                            <strong>Đề xuất Quản trị viên:</strong> {activeRule.adminActionText}
                          </p>
                        )
                      }
                </div>
                </div>
              )}

              {/* Cảnh báo miễn trừ trùng lặp */}
              {hasNoShowSameShift && outcome === DISPUTE_OUTCOMES.WORKER_FAULT && (
                <div className="dispute-waiver-alert">
                  <strong>Miễn trừ trùng lặp:</strong> Ca làm việc này đã được hệ thống ghi nhận
                  xử lý vắng mặt không phép trước đó. Hệ thống đã tự động kích hoạt lý do miễn
                  trừ để tránh xử phạt trùng lặp cho cùng một sự việc.
                </div>
              )}

              {outcome === DISPUTE_OUTCOMES.WORKER_FAULT && !hasNoShowSameShift && (
                <div className="dispute-rule-notice">
                  <strong>Lưu ý:</strong> Quyết định có lỗi thuộc về nhân sự theo mặc định sẽ
                  khấu trừ 10 điểm uy tín (trừ trường hợp có lý do miễn trừ hợp lệ bên dưới).
                </div>
              )}

              {/* Lý do quyết định */}
              <div className="dispute-field-group">
                <label>Lý do quyết định (Tối thiểu 100 ký tự) *</label>
                <textarea
                  className="dispute-textarea"
                  rows={5}
                  placeholder="Nêu rõ căn cứ bằng chứng, đối chiếu nhật ký chấm công, điều khoản thỏa thuận ca và giải trình của hai bên..."
                  value={rationale}
                  onChange={(e) => setRationale(e.target.value)}
                  required
                />
                <div className={`char-counter ${isRationaleValid ? 'valid' : 'invalid'}`}>
                  {rationaleLen} / 100 ký tự tối thiểu{' '}
                  {isRationaleValid ? '— Đạt yêu cầu' : '— Chưa đủ'}
                </div>
              </div>

              {/* Lý do miễn trừ phạt điểm uy tín */}
              <div className="dispute-field-group">
                <label>Lý do miễn trừ phạt điểm uy tín (Nếu có)</label>
                <input
                  type="text"
                  className="dispute-input"
                  placeholder="Để trống nếu áp dụng trừ điểm theo quy định chuẩn..."
                  value={penaltyWaiverReason}
                  onChange={(e) => setPenaltyWaiverReason(e.target.value)}
                />
              </div>
            </div>

            <div className="dispute-modal-footer">
                <button
                  type="button"
                  className="btn-cancel"
                  onClick={onClose}
                  disabled={submitting}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="btn-submit"
                  disabled={submitting || !isRationaleValid}
                >
                  {submitting ? 'Đang lưu...' : 'Ban hành quyết định'}
                </button>
            </div>
          </form>
        </div>
      </div>,
      document.body
    );
  };

  export default DisputeDecisionModal;
