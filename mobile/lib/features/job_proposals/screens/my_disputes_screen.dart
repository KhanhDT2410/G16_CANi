import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import '../data/proposal_repository.dart';
import '../models/dispute_models.dart';

class MyDisputesScreen extends ConsumerStatefulWidget {
  const MyDisputesScreen({super.key});

  @override
  ConsumerState<MyDisputesScreen> createState() => _MyDisputesScreenState();
}

class _MyDisputesScreenState extends ConsumerState<MyDisputesScreen> {
  Future<void> _refresh() async {
    ref.invalidate(myDisputesListProvider);
  }

  void _showCreateDisputeDialog() {
    final titleCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    final evidenceCtrl = TextEditingController();
    String selectedType = 'PAYMENT';
    bool isSubmitting = false;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (bottomSheetCtx) {
        return StatefulBuilder(
          builder: (ctx, setModalState) {
            final cani = Theme.of(ctx).cani;
            final isDark = Theme.of(ctx).brightness == Brightness.dark;

            return Container(
              padding: EdgeInsets.only(
                top: 20,
                left: 20,
                right: 20,
                bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
              ),
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF1E293B) : Colors.white,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Center(
                      child: Container(
                        width: 40,
                        height: 4,
                        decoration: BoxDecoration(
                          color: Colors.grey.withValues(alpha: 0.3),
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: cani.danger.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Icon(Icons.gavel_rounded, color: cani.danger, size: 22),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            'Tạo Khiếu Nại Tranh Chấp',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w800,
                              color: cani.textMain,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    DropdownButtonFormField<String>(
                      initialValue: selectedType,
                      decoration: InputDecoration(
                        labelText: 'Phân loại khiếu nại',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      ),
                      items: const [
                        DropdownMenuItem(value: 'PAYMENT', child: Text('Thù lao & Thanh toán lương')),
                        DropdownMenuItem(value: 'WORK_QUALITY', child: Text('Bất đồng về nhiệm vụ / Yêu cầu')),
                        DropdownMenuItem(value: 'NO_SHOW', child: Text('Thời gian / Điểm danh không chuẩn')),
                        DropdownMenuItem(value: 'CANCELATION', child: Text('Hủy ca đột xuất không báo trước')),
                        DropdownMenuItem(value: 'OTHER', child: Text('Vấn đề khác')),
                      ],
                      onChanged: (val) {
                        if (val != null) setModalState(() => selectedType = val);
                      },
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: titleCtrl,
                      decoration: InputDecoration(
                        labelText: 'Tiêu đề tóm tắt (*)',
                        hintText: 'Ví dụ: Chưa nhận được lương ca Phục vụ ngày 19/11',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      ),
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: descCtrl,
                      maxLines: 4,
                      decoration: InputDecoration(
                        labelText: 'Nội dung chi tiết vụ việc (*)',
                        hintText: 'Mô tả cụ thể thời gian, thỏa thuận với BTC và vấn đề phát sinh...',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                        contentPadding: const EdgeInsets.all(14),
                      ),
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: evidenceCtrl,
                      decoration: InputDecoration(
                        labelText: 'Bằng chứng (Link ảnh / ghi chú)',
                        hintText: 'Link ảnh chụp tin nhắn, biên lai...',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        prefixIcon: const Icon(Icons.link, size: 20),
                      ),
                    ),
                    const SizedBox(height: 20),
                    FilledButton(
                      style: FilledButton.styleFrom(
                        backgroundColor: cani.danger,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      onPressed: isSubmitting
                          ? null
                          : () async {
                              final title = titleCtrl.text.trim();
                              final desc = descCtrl.text.trim();
                              if (title.isEmpty || desc.isEmpty) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(content: Text('Vui lòng điền đủ tiêu đề và nội dung khiếu nại.')),
                                );
                                return;
                              }

                              setModalState(() => isSubmitting = true);
                              try {
                                final repo = ref.read(proposalRepositoryProvider);
                                await repo.createDispute(CreateDisputePayload(
                                  type: selectedType,
                                  title: title,
                                  description: desc,
                                  evidence: evidenceCtrl.text.trim().isNotEmpty ? evidenceCtrl.text.trim() : null,
                                ));

                                if (ctx.mounted) Navigator.pop(ctx);
                                ref.invalidate(myDisputesListProvider);
                                if (mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(
                                      content: Text('Đã gửi khiếu nại thành công! Bộ phận CSKH sẽ xem xét.'),
                                      backgroundColor: Colors.green,
                                    ),
                                  );
                                }
                              } catch (e) {
                                setModalState(() => isSubmitting = false);
                                if (mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Lỗi: $e'), backgroundColor: Colors.red),
                                  );
                                }
                              }
                            },
                      child: isSubmitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : const Text(
                              'GỬI KHIẾU NẠI NGAY',
                              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15),
                            ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final disputesAsync = ref.watch(myDisputesListProvider);

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Khiếu Nại & Tranh Chấp',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18),
        ),
        centerTitle: true,
        elevation: 0,
        backgroundColor: isDark ? const Color(0xFF1E293B) : Colors.white,
        actions: [
          IconButton(
            tooltip: 'Tạo khiếu nại mới',
            icon: const Icon(Icons.add_circle_outline, size: 24),
            onPressed: _showCreateDisputeDialog,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: disputesAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (err, _) => ListView(
            padding: const EdgeInsets.all(24),
            children: [
              const SizedBox(height: 40),
              Center(
                child: Column(
                  children: [
                    Icon(Icons.error_outline, size: 48, color: cani.danger),
                    const SizedBox(height: 12),
                    Text('Không thể tải danh sách khiếu nại: $err', textAlign: TextAlign.center),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      onPressed: _refresh,
                      child: const Text('Thử lại'),
                    ),
                  ],
                ),
              ),
            ],
          ),
          data: (disputes) {
            if (disputes.isEmpty) {
              return ListView(
                padding: const EdgeInsets.all(24),
                children: [
                  const SizedBox(height: 60),
                  Icon(Icons.verified_user_outlined, size: 64, color: cani.brand.withValues(alpha: 0.5)),
                  const SizedBox(height: 16),
                  Text(
                    'Không có tranh chấp nào',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: cani.textMain,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Mọi hoạt động ca làm và thanh toán của bạn hiện đang diễn ra thuận lợi. Nếu có phát sinh bất đồng với BTC, bạn có thể tạo khiếu nại tại đây.',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 13.5, color: cani.textMuted, height: 1.5),
                  ),
                  const SizedBox(height: 24),
                  Center(
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.add),
                      label: const Text('Tạo khiếu nại mới'),
                      onPressed: _showCreateDisputeDialog,
                    ),
                  ),
                ],
              );
            }

            return ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
              itemCount: disputes.length,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (ctx, idx) {
                final item = disputes[idx];
                final statusColor = _getStatusColor(item.status, cani);

                return Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF1E293B) : Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: cani.border),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.03),
                        blurRadius: 8,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(
                              color: cani.brandSoft,
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              item.typeLabel,
                              style: TextStyle(
                                fontSize: 11.5,
                                fontWeight: FontWeight.w700,
                                color: cani.brand,
                              ),
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(
                              color: statusColor.withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              item.statusLabel,
                              style: TextStyle(
                                fontSize: 11.5,
                                fontWeight: FontWeight.w800,
                                color: statusColor,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        item.title,
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w800,
                          color: cani.textMain,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        item.description,
                        style: TextStyle(
                          fontSize: 13,
                          color: cani.textMuted,
                          height: 1.4,
                        ),
                      ),
                      if (item.evidence != null && item.evidence!.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            const Icon(Icons.attachment, size: 14, color: Colors.blueGrey),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                'Bằng chứng: ${item.evidence}',
                                style: const TextStyle(fontSize: 11.5, color: Colors.blueGrey),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ],
                      if (item.resolution != null && item.resolution!.isNotEmpty) ...[
                        const SizedBox(height: 10),
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: Colors.green.withValues(alpha: 0.08),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: Colors.green.withValues(alpha: 0.2)),
                          ),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Icon(Icons.check_circle, size: 16, color: Colors.green),
                              const SizedBox(width: 6),
                              Expanded(
                                child: Text(
                                  'Kết quả phân xử: ${item.resolution}',
                                  style: const TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                    color: Colors.green,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                      if (item.createdAt != null) ...[
                        const SizedBox(height: 8),
                        Text(
                          'Ngày gửi: ${item.createdAt!.day}/${item.createdAt!.month}/${item.createdAt!.year}',
                          style: TextStyle(fontSize: 11, color: cani.textMuted),
                        ),
                      ],
                    ],
                  ),
                );
              },
            );
          },
        ),
      ),
    );
  }

  Color _getStatusColor(String status, CaniTokens cani) {
    switch (status) {
      case 'PENDING':
        return Colors.orange;
      case 'IN_REVIEW':
        return cani.brand;
      case 'RESOLVED':
        return Colors.green;
      case 'REJECTED':
        return cani.danger;
      case 'ESCALATED':
        return Colors.purple;
      default:
        return cani.textMuted;
    }
  }
}
