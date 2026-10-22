import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';

class WorkerProfileTab extends ConsumerWidget {
  const WorkerProfileTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authStateProvider);
    final skillState = ref.watch(skillVerificationProvider);
    final user = authState.user;
    final theme = Theme.of(context);

    if (user == null) {
      return const Center(child: Text('Chưa có thông tin người dùng'));
    }

    final totalSp = skillState.skillPoints.fold<int>(0, (sum, p) => sum + p.totalSp);
    final currentTier = skillState.skillPoints.isNotEmpty ? skillState.skillPoints.first.tier : 'STARTER';

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20.0),
      child: Column(
        children: [
          // Profile Card
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            elevation: 1,
            child: Padding(
              padding: const EdgeInsets.all(24.0),
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 44,
                    backgroundColor: theme.colorScheme.primaryContainer,
                    child: Text(
                      user.fullName.isNotEmpty ? user.fullName[0].toUpperCase() : 'W',
                      style: theme.textTheme.headlineMedium?.copyWith(
                        color: theme.colorScheme.onPrimaryContainer,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    user.fullName,
                    style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    user.email,
                    style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                  const SizedBox(height: 8),
                  Chip(
                    label: Text('Vai trò: ${user.role}'),
                    backgroundColor: theme.colorScheme.secondaryContainer,
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 16),

          // Thẻ eKYC Định danh Căn cước
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            elevation: 1,
            color: theme.colorScheme.primaryContainer.withValues(alpha: 0.3),
            child: ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              leading: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary,
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.verified_user, color: Colors.white, size: 24),
              ),
              title: const Text('Xác Thực Định Danh eKYC (CCCD)', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
              subtitle: const Text('Quét căn cước & so khớp khuôn mặt AI để nhận tích xanh'),
              trailing: const Icon(Icons.arrow_forward_ios, size: 16),
              onTap: () {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
                );
              },
            ),
          ),

          const SizedBox(height: 16),

          // Chỉ số uy tín & kỹ năng
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            elevation: 1,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                children: [
                  const ListTile(
                    leading: Icon(Icons.verified_user_outlined, color: Colors.blue),
                    title: Text('Điểm Tin Cậy Khởi Điểm'),
                    trailing: Text('100 / 100', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                  ),
                  const Divider(),
                  ListTile(
                    leading: const Icon(Icons.workspace_premium, color: Colors.amber),
                    title: const Text('Hạng Bậc Kỹ Năng'),
                    trailing: Text(
                      currentTier,
                      style: TextStyle(color: theme.colorScheme.primary, fontWeight: FontWeight.bold, fontSize: 16),
                    ),
                  ),
                  const Divider(),
                  ListTile(
                    leading: const Icon(Icons.bolt, color: Colors.purple),
                    title: const Text('Tổng Điểm Tích Lũy'),
                    trailing: Text(
                      '$totalSp SP',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.purple),
                    ),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 20),

          // CV & Thông tin hồ sơ sự kiện
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            elevation: 1,
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Thông Tin CV & Nghề Nghiệp',
                        style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                      ),
                      IconButton(
                        icon: const Icon(Icons.edit_outlined, size: 20),
                        tooltip: 'Chỉnh sửa CV',
                        onPressed: () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Tính năng cập nhật Bio/Lương đang được lưu tự động trên hệ thống')),
                          );
                        },
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  const ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.history, color: Colors.teal),
                    title: Text('Kinh nghiệm làm việc'),
                    trailing: Text('1 năm', style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                  const ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.attach_money, color: Colors.green),
                    title: Text('Mức lương mong muốn'),
                    trailing: Text('35.000 đ / giờ', style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                  const ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.location_on_outlined, color: Colors.redAccent),
                    title: Text('Khu vực hoạt động'),
                    trailing: Text('TP. Hồ Chí Minh', style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                  const Divider(),
                  const SizedBox(height: 8),
                  const Text('Giới thiệu bản thân (Bio):', style: TextStyle(fontSize: 12, color: Colors.grey)),
                  const SizedBox(height: 6),
                  const Text(
                    'Nhân viên sự kiện năng động, trách nhiệm cao, có kinh nghiệm phục vụ tiệc cưới, hội nghị và pha chế đồ uống. Luôn đúng giờ và tuân thủ quy chuẩn dịch vụ.',
                    style: TextStyle(fontSize: 13, height: 1.4),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 24),

          // Nút Đăng Xuất
          SizedBox(
            width: double.infinity,
            height: 48,
            child: OutlinedButton.icon(
              onPressed: () => ref.read(authStateProvider.notifier).logout(),
              icon: const Icon(Icons.logout, color: Colors.redAccent),
              label: const Text('Đăng Xuất Khỏi Tài Khoản', style: TextStyle(color: Colors.redAccent, fontWeight: FontWeight.bold)),
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Colors.redAccent),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}
