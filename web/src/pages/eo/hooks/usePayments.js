import { useState, useCallback, useEffect } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { roundVndAmount } from '../utils/eventHelpers';

const DEMO_PAYMENTS = [
  {
    paymentId: 'pay-01',
    assignmentId: 'assign-101',
    workerName: 'Nguyễn Văn An',
    jobType: 'Lễ tân / Check-in',
    agreedAmount: 220000,
    declaredAmount: 220000,
    paymentMethod: 'BANK_TRANSFER',
    externalRef: 'VCB-987654321',
    status: 'DECLARED_PENDING_WORKER',
  },
  {
    paymentId: 'pay-02',
    assignmentId: 'assign-102',
    workerName: 'Trần Thị Hoa',
    jobType: 'Phục vụ bàn',
    agreedAmount: 200000,
    declaredAmount: 200000,
    paymentMethod: 'CASH',
    externalRef: 'TIENMAT-01',
    status: 'CONFIRMED_FULL',
  },
  {
    paymentId: 'pay-03',
    assignmentId: 'assign-103',
    workerName: 'Lê Văn Tuấn',
    jobType: 'Hậu cần / Setup sự kiện',
    agreedAmount: 250000,
    declaredAmount: 0,
    paymentMethod: '',
    externalRef: '',
    status: 'NOT_DECLARED',
  },
  {
    paymentId: 'pay-04',
    assignmentId: 'assign-104',
    workerName: 'Phạm Minh Đức',
    jobType: 'An ninh / Bảo vệ',
    agreedAmount: 300000,
    declaredAmount: 200000,
    paymentMethod: 'BANK_TRANSFER',
    externalRef: 'VCB-11223344',
    status: 'DISPUTED',
    disputeId: 'disp-01',
    disputeStatus: 'INVESTIGATING',
    disputeOutcome: null,
    disputeRationale: 'Worker phản hồi chưa nhận đủ phụ cấp làm thêm giờ sau ca. Chuyên viên CSKH đang thu thập đối soát thẻ chấm công.',
  },
  {
    paymentId: 'pay-05',
    assignmentId: 'assign-105',
    workerName: 'Hoàng Thị Mai',
    jobType: 'PG / Giới thiệu sản phẩm',
    agreedAmount: 350000,
    declaredAmount: 350000,
    paymentMethod: 'BANK_TRANSFER',
    externalRef: 'MB-55667788',
    status: 'CONFIRMED_FULL',
    disputeId: 'disp-02',
    disputeStatus: 'RESOLVED',
    disputeOutcome: 'WORKER_FAULT',
    disputeRationale: 'Qua đối chiếu nhật ký điểm danh GPS QR, Worker tự ý rời sự kiện sớm 2 tiếng không thông báo. Trừ 10 điểm RS và bảo lưu quyết toán cho EO.',
    disputeResolvedAt: '2026-11-20T10:30:00Z',
  },
];

