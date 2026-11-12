import { useState, useCallback } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';

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
];

export function usePayments() {
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedAssignment, setSelectedAssignment] = useState(null);

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

  const fetchShiftPayments = useCallback(async (shiftId) => {
    if (!shiftId) return;
    try {
      const data = await eoService.getShiftPayments(shiftId);
      if (Array.isArray(data) && data.length > 0) {
        setPayments(data);
      } else if (import.meta.env.VITE_DEMO === 'true') {
        setPayments(DEMO_PAYMENTS);
      } else {
        setPayments([]);
      }
    } catch (error) {
      if (import.meta.env.VITE_DEMO === 'true') {
        setPayments(DEMO_PAYMENTS);
      } else {
        showToast(error.response?.data?.message || 'Không thể tải danh sách thanh toán', 'error');
      }
    }
  }, []);

  const handleDeclarePayment = async (e) => {
    e.preventDefault();
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
                status: 'DECLARED_PENDING_WORKER',
              }
            : p
        )
      );
      showToast('Khai báo thanh toán thành công! Hệ thống đã gửi thông báo đến Worker để xác nhận.', 'success');
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

  const handleOpenPaymentDeclarationFromAttendance = (attendanceItem) => {
    setSelectedAssignment(attendanceItem);
    setPaymentForm({
      assignmentId: attendanceItem.assignmentId,
      declaredAmount: attendanceItem.agreedWage || 200000,
      paymentMethod: 'BANK_TRANSFER',
      externalRef: `VCB-${Date.now().toString().slice(-6)}`,
      paymentProofUrl: 'https://storage.cani.vn/proofs/bill_example.jpg',
    });
    setShowPaymentModal(true);
  };

  return {
    payments,
    setPayments,
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

