import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import '../../ekyc/screens/card_scanner_screen.dart';
import '../data/proposal_repository.dart';
import '../models/dispute_models.dart';
import '../models/proposal_models.dart';
import '../widgets/shift_checklist_section.dart';
import 'dispute_chat_screen.dart';

class MyDisputesScreen extends ConsumerStatefulWidget {
  final ShiftAssignmentItem? assignment;

  const MyDisputesScreen({super.key, this.assignment});

  @override
  ConsumerState<MyDisputesScreen> createState() => _MyDisputesScreenState();
}

class _MyDisputesScreenState extends ConsumerState<MyDisputesScreen> {
  static const List<({String value, String label, IconData icon})> _disputeTypes = [
    (value: 'PAYMENT', label: 'Thù lao', icon: Icons.payments_outlined),
    (value: 'WORK_QUALITY', label: 'Công việc', icon: Icons.assignment_late_outlined),
    (value: 'ATTENDANCE', label: 'Điểm danh', icon: Icons.schedule_outlined),
    (value: 'CONDUCT', label: 'Ứng xử', icon: Icons.report_problem_outlined),
    (value: 'OTHER', label: 'Khác', icon: Icons.more_horiz_rounded),
  ];

  Future<void> _refresh() async {
    ref.invalidate(myDisputesListProvider);
  }

  String _formatTime(DateTime dt) {
    final l = dt.toLocal();
    return '${l.hour.toString().padLeft(2, '0')}:${l.minute.toString().padLeft(2, '0')}';
  }

  String _formatDate(DateTime dt) {
    final l = dt.toLocal();
    return '${l.day.toString().padLeft(2, '0')}/${l.month.toString().padLeft(2, '0')}/${l.year}';
  }

  String _formatCurrency(num value) {
    return '${value.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ';
  }

