import React, { useMemo } from 'react';

/**
 * EOOverviewAnalytics
 * Trung Tâm Báo Cáo Hiệu Quả Vận Hành & Ngân Sách Nhân Sự Sự Kiện (Executive Operations & Staffing Analytics)
 * 
 * Giá trị nghiệp vụ thực tế 100%:
 * 1. Thẻ chỉ số ngân sách nhân sự: Dự toán quỹ lương, Đã quyết toán chi trả, Cần chi trả còn lại, Tiến độ tuyển dụng (Fill rate).
 * 2. Biểu đồ hình CỘT ĐỨNG (Vertical Column Chart): So sánh Dự toán quỹ lương vs Chi phí thực tế đã thanh toán.
 * 3. Cơ cấu chi phí nhân sự theo chuyên môn: Phân bổ quỹ lương theo từng vị trí (F&B, PG, Setup, Điều phối).
 * 4. Bảng quy mô & tiến độ sự kiện: Đối chiếu số ca, tỷ lệ lấp đầy nhân sự, dự toán và thực chi từng sự kiện.
 */
export default function EOOverviewAnalytics({ events = [], onSelectEvent }) {
  // 1. Lọc danh sách sự kiện chưa hủy
  const activeEvents = useMemo(() => {
    return (events || [])
      .filter((e) => e && e.status !== 'CANCELLED')
      .sort((a, b) => new Date(b.createdAt || b.eventDate || 0) - new Date(a.createdAt || a.eventDate || 0));
  }, [events]);

  // Format tiền tệ VNĐ
  const formatCurrency = (val) => new Intl.NumberFormat('vi-VN').format(Math.round(val || 0));

  // Format tiền tệ rút gọn cho trục biểu đồ (ví dụ: 15.5 Tr, 5 Tr)
  const formatShortCurrency = (val) => {
    if (!val || val === 0) return '0 đ';
    if (val >= 1000000) {
      const millions = val / 1000000;
      return `${millions % 1 === 0 ? millions : millions.toFixed(1)} Tr`;
    }
    if (val >= 1000) {
      return `${Math.round(val / 1000)}k`;
    }
    return `${val} đ`;
  };

  // 2. Tính toán ngân sách chi tiết dựa trên dữ liệu thật của từng sự kiện
  const eventsFinancialData = useMemo(() => {
    return activeEvents.map((evt) => {
      const shifts = evt.shifts || [];
      let calculatedLaborCost = 0;
      let totalReq = evt.totalRequiredWorkers || 0;
      let totalConf = evt.totalConfirmedWorkers || 0;

      shifts.forEach((sh) => {
        if (sh.status === 'CANCELLED') return;
        const req = Number(sh.requiredWorkers) || 1;
        const wage = Number(sh.hourlyWage) || 50000;
        const allowance = Number(sh.allowance) || 0;
        let hours = 4;
        if (sh.startTime && sh.endTime) {
          try {
            const h = (new Date(sh.endTime) - new Date(sh.startTime)) / 3600000;
            if (h > 0 && h <= 24) hours = h;
          } catch {
            // fallback
          }
        }
        calculatedLaborCost += (hours * wage * req) + (allowance * req);
      });

      const estimatedBudget = evt.totalEstimatedCost ? Number(evt.totalEstimatedCost) : calculatedLaborCost;
      const paidAmount = evt.totalPaid ? Number(evt.totalPaid) : 0;
      const remainingAmount = Math.max(0, estimatedBudget - paidAmount);
      const fillRate = totalReq > 0 ? Math.round((totalConf / totalReq) * 100) : 0;

      return {
        event: evt,
        eventId: evt.eventId,
        name: evt.name || 'Sự kiện không tên',
        eventDate: evt.eventDate,
        venueAddress: evt.venueAddress,
        shiftsCount: evt.totalShifts || shifts.length || 1,
        totalRequired: totalReq,
        totalConfirmed: totalConf,
        fillRate,
        estimatedBudget,
        paidAmount,
        remainingAmount,
      };
    });
  }, [activeEvents]);

  // 3. Tổng hợp toàn bộ chỉ số ngân sách nhân sự thực tế
  const totalSummary = useMemo(() => {
    const totalEstimatedBudget = eventsFinancialData.reduce((sum, item) => sum + item.estimatedBudget, 0);
    const totalPaidAmount = eventsFinancialData.reduce((sum, item) => sum + item.paidAmount, 0);
    const totalRemainingAmount = Math.max(0, totalEstimatedBudget - totalPaidAmount);
    const totalRequired = eventsFinancialData.reduce((sum, item) => sum + item.totalRequired, 0);
    const totalConfirmed = eventsFinancialData.reduce((sum, item) => sum + item.totalConfirmed, 0);
    const overallFillRate = totalRequired > 0 ? Math.round((totalConfirmed / totalRequired) * 100) : 0;

    return {
      totalEstimatedBudget,
      totalPaidAmount,
      totalRemainingAmount,
      totalRequired,
      totalConfirmed,
      overallFillRate,
    };
  }, [eventsFinancialData]);

  // 4. Cơ cấu chi phí nhân sự theo chuyên môn (Role Cost Allocation)
  const roleCostBreakdown = useMemo(() => {
    const map = {};
    activeEvents.forEach((evt) => {
      (evt.shifts || []).forEach((sh) => {
        if (sh.status === 'CANCELLED') return;
        const role = sh.jobType || 'Nhân sự tổng hợp';
        const req = Number(sh.requiredWorkers) || 1;
        const wage = Number(sh.hourlyWage) || 50000;
        const allowance = Number(sh.allowance) || 0;
        let hours = 4;
        if (sh.startTime && sh.endTime) {
          try {
            const h = (new Date(sh.endTime) - new Date(sh.startTime)) / 3600000;
            if (h > 0 && h <= 24) hours = h;
          } catch {
            // fallback
          }
        }
        const cost = (hours * wage * req) + (allowance * req);

        if (!map[role]) {
          map[role] = { role, totalCost: 0, totalWorkers: 0 };
        }
        map[role].totalCost += cost;
        map[role].totalWorkers += req;
      });
    });

    const list = Object.values(map);
    if (list.length === 0) {
      return [];
    }

    const totalCalculatedCost = list.reduce((sum, item) => sum + item.totalCost, 0) || 1;
    const colors = ['#6366f1', '#0ea5e9', '#f59e0b', '#10b981', '#ec4899', '#8b5cf6'];

    return list.map((item, idx) => ({
      ...item,
      color: colors[idx % colors.length],
      percent: Math.round((item.totalCost / totalCalculatedCost) * 100),
    }));
  }, [activeEvents]);

  // 5. Tính toán trần trục Y cho Biểu đồ Cột Đứng
  const chartMax = useMemo(() => {
    const maxVal = Math.max(...eventsFinancialData.map((e) => e.estimatedBudget), 5000000);
    const step = 2000000;
    return Math.ceil((maxVal * 1.15) / step) * step;
  }, [eventsFinancialData]);

  // Các mốc vạch trục Y: 100%, 75%, 50%, 25%, 0%
  const yAxisTicks = useMemo(() => {
    return [
      { percent: 100, val: chartMax },
      { percent: 75, val: chartMax * 0.75 },
      { percent: 50, val: chartMax * 0.5 },
      { percent: 25, val: chartMax * 0.25 },
      { percent: 0, val: 0 },
    ];
  }, [chartMax]);

  if (!activeEvents || activeEvents.length === 0 || eventsFinancialData.length === 0) {
    return (
      <div className="eo-widget-card animate-fade-in-up" style={{ marginTop: '1.25rem', padding: '3.5rem 1.5rem', textAlign: 'center', color: '#64748b' }}>
        <div style={{ fontSize: '2.5rem', marginBottom: '0.75rem' }}>📊</div>
        <h4 style={{ margin: '0 0 0.5rem', fontSize: '1.1rem', fontWeight: 700, color: '#1e293b' }}>
          Chưa Có Dữ Liệu Vận Hành & Ngân Sách
        </h4>
        <p style={{ margin: '0 auto', maxWidth: '480px', fontSize: '0.85rem', lineHeight: 1.5 }}>
          Khi bạn tạo và xuất bản các sự kiện, hệ thống sẽ tự động tổng hợp chi phí nhân sự, tiến độ tuyển dụng và cơ cấu vị trí việc làm tại đây.
        </p>
      </div>
    );
  }

  return (
    <div className="eo-overview-analytics-container animate-fade-in-up" style={{ marginTop: '1.25rem', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      
      {/* =========================================================================
          KHỐI 1: BÁO CÁO NGÂN SÁCH & VẬN HÀNH THỰC TẾ (REAL OPERATIONAL METRICS)
          ========================================================================= */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
        
        {/* Thẻ 1: Dự Toán Ngân Sách Nhân Sự */}
        <div className="eo-widget-card" style={{ padding: '1.15rem', borderLeft: '4px solid #6366f1' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            Dự Toán Ngân Sách
          </div>
          <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#1e1b4b', margin: '0.35rem 0 0.2rem' }}>
            {formatCurrency(totalSummary.totalEstimatedBudget)} đ
          </div>
          <div style={{ fontSize: '0.72rem', color: '#64748b' }}>
            Quỹ lương dự kiến cho {activeEvents.length} sự kiện
          </div>
        </div>

        {/* Thẻ 2: Đã Quyết Toán Chi Trả */}
        <div className="eo-widget-card" style={{ padding: '1.15rem', borderLeft: '4px solid #10b981' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            Đã Quyết Toán Chi Trả
          </div>
          <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#047857', margin: '0.35rem 0 0.2rem' }}>
            {formatCurrency(totalSummary.totalPaidAmount)} đ
          </div>
          <div style={{ fontSize: '0.72rem', color: '#64748b' }}>
            Tiền công đã thanh toán cho nhân sự
          </div>
        </div>

        {/* Thẻ 3: Cần Chi Trả Còn Lại */}
        <div className="eo-widget-card" style={{ padding: '1.15rem', borderLeft: '4px solid #f59e0b' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            Cần Chi Trả Còn Lại
          </div>
          <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#c2410c', margin: '0.35rem 0 0.2rem' }}>
            {formatCurrency(totalSummary.totalRemainingAmount)} đ
          </div>
          <div style={{ fontSize: '0.72rem', color: '#64748b' }}>
            Dự toán trừ tiền đã quyết toán
          </div>
        </div>

        {/* Thẻ 4: Tiến Độ Tuyển Dụng Tổng Thể */}
        <div className="eo-widget-card" style={{ padding: '1.15rem', borderLeft: '4px solid #0ea5e9' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            Tiến Độ Tuyển Dụng
          </div>
          <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#0369a1', margin: '0.35rem 0 0.2rem' }}>
            {totalSummary.overallFillRate}%
          </div>
          <div style={{ fontSize: '0.72rem', color: '#64748b' }}>
            {totalSummary.totalConfirmed}/{totalSummary.totalRequired} nhân sự đã xác nhận
          </div>
        </div>

      </div>

      {/* =========================================================================
          KHỐI 2: GRID 2 CỘT: BIỂU ĐỒ HÌNH CỘT ĐỨNG & CƠ CẤU CHI PHÍ THEO VAI TRÒ
          ========================================================================= */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '1.25rem', alignItems: 'stretch' }}>
        
        {/* CỘT TRÁI: BIỂU ĐỒ HÌNH CỘT ĐỨNG SO SÁNH DỰ TOÁN VS THỰC CHI */}
        <div className="eo-widget-card" style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.5rem' }}>
            <div>
              <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: '#0f172a' }}>
                Biểu Đồ Ngân Sách & Chi Trả Nhân Sự
              </h4>
              <p style={{ margin: '0.2rem 0 0', fontSize: '0.75rem', color: '#64748b' }}>
                Cột 1: Dự toán quỹ lương &bull; Cột 2: Đã quyết toán chi trả
              </p>
            </div>

            {/* Legend chú thích màu sắc cột kép */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem', fontSize: '0.72rem', fontWeight: 600 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                <span style={{ width: 10, height: 10, backgroundColor: '#6366f1', borderRadius: 2 }} />
                <span style={{ color: '#475569' }}>Dự toán</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                <span style={{ width: 10, height: 10, backgroundColor: '#10b981', borderRadius: 2 }} />
                <span style={{ color: '#475569' }}>Đã chi trả</span>
              </div>
            </div>
          </div>

          {/* VÙNG VẼ BIỂU ĐỒ CỘT ĐỨNG */}
          <div style={{ position: 'relative', height: '240px', display: 'flex', flexDirection: 'column', flex: 1 }}>
            
            {/* 1. Các vạch gióng ngang trục tung Y (Horizontal Grid Lines) */}
            <div style={{ position: 'absolute', top: 0, left: 45, right: 0, bottom: 42 }}>
              {yAxisTicks.map((tick, tIdx) => {
                const topPos = 100 - tick.percent;
                return (
                  <div
                    key={tIdx}
                    style={{
                      position: 'absolute',
                      top: `${topPos}%`,
                      left: 0,
                      right: 0,
                      borderTop: tick.percent === 0 ? '1px solid #cbd5e1' : '1px dashed #f1f5f9',
                      display: 'flex',
                      alignItems: 'center',
                    }}
                  >
                    <span
                      style={{
                        position: 'absolute',
                        left: -45,
                        fontSize: '0.68rem',
                        fontWeight: 600,
                        color: '#94a3b8',
                        width: 40,
                        textAlign: 'right',
                        transform: 'translateY(-50%)',
                      }}
                    >
                      {formatShortCurrency(tick.val)}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* 2. Các nhóm cột đứng (Vertical Columns Group) */}
            <div
              style={{
                position: 'absolute',
                top: 0,
                left: 55,
                right: 10,
                bottom: 0,
                display: 'flex',
                alignItems: 'flex-end',
                justifyContent: 'space-around',
                zIndex: 2,
              }}
            >
              {eventsFinancialData.slice(0, 4).map((item) => {
                const budgetHeightPercent = Math.min(100, Math.max(6, Math.round((item.estimatedBudget / chartMax) * 100)));
                const paidHeightPercent = item.paidAmount > 0
                  ? Math.min(100, Math.max(6, Math.round((item.paidAmount / chartMax) * 100)))
                  : 2;

                return (
                  <div
                    key={item.eventId}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      minWidth: '75px',
                      maxWidth: '110px',
                      flex: 1,
                    }}
                  >
                    {/* Cặp 2 cột đứng song song */}
                    <div
                      style={{
                        height: '185px',
                        display: 'flex',
                        alignItems: 'flex-end',
                        gap: '5px',
                        width: '100%',
                        justifyContent: 'center',
                        position: 'relative',
                      }}
                    >
                      {/* Cột 1: Dự toán ngân sách (Indigo) */}
                      <div
                        style={{
                          width: '20px',
                          height: `${budgetHeightPercent}%`,
                          background: 'linear-gradient(180deg, #6366f1 0%, #4f46e5 100%)',
                          borderRadius: '5px 5px 0 0',
                          boxShadow: '0 2px 6px rgba(99, 102, 241, 0.25)',
                          transition: 'height 0.4s ease',
                          cursor: 'pointer',
                          position: 'relative',
                        }}
                        title={`Dự toán: ${formatCurrency(item.estimatedBudget)} đ`}
                      />

                      {/* Cột 2: Đã thanh toán (Green) */}
                      <div
                        style={{
                          width: '20px',
                          height: `${paidHeightPercent}%`,
                          background: 'linear-gradient(180deg, #10b981 0%, #059669 100%)',
                          borderRadius: '5px 5px 0 0',
                          boxShadow: '0 2px 6px rgba(16, 185, 129, 0.25)',
                          transition: 'height 0.4s ease',
                          cursor: 'pointer',
                          position: 'relative',
                        }}
                        title={`Đã chi trả: ${formatCurrency(item.paidAmount)} đ`}
                      />
                    </div>

                    {/* Nhãn trục X dưới chân cột */}
                    <div
                      style={{
                        marginTop: '8px',
                        textAlign: 'center',
                        width: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                      }}
                    >
                      <span
                        style={{
                          fontSize: '0.72rem',
                          fontWeight: 700,
                          color: '#1e293b',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          maxWidth: '85px',
                          display: 'block',
                        }}
                        title={item.name}
                      >
                        {item.name}
                      </span>
                      <span
                        style={{
                          fontSize: '0.68rem',
                          fontWeight: 700,
                          color: item.fillRate >= 100 ? '#047857' : '#0369a1',
                          backgroundColor: item.fillRate >= 100 ? '#ecfdf5' : '#f0f9ff',
                          padding: '1px 5px',
                          borderRadius: 4,
                          marginTop: '2px',
                        }}
                      >
                        {item.totalConfirmed}/{item.totalRequired} NS ({item.fillRate}%)
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>

          </div>
        </div>

        {/* CỘT PHẢI: Cơ Cấu Chi Phí Lương Theo Vị Trí Chuyên Môn */}
        <div className="eo-widget-card" style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.15rem' }}>
            <div>
              <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: '#0f172a' }}>
                Cơ Cấu Chi Phí Lương Theo Vị Trí
              </h4>
              <p style={{ margin: '0.2rem 0 0', fontSize: '0.75rem', color: '#64748b' }}>
                Phân bổ quỹ lương theo từng nhóm chuyên môn ca làm
              </p>
            </div>
            <span style={{ fontSize: '0.72rem', fontWeight: 700, color: '#6366f1', backgroundColor: '#f5f3ff', padding: '3px 8px', borderRadius: 6 }}>
              {roleCostBreakdown.length} vị trí
            </span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.95rem', flex: 1, justifyContent: 'center' }}>
            {roleCostBreakdown.map((item, idx) => (
              <div key={idx} style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.82rem' }}>
                  <span style={{ fontWeight: 600, color: '#334155' }}>
                    {item.role} <span style={{ fontSize: '0.72rem', color: '#94a3b8' }}>({item.totalWorkers} người)</span>
                  </span>
                  <span style={{ fontWeight: 700, color: '#0f172a' }}>
                    {formatCurrency(item.totalCost)} đ <span style={{ fontSize: '0.72rem', color: '#64748b' }}>({item.percent}%)</span>
                  </span>
                </div>
                <div style={{ width: '100%', height: '8px', backgroundColor: '#f1f5f9', borderRadius: 4, overflow: 'hidden' }}>
                  <div
                    style={{
                      width: `${Math.min(100, Math.max(6, item.percent))}%`,
                      height: '100%',
                      backgroundColor: item.color || '#6366f1',
                      borderRadius: 4,
                      transition: 'width 0.4s ease-in-out',
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>

      </div>

      {/* =========================================================================
          KHỐI 3: BẢNG QUY MÔ & TIẾN ĐỘ NGÂN SÁCH CÁC SỰ KIỆN ĐANG QUẢN LÝ
          (CHUẨN ENTERPRISE, 1 NÚT THAO TÁC DUY NHẤT)
          ========================================================================= */}
      <div className="eo-widget-card" style={{ padding: '1.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
          <div>
            <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: '#0f172a' }}>
              Quy Mô & Tiến Độ Ngân Sách Các Sự Kiện Đang Quản Lý
            </h4>
            <p style={{ margin: '0.2rem 0 0', fontSize: '0.75rem', color: '#64748b' }}>
              Đối chiếu số ca, tiến độ lấp đầy nhân sự, dự toán quỹ lương và số tiền đã chi trả từng sự kiện
            </p>
          </div>
          <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
            Hiển thị <strong>{eventsFinancialData.length}</strong> sự kiện
          </span>
        </div>

        {eventsFinancialData.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '2rem', color: '#94a3b8', fontSize: '0.85rem' }}>
            Chưa có sự kiện nào đang hoạt động.
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="sub-table" style={{ width: '100%', textAlign: 'left', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #e2e8f0', color: '#64748b', fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  <th style={{ padding: '10px 12px' }}>Tên Sự Kiện</th>
                  <th style={{ padding: '10px 12px' }}>Thời Gian / Địa Điểm</th>
                  <th style={{ padding: '10px 12px', textAlign: 'center' }}>Số Ca</th>
                  <th style={{ padding: '10px 12px' }}>Lấp Đầy Nhân Sự</th>
                  <th style={{ padding: '10px 12px', textAlign: 'right' }}>Dự Toán Quỹ Lương</th>
                  <th style={{ padding: '10px 12px', textAlign: 'right' }}>Đã Quyết Toán</th>
                  <th style={{ padding: '10px 12px', textAlign: 'right' }}>Cần Chi Trả</th>
                  <th style={{ padding: '10px 12px', textAlign: 'center' }}>Thao Tác</th>
                </tr>
              </thead>
              <tbody>
                {eventsFinancialData.slice(0, 5).map((item) => (
                  <tr
                    key={item.eventId}
                    style={{ borderBottom: '1px solid #f1f5f9', fontSize: '0.85rem', transition: 'background 0.2s' }}
                    className="table-row-hover"
                  >
                    <td style={{ padding: '12px', fontWeight: 700, color: '#0f172a' }}>
                      {item.name}
                    </td>
                    <td style={{ padding: '12px', color: '#64748b', maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      <div>{item.eventDate ? new Date(item.eventDate).toLocaleDateString('vi-VN') : 'Chưa định ngày'}</div>
                      <div style={{ fontSize: '0.72rem', color: '#94a3b8' }}>{item.venueAddress || 'Chưa cập nhật địa chỉ'}</div>
                    </td>
                    <td style={{ padding: '12px', textAlign: 'center', fontWeight: 600 }}>
                      <span style={{ backgroundColor: '#f1f5f9', padding: '3px 8px', borderRadius: 6, fontSize: '0.78rem' }}>
                        {item.shiftsCount} ca
                      </span>
                    </td>
                    <td style={{ padding: '12px', minWidth: '140px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem', marginBottom: 3 }}>
                        <span style={{ fontWeight: 600, color: '#334155' }}>{item.totalConfirmed}/{item.totalRequired} người</span>
                        <span style={{ fontWeight: 700, color: item.fillRate >= 100 ? '#10b981' : '#6366f1' }}>{item.fillRate}%</span>
                      </div>
                      <div style={{ width: '100%', height: '5px', backgroundColor: '#f1f5f9', borderRadius: 3, overflow: 'hidden' }}>
                        <div
                          style={{
                            width: `${Math.min(100, Math.max(5, item.fillRate))}%`,
                            height: '100%',
                            backgroundColor: item.fillRate >= 100 ? '#10b981' : '#6366f1',
                            borderRadius: 3,
                          }}
                        />
                      </div>
                    </td>
                    <td style={{ padding: '12px', textAlign: 'right', fontWeight: 700, color: '#4338ca' }}>
                      {formatCurrency(item.estimatedBudget)} đ
                    </td>
                    <td style={{ padding: '12px', textAlign: 'right', fontWeight: 700, color: '#047857' }}>
                      {formatCurrency(item.paidAmount)} đ
                    </td>
                    <td style={{ padding: '12px', textAlign: 'right', fontWeight: 700, color: '#c2410c' }}>
                      {formatCurrency(item.remainingAmount)} đ
                    </td>
                    <td style={{ padding: '12px', textAlign: 'center' }}>
                      {/* DUY NHẤT 1 NÚT HÀNH ĐỘNG */}
                      <button
                        type="button"
                        className="eo-btn-secondary"
                        style={{ padding: '4px 10px', fontSize: '0.75rem', borderRadius: 6 }}
                        onClick={() => onSelectEvent?.(item.event)}
                      >
                        Chi tiết &rarr;
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

    </div>
  );
}
