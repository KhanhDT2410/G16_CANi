import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';

class CancelAssignmentDialog extends ConsumerStatefulWidget {
  final ShiftAssignmentItem assignment;

  const CancelAssignmentDialog({
    super.key,
    required this.assignment,
  });

  @override
  ConsumerState<CancelAssignmentDialog> createState() => _CancelAssignmentDialogState();
}

class _CancelAssignmentDialogState extends ConsumerState<CancelAssignmentDialog> {
  final TextEditingController _reasonCtrl = TextEditingController();
  String _selectedQuickReason = 'Có việc cá nhân / lịch học đột xuất';
  bool _isSubmitting = false;

  final List<String> _quickReasons = const [
    'Có việc cá nhân / lịch học đột xuất',
    'Vấn đề sức khỏe / ốm đột xuất',
    'Phương tiện di chuyển gặp sự cố',
    'Lý do khác',
  ];

  @override
  void dispose() {
    _reasonCtrl.dispose();
    super.dispose();
  }

  int _calculatePenalty() {
    final hoursUntilStart = widget.assignment.startTime.difference(DateTime.now()).inHours;
    if (hoursUntilStart < 12) return 20;
    if (hoursUntilStart < 24) return 10;
    return 5;
  }

  Future<void> _submitCancel() async {
    setState(() => _isSubmitting = true);
    final finalReason = _selectedQuickReason == 'Lý do khác' && _reasonCtrl.text.trim().isNotEmpty
        ? _reasonCtrl.text.trim()
        : _selectedQuickReason;

    await ref
        .read(proposalRepositoryProvider)
        .cancelAssignment(widget.assignment.assignmentId, finalReason);

    if (mounted) {
      setState(() => _isSubmitting = false);
      Navigator.pop(context, true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final penalty = _calculatePenalty();

    return AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      titlePadding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
      contentPadding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: const Color(0xFFFEE2E2),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(Icons.warning_amber_rounded, color: Color(0xFFDC2626), size: 22),
          ),
          const SizedBox(width: 10),
          const Expanded(
            child: Text(
              'Xác Nhận Hủy Nhận Ca',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17, color: Color(0xFF0F172A)),
            ),
          ),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFFFFBEB),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFFDE68A)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.shield_outlined, size: 16, color: Color(0xFFD97706)),
                      const SizedBox(width: 6),
                      Text(
                        'Cảnh báo trừ -$penalty điểm Uy Tín',
                        style: const TextStyle(
                          fontWeight: FontWeight.w800,
                          fontSize: 12.5,
                          color: Color(0xFFB45309),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  const Text(
                    '• Hủy trước > 24h: -5 điểm Uy tín\n• Hủy từ 12h - 24h: -10 điểm Uy tín\n• Hủy cận giờ (< 12h): -20 điểm Uy tín',
                    style: TextStyle(fontSize: 11.5, color: Color(0xFF78350F), height: 1.4),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),
            const Text(
              'Chọn lý do hủy ca:',
              style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: Color(0xFF334155)),
            ),
            const SizedBox(height: 6),
            ..._quickReasons.map(
              (r) => RadioListTile<String>(
                value: r,
                groupValue: _selectedQuickReason,
                dense: true,
                contentPadding: EdgeInsets.zero,
                activeColor: const Color(0xFFDC2626),
                title: Text(r, style: const TextStyle(fontSize: 13)),
                onChanged: (val) {
                  if (val != null) setState(() => _selectedQuickReason = val);
                },
              ),
            ),
            if (_selectedQuickReason == 'Lý do khác') ...[
              const SizedBox(height: 6),
              TextField(
                controller: _reasonCtrl,
                maxLines: 2,
                decoration: InputDecoration(
                  hintText: 'Nhập lý do cụ thể của bạn...',
                  hintStyle: const TextStyle(fontSize: 12.5),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                  contentPadding: const EdgeInsets.all(10),
                ),
              ),
            ],
          ],
        ),
      ),
      actionsPadding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.pop(context, false),
          child: const Text('Giữ lại ca', style: TextStyle(fontWeight: FontWeight.w700, color: Color(0xFF64748B))),
        ),
        FilledButton(
          onPressed: _isSubmitting ? null : _submitCancel,
          style: FilledButton.styleFrom(
            backgroundColor: const Color(0xFFDC2626),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          ),
          child: Text(
            _isSubmitting ? 'Đang xử lý...' : 'Xác nhận hủy (-$penalty Đ)',
            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5),
          ),
        ),
      ],
    );
  }
}

