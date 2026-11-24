import { useState, useEffect, useMemo } from 'react';
import { disputeRuleApi } from '../api/disputeRuleApi';
import { DisputeRuleModal } from './DisputeRuleModal';
import { showToast } from '../../../components/common/Toast';
import { stripEnglishParentheses } from '../../disputes/types/dispute.constants';
import './AdminDisputeRules.css';

const TYPE_OPTIONS = [
  { value: 'ALL', label: 'Tất cả danh mục' },
  { value: 'ATTENDANCE', label: 'Điểm danh / Bỏ ca' },
  { value: 'WORK_QUALITY', label: 'Chất lượng công việc' },
  { value: 'PAYMENT', label: 'Thù lao tiền công' },
  { value: 'CONDUCT', label: 'Hành vi ứng xử' },
  { value: 'OTHER', label: 'Khác / Vu khống / Giả mạo' },
];

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'Tất cả trạng thái' },
  { value: 'ENABLED', label: 'Đang kích hoạt' },
  { value: 'DISABLED', label: 'Đã vô hiệu hóa' },
];

const VIOLATING_PARTY_MAP = {
  WORKER: 'Nhân sự',
  EO: 'Nhà tổ chức',
  BOTH: 'Cả hai bên',
};

const RULE_TYPE_MAP = {
  ATTENDANCE: 'Điểm danh',
  WORK_QUALITY: 'Chất lượng',
  PAYMENT: 'Thù lao',
  CONDUCT: 'Ứng xử',
  OTHER: 'Khác',
};

const FAULT_OUTCOME_MAP = {
  WORKER_FAULT: 'Lỗi Nhân sự',
  EO_FAULT: 'Lỗi Nhà tổ chức',
  PARTIAL: 'Lỗi cả hai',
  UNVERIFIED: 'Chưa xác minh',
  UPHOLD: 'Bác khiếu nại',
};

