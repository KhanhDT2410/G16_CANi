import { supabase } from '../../auth/api/supabaseClient';

/**
 * API truy vấn dữ liệu người dùng trực tiếp từ Supabase (W-39).
 * Dùng khi backend Spring Boot chưa bật hoặc chưa triển khai endpoint tương ứng.
 * Mapping snake_case (DB) → camelCase (UI).
 */

/** Map một bản ghi Supabase → object UI chuẩn */
const mapUserRow = (row) => {
  const worker = row.workers;
  const eo = row.event_organizers;

  return {
    id: row.id,
    fullName: row.full_name,
    email: row.email,
    phone: row.phone || '',
    avatar: row.avatar_url || '',
    role: row.role,
    status: row.status,
    emailVerified: row.email_verified,
    createdAt: row.created_at
      ? new Date(row.created_at).toLocaleDateString('vi-VN')
      : '',
    lastLoginAt: row.last_login_at
      ? new Date(row.last_login_at).toLocaleString('vi-VN')
      : 'Chưa đăng nhập',

    // Worker-specific fields
    reliabilityScore: worker?.reliability_score ?? null,
    ratingAvg: worker ? parseFloat(worker.rating_avg) : null,
    shiftsCompleted: worker?.total_shifts_completed ?? 0,
    noShowCount: worker?.total_noshows ?? 0,
    ekycStatus: worker?.kyc_status || (eo?.verification_status) || 'N/A',
    skillTier: worker?.skill_tier || null,

    // EO-specific fields
    companyName: eo?.company_name || null,
  };
};

/** Lấy danh sách người dùng kèm thông tin worker/EO */
export const fetchUsersFromSupabase = async () => {
  const { data, error } = await supabase
    .from('users')
    .select(
      '*, workers(*), event_organizers:event_organizers!event_organizers_user_id_fkey(*)'
    )
    .order('created_at', { ascending: false });

  if (error) {
    console.error('[Supabase] fetchUsers error:', error);
    return null;
  }

  return data.map(mapUserRow);
};

/** Tính toán metrics tổng quan từ danh sách users */
export const computeMetricsFromUsers = (users) => {
  if (!users || users.length === 0) return null;

  const totalUsers = users.length;
  const activeUsers = users.filter((u) => u.status === 'ACTIVE').length;
  const workerCount = users.filter((u) => u.role === 'WORKER').length;
  const eoCount = users.filter((u) => u.role === 'EVENT_ORGANIZER').length;
  const suspendedOrBanned = users.filter(
    (u) => u.status === 'SUSPENDED' || u.status === 'BANNED'
  ).length;
  const pendingEkyc = users.filter(
    (u) => u.ekycStatus === 'PENDING'
  ).length;

  return {
    totalUsers,
    activeUsers,
    workerCount,
    eoCount,
    workerGrowth: 0,
    eoGrowth: 0,
    flaggedOrRestricted: suspendedOrBanned,
    pendingEkycCount: pendingEkyc,
  };
};

