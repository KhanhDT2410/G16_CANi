import React, { useState, useMemo } from 'react';

/**
 * Chuyển đổi số tiền thành chữ tiếng Việt chuẩn kế toán
 */
function numberToVietnameseWords(n) {
  if (!n || isNaN(n) || n === 0) return 'Không đồng';
  const defaultNumbers = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
  const units = ['', 'nghìn', 'triệu', 'tỷ', 'nghìn tỷ', 'triệu tỷ'];

  const readThreeDigits = (threeDigits, isFirstGroup) => {
    let result = '';
    const h = Math.floor(threeDigits / 100);
    const t = Math.floor((threeDigits % 100) / 10);
    const o = threeDigits % 10;

    if (h > 0 || !isFirstGroup) {
      result += defaultNumbers[h] + ' trăm ';
    }

    if (t > 1) {
      result += defaultNumbers[t] + ' mươi ';
      if (o === 1) result += 'mốt ';
      else if (o === 5) result += 'lăm ';
      else if (o > 0) result += defaultNumbers[o] + ' ';
    } else if (t === 1) {
      result += 'mười ';
      if (o === 5) result += 'lăm ';
      else if (o > 0) result += defaultNumbers[o] + ' ';
    } else {
      if (h > 0 && o > 0) result += 'lẻ ' + defaultNumbers[o] + ' ';
      else if (o > 0) result += defaultNumbers[o] + ' ';
    }

    return result.trim();
  };

  let num = Math.abs(Math.floor(n));
  const groups = [];
  while (num > 0) {
    groups.push(num % 1000);
    num = Math.floor(num / 1000);
  }

  let words = '';
  for (let i = groups.length - 1; i >= 0; i--) {
    const val = groups[i];
    if (val > 0) {
      const isFirst = i === groups.length - 1;
      const groupText = readThreeDigits(val, isFirst);
      words += (words ? ' ' : '') + groupText + ' ' + units[i];
    }
  }

  words = words.trim();
  if (!words) return 'Không đồng';
  return words.charAt(0).toUpperCase() + words.slice(1) + ' đồng chẵn.';
}

/**
 * SettlementVoucherModal
 * Xuất Phiếu Chi Thù Lao & Bảng Quyết Toán Nhân Sự Sự Kiện Chuẩn Kế Toán (Mẫu số 02-TT / NĐ 52/2024/NĐ-CP)
 */
