import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/profile/providers/block_list_provider.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';

class BlockedUsersScreen extends ConsumerWidget {
  const BlockedUsersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = Theme.of(context).profileX;
    final state = ref.watch(blockListProvider);
    final users = state.blockedUsers;

    return Scaffold(
      backgroundColor: t.canvas,
      appBar: AppBar(
        title: const Text(
          'Danh sách chặn',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.read(blockListProvider.notifier).loadBlockedList(),
          ),
        ],
      ),
      body: state.isLoading
          ? const Center(child: CircularProgressIndicator())
          : users.isEmpty
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.block_outlined, size: 64, color: t.muted),
                        const SizedBox(height: 16),
                        Text(
                          'Chưa có người dùng nào bị chặn',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                            color: t.text,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Những Ban tổ chức hoặc người dùng bạn đã chặn sẽ xuất hiện tại đây. Bạn sẽ không nhận được ca làm việc từ họ.',
                          textAlign: TextAlign.center,
                          style: TextStyle(fontSize: 13, color: t.muted, height: 1.4),
                        ),
                      ],
                    ),
                  ),
                )
              : ListView.separated(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                  itemCount: users.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final item = users[index];
                    return Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: t.surface,
                        borderRadius: BorderRadius.circular(t.radius),
                        border: Border.all(color: t.border),
                      ),
                      child: Row(
                        children: [
                          CircleAvatar(
                            radius: 20,
                            backgroundColor: const Color(0xFFFEE2E2),
                            child: const Icon(Icons.person_off_rounded, color: Color(0xFFDC2626), size: 20),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  item.blockedUserName,
                                  style: TextStyle(
                                    fontWeight: FontWeight.w700,
                                    fontSize: 14,
                                    color: t.text,
                                  ),
                                ),
                                if (item.reason != null && item.reason!.isNotEmpty) ...[
                                  const SizedBox(height: 2),
                                  Text(
                                    'Lý do: ${item.reason}',
                                    style: const TextStyle(fontSize: 12, color: Color(0xFFDC2626)),
                                  ),
                                ],
                              ],
                            ),
                          ),
                          OutlinedButton(
                            style: OutlinedButton.styleFrom(
                              foregroundColor: const Color(0xFF0066FF),
                              side: const BorderSide(color: Color(0xFFBFDBFE)),
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8),
                              ),
                            ),
                            onPressed: () async {
                              final confirm = await showDialog<bool>(
                                context: context,
                                builder: (ctx) => AlertDialog(
                                  title: const Text('Xác nhận bỏ chặn'),
                                  content: Text('Bạn có chắc chắn muốn bỏ chặn "${item.blockedUserName}" không?'),
                                  actions: [
                                    TextButton(
                                      onPressed: () => Navigator.of(ctx).pop(false),
                                      child: const Text('Hủy'),
                                    ),
                                    TextButton(
                                      onPressed: () => Navigator.of(ctx).pop(true),
                                      child: const Text('Bỏ chặn', style: TextStyle(color: Colors.red)),
                                    ),
                                  ],
                                ),
                              );

                              if (confirm == true) {
                                final targetId = item.blockedUserId.isNotEmpty ? item.blockedUserId : item.id;
                                final ok = await ref.read(blockListProvider.notifier).unblockUser(targetId);
                                if (context.mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(
                                      content: Text(ok
                                          ? 'Đã bỏ chặn thành công'
                                          : 'Không thể bỏ chặn người dùng'),
                                    ),
                                  );
                                }
                              }
                            },
                            child: const Text(
                              'Bỏ chặn',
                              style: TextStyle(
                                fontWeight: FontWeight.w600,
                                fontSize: 12.5,
                              ),
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
    );
  }
}
