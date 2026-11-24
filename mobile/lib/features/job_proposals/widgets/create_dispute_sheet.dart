import 'dart:typed_data';
import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';

import '../../ekyc/screens/card_scanner_screen.dart';
import '../data/proposal_repository.dart';
import '../models/dispute_models.dart';

typedef DisputeTypeOption = ({String value, String label, IconData icon});

/// Bottom sheet tạo khiếu nại ca làm việc.
///
/// Tách thành [StatefulWidget] riêng để các [TextEditingController] được giải phóng
/// đúng lúc trong [State.dispose] sau khi animation đóng bottom sheet hoàn tất.
class CreateDisputeSheet extends StatefulWidget {
  final ProposalRepository repository;
  final List<DisputeTypeOption> disputeTypes;
  final List<DisputeEligibleAssignmentItem> eligibleOptions;
  final String? initialAssignmentId;
  final Widget? shiftReferenceCard;
  final String Function(DateTime) formatDate;

  const CreateDisputeSheet({
    super.key,
    required this.repository,
    required this.disputeTypes,
    required this.eligibleOptions,
    required this.initialAssignmentId,
    required this.formatDate,
    this.shiftReferenceCard,
  });

  @override
  State<CreateDisputeSheet> createState() => _CreateDisputeSheetState();
}

class _CreateDisputeSheetState extends State<CreateDisputeSheet> {
  final TextEditingController _titleCtrl = TextEditingController();
  final TextEditingController _descCtrl = TextEditingController();
  final TextEditingController _evidenceCtrl = TextEditingController();

  String? _selectedAssignmentId;
  String _selectedType = 'PAYMENT';
  bool _isSubmitting = false;
  bool _isUploadingPhoto = false;
  String? _uploadedEvidenceUrl;
  Uint8List? _previewBytes;
  String? _modalError;

  @override
  void initState() {
    super.initState();
    _selectedAssignmentId = widget.initialAssignmentId;
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _descCtrl.dispose();
    _evidenceCtrl.dispose();
    super.dispose();
  }

  Future<void> _captureProofPhoto() async {
    final scanResult = await Navigator.of(context).push<CardScanResult>(
      MaterialPageRoute(
        builder: (_) => const CardScannerScreen(scanType: ScanType.workProof),
      ),
    );
    if (scanResult == null || !mounted) return;

    setState(() {
      _isUploadingPhoto = true;
      _modalError = null;
    });
    try {
      final url = await widget.repository.uploadProofBytes(
        scanResult.bytes,
        scanResult.fileName,
      );
      if (!mounted) return;
      setState(() {
        _isUploadingPhoto = false;
        _uploadedEvidenceUrl = url;
        _previewBytes = scanResult.bytes;
        _evidenceCtrl.text = url;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isUploadingPhoto = false;
        _modalError = 'Không thể tải ảnh minh chứng: $e';
      });
    }
  }

  Future<void> _submit() async {
    final title = _titleCtrl.text.trim();
    final desc = _descCtrl.text.trim();
    final targetAssignmentId = _selectedAssignmentId;

    if (targetAssignmentId == null || targetAssignmentId.isEmpty) {
      setState(() {
        _modalError = 'Vui lòng chọn ca làm việc liên quan đến khiếu nại.';
      });
      return;
    }
    if (title.isEmpty) {
      setState(() {
        _modalError = 'Vui lòng nhập tiêu đề tóm tắt.';
      });
      return;
    }
    if (desc.length < 50) {
      setState(() {
        _modalError = 'Mô tả chi tiết phải có ít nhất 50 ký tự để CSKH đủ thông tin phân xử.';
      });
      return;
    }

    setState(() {
      _isSubmitting = true;
      _modalError = null;
    });
    try {
      await widget.repository.createDispute(
        CreateDisputePayload(
          assignmentId: targetAssignmentId,
          type: _selectedType,
          title: title,
          description: desc,
          evidence: _evidenceCtrl.text.trim().isNotEmpty ? _evidenceCtrl.text.trim() : null,
        ),
      );
      if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isSubmitting = false;
          _modalError = e.toString().replaceFirst('RepositoryException: ', '');
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final descLength = _descCtrl.text.trim().length;
    final isDescValid = descLength >= 50;

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.90,
      ),
      padding: EdgeInsets.only(
        top: 12,
        left: 20,
        right: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
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
                  onPressed: _isSubmitting ? null : () => Navigator.pop(context),
                ),
              ],
            ),
            const SizedBox(height: 14),
            if (widget.shiftReferenceCard != null)
              widget.shiftReferenceCard!
            else
              DropdownButtonFormField<String>(
                initialValue: _selectedAssignmentId,
                isExpanded: true,
                decoration: InputDecoration(
                  labelText: 'Ca làm việc cần khiếu nại (*)',
                  prefixIcon: const Icon(Icons.work_outline_rounded, size: 20),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                ),
                items: widget.eligibleOptions.map((item) {
                  final timeLabel = item.startTime != null ? ' • ${widget.formatDate(item.startTime!)}' : '';
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
                  if (val != null) setState(() => _selectedAssignmentId = val);
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
              children: widget.disputeTypes.map((t) {
                final isSelected = _selectedType == t.value;
                return InkWell(
                  borderRadius: BorderRadius.circular(10),
                  onTap: () => setState(() => _selectedType = t.value),
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
              controller: _titleCtrl,
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
              controller: _descCtrl,
              maxLines: 4,
              maxLength: 3000,
              onChanged: (_) => setState(() {}),
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
                  isDescValid ? 'Đạt yêu cầu độ dài mô tả' : 'Cần thêm ${50 - descLength} ký tự',
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
                  onPressed: _isUploadingPhoto || _isSubmitting ? null : _captureProofPhoto,
                  icon: _isUploadingPhoto
                      ? const SizedBox(width: 15, height: 15, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.camera_alt_outlined, size: 17),
                  label: Text(_uploadedEvidenceUrl != null ? 'Chụp lại ảnh' : 'Chụp ảnh minh chứng'),
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  ),
                ),
                if (_previewBytes != null) ...[
                  const SizedBox(width: 10),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: Image.memory(_previewBytes!, width: 42, height: 42, fit: BoxFit.cover),
                  ),
                  IconButton(
                    tooltip: 'Xóa ảnh',
                    onPressed: () => setState(() {
                      _previewBytes = null;
                      _uploadedEvidenceUrl = null;
                      _evidenceCtrl.clear();
                    }),
                    icon: Icon(Icons.close_rounded, size: 18, color: cani.danger),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _evidenceCtrl,
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
            if (_modalError != null) ...[
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  color: const Color(0xFFFEF2F2),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: const Color(0xFFFECACA)),
                ),
                child: Text(
                  _modalError!,
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
              icon: _isSubmitting
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.send_rounded, size: 18),
              label: Text(
                _isSubmitting ? 'ĐANG GỬI...' : 'GỬI KHIẾU NẠI NGAY',
                style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14.5),
              ),
              onPressed: _isSubmitting || _isUploadingPhoto ? null : _submit,
            ),
          ],
        ),
      ),
    );
  }
}
