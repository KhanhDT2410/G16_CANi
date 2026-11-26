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
      } else if (
        searchParams.get('payos') === 'success' ||
        searchParams.get('status') === 'PAID' ||
        /[?&]status=PAID\b/.test(window.location.href) ||
        (searchParams.get('orderCode') && searchParams.get('cancel') !== 'true') ||
        (/[?&]orderCode=(\d+)/.test(window.location.href) && !/[?&]cancel=true\b/.test(window.location.href))
      ) {
        const orderCode = searchParams.get('orderCode') || (window.location.href.match(/[?&]orderCode=(\d+)/)?.[1]);
        try {
          if (orderCode) {
            const activatedSub = await eoService.confirmPayOSPayment(orderCode);
            showToast('🎉 Thanh toán VietQR PayOS thành công! Gói cước đã được kích hoạt.', 'success');
            if (activatedSub) {
              setCurrentSubscription(activatedSub);
            }
          }
        } catch (error) {
          console.warn('Lỗi xác nhận PayOS từ callback:', error);
          const msg = error.response?.data?.message || error.message || 'Không thể kích hoạt gói dịch vụ';
          showToast(`Lỗi kích hoạt gói: ${msg}`, 'error');
        } finally {
          await loadSubscriptionData();
          const newParams = new URLSearchParams(searchParams);
          ['payos', 'orderCode', 'status', 'code', 'id', 'cancel'].forEach((k) => newParams.delete(k));
          newParams.set('tab', 'subscription');
          setSearchParams(newParams, { replace: true });
        }
      } else if (searchParams.get('payos') === 'cancel' || searchParams.get('cancel') === 'true' || /[?&]cancel=true\b/.test(window.location.href)) {
        showToast('Giao dịch thanh toán PayOS đã bị hủy.', 'info');
        const newParams = new URLSearchParams(searchParams);
        ['payos', 'orderCode', 'status', 'code', 'id', 'cancel'].forEach((k) => newParams.delete(k));
        newParams.set('tab', 'subscription');
        setSearchParams(newParams, { replace: true });
      }
    };

    handleVNPayReturn();
  }, [searchParams, setSearchParams, loadSubscriptionData]);

  useEffect(() => {
    loadSubscriptionData();
  }, [loadSubscriptionData]);

  // Khởi tạo thanh toán VietQR qua PayOS Gateway (Thanh toán thật - Redirect trực tiếp)
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
        // Thanh toán thật: Điều hướng trực tiếp sang trang PayOS Checkout
        if (result.checkoutUrl && result.checkoutUrl.startsWith('http')) {
          showToast('Đang chuyển hướng sang cổng thanh toán PayOS VietQR...', 'info');
          window.location.href = result.checkoutUrl;
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
    handlePurchasePayOS,
    handleConfirmPayOS,
  };
}

export default useSubscription;

