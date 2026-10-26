import { useState, useEffect, useCallback } from 'react';
import * as api from '../api/adminDashboardApi';
import {
  MOCK_METRICS,
  MOCK_SYSTEM_HEALTH,
  MOCK_ACTIVE_EVENTS,
  MOCK_RECENT_DISPUTES,
  MOCK_CSKH_OPS,
} from '../types/adminDashboard.constants';

/**
 * Hook quản lý toàn bộ state cho Admin System Dashboard (W-38).
 * Tự động fallback sang mock data khi API chưa sẵn sàng.
 */
export const useAdminDashboard = () => {
  const [metrics, setMetrics] = useState(null);
  const [systemHealth, setSystemHealth] = useState([]);
  const [activeEvents, setActiveEvents] = useState([]);
  const [recentDisputes, setRecentDisputes] = useState([]);
  const [cskhOps, setCskhOps] = useState(null);
  const [loading, setLoading] = useState(true);
  const [lastRefresh, setLastRefresh] = useState(null);

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    try {
      const [metricsRes, healthRes, eventsRes, disputesRes, cskhRes] =
        await Promise.allSettled([
          api.fetchDashboardMetrics(),
          api.fetchSystemHealth(),
          api.fetchActiveEvents(),
          api.fetchRecentDisputes(),
          api.fetchCskhOperations(),
        ]);

      setMetrics(
        metricsRes.status === 'fulfilled'
          ? metricsRes.value.data?.data
          : MOCK_METRICS
      );
      setSystemHealth(
        healthRes.status === 'fulfilled'
          ? healthRes.value.data?.data
          : MOCK_SYSTEM_HEALTH
      );
      setActiveEvents(
        eventsRes.status === 'fulfilled'
          ? eventsRes.value.data?.data
          : MOCK_ACTIVE_EVENTS
      );
      setRecentDisputes(
        disputesRes.status === 'fulfilled'
          ? disputesRes.value.data?.data
          : MOCK_RECENT_DISPUTES
      );
      setCskhOps(
        cskhRes.status === 'fulfilled'
          ? cskhRes.value.data?.data
          : MOCK_CSKH_OPS
      );
    } catch {
      setMetrics(MOCK_METRICS);
      setSystemHealth(MOCK_SYSTEM_HEALTH);
      setActiveEvents(MOCK_ACTIVE_EVENTS);
      setRecentDisputes(MOCK_RECENT_DISPUTES);
      setCskhOps(MOCK_CSKH_OPS);
    } finally {
      setLoading(false);
      setLastRefresh(new Date());
    }
  }, []);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  return {
    metrics,
    systemHealth,
    activeEvents,
    recentDisputes,
    cskhOps,
    loading,
    lastRefresh,
    refresh: loadDashboard,
  };
};