export default function SettlementVoucherModal({
  isOpen,
  onClose,
  events = [],
  payments = [],
  companyInfo = {},
}) {
  const [selectedEventId, setSelectedEventId] = useState('ALL');

  if (!isOpen) return null;

  // Lọc thanh toán theo sự kiện được chọn
  const filteredPayments = useMemo(() => {
    if (selectedEventId === 'ALL') return payments;
    return payments.filter((p) => p.eventId === selectedEventId || p.event_id === selectedEventId);
  }, [payments, selectedEventId]);

  // Tìm sự kiện được chọn để lấy địa chỉ và tên
  const activeEvent = useMemo(() => {
    if (selectedEventId === 'ALL') {
      return events[0] || { name: 'Toàn bộ sự kiện đã kết thúc', venueAddress: 'Các địa điểm sự kiện đã tổ chức' };
    }
    return events.find((e) => e.eventId === selectedEventId) || { name: 'Sự kiện điều phối CANi', venueAddress: '' };
  }, [events, selectedEventId]);

  const totalAmount = filteredPayments.reduce((acc, p) => acc + (p.declaredAmount || p.agreedAmount || 0), 0);
  const totalAgreed = filteredPayments.reduce((acc, p) => acc + (p.agreedAmount || 0), 0);
  const totalAllowance = Math.max(0, totalAmount - totalAgreed);
  const amountInWords = numberToVietnameseWords(totalAmount);

  const today = new Date();
  const voucherCode = `PC-CANi-${today.getFullYear()}${String(today.getMonth() + 1).padStart(2, '0')}${String(today.getDate()).padStart(2, '0')}-${String(Math.floor(1000 + Math.random() * 9000))}`;

  const formatCurrency = (val) => new Intl.NumberFormat('vi-VN').format(val || 0);

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="eo-modal-overlay cani-voucher-modal-overlay">
      <style>{`
        @media print {
          body * {
            visibility: hidden !important;
          }
          .cani-voucher-modal-overlay,
          .cani-printable-voucher,
          .cani-printable-voucher * {
            visibility: visible !important;
          }
          .cani-voucher-modal-overlay {
            position: absolute !important;
            left: 0 !important;
            top: 0 !important;
            width: 100% !important;
            height: auto !important;
            background: transparent !important;
            padding: 0 !important;
            margin: 0 !important;
            display: block !important;
          }
          .cani-printable-voucher {
            width: 100% !important;
            max-width: 100% !important;
            box-shadow: none !important;
            border: none !important;
            padding: 1.5cm 1.5cm !important;
            margin: 0 !important;
          }
          .cani-voucher-no-print {
            display: none !important;
          }
        }
      `}</style>

      <div
        className="eo-modal-box animate-fade-in-up"
        style={{
          maxWidth: 920,
          width: '96%',
          maxHeight: '94vh',
          overflowY: 'auto',
          padding: '1.75rem',
          backgroundColor: '#ffffff',
        }}
      >
        {/* THANH ĐIỀU KHIỂN TRÊN CÙNG (ẨN KHI IN) */}
        <div
          className="cani-voucher-no-print"
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            paddingBottom: '1rem',
            borderBottom: '1px solid #e2e8f0',
            marginBottom: '1.5rem',
            flexWrap: 'wrap',
            gap: '0.75rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#334155' }}>
              Chọn sự kiện quyết toán:
            </span>
            <select
              className="filter-select"
              value={selectedEventId}
              onChange={(e) => setSelectedEventId(e.target.value)}
              style={{ minWidth: 240 }}
            >
              <option value="ALL">Tất cả khoản thanh toán ({payments.length})</option>
              {events.map((evt) => (
                <option key={evt.eventId} value={evt.eventId}>
                  {evt.name}
                </option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', gap: '0.6rem' }}>
            <button
              type="button"
              onClick={handlePrint}
              style={{
                backgroundColor: '#059669',
                color: '#ffffff',
                border: 'none',
                borderRadius: 6,
                padding: '0.55rem 1.15rem',
                fontSize: '0.85rem',
                fontWeight: 700,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                boxShadow: '0 2px 8px rgba(5, 150, 105, 0.25)',
              }}
            >
              In Phiếu Chi / Xuất PDF (A4)
            </button>
            <button
              type="button"
              onClick={onClose}
              style={{
                backgroundColor: '#f1f5f9',
                color: '#475569',
                border: '1px solid #cbd5e1',
                borderRadius: 6,
                padding: '0.55rem 1rem',
                fontSize: '0.85rem',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Đóng
            </button>
          </div>
        </div>

        {/* NỘI DUNG PHIẾU CHI CHUẨN KẾ TOÁN IN ẤN */}
        <div
          className="cani-printable-voucher"
          style={{
            color: '#1e293b',
            fontFamily: "'Times New Roman', Times, serif, system-ui",
            fontSize: '13pt',
            lineHeight: 1.5,
          }}
        >
          {/* QUỐC HIỆU & TIÊU ĐỀ BIỂU MẪU */}
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ textAlign: 'left', maxWidth: '55%' }}>
              <div style={{ fontWeight: 'bold', textTransform: 'uppercase', fontSize: '11pt' }}>
                {companyInfo.companyName || 'BAN TỔ CHỨC SỰ KIỆN CANI'}
              </div>
              <div style={{ fontSize: '10pt' }}>
                MST: {companyInfo.taxCode || '0108923489'}
              </div>
              <div style={{ fontSize: '10pt', color: '#334155' }}>
                Địa chỉ: {companyInfo.address || 'Tòa nhà FPT, Khu CNC Hòa Lạc, Thạch Thất, Hà Nội'}
              </div>
            </div>

            <div style={{ textAlign: 'center' }}>
              <div style={{ fontWeight: 'bold', fontSize: '11pt' }}>Mẫu số 02 - TT</div>
              <div style={{ fontSize: '9.5pt', fontStyle: 'italic', maxWidth: 260 }}>
                (Ban hành theo Thông tư số 200/2014/TT-BTC & NĐ 52/2024/NĐ-CP)
              </div>
              <div style={{ fontSize: '10pt', marginTop: '4px' }}>
                Số: <strong>{voucherCode}</strong>
              </div>
            </div>
          </div>

          {/* TIÊU ĐỀ CHÍNH */}
          <div style={{ textAlign: 'center', margin: '1.5rem 0 1rem' }}>
            <h2
              style={{
                margin: '0 0 0.25rem',
                fontSize: '17pt',
                fontWeight: 'bold',
                textTransform: 'uppercase',
                letterSpacing: '0.02em',
              }}
            >
              PHIẾU CHI THÙ LAO & BẢNG QUYẾT TOÁN NHÂN SỰ
            </h2>
            <div style={{ fontStyle: 'italic', fontSize: '11pt' }}>
              Ngày {today.getDate()} tháng {today.getMonth() + 1} năm {today.getFullYear()}
            </div>
          </div>

          {/* THÔNG TIN CĂN CỨ CHI */}
          <div style={{ marginBottom: '1.25rem', fontSize: '11.5pt' }}>
            <div style={{ marginBottom: '0.35rem' }}>
              - Sự kiện: <strong>{activeEvent.name}</strong>
            </div>
            <div style={{ marginBottom: '0.35rem' }}>
              - Địa điểm tổ chức: {activeEvent.venueAddress || 'Tại địa điểm thỏa thuận'}
            </div>
            <div style={{ marginBottom: '0.35rem' }}>
              - Lý do chi: Quyết toán thù lao ca làm việc, hỗ trợ phụ cấp và điều phối nhân sự sự kiện.
            </div>
            <div style={{ marginBottom: '0.35rem' }}>
              - Phương thức thanh toán: <strong>Chuyển khoản trực tiếp VietQR NAPAS 247 (Non-Escrow)</strong>
            </div>
          </div>

          {/* BẢNG QUYẾT TOÁN CHI TIẾT */}
          <table
            style={{
              width: '100%',
              borderCollapse: 'collapse',
              marginBottom: '1.25rem',
              fontSize: '10.5pt',
            }}
          >
            <thead>
              <tr style={{ backgroundColor: '#f8fafc' }}>
                <th style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'center', width: '5%' }}>STT</th>
                <th style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'left', width: '22%' }}>Họ & Tên Nhân Sự</th>
                <th style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'center', width: '15%' }}>CCCD (Đã mã hóa)</th>
                <th style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'left', width: '18%' }}>Vị Trí / Ca</th>
                <th style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'right', width: '18%' }}>Thù Lao Cơ Bản</th>
                <th style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'right', width: '22%' }}>Thực Lĩnh (VNĐ)</th>
              </tr>
            </thead>
            <tbody>
              {filteredPayments.length === 0 ? (
                <tr>
                  <td colSpan="6" style={{ border: '1px solid #334155', padding: '12px', textAlign: 'center', fontStyle: 'italic' }}>
                    Chưa có giao dịch thanh toán nào được ghi nhận cho sự kiện này.
                  </td>
                </tr>
              ) : (
                filteredPayments.map((p, idx) => {
                  const workerCccd = p.workerCccd || '001099******';
                  const netAmount = p.declaredAmount > 0 ? p.declaredAmount : p.agreedAmount;
                  return (
                    <tr key={p.paymentId || idx}>
                      <td style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'center' }}>{idx + 1}</td>
                      <td style={{ border: '1px solid #334155', padding: '6px 8px', fontWeight: 600 }}>{p.workerName || 'Nhân sự CANi'}</td>
                      <td style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'center', fontFamily: 'monospace' }}>
                        {workerCccd}
                      </td>
                      <td style={{ border: '1px solid #334155', padding: '6px 8px' }}>{p.jobType || 'Nhân viên sự kiện'}</td>
                      <td style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'right' }}>
                        {formatCurrency(p.agreedAmount)} đ
                      </td>
                      <td style={{ border: '1px solid #334155', padding: '6px 8px', textAlign: 'right', fontWeight: 'bold' }}>
                        {formatCurrency(netAmount)} đ
                      </td>
                    </tr>
                  );
                })
              )}
              {/* DÒNG TỔNG CỘNG */}
              <tr style={{ fontWeight: 'bold', backgroundColor: '#f1f5f9' }}>
                <td colSpan="4" style={{ border: '1px solid #334155', padding: '8px', textAlign: 'center', textTransform: 'uppercase' }}>
                  Tổng Cộng Thực Chi
                </td>
                <td style={{ border: '1px solid #334155', padding: '8px', textAlign: 'right' }}>
                  {formatCurrency(totalAgreed)} đ
                </td>
                <td style={{ border: '1px solid #334155', padding: '8px', textAlign: 'right', fontSize: '11.5pt' }}>
                  {formatCurrency(totalAmount)} đ
                </td>
              </tr>
            </tbody>
          </table>

          {/* TỔNG TIỀN BẰNG CHỮ */}
          <div style={{ marginBottom: '1.25rem', fontSize: '11.5pt' }}>
            <div>
              - Tổng số tiền thanh toán (bằng số): <strong>{formatCurrency(totalAmount)} VNĐ</strong>
            </div>
            <div>
              - Tổng số tiền thanh toán (bằng chữ): <em>{amountInWords}</em>
            </div>
            <div style={{ fontSize: '10pt', color: '#475569', marginTop: '0.4rem', fontStyle: 'italic' }}>
              * Chứng từ kèm theo: Dữ liệu điểm danh GPS Check-in & Check-out qua QR Code hợp lệ trên nền tảng CANi Platform.
              Hai bên đã đối soát hoàn tất và không có khiếu nại phát sinh.
            </div>
          </div>

          {/* 3 KHỐI CHỮ KÝ TRANG TRỌNG */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              textAlign: 'center',
              marginTop: '2rem',
              pageBreakInside: 'avoid',
            }}
          >
            <div>
              <div style={{ fontWeight: 'bold' }}>Người Lập Phiếu</div>
              <div style={{ fontSize: '9.5pt', fontStyle: 'italic' }}>(Ký, ghi rõ họ tên)</div>
              <div style={{ height: 60 }} />
              <div style={{ fontWeight: 600 }}>{companyInfo.preparerName || 'Nguyễn Văn Quản Lý'}</div>
            </div>

            <div>
              <div style={{ fontWeight: 'bold' }}>Kế Toán Thanh Toán</div>
              <div style={{ fontSize: '9.5pt', fontStyle: 'italic' }}>(Ký, ghi rõ họ tên)</div>
              <div style={{ height: 60 }} />
              <div style={{ fontWeight: 600 }}>{companyInfo.accountantName || 'Trần Thị Kế Toán'}</div>
            </div>

            <div>
              <div style={{ fontWeight: 'bold' }}>Trưởng Ban Tổ Chức (EO)</div>
              <div style={{ fontSize: '9.5pt', fontStyle: 'italic' }}>(Ký, đóng dấu, họ tên)</div>
              <div style={{ height: 60 }} />
              <div style={{ fontWeight: 'bold', textTransform: 'uppercase' }}>
                {companyInfo.organizerName || 'Duy Hưng Phạm'}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