  Future<void> _showCreateDisputeDialog() async {
    final repo = ref.read(proposalRepositoryProvider);

    List<DisputeEligibleAssignmentItem> eligibleOptions = const [];
    String? selectedAssignmentId = widget.assignment?.assignmentId;

    if (selectedAssignmentId == null || selectedAssignmentId.isEmpty) {
      try {
        eligibleOptions = await repo.fetchEligibleAssignmentsForDispute();
      } catch (_) {
        eligibleOptions = const [];
      }
      if (!mounted) return;
      if (eligibleOptions.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Khiếu nại gắn theo ca làm việc. Vui lòng vào chi tiết một ca đã nhận để tạo khiếu nại.'),
            backgroundColor: Theme.of(context).cani.warning,
          ),
        );
        return;
      }
      selectedAssignmentId = eligibleOptions.first.assignmentId;
    }

    if (!mounted) return;

    final titleCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    final evidenceCtrl = TextEditingController();
    String selectedType = 'PAYMENT';
    bool isSubmitting = false;
    bool isUploadingPhoto = false;
    String? uploadedEvidenceUrl;
    Uint8List? previewBytes;
    String? modalError;

    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (bottomSheetCtx) {
        return StatefulBuilder(
          builder: (ctx, setModalState) {
            final cani = Theme.of(ctx).cani;
            final isDark = Theme.of(ctx).brightness == Brightness.dark;
            final descLength = descCtrl.text.trim().length;
            final isDescValid = descLength >= 50;

            return Container(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.of(ctx).size.height * 0.90,
              ),
              padding: EdgeInsets.only(
                top: 12,
                left: 20,
                right: 20,
                bottom: MediaQuery.of(ctx).viewInsets.bottom + 20,
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
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: cani.danger.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Icon(Icons.gavel_rounded, color: cani.danger, size: 22),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Tạo Khiếu Nại Ca Làm',
                                style: TextStyle(
                                  fontSize: 17.5,
                                  fontWeight: FontWeight.w800,
                                  color: cani.textMain,
                                ),
                              ),
                              Text(
                                'Gửi CSKH xem xét trong thời hạn 14 ngày',
                                style: TextStyle(fontSize: 12, color: cani.textMuted),
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.close, size: 20),
                          onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),

                    // Ca làm việc được gắn trực tiếp vào Dispute
                    if (widget.assignment != null)
                      _buildShiftReferenceCard(widget.assignment!, cani, isDark)
                    else
                      DropdownButtonFormField<String>(
                        initialValue: selectedAssignmentId,
                        isExpanded: true,
                        decoration: InputDecoration(
                          labelText: 'Ca làm việc cần khiếu nại (*)',
                          prefixIcon: const Icon(Icons.work_outline_rounded, size: 20),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                        ),
                        items: eligibleOptions.map((item) {
                          final timeLabel = item.startTime != null ? ' • ${_formatDate(item.startTime!)}' : '';
                          return DropdownMenuItem<String>(
                            value: item.assignmentId,
                            child: Text(
                              '${item.jobType} - ${item.eventName}$timeLabel',
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontSize: 13),
                            ),
                          );
                        }).toList(),
                        onChanged: (val) {
                          if (val != null) setModalState(() => selectedAssignmentId = val);
                        },
                      ),

                    const SizedBox(height: 16),
                    Text(
                      'Phân loại vấn đề (*)',
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: cani.textMain),
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: _disputeTypes.map((t) {
                        final isSelected = selectedType == t.value;
                        return InkWell(
                          borderRadius: BorderRadius.circular(10),
                          onTap: () => setModalState(() => selectedType = t.value),
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 160),
                            padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 8),
                            decoration: BoxDecoration(
                              color: isSelected
                                  ? cani.brand.withValues(alpha: 0.12)
                                  : (isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC)),
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(
                                color: isSelected ? cani.brand : cani.border,
                                width: isSelected ? 1.5 : 1,
                              ),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(t.icon, size: 15, color: isSelected ? cani.brand : cani.textMuted),
                                const SizedBox(width: 6),
                                Text(
                                  t.label,
                                  style: TextStyle(
                                    fontSize: 12.5,
                                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w600,
                                    color: isSelected ? cani.brand : cani.textMain,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      }).toList(),
                    ),

                    const SizedBox(height: 16),
                    TextField(
                      controller: titleCtrl,
                      maxLength: 200,
                      decoration: InputDecoration(
                        labelText: 'Tiêu đề tóm tắt (*)',
                        hintText: 'Ví dụ: Chưa nhận đủ thù lao ca làm việc',
                        counterText: '',
                        filled: true,
                        fillColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: cani.border),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: cani.border),
                        ),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      ),
                    ),

                    const SizedBox(height: 14),
                    TextField(
                      controller: descCtrl,
                      maxLines: 4,
                      maxLength: 3000,
                      onChanged: (_) => setModalState(() {}),
                      decoration: InputDecoration(
                        labelText: 'Mô tả chi tiết vụ việc (*)',
                        hintText: 'Trình bày rõ thời gian, thỏa thuận với BTC và vấn đề phát sinh (tối thiểu 50 ký tự)...',
                        filled: true,
                        fillColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: cani.border),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: cani.border),
                        ),
                        counterText: '',
                        contentPadding: const EdgeInsets.all(14),
                      ),
                    ),
                    const SizedBox(height: 6),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          isDescValid
                              ? 'Đạt yêu cầu độ dài mô tả'
                              : 'Cần thêm ${50 - descLength} ký tự',
                          style: TextStyle(
                            fontSize: 11.5,
                            fontWeight: FontWeight.w600,
                            color: isDescValid ? const Color(0xFF16A34A) : cani.textMuted,
                          ),
                        ),
                        Text(
                          '$descLength / 50+',
                          style: TextStyle(
                            fontSize: 11.5,
                            fontWeight: FontWeight.w700,
                            color: isDescValid ? const Color(0xFF16A34A) : const Color(0xFFD97706),
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 14),
                    Text(
                      'Bằng chứng đính kèm (Tùy chọn)',
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: cani.textMain),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        OutlinedButton.icon(
                          onPressed: isUploadingPhoto || isSubmitting
                              ? null
                              : () async {
                                  final scanResult = await Navigator.of(ctx).push<CardScanResult>(
                                    MaterialPageRoute(
                                      builder: (_) => const CardScannerScreen(scanType: ScanType.workProof),
                                    ),
                                  );
                                  if (scanResult == null || !ctx.mounted) return;
                                  setModalState(() {
                                    isUploadingPhoto = true;
                                    modalError = null;
                                  });
                                  try {
                                    final url = await repo.uploadProofBytes(
                                      scanResult.bytes,
                                      scanResult.fileName,
                                    );
                                    if (!ctx.mounted) return;
                                    setModalState(() {
                                      isUploadingPhoto = false;
                                      uploadedEvidenceUrl = url;
                                      previewBytes = scanResult.bytes;
                                      evidenceCtrl.text = url;
                                    });
                                  } catch (e) {
                                    if (!ctx.mounted) return;
                                    setModalState(() {
                                      isUploadingPhoto = false;
                                      modalError = 'Không thể tải ảnh minh chứng: $e';
                                    });
                                  }
                                },
                          icon: isUploadingPhoto
                              ? const SizedBox(width: 15, height: 15, child: CircularProgressIndicator(strokeWidth: 2))
                              : const Icon(Icons.camera_alt_outlined, size: 17),
                          label: Text(uploadedEvidenceUrl != null ? 'Chụp lại ảnh' : 'Chụp ảnh minh chứng'),
                          style: OutlinedButton.styleFrom(
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                          ),
                        ),
                        if (previewBytes != null) ...[
                          const SizedBox(width: 10),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image.memory(previewBytes!, width: 42, height: 42, fit: BoxFit.cover),
                          ),
                          IconButton(
                            tooltip: 'Xóa ảnh',
                            onPressed: () => setModalState(() {
                              previewBytes = null;
                              uploadedEvidenceUrl = null;
                              evidenceCtrl.clear();
                            }),
                            icon: Icon(Icons.close_rounded, size: 18, color: cani.danger),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: evidenceCtrl,
                      decoration: InputDecoration(
                        hintText: 'Hoặc dán link / mô tả bằng chứng bổ sung...',
                        filled: true,
                        fillColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: cani.border),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: cani.border),
                        ),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        prefixIcon: const Icon(Icons.link, size: 18),
                      ),
                    ),

                    const SizedBox(height: 16),
                    if (modalError != null) ...[
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFEF2F2),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: const Color(0xFFFECACA)),
                        ),
                        child: Text(
                          modalError!,
                          style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: Color(0xFFB91C1C)),
                        ),
                      ),
                      const SizedBox(height: 12),
                    ],
                    FilledButton.icon(
                      style: FilledButton.styleFrom(
                        backgroundColor: cani.danger,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      icon: isSubmitting
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : const Icon(Icons.send_rounded, size: 18),
                      label: Text(
                        isSubmitting ? 'ĐANG GỬI...' : 'GỬI KHIẾU NẠI NGAY',
                        style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14.5),
                      ),
                      onPressed: isSubmitting || isUploadingPhoto
                          ? null
                          : () async {
                              final title = titleCtrl.text.trim();
                              final desc = descCtrl.text.trim();
                              final targetAssignmentId = selectedAssignmentId;
                              if (targetAssignmentId == null || targetAssignmentId.isEmpty) {
                                setModalState(() {
                                  modalError = 'Vui lòng chọn ca làm việc liên quan đến khiếu nại.';
                                });
                                return;
                              }
                              if (title.isEmpty) {
                                setModalState(() {
                                  modalError = 'Vui lòng nhập tiêu đề tóm tắt.';
                                });
                                return;
                              }
                              if (desc.length < 50) {
                                setModalState(() {
                                  modalError = 'Mô tả chi tiết phải có ít nhất 50 ký tự để CSKH đủ thông tin phân xử.';
                                });
                                return;
                              }

                              setModalState(() {
                                isSubmitting = true;
                                modalError = null;
                              });
                              try {
                                await repo.createDispute(CreateDisputePayload(
                                  assignmentId: targetAssignmentId,
                                  type: selectedType,
                                  title: title,
                                  description: desc,
                                  evidence: evidenceCtrl.text.trim().isNotEmpty ? evidenceCtrl.text.trim() : null,
                                ));

                                if (ctx.mounted) Navigator.pop(ctx);
                                if (!mounted) return;
                                ref.invalidate(myDisputesListProvider);
                                ref.invalidate(assignmentsListProvider);
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text('Đã gửi khiếu nại thành công! CSKH sẽ phản hồi trong thời gian sớm nhất.'),
                                    backgroundColor: Color(0xFF16A34A),
                                  ),
                                );
                              } catch (e) {
                                if (ctx.mounted) {
                                  setModalState(() {
                                    isSubmitting = false;
                                    modalError = e.toString().replaceFirst('RepositoryException: ', '');
                                  });
                                }
                              }
                            },
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );

    titleCtrl.dispose();
    descCtrl.dispose();
    evidenceCtrl.dispose();
  }

  Widget _buildShiftReferenceCard(ShiftAssignmentItem a, CaniTokens cani, bool isDark) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF0F9FF),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: cani.brand.withValues(alpha: 0.28)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(9),
            decoration: BoxDecoration(
              color: cani.brand.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(Icons.work_history_rounded, color: cani.brand, size: 20),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${a.jobType} • ${a.eventName}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
                ),
                const SizedBox(height: 3),
                Text(
                  '${_formatDate(a.startTime)} (${_formatTime(a.startTime)} - ${_formatTime(a.endTime)}) • ${_formatCurrency(a.totalPay)}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 11.5, color: cani.textMuted, fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final disputesAsync = ref.watch(myDisputesListProvider);
    final shiftAssignment = widget.assignment;

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: Column(
          children: [
            const Text(
              'Khiếu Nại & Tranh Chấp',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17),
            ),
            if (shiftAssignment != null)
              Text(
                '${shiftAssignment.jobType} · ${shiftAssignment.eventName}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 11.5, color: cani.textMuted, fontWeight: FontWeight.w600),
              ),
          ],
        ),
        centerTitle: true,
        elevation: 0.5,
        backgroundColor: isDark ? const Color(0xFF1E293B) : Colors.white,
        actions: [
          IconButton(
            tooltip: 'Tạo khiếu nại cho ca này',
            icon: Icon(Icons.add_circle_outline_rounded, size: 24, color: cani.brand),
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
                    ElevatedButton(onPressed: _refresh, child: const Text('Thử lại')),
                  ],
                ),
              ),
            ],
          ),
          data: (allDisputes) {
            final disputes = shiftAssignment == null
                ? allDisputes
                : allDisputes.where((d) => d.assignmentId == shiftAssignment.assignmentId).toList();
            final hasActiveDisputeForShift = shiftAssignment != null && disputes.any((d) => d.isOpen);

            return Column(
              children: [
                if (shiftAssignment != null)
                  Container(
                    color: isDark ? const Color(0xFF1E293B) : Colors.white,
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
                    child: Column(
                      children: [
                        _buildShiftReferenceCard(shiftAssignment, cani, isDark),
                        if (hasActiveDisputeForShift) ...[
                          const SizedBox(height: 10),
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            decoration: BoxDecoration(
                              color: const Color(0xFFEFF6FF),
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(color: const Color(0xFFBFDBFE)),
                            ),
                            child: const Row(
                              children: [
                                Icon(Icons.info_outline_rounded, size: 16, color: Color(0xFF0284C7)),
                                SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    'Ca làm này đang có hồ sơ khiếu nại chờ xử lý giữa bạn và đối tác.',
                                    style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: Color(0xFF0369A1)),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                Expanded(
                  child: disputes.isEmpty
                      ? ListView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.all(24),
                          children: [
                            const SizedBox(height: 36),
                            Center(
                              child: Container(
                                width: 84,
                                height: 84,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: cani.brand.withValues(alpha: 0.1),
                                ),
                                child: Icon(Icons.verified_user_rounded, size: 44, color: cani.brand),
                              ),
                            ),
                            const SizedBox(height: 18),
                            Text(
                              shiftAssignment != null
                                  ? 'Chưa có khiếu nại cho ca làm này'
                                  : 'Chưa có tranh chấp nào',
                              textAlign: TextAlign.center,
                              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: cani.textMain),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'Nếu phát sinh vấn đề về thù lao, giờ công hoặc ứng xử trong ca làm việc, bạn có thể gửi khiếu nại kèm bằng chứng trong vòng 14 ngày để CSKH hỗ trợ phân xử.',
                              textAlign: TextAlign.center,
                              style: TextStyle(fontSize: 13.5, color: cani.textMuted, height: 1.5),
                            ),
                            const SizedBox(height: 24),
                            Center(
                              child: FilledButton.icon(
                                icon: const Icon(Icons.gavel_rounded, size: 18),
                                label: const Text(
                                  'Tạo khiếu nại cho ca này',
                                  style: TextStyle(fontWeight: FontWeight.w700),
                                ),
                                style: FilledButton.styleFrom(
                                  backgroundColor: cani.brand,
                                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 13),
                                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                                ),
                                onPressed: _showCreateDisputeDialog,
                              ),
                            ),
                          ],
                        )
                      : ListView.separated(
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.fromLTRB(16, 16, 16, 90),
                          itemCount: disputes.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 12),
                          itemBuilder: (ctx, idx) => _buildDisputeCard(disputes[idx], cani, isDark),
                        ),
                ),
              ],
            );
          },
        ),
      ),
      floatingActionButton: disputesAsync.maybeWhen(
        data: (allDisputes) {
          final filtered = shiftAssignment == null
              ? allDisputes
              : allDisputes.where((d) => d.assignmentId == shiftAssignment.assignmentId).toList();
          if (filtered.isEmpty) return null;
          final hasOpen = shiftAssignment != null && filtered.any((d) => d.isOpen);
          if (hasOpen) return null;
          return FloatingActionButton.extended(
            onPressed: _showCreateDisputeDialog,
            backgroundColor: cani.danger,
            foregroundColor: Colors.white,
            icon: const Icon(Icons.gavel_rounded, size: 18),
            label: const Text('Gửi khiếu nại', style: TextStyle(fontWeight: FontWeight.w700)),
          );
        },
        orElse: () => null,
      ),
    );
  }

  Widget _buildDisputeCard(WorkerDisputeItem item, CaniTokens cani, bool isDark) {
    final statusColor = _getStatusColor(item.status, cani);
    final resText = item.effectiveResolutionText;
    final evidenceUrl = item.evidence;
    final looksLikeImage = evidenceUrl != null &&
        (evidenceUrl.startsWith('/api/') ||
            evidenceUrl.startsWith('http://') ||
            evidenceUrl.startsWith('https://') ||
            evidenceUrl.startsWith('data:image/'));

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E293B) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: cani.border),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 10,
            offset: const Offset(0, 3),
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
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                decoration: BoxDecoration(
                  color: cani.brandSoft,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  item.typeLabel,
                  style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: cani.brand),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 6,
                      height: 6,
                      decoration: BoxDecoration(color: statusColor, shape: BoxShape.circle),
                    ),
                    const SizedBox(width: 5),
                    Text(
                      item.statusLabel,
                      style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w800, color: statusColor),
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (item.eventTitle != null || item.shiftTitle != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(Icons.event_note_rounded, size: 14, color: cani.textMuted),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      [
                        if (item.shiftTitle != null) item.shiftTitle!,
                        if (item.eventTitle != null) item.eventTitle!,
                        if (item.shiftStartTime != null) _formatDate(item.shiftStartTime!),
                      ].join(' · '),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: cani.textMuted),
                    ),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 10),
          Text(
            item.title,
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: cani.textMain),
          ),
          const SizedBox(height: 6),
          Text(
            item.description,
            style: TextStyle(fontSize: 13, color: cani.textMuted, height: 1.45),
          ),
          if (evidenceUrl != null && evidenceUrl.isNotEmpty) ...[
            const SizedBox(height: 10),
            if (looksLikeImage)
              ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: buildResolvedImageWidget(
                  evidenceUrl,
                  height: 110,
                  width: double.infinity,
                  fit: BoxFit.cover,
                  errorText: null,
                ),
              )
            else
              Row(
                children: [
                  Icon(Icons.attachment, size: 14, color: cani.textMuted),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      'Bằng chứng: $evidenceUrl',
                      style: TextStyle(fontSize: 11.5, color: cani.textMuted),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
          ],
          if (resText != null) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(11),
              decoration: BoxDecoration(
                color: const Color(0xFF16A34A).withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFF16A34A).withValues(alpha: 0.25)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.verified_rounded, size: 16, color: Color(0xFF16A34A)),
                      const SizedBox(width: 6),
                      Text(
                        item.outcomeLabel.isNotEmpty
                            ? 'Kết quả: ${item.outcomeLabel}'
                            : 'Kết quả phân xử',
                        style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w800, color: Color(0xFF15803D)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    resText,
                    style: const TextStyle(fontSize: 12.5, color: Color(0xFF166534), height: 1.4),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              if (item.createdAt != null)
                Text(
                  'Đã gửi: ${_formatDate(item.createdAt!)}',
                  style: TextStyle(fontSize: 11, color: cani.textMuted),
                ),
              if (item.slaDeadline != null && item.isOpen)
                Text(
                  'Hạn xử lý SLA: ${_formatDate(item.slaDeadline!)}',
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: Color(0xFF0284C7)),
                ),
            ],
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              icon: Icon(Icons.forum_outlined, size: 16, color: cani.brand),
              label: Text(
                'Trao đổi với Trọng tài CSKH',
                style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: cani.brand),
              ),
              style: OutlinedButton.styleFrom(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                side: BorderSide(color: cani.brand.withValues(alpha: 0.3)),
                padding: const EdgeInsets.symmetric(vertical: 9),
              ),
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => DisputeChatScreen(
                      disputeId: item.id,
                      disputeTitle: item.title,
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Color _getStatusColor(String status, CaniTokens cani) {
    switch (status) {
      case 'OPEN':
      case 'PENDING':
        return const Color(0xFFD97706);
      case 'INVESTIGATING':
      case 'IN_REVIEW':
        return cani.brand;
      case 'RESOLVED':
      case 'FINAL_RESOLVED':
        return const Color(0xFF16A34A);
      case 'UNVERIFIED':
      case 'REJECTED':
        return cani.danger;
      case 'ESCALATED':
        return const Color(0xFF7E22CE);
      default:
        return cani.textMuted;
    }
  }
}
