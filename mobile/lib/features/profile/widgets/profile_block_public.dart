// CANi · tab "Tài khoản" — Block 7 · Public Profile / Hồ sơ công khai.
// Cho phép Worker bật/tắt hiển thị công khai trên Matching Radar và chia sẻ link profile (Privacy-first).

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfilePublicProfileBlock extends ConsumerStatefulWidget {
  const ProfilePublicProfileBlock({super.key});

  @override
  ConsumerState<ProfilePublicProfileBlock> createState() => _ProfilePublicProfileBlockState();
}

class _ProfilePublicProfileBlockState extends ConsumerState<ProfilePublicProfileBlock> {
  // Privacy-first: Mặc định không công khai rộng rãi khi chưa chủ động opt-in
  bool _isPublic = false;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    final user = ref.watch(authStateProvider).user;
    final workerCode = (user != null && user.id.length >= 8)
        ? user.id.substring(0, 8)
        : (user?.id ?? 'worker');
    final profileUrl = 'https://cani.vn/p/$workerCode';

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Hồ sơ công khai & Matching Radar',
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            color: t.text,
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Cho phép Ban Tổ Chức (EO) tìm thấy hồ sơ của bạn trên Radar tuyển dụng',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted, fontSize: 11.5),
                    ),
                  ],
                ),
              ),
              Switch(
                value: _isPublic,
                onChanged: (val) {
                  setState(() => _isPublic = val);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(val
                          ? 'Đã bật chế độ sẵn sàng nhận ca trên Radar (Chế độ Demo)'
                          : 'Đã ẩn hồ sơ khỏi Radar tìm việc (Chế độ Demo)'),
                      duration: const Duration(seconds: 2),
                    ),
                  );
                },
                activeThumbColor: t.accent,
                inactiveTrackColor: t.skeleton,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFFE2E8F0)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    '$profileUrl (Sắp ra mắt)',
                    style: TextStyle(
                      fontSize: 12,
                      color: t.muted,
                      fontWeight: FontWeight.w600,
                      fontStyle: FontStyle.italic,
                    ),
                  ),
                ),
                InkWell(
                  onTap: () {
                    Clipboard.setData(ClipboardData(text: profileUrl));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Đã sao chép link hồ sơ cá nhân (Tính năng Web Demo)!'),
                        duration: Duration(seconds: 2),
                      ),
                    );
                  },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    child: Text(
                      'Sao chép',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: t.accentDeep),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '* Tính năng hiển thị chi tiết hồ sơ Web công khai đang được cập nhật ở phiên bản tiếp theo.',
            style: TextStyle(fontSize: 10.5, color: t.muted, fontStyle: FontStyle.italic),
          ),
        ],
      ),
    );
  }
}
