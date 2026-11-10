import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';

export function useSubscription() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [plans, setPlans] = useState([]);
  const [currentSubscription, setCurrentSubscription] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [purchasingPlan, setPurchasingPlan] = useState(null);
  const [activeSubTab, setActiveSubTab] = useState('plans'); // 'plans' | 'history'
  const [payOSModalData, setPayOSModalData] = useState(null);

  const loadSubscriptionData = useCallback(async () => {
    setLoading(true);
    try {
      const [plansData, currentData, historyData] = await Promise.all([
        eoService.getSubscriptionPlans(),
        eoService.getCurrentSubscription(),
        eoService.getSubscriptionHistory(),
      ]);
      setPlans(plansData || []);
      setCurrentSubscription(currentData || null);
      setHistory(historyData || []);
    } catch (err) {
      console.error('Lỗi nạp dữ liệu subscription:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  // Kiểm tra callback từ VNPay sau khi redirect về trang
  useEffect(() => {
    const handleVNPayReturn = async () => {
      if (searchParams.has('vnp_ResponseCode')) {
        const queryParams = {};
        for (const [key, value] of searchParams.entries()) {
          queryParams[key] = value;
        }

        const responseCode = queryParams.vnp_ResponseCode;
        if (responseCode === '00') {
          try {
            const activatedSub = await eoService.processVNPayCallback(queryParams);
            showToast('🎉 Thanh toán VNPay thành công! Gói cước đã được kích hoạt.', 'success');
            setCurrentSubscription(activatedSub);
            loadSubscriptionData();
          } catch (error) {
            showToast(
              'Xác thực giao dịch VNPay thất bại: ' + (error.response?.data?.message || error.message),
              'error'
            );
          }
        } else if (responseCode === '24') {
          showToast('Giao dịch thanh toán VNPay đã bị hủy bởi người dùng.', 'info');
        } else {
          showToast(`Thanh toán VNPay không thành công (Mã lỗi: ${responseCode}).`, 'error');
        }

        // Dọn sạch query params VNPay khỏi URL để không kích hoạt lại khi reload
        const newParams = new URLSearchParams(searchParams);
        const vnpKeys = Array.from(newParams.keys()).filter((k) => k.startsWith('vnp_'));
        vnpKeys.forEach((k) => newParams.delete(k));
        setSearchParams(newParams, { replace: true });
      } else if (searchParams.get('payos') === 'success') {
        showToast('🎉 Thanh toán VietQR PayOS thành công! Gói cước đã được kích hoạt.', 'success');
        loadSubscriptionData();
        const newParams = new URLSearchParams(searchParams);
        newParams.delete('payos');
        setSearchParams(newParams, { replace: true });
      }
    };

    handleVNPayReturn();
  }, [searchParams, setSearchParams, loadSubscriptionData]);

  useEffect(() => {
    loadSubscriptionData();
  }, [loadSubscriptionData]);

  // UC-11: Khởi tạo thanh toán VNPay Sandbox
  const handlePurchaseVNPay = async (planType) => {
    setPurchasingPlan(planType);
    try {
      const result = await eoService.purchaseSubscription({ planType });
      if (result && result.paymentUrl) {
        showToast('Đang chuyển hướng sang cổng thanh toán VNPay Sandbox...', 'info');
        // Chuyển hướng trình duyệt sang cổng thanh toán VNPay
        window.location.href = result.paymentUrl;
      } else {
        showToast('Không nhận được liên kết thanh toán từ máy chủ.', 'error');
      }
    } catch (err) {
      showToast('Lỗi khởi tạo thanh toán: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setPurchasingPlan(null);
    }
  };

  // Khởi tạo thanh toán VietQR qua PayOS Gateway
  const handlePurchasePayOS = async (planType) => {
    setPurchasingPlan(planType);
    try {
      const result = await eoService.purchaseSubscriptionPayOS({ planType });
      if (result) {
        if (!result.checkoutUrl || result.amount === 0) {
          showToast('Kích hoạt Gói Trải Nghiệm Khởi Nghiệp thành công!', 'success');
          await loadSubscriptionData();
          return;
        }
        setPayOSModalData(result);
        showToast('Khởi tạo mã VietQR PayOS thành công!', 'success');
      }
    } catch (err) {
      showToast('Lỗi khởi tạo PayOS: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setPurchasingPlan(null);
    }
  };

  // Xác nhận đã chuyển khoản PayOS
  const handleConfirmPayOS = async (orderCode) => {
    try {
      const sub = await eoService.confirmPayOSPayment(orderCode);
      showToast('🎉 Kích hoạt gói cước thành công!', 'success');
      setCurrentSubscription(sub);
      setPayOSModalData(null);
      loadSubscriptionData();
    } catch (err) {
      showToast('Xác nhận thanh toán: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  // UC-11 (Demo Sandbox): Giả lập thanh toán thành công tức thì phục vụ demo hội đồng
  const handleSimulateDemo = async (planType = 'MATCHING_PLUS') => {
    setPurchasingPlan(planType);
    try {
      const result = await eoService.simulateSubscriptionDemo(planType);
      showToast('⚡ [DEMO] Giả lập thanh toán thành công! Gói dịch vụ đã được kích hoạt tức thì.', 'success');
      setCurrentSubscription(result);
      loadSubscriptionData();
    } catch (err) {
      showToast('Lỗi giả lập thanh toán: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setPurchasingPlan(null);
    }
  };

  return {
    plans,
    currentSubscription,
    history,
    loading,
    purchasingPlan,
    activeSubTab,
    setActiveSubTab,
    payOSModalData,
    setPayOSModalData,
    loadSubscriptionData,
    handlePurchaseVNPay,
    handlePurchasePayOS,
    handleConfirmPayOS,
    handleSimulateDemo,
  };
}

