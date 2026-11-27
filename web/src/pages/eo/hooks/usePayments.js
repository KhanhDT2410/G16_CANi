import { useState, useCallback, useEffect } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { roundVndAmount } from '../utils/eventHelpers';

export function usePayments() {
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedAssignment, setSelectedAssignment] = useState(null);
  const [paymentQueue, setPaymentQueue] = useState([]);
  const [paymentQueueIndex, setPaymentQueueIndex] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const [paymentForm, setPaymentForm] = useState({
    assignmentId: '',
    declaredAmount: 220000,
    paymentMethod: 'BANK_TRANSFER',
    externalRef: '',
    paymentProofUrl: '',
  });

  const [payments, setPayments] = useState([]);

  const loadEoPayments = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setLoading(true);
    setError(null);
    try {
      const data = await eoService.getEoPayments();
      if (Array.isArray(data) && data.length > 0) {
        setPayments(data);
      } else {
        setPayments([]);
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải danh sách thanh toán';
      setError(msg);
    } finally {
      if (!silent) setLoading(false);
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
      } else {
        setPayments([]);
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải danh sách thanh toán';
      setError(msg);
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  const buildInitialFormForAssignment = useCallback((attendanceItem, preferredMethod = null) => {
    const hasBank = Boolean(attendanceItem.bankCode && attendanceItem.bankAccountNumber);
    const resolvedMethod =
      preferredMethod === 'BANK_TRANSFER'
        ? (hasBank ? 'BANK_TRANSFER' : 'CASH')
        : preferredMethod || attendanceItem.paymentMethod || (hasBank ? 'BANK_TRANSFER' : 'CASH');
    return {
      assignmentId: attendanceItem.assignmentId,
      declaredAmount: roundVndAmount(
        attendanceItem.declaredAmount ?? attendanceItem.agreedWage ?? attendanceItem.agreedAmount ?? 200000
      ),
      paymentMethod: resolvedMethod,
      externalRef: attendanceItem.externalRef || `VCB-${Date.now().toString().slice(-6)}`,
      paymentProofUrl: attendanceItem.paymentProofUrl || '',
    };
  }, []);

  const advanceOrClosePaymentQueue = useCallback((preferredMethod = null) => {
    if (paymentQueue.length > 0 && paymentQueueIndex < paymentQueue.length - 1) {
      const nextIdx = paymentQueueIndex + 1;
      const nextItem = paymentQueue[nextIdx];
      setPaymentQueueIndex(nextIdx);
      setSelectedAssignment(nextItem);
      setPaymentForm(buildInitialFormForAssignment(nextItem, preferredMethod));
      return true;
    }
    setShowPaymentModal(false);
    setPaymentQueue([]);
    setPaymentQueueIndex(0);
    return false;
  }, [paymentQueue, paymentQueueIndex, buildInitialFormForAssignment]);

  const handleDeclarePayment = async (e) => {
    e.preventDefault();
    if (paymentForm.paymentMethod === 'BANK_TRANSFER' && !paymentForm.paymentProofUrl) {
      showToast('Vui lòng tải lên ảnh chụp biên lai chuyển khoản làm bằng chứng đối soát!', 'warning');
      return;
    }
    try {
      await eoService.declarePayment(paymentForm);
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
      const movedNext = advanceOrClosePaymentQueue(paymentForm.paymentMethod);
      showToast(
        movedNext
          ? 'Đã lưu! Đã chuyển sang nhân sự tiếp theo trong hàng đợi.'
          : 'Khai báo thanh toán thành công! Hệ thống đã gửi thông báo & email chứng từ đến Worker.',
        'success'
      );
    } catch (error) {
      showToast(
        error.response?.data?.message || error.userMessage || error.message || 'Khai báo thanh toán thất bại. Vui lòng thử lại!',
        'error'
      );
    }
  };

  const handleOpenPaymentDeclarationFromAttendance = useCallback((attendanceItem) => {
    setPaymentQueue([]);
    setPaymentQueueIndex(0);
    setSelectedAssignment(attendanceItem);
    setPaymentForm(buildInitialFormForAssignment(attendanceItem));
    setShowPaymentModal(true);
  }, [buildInitialFormForAssignment]);

  const openPaymentQueue = useCallback((items = []) => {
    const valid = (Array.isArray(items) ? items : []).filter(Boolean);
    if (valid.length === 0) return;
    setPaymentQueue(valid);
    setPaymentQueueIndex(0);
    setSelectedAssignment(valid[0]);
    setPaymentForm(buildInitialFormForAssignment(valid[0]));
    setShowPaymentModal(true);
  }, [buildInitialFormForAssignment]);

  const closePaymentModal = useCallback(() => {
    setShowPaymentModal(false);
    setPaymentQueue([]);
    setPaymentQueueIndex(0);
  }, []);

  const skipPaymentQueueItem = useCallback(() => {
    advanceOrClosePaymentQueue(paymentForm.paymentMethod);
  }, [advanceOrClosePaymentQueue, paymentForm.paymentMethod]);

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
    closePaymentModal,
    selectedAssignment,
    setSelectedAssignment,
    paymentForm,
    setPaymentForm,
    handleDeclarePayment,
    handleOpenPaymentDeclarationFromAttendance,
    openPaymentQueue,
    skipPaymentQueueItem,
    paymentQueue,
    paymentQueueIndex,
  };
}

export default usePayments;

