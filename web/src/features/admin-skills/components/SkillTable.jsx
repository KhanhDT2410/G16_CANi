import { CATEGORY_COLOR_MAP } from '../types/adminSkill.constants';

/**
 * Bảng hiển thị danh sách kỹ năng
 * Tối giản, không icon, tuân thủ dưới 200 dòng
 */
export const SkillTable = ({
  skills = [],
  loading = false,
  onEdit,
  onToggleStatus,
  togglingId = null,
}) => {
  if (loading && skills.length === 0) {
    return (
      <div className="admin-skills-table-card">
        <div className="admin-skills-empty-state">
          Đang tải dữ liệu danh mục kỹ năng...
        </div>
      </div>
    );
  }

  if (!loading && skills.length === 0) {
    return (
      <div className="admin-skills-table-card">
        <div className="admin-skills-empty-state">
          Không tìm thấy kỹ năng nào phù hợp với bộ lọc hiện tại.
        </div>
      </div>
    );
  }

  return (
    <div className="admin-skills-table-card">
      <table className="admin-skills-table">
        <thead>
          <tr>
            <th style={{ width: '22%' }}>Tên Kỹ Năng</th>
            <th style={{ width: '18%' }}>Danh Mục</th>
            <th style={{ width: '38%' }}>Mô Tả Nghiệp Vụ</th>
            <th style={{ width: '12%' }}>Trạng Thái</th>
            <th style={{ width: '10%', textAlign: 'right' }}>Thao Tác</th>
          </tr>
        </thead>
        <tbody>
          {skills.map((skill) => {
            const catStyle = CATEGORY_COLOR_MAP[skill.category] || {
              bg: '#f1f5f9',
              text: '#475569',
              border: '#e2e8f0',
            };
            const isActive = skill.isActive !== false;
            const isToggling = togglingId === skill.skillId;

            return (
              <tr key={skill.skillId}>
                <td>
                  <strong style={{ color: '#0f172a' }}>{skill.name}</strong>
                </td>
                <td>
                  <span
                    className="skill-badge-category"
                    style={{
                      backgroundColor: catStyle.bg,
                      color: catStyle.text,
                      border: `1px solid ${catStyle.border}`,
                    }}
                  >
                    {skill.category || 'Khác'}
                  </span>
                </td>
                <td>
                  <span style={{ color: '#475569', lineHeight: 1.4 }}>
                    {skill.description || '—'}
                  </span>
                </td>
                <td>
                  <span className={`skill-badge-status ${isActive ? 'active' : 'inactive'}`}>
                    {isActive ? 'Đang hoạt động' : 'Tạm ẩn'}
                  </span>
                </td>
                <td style={{ textAlign: 'right' }}>
                  <div className="skill-actions-cell" style={{ justifyContent: 'flex-end' }}>
                    <button
                      type="button"
                      className="skill-action-btn edit"
                      onClick={() => onEdit(skill)}
                    >
                      Sửa
                    </button>
                    <button
                      type="button"
                      className={`skill-action-btn ${isActive ? 'toggle-hide' : 'toggle-active'}`}
                      onClick={() => onToggleStatus(skill.skillId)}
                      disabled={isToggling}
                    >
                      {isToggling ? '...' : isActive ? 'Ẩn' : 'Kích hoạt'}
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};