export function usePayments() {
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedAssignment, setSelectedAssignment] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const [paymentForm, setPaymentForm] = useState({
    assignmentId: '',
    declaredAmount: 220000,
    paymentMethod: 'BANK_TRANSFER',
    externalRef: '',
    paymentProofUrl: '',
  });

  const [payments, setPayments] = useState(
    import.meta.env.VITE_DEMO === 'true' ? DEMO_PAYMENTS : []
  );

  const loadEoPayments = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await eoService.getEoPayments();
      if (Array.isArray(data) && data.length > 0) {
        setPayments(data);
      } else if (import.meta.env.VITE_DEMO === 'true') {
        setPayments(DEMO_PAYMENTS);
      } else {
        setPayments([]);
      }
    } catch (err) {
      if (import.meta.env.VITE_DEMO === 'true') {
        setPayments(DEMO_PAYMENTS);
      } else {
        const msg = err.response?.data?.message || 'Không thể tải danh sách thanh toán';
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchShiftPayments = useCallback(async (shiftId) => {
    if (!shiftId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await eoService.getShiftPayments(shiftId);
      if (Array.isArray(data) && data.length > 0) {
        setPayments(data);
      } else if (import.meta.env.VITE_DEMO === 'true') {
        setPayments(DEMO_PAYMENTS);
      } else {
        setPayments([]);
      }
    } catch (err) {
      if (import.meta.env.VITE_DEMO === 'true') {
        setPayments(DEMO_PAYMENTS);
      } else {
        const msg = err.response?.data?.message || 'Không thể tải danh sách thanh toán';
        setError(msg);
        showToast(msg, 'error');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  const handleDeclarePayment = async (e) => {
    e.preventDefault();
    if (paymentForm.paymentMethod === 'BANK_TRANSFER' && !paymentForm.paymentProofUrl) {
      showToast('Vui lòng tải lên ảnh chụp biên lai chuyển khoản làm bằng chứng đối soát!', 'warning');
      return;
    }
    try {
      await eoService.declarePayment(paymentForm);
      setShowPaymentModal(false);
      setPayments((prev) =>
        prev.map((p) =>
          p.assignmentId === paymentForm.assignmentId
            ? {
                ...p,
                declaredAmount: paymentForm.declaredAmount,
                paymentMethod: paymentForm.paymentMethod,
                externalRef: paymentForm.externalRef,
                paymentProofUrl: paymentForm.paymentProofUrl,
                status: 'DECLARED_PENDING_WORKER',
              }
            : p
        )
      );
      window.dispatchEvent(
        new CustomEvent('cani_payment_updated', {
          detail: { assignmentId: paymentForm.assignmentId },
        })
      );
      showToast('Khai báo thanh toán thành công! Hệ thống đã gửi thông báo & email chứng từ đến Worker.', 'success');
    } catch (error) {
      if (import.meta.env.VITE_DEMO === 'true') {
        setPayments((prev) =>
          prev.map((p) =>
            p.assignmentId === paymentForm.assignmentId
              ? {
                  ...p,
                  declaredAmount: paymentForm.declaredAmount,
                  paymentMethod: paymentForm.paymentMethod,
                  externalRef: paymentForm.externalRef,
                  status: 'DECLARED_PENDING_WORKER',
                }
              : p
          )
        );
        setShowPaymentModal(false);
        showToast('Khai báo thanh toán thành công (Chế độ Demo)! Đang chờ Worker xác nhận.', 'info');
      } else {
        showToast(error.response?.data?.message || 'Khai báo thanh toán thất bại. Vui lòng thử lại!', 'error');
      }
    }
  };

  const handleOpenPaymentDeclarationFromAttendance = useCallback((attendanceItem) => {
    setSelectedAssignment(attendanceItem);
    const hasBank = Boolean(attendanceItem.bankCode && attendanceItem.bankAccountNumber);
    setPaymentForm({
      assignmentId: attendanceItem.assignmentId,
      declaredAmount: roundVndAmount(
        attendanceItem.declaredAmount ?? attendanceItem.agreedWage ?? attendanceItem.agreedAmount ?? 200000
      ),
      paymentMethod: attendanceItem.paymentMethod || (hasBank ? 'BANK_TRANSFER' : 'CASH'),
      externalRef: attendanceItem.externalRef || `VCB-${Date.now().toString().slice(-6)}`,
      paymentProofUrl: attendanceItem.paymentProofUrl || '',
    });
    setShowPaymentModal(true);
  }, []);

  useEffect(() => {
    const handlePaymentUpdated = () => {
      loadEoPayments();
    };
    window.addEventListener('cani_payment_updated', handlePaymentUpdated);
    return () => window.removeEventListener('cani_payment_updated', handlePaymentUpdated);
  }, [loadEoPayments]);

  return {
    payments,
    loading,
    error,
    setPayments,
    loadEoPayments,
    fetchShiftPayments,
    showPaymentModal,
    setShowPaymentModal,
    selectedAssignment,
    setSelectedAssignment,
    paymentForm,
    setPaymentForm,
    handleDeclarePayment,
    handleOpenPaymentDeclarationFromAttendance,
  };
}

export default usePayments;

