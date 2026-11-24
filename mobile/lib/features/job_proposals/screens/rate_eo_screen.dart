import 'package:cani/app/cani_tokens.dart';
import 'package:cani/app/home_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/shift_workflow_stepper.dart';

/// Bước 6: Trang Đánh giá Ban tổ chức sau khi Check-out kết thúc ca.
class RateEoScreen extends ConsumerStatefulWidget {
  final ShiftAssignmentItem assignment;
  final double? checkoutHoursWorked;

  const RateEoScreen({
    super.key,
    required this.assignment,
    this.checkoutHoursWorked,
  });

  @override
  ConsumerState<RateEoScreen> createState() => _RateEoScreenState();
}

class _RateEoScreenState extends ConsumerState<RateEoScreen> {
  int _rating = 5;
  int _envScore = 5;
  int _supportScore = 5;
  int _paymentScore = 5;
  final Set<String> _selectedTags = {'Chuyên nghiệp', 'Đúng mô tả'};
  final TextEditingController _commentCtrl = TextEditingController();
  bool _isSubmitting = false;
  late bool _isAlreadyReviewed;

  @override
  void initState() {
    super.initState();
    _isAlreadyReviewed = widget.assignment.hasWorkerReviewed == true;
  }

  static const List<String> _allTags = [
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

  void _returnToAssignmentsList() {
    ref.invalidate(assignmentsListProvider);
    ref.invalidate(incomeSummaryProvider);
    ref.read(homeTabIndexProvider.notifier).state = 1;
    final navigator = Navigator.of(context);
    if (navigator.canPop()) {
      navigator.popUntil((route) => route.isFirst);
    }
  }

  Future<void> _submit() async {
    final cani = Theme.of(context).cani;
    if (_isAlreadyReviewed) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Bạn đã gửi đánh giá cho ca làm việc này trước đó rồi.'),
          backgroundColor: cani.warning,
        ),
      );
      _returnToAssignmentsList();
      return;
    }

    setState(() => _isSubmitting = true);
    try {
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
        setState(() => _isAlreadyReviewed = true);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Cảm ơn bạn đã hoàn thành ca và gửi đánh giá Ban tổ chức!'),
            backgroundColor: cani.success,
          ),
        );
        _returnToAssignmentsList();
      }
    } catch (e) {
      if (mounted) {
        final cleanMsg = e is RepositoryException
            ? e.message
            : e.toString().replaceFirst(RegExp(r'^Exception:\s*'), '');
        if (cleanMsg.toLowerCase().contains('đã gửi đánh giá')) {
          setState(() => _isAlreadyReviewed = true);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(cleanMsg),
              backgroundColor: cani.warning,
            ),
          );
          _returnToAssignmentsList();
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Gửi đánh giá thất bại: $cleanMsg'),
              backgroundColor: cani.danger,
            ),
          );
        }
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  Widget _buildStarRow(
    CaniTokens cani,
    String label,
    int currentVal,
    ValueChanged<int> onChanged,
  ) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: cani.textBody,
              ),
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
                    color: cani.warning,
                    size: 24,
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
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final a = widget.assignment;
    final hoursWorked = widget.checkoutHoursWorked ?? a.hoursWorked;
    final displayPay = a.declaredAmount > 0 ? a.declaredAmount : a.totalPay;

    return Scaffold(
      backgroundColor: cani.canvas,
      appBar: AppBar(
        backgroundColor: cani.surface,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        surfaceTintColor: Colors.transparent,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(1),
          child: Container(height: 1, color: cani.border),
        ),
        leading: IconButton(
          icon: Icon(Icons.arrow_back_ios_new_rounded, size: 19, color: cani.textMain),
          onPressed: _isSubmitting ? null : () => Navigator.pop(context, true),
        ),
        title: Text(
          'Đánh giá Ban tổ chức · Bước 6/6',
          style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: cani.textMain),
        ),
        centerTitle: true,
      ),
      body: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Stepper hoàn tất Bước 1 -> 5, đang ở Bước 6
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: cani.surface,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: cani.border),
                    ),
                    child: Column(
                      children: [
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                          decoration: BoxDecoration(
                            color: cani.successBg,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Row(
                            children: [
                              Icon(Icons.verified_rounded, color: cani.success, size: 18),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  _isAlreadyReviewed
                                      ? 'Bạn đã hoàn thành ca và gửi đánh giá Ban tổ chức rồi.'
                                      : 'Check-out thành công! Hãy gửi đánh giá để hoàn tất trọn vẹn ca làm.',
                                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: cani.success),
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 12),
                        ShiftWorkflowStepper(
                          isExpired: false,
                          isCheckedIn: true,
                          isWorkReady: true,
                          isEoConfirmed: true,
                          isPaymentConfirmed: isAssignmentPaymentConfirmed(a),
                          isCompleted: true,
                          isReviewed: _isAlreadyReviewed,
                          forcedCurrentStep: 6,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Tóm tắt ca đã kết thúc
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: cani.surface,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: cani.border),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${a.jobType} · ${a.eventName}',
                          style: TextStyle(fontSize: 15.5, fontWeight: FontWeight.w800, color: cani.textMain),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          a.eoName ?? 'Ban tổ chức sự kiện',
                          style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: cani.brand),
                        ),
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            Expanded(
                              child: _InfoChip(
                                cani: cani,
                                label: 'Thời gian làm',
                                value: hoursWorked != null && hoursWorked > 0
                                    ? '${hoursWorked.toStringAsFixed(1)} giờ'
                                    : '${a.startTime.hour.toString().padLeft(2, '0')}:${a.startTime.minute.toString().padLeft(2, '0')} - ${a.endTime.hour.toString().padLeft(2, '0')}:${a.endTime.minute.toString().padLeft(2, '0')}',
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: _InfoChip(
                                cani: cani,
                                label: 'Thù lao đối soát',
                                value: formatVndCurrency(displayPay),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Khung chọn sao & nhận xét
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: cani.surface,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: cani.border),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Center(
                          child: Column(
                            children: [
                              Text(
                                'Trải nghiệm chung của bạn tại sự kiện',
                                style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: cani.textMain),
                              ),
                              const SizedBox(height: 8),
                              Row(
                                mainAxisSize: MainAxisSize.min,
                                children: List.generate(5, (i) {
                                  final star = i + 1;
                                  return GestureDetector(
                                    onTap: () => setState(() => _rating = star),
                                    child: Padding(
                                      padding: const EdgeInsets.symmetric(horizontal: 5),
                                      child: Icon(
                                        star <= _rating ? Icons.star_rounded : Icons.star_border_rounded,
                                        color: cani.warning,
                                        size: 40,
                                      ),
                                    ),
                                  );
                                }),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 14),
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: cani.canvas,
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: cani.border),
                          ),
                          child: Column(
                            children: [
                              _buildStarRow(cani, 'Môi trường làm việc', _envScore, (v) => setState(() => _envScore = v)),
                              _buildStarRow(cani, 'Sự hỗ trợ của Quản lý', _supportScore, (v) => setState(() => _supportScore = v)),
                              _buildStarRow(cani, 'Thanh toán minh bạch', _paymentScore, (v) => setState(() => _paymentScore = v)),
                            ],
                          ),
                        ),
                        const SizedBox(height: 14),
                        Text(
                          'Điểm cộng nổi bật:',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: cani.textMain),
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: _allTags.map((tag) {
                            final selected = _selectedTags.contains(tag);
                            return FilterChip(
                              label: Text(
                                tag,
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: selected ? cani.brand : cani.textBody,
                                ),
                              ),
                              selected: selected,
                              selectedColor: cani.brandSoft,
                              checkmarkColor: cani.brand,
                              backgroundColor: isDark ? const Color(0xFF1E293B) : cani.canvas,
                              side: BorderSide(
                                color: selected ? cani.brand.withValues(alpha: 0.5) : cani.border,
                              ),
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
                        const SizedBox(height: 14),
                        TextField(
                          controller: _commentCtrl,
                          maxLines: 3,
                          decoration: InputDecoration(
                            labelText: 'Nhận xét thêm (không bắt buộc)',
                            hintText: 'Chia sẻ thêm cảm nhận của bạn về Ban tổ chức...',
                            hintStyle: const TextStyle(fontSize: 12.5),
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                            contentPadding: const EdgeInsets.all(12),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (!_isAlreadyReviewed)
            Container(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
              decoration: BoxDecoration(
                color: cani.surface,
                border: Border(top: BorderSide(color: cani.border)),
              ),
              child: SafeArea(
                top: false,
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _isSubmitting ? null : () => Navigator.pop(context, true),
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          side: BorderSide(color: cani.border),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                        child: Text('Để sau', style: TextStyle(fontWeight: FontWeight.w700, color: cani.textMuted)),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      flex: 2,
                      child: FilledButton.icon(
                        onPressed: _isSubmitting ? null : _submit,
                        icon: const Icon(Icons.send_rounded, size: 18),
                        label: Text(
                          _isSubmitting ? 'Đang gửi...' : 'Gửi Đánh Giá & Hoàn Tất',
                          style: const TextStyle(fontWeight: FontWeight.w800),
                        ),
                        style: FilledButton.styleFrom(
                          backgroundColor: cani.brand,
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  final CaniTokens cani;
  final String label;
  final String value;

  const _InfoChip({
    required this.cani,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: cani.canvas,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: cani.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: TextStyle(fontSize: 11, color: cani.textMuted)),
          const SizedBox(height: 2),
          Text(
            value,
            style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
          ),
        ],
      ),
    );
  }
}