export const AdminDisputeRulesView = () => {
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedType, setSelectedType] = useState('ALL');
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [modalRule, setModalRule] = useState(null); // null: closed, {}: create, ruleObj: edit
  const [page, setPage] = useState(1);
  const pageSize = 8;

  const loadRules = async () => {
    setLoading(true);
    try {
      const data = await disputeRuleApi.getRules();
      setRules(data);
    } catch {
      showToast('Không thể tải danh sách quy tắc khiếu nại', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRules();
  }, []);

  const handleSaveRule = async (payload) => {
    try {
      if (modalRule?.id) {
        await disputeRuleApi.updateRule(modalRule.id, payload);
        showToast('Cập nhật quy tắc thành công', 'success');
      } else {
        await disputeRuleApi.createRule(payload);
        showToast('Tạo quy tắc mới thành công', 'success');
      }
      await loadRules();
    } catch {
      showToast('Thao tác lưu quy tắc thất bại', 'error');
      throw new Error();
    }
  };

  const handleDeleteRule = async (rule) => {
    if (!window.confirm(`Bạn có chắc chắn muốn vô hiệu hóa quy tắc "${rule.code}" không?`)) {
      return;
    }
    try {
      await disputeRuleApi.deleteRule(rule.id);
      showToast('Đã vô hiệu hóa quy tắc thành công', 'success');
      await loadRules();
    } catch {
      showToast('Vô hiệu hóa quy tắc thất bại', 'error');
    }
  };

  const filteredRules = useMemo(() => {
    return rules.filter((r) => {
      const matchType = selectedType === 'ALL' || r.ruleType === selectedType;
      const matchStatus =
        selectedStatus === 'ALL' ||
        (selectedStatus === 'ENABLED' && r.enabled) ||
        (selectedStatus === 'DISABLED' && !r.enabled);
      const s = searchTerm.trim().toLowerCase();
      const matchSearch =
        !s ||
        r.code?.toLowerCase().includes(s) ||
        r.behavior?.toLowerCase().includes(s) ||
        r.sanctionText?.toLowerCase().includes(s) ||
        r.specificPenalty?.toLowerCase().includes(s);
      return matchType && matchStatus && matchSearch;
    });
  }, [rules, selectedType, selectedStatus, searchTerm]);

  const totalPages = Math.max(1, Math.ceil(filteredRules.length / pageSize));
  const pagedRules = useMemo(() => {
    const start = (page - 1) * pageSize;
    return filteredRules.slice(start, start + pageSize);
  }, [filteredRules, page, pageSize]);

  return (
    <div className="admin-dispute-rules-container">
      {/* Action Header */}
      <div className="admin-dispute-rules-header">
        <div>
          <h3>Bộ Quy Tắc Xử Lý Khiếu Nại & Chế Tài</h3>
          <p className="admin-dispute-rules-subtitle">
            Cơ sở pháp lý và quy chuẩn mức phạt giúp CSKH (CC) và Quản trị viên ra phán quyết nhất quán.
          </p>
        </div>
        <button
          type="button"
          className="btn-primary-action"
          onClick={() => setModalRule({})}
        >
          Thêm quy tắc mới
        </button>
      </div>

      {/* Filter Bar */}
      <div className="admin-dispute-rules-filters">
        <input
          type="text"
          className="filter-search-input"
          placeholder="Tìm theo mã, hành vi, nội dung chế tài..."
          value={searchTerm}
          onChange={(e) => {
            setSearchTerm(e.target.value);
            setPage(1);
          }}
        />

        <select
          className="filter-select"
          value={selectedType}
          onChange={(e) => {
            setSelectedType(e.target.value);
            setPage(1);
          }}
        >
          {TYPE_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>

        <select
          className="filter-select"
          value={selectedStatus}
          onChange={(e) => {
            setSelectedStatus(e.target.value);
            setPage(1);
          }}
        >
          {STATUS_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>

        <button
          type="button"
          className="btn-filter-reset"
          onClick={() => {
            setSearchTerm('');
            setSelectedType('ALL');
            setSelectedStatus('ALL');
            setPage(1);
          }}
        >
          Đặt lại
        </button>
      </div>

      {/* Table */}
      <div className="admin-dispute-rules-table-wrapper">
        {loading ? (
          <div className="loading-state">Đang tải dữ liệu quy tắc...</div>
        ) : filteredRules.length === 0 ? (
          <div className="empty-state">Không tìm thấy quy tắc xử lý nào phù hợp.</div>
        ) : (
          <table className="admin-dispute-rules-table">
            <thead>
              <tr>
                <th style={{ width: '150px' }}>Mã & Phân loại</th>
                <th>Hành vi vi phạm</th>
                <th style={{ width: '90px' }}>Bên lỗi</th>
                <th style={{ width: '100px' }}>Tái phạm</th>
                <th>Chế tài quy chuẩn</th>
                <th>Lệnh cần Admin</th>
                <th style={{ width: '90px' }}>Trạng thái</th>
                <th style={{ width: '120px' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {pagedRules.map((rule) => (
                <tr key={rule.id} className={!rule.enabled ? 'row-disabled' : ''}>
                  <td className="rule-meta-cell">
                    <div className="rule-meta-col">
                      <span className="rule-code-badge">{rule.code}</span>
                      <span className="rule-type-badge">{RULE_TYPE_MAP[rule.ruleType] || rule.ruleType}</span>
                      <span className="rule-outcome-badge">{FAULT_OUTCOME_MAP[rule.faultOutcome] || rule.faultOutcome}</span>
                    </div>
                  </td>
                  <td>
                    <div className="rule-behavior-text">{stripEnglishParentheses(rule.behavior)}</div>
                    <small className="rule-sanction-form">{stripEnglishParentheses(rule.sanctionForm)}</small>
                  </td>
                  <td>
                    <span className={`party-badge party-${rule.violatingParty.toLowerCase()}`}>
                      {VIOLATING_PARTY_MAP[rule.violatingParty] || rule.violatingParty}
                    </span>
                  </td>
                  <td>
                    <div className="repeat-badge">
                      <span className="repeat-count">Lần {rule.repeatIndex}</span>
                      <span className="repeat-desc">
                        {rule.repeatWindowDays ? `(${rule.repeatWindowDays} ngày)` : '(mặc định)'}
                      </span>
                    </div>
                  </td>
                  <td>
                    <div className="rule-sanction-text">{stripEnglishParentheses(rule.sanctionText)}</div>
                    <div className="rule-specific-penalty">{stripEnglishParentheses(rule.specificPenalty)}</div>
                  </td>
                  <td>
                    {rule.adminActionText ? (
                      <span className="admin-action-badge">{stripEnglishParentheses(rule.adminActionText)}</span>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </td>
                  <td>
                    <span className={`status-pill ${rule.enabled ? 'status-enabled' : 'status-disabled'}`}>
                      {rule.enabled ? 'Đang bật' : 'Đã tắt'}
                    </span>
                  </td>
                  <td>
                    <div className="action-button-group">
                      <button
                        type="button"
                        className="btn-action-edit"
                        onClick={() => setModalRule(rule)}
                      >
                        Sửa
                      </button>
                      {rule.enabled && (
                        <button
                          type="button"
                          className="btn-action-delete"
                          onClick={() => handleDeleteRule(rule)}
                        >
                          Tắt
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="admin-dispute-rules-pagination">
          <button
            type="button"
            className="pagination-btn"
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            Trước
          </button>
          <span className="pagination-info">
            Trang <strong>{page}</strong> / {totalPages} (Tổng {filteredRules.length} quy tắc)
          </span>
          <button
            type="button"
            className="pagination-btn"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            Sau
          </button>
        </div>
      )}

      {/* Modal create / edit */}
      {modalRule && (
        <DisputeRuleModal
          rule={modalRule}
          onClose={() => setModalRule(null)}
          onSave={handleSaveRule}
        />
      )}
    </div>
  );
};
