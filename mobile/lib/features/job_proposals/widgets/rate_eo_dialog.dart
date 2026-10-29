import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';

class RateEoDialog extends ConsumerStatefulWidget {
  final ShiftAssignmentItem assignment;

  const RateEoDialog({
    super.key,
    required this.assignment,
  });

  @override
  ConsumerState<RateEoDialog> createState() => _RateEoDialogState();
}

class _RateEoDialogState extends ConsumerState<RateEoDialog> {
  int _rating = 5;
  int _envScore = 5;
  int _supportScore = 5;
  int _paymentScore = 5;
  final Set<String> _selectedTags = {'Chuyên nghiệp', 'Đúng mô tả'};
  final TextEditingController _commentCtrl = TextEditingController();
  bool _isSubmitting = false;

  final List<String> _allTags = const [
    'Chuyên nghiệp',
    'Đúng mô tả',
    'Hỗ trợ nhiệt tình',
    'Thanh toán nhanh',
    'Môi trường thân thiện',
    'Cung cấp bữa ăn',
  ];

  @override
  void dispose() {
    _commentCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _isSubmitting = true);
    await ref.read(proposalRepositoryProvider).submitReview(
          assignmentId: widget.assignment.assignmentId,
          rating: _rating,
          criteriaWorkEnvironment: _envScore,
          criteriaSupport: _supportScore,
          criteriaPaymentTiming: _paymentScore,
          comment: _commentCtrl.text.trim(),
          tags: _selectedTags.toList(),
        );
    if (mounted) {
      setState(() => _isSubmitting = false);
      Navigator.pop(context, true);
    }
  }

  Widget _buildStarRow(String label, int currentVal, ValueChanged<int> onChanged) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Text(
              label,
              style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: Color(0xFF475569)),
            ),
          ),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: List.generate(5, (i) {
              final star = i + 1;
              return GestureDetector(
                onTap: () => onChanged(star),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 2),
                  child: Icon(
                    star <= currentVal ? Icons.star_rounded : Icons.star_border_rounded,
                    color: const Color(0xFFF59E0B),
                    size: 22,
                  ),
                ),
              );
            }),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
      insetPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 24),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFEF3C7),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.star_rounded, color: Color(0xFFD97706), size: 24),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Đánh Giá Ban Tổ Chức',
                        style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17, color: Color(0xFF0F172A)),
                      ),
                      Text(
                        widget.assignment.eventName,
                        style: const TextStyle(fontSize: 12.5, color: Color(0xFF64748B)),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Center(
              child: Column(
                children: [
                  const Text(
                    'Trải nghiệm chung của bạn tại sự kiện',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Color(0xFF334155)),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: List.generate(5, (i) {
                      final star = i + 1;
                      return GestureDetector(
                        onTap: () => setState(() => _rating = star),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: Icon(
                            star <= _rating ? Icons.star_rounded : Icons.star_border_rounded,
                            color: const Color(0xFFF59E0B),
                            size: 36,
                          ),
                        ),
                      );
                    }),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: Column(
                children: [
                  _buildStarRow('Môi trường làm việc', _envScore, (v) => setState(() => _envScore = v)),
                  _buildStarRow('Sự hỗ trợ của Quản lý', _supportScore, (v) => setState(() => _supportScore = v)),
                  _buildStarRow('Thanh toán minh bạch', _paymentScore, (v) => setState(() => _paymentScore = v)),
                ],
              ),
            ),
            const SizedBox(height: 14),
            const Text(
              'Điểm cộng nổi bật:',
              style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Color(0xFF334155)),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: _allTags.map((tag) {
                final selected = _selectedTags.contains(tag);
                return FilterChip(
                  label: Text(tag, style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: selected ? const Color(0xFF0066FF) : const Color(0xFF475569))),
                  selected: selected,
                  selectedColor: const Color(0xFFDBEAFE),
                  checkmarkColor: const Color(0xFF0066FF),
                  backgroundColor: const Color(0xFFF1F5F9),
                  side: BorderSide(color: selected ? const Color(0xFF93C5FD) : Colors.transparent),
                  onSelected: (val) {
                    setState(() {
                      if (val) {
                        _selectedTags.add(tag);
                      } else {
                        _selectedTags.remove(tag);
                      }
                    });
                  },
                );
              }).toList(),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _commentCtrl,
              maxLines: 3,
              decoration: InputDecoration(
                hintText: 'Chia sẻ thêm cảm nhận của bạn về Ban tổ chức...',
                hintStyle: const TextStyle(fontSize: 12.5),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                contentPadding: const EdgeInsets.all(12),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _isSubmitting ? null : () => Navigator.pop(context, false),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: const Text('Để sau', style: TextStyle(fontWeight: FontWeight.w700)),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  flex: 2,
                  child: FilledButton.icon(
                    onPressed: _isSubmitting ? null : _submit,
                    icon: const Icon(Icons.send_rounded, size: 17),
                    label: Text(
                      _isSubmitting ? 'Đang gửi...' : 'Gửi Đánh Giá',
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    style: FilledButton.styleFrom(
                      backgroundColor: const Color(0xFF0066FF),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

