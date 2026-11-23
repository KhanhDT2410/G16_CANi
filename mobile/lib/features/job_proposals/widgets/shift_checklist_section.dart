import 'dart:convert';
import 'dart:typed_data';

import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';

import '../../../core/constants/api_constants.dart';
import '../models/proposal_models.dart';

/// Tách biệt khỏi màn vận hành chính để giữ module sạch sẽ, tái sử dụng được cả Inline và Chế độ mở rộng toàn màn hình.
class ShiftChecklistSection extends StatelessWidget {
  final List<ShiftChecklistItem> items;
  final bool isLoading;
  final bool isSaving;
  final String? errorMessage;
  final bool canInteract;
  final bool isExpired;
  final int? uploadingTaskIndex;
  final VoidCallback onRetryLoad;
  final ValueChanged<int> onToggleItem;
  final ValueChanged<int> onCaptureEvidence;
  final void Function(String imageUrl, String title) onViewEvidence;
  final VoidCallback? onOpenFullscreen;

  const ShiftChecklistSection({
    super.key,
    required this.items,
    required this.isLoading,
    required this.isSaving,
    this.errorMessage,
    required this.canInteract,
    this.isExpired = false,
    this.uploadingTaskIndex,
    required this.onRetryLoad,
    required this.onToggleItem,
    required this.onCaptureEvidence,
    required this.onViewEvidence,
    this.onOpenFullscreen,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final cani = theme.cani;

    final completedCount = items.where((i) => i.isCompleted).length;
    final totalCount = items.length;
    final progress = totalCount > 0 ? completedCount / totalCount : 0.0;

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      decoration: BoxDecoration(
        color: cani.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: cani.border),
        boxShadow: [
          BoxShadow(
            color: isDark
                ? Colors.black.withValues(alpha: 0.16)
                : const Color(0xFF0F172A).withValues(alpha: 0.03),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Đầu việc trong ca',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: cani.textMain),
                ),
              ),
              if (onOpenFullscreen != null && items.isNotEmpty)
                TextButton.icon(
                  onPressed: onOpenFullscreen,
                  icon: Icon(Icons.open_in_full_rounded, size: 14, color: cani.brand),
                  label: Text(
                    'Mở rộng',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: cani.brand),
                  ),
                  style: TextButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Từ Ban tổ chức ($totalCount đầu việc)',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: cani.textMuted),
              ),
              Row(
                children: [
                  if (isSaving)
                    Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: SizedBox(
                        width: 12,
                        height: 12,
                        child: CircularProgressIndicator(strokeWidth: 2, color: cani.brand),
                      ),
                    ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: (totalCount > 0 && completedCount == totalCount) ? cani.successBg : cani.brandSoft,
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      '$completedCount / $totalCount hoàn thành',
                      style: TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w700,
                        color: (totalCount > 0 && completedCount == totalCount) ? cani.success : cani.brand,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 6,
              backgroundColor: cani.border,
              valueColor: AlwaysStoppedAnimation<Color>(
                (totalCount > 0 && completedCount == totalCount) ? cani.success : cani.brand,
              ),
            ),
          ),
          const SizedBox(height: 12),
          if (!canInteract)
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 10),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: isExpired
                    ? (isDark ? const Color(0xFF3F1D1D) : const Color(0xFFFEF2F2))
                    : (isDark ? const Color(0xFF451A03) : const Color(0xFFFEF3C7)),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: isExpired ? const Color(0xFFFCA5A5) : const Color(0xFFFBBF24)),
              ),
              child: Row(
                children: [
                  Icon(
                    isExpired ? Icons.visibility_outlined : Icons.lock_clock_outlined,
                    size: 18,
                    color: isExpired ? const Color(0xFFDC2626) : const Color(0xFFD97706),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      isExpired
                          ? 'Ca làm việc đã quá hạn — danh sách đầu việc chỉ ở chế độ xem.'
                          : 'Chế độ xem trước: Quét QR Check-in khi vào ca để tích chọn & chụp ảnh nghiệm thu.',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: isExpired ? const Color(0xFFB91C1C) : const Color(0xFFB45309),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          if (isLoading)
            const Center(
              child: Padding(
                padding: EdgeInsets.all(16.0),
                child: CircularProgressIndicator(),
              ),
            )
          else if (errorMessage != null)
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF3F1D1D) : const Color(0xFFFEF2F2),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFFFCA5A5)),
              ),
              child: Column(
                children: [
                  Text(
                    errorMessage!,
                    style: const TextStyle(fontSize: 12.5, color: Color(0xFFB91C1C)),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  TextButton.icon(
                    onPressed: onRetryLoad,
                    icon: const Icon(Icons.refresh, size: 16),
                    label: const Text('Thử lại', style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                ],
              ),
            )
          else if (items.isEmpty)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: isDark ? const Color(0xFF334155) : cani.border),
              ),
              child: Text(
                'Ban tổ chức chưa thiết lập đầu việc chi tiết cho ca này.',
                style: TextStyle(fontSize: 12.5, color: cani.textMuted, height: 1.4),
                textAlign: TextAlign.center,
              ),
            )
          else
            ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: items.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              itemBuilder: (ctx, idx) => ChecklistTaskTile(
                index: idx,
                item: items[idx],
                canInteract: canInteract,
                isUploading: uploadingTaskIndex == idx,
                onToggle: () => onToggleItem(idx),
                onCaptureEvidence: () => onCaptureEvidence(idx),
                onViewEvidence: () => onViewEvidence(
                  items[idx].evidenceImageUrl!,
                  items[idx].title,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class ChecklistTaskTile extends StatelessWidget {
  final int index;
  final ShiftChecklistItem item;
  final bool canInteract;
  final bool isUploading;
  final VoidCallback onToggle;
  final VoidCallback onCaptureEvidence;
  final VoidCallback onViewEvidence;

  const ChecklistTaskTile({
    super.key,
    required this.index,
    required this.item,
    this.canInteract = true,
    required this.isUploading,
    required this.onToggle,
    required this.onCaptureEvidence,
    required this.onViewEvidence,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final cani = theme.cani;

    final hasInstruction = item.instruction != null && item.instruction!.trim().isNotEmpty;
    final hasEvidence = item.evidenceImageUrl != null && item.evidenceImageUrl!.trim().isNotEmpty;
    final needsPhotoBar = item.isRequiredEvidence || hasEvidence || isUploading;

    return Container(
      decoration: BoxDecoration(
        color: isDark ? cani.canvas : const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: cani.border,
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: canInteract ? onToggle : null,
            borderRadius: BorderRadius.circular(14),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 11, 12, 11),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Container(
                    width: 22,
                    height: 22,
                    margin: const EdgeInsets.only(right: 10),
                    decoration: BoxDecoration(
                      color: item.isCompleted
                          ? cani.success
                          : (canInteract ? Colors.transparent : cani.border.withValues(alpha: isDark ? 0.25 : 0.6)),
                      borderRadius: BorderRadius.circular(7),
                      border: Border.all(
                        color: item.isCompleted
                            ? cani.success
                            : (canInteract ? cani.borderStrong : cani.border),
                        width: 1.8,
                      ),
                    ),
                    child: item.isCompleted
                        ? const Icon(Icons.check_rounded, size: 14, color: Colors.white)
                        : (!canInteract
                            ? Icon(Icons.lock_outline_rounded, size: 12, color: cani.textMuted)
                            : null),
                  ),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${(index + 1).toString().padLeft(2, '0')}. ${item.title}',
                          style: TextStyle(
                            fontSize: 13.5,
                            fontWeight: FontWeight.w700,
                            color: item.isCompleted ? cani.textBody : cani.textMain,
                          ),
                        ),
                        if (hasInstruction) ...[
                          const SizedBox(height: 5),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                            decoration: BoxDecoration(
                              color: isDark ? cani.surface : cani.brandSoft.withValues(alpha: 0.65),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Icon(Icons.info_outline_rounded, size: 13, color: cani.brand),
                                const SizedBox(width: 5),
                                Expanded(
                                  child: Text(
                                    item.instruction!.trim(),
                                    style: TextStyle(
                                      fontSize: 11.5,
                                      color: cani.textBody,
                                      height: 1.35,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: item.isCompleted ? cani.successBg : cani.warningBg,
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      item.isCompleted ? 'Hoàn thành' : 'Chưa xong',
                      style: TextStyle(
                        fontSize: 10.5,
                        fontWeight: FontWeight.w700,
                        color: item.isCompleted ? cani.success : cani.warning,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (needsPhotoBar) ...[
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 9),
              child: Row(
                children: [
                  if (hasEvidence) ...[
                    GestureDetector(
                      onTap: onViewEvidence,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Stack(
                          children: [
                            SizedBox(
                              width: 44,
                              height: 44,
                              child: buildResolvedImageWidget(
                                item.evidenceImageUrl!,
                                fit: BoxFit.cover,
                                errorText: null,
                              ),
                            ),
                            Positioned(
                              bottom: 0,
                              right: 0,
                              child: Container(
                                padding: const EdgeInsets.all(2),
                                decoration: const BoxDecoration(
                                  color: Colors.black54,
                                  borderRadius: BorderRadius.only(topLeft: Radius.circular(4)),
                                ),
                                child: const Icon(Icons.zoom_in, size: 12, color: Colors.white),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Row(
                            children: [
                              Icon(Icons.check_circle, size: 14, color: Color(0xFF16A34A)),
                              SizedBox(width: 4),
                              Text(
                                'Đã nộp ảnh nghiệm thu',
                                style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: Color(0xFF16A34A)),
                              ),
                            ],
                          ),
                          if (item.evidenceSubmittedAt != null)
                            Text(
                              'Lúc ${item.evidenceSubmittedAt!.hour.toString().padLeft(2, '0')}:${item.evidenceSubmittedAt!.minute.toString().padLeft(2, '0')}',
                              style: const TextStyle(fontSize: 10.5, color: Color(0xFF94A3B8)),
                            ),
                        ],
                      ),
                    ),
                    if (canInteract)
                      TextButton.icon(
                        onPressed: onCaptureEvidence,
                        icon: const Icon(Icons.camera_alt_outlined, size: 14),
                        label: const Text('Chụp lại', style: TextStyle(fontSize: 11)),
                        style: TextButton.styleFrom(
                          visualDensity: VisualDensity.compact,
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                        ),
                      ),
                  ] else if (isUploading) ...[
                    const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      'Đang tải ảnh nghiệm thu...',
                      style: TextStyle(fontSize: 12, color: Color(0xFF0284C7), fontWeight: FontWeight.w600),
                    ),
                  ] else ...[
                    const Icon(Icons.camera_alt_outlined, size: 16, color: Color(0xFF0284C7)),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        canInteract
                            ? 'Bắt buộc chụp ảnh tại chỗ để hoàn tất'
                            : 'Chụp ảnh bằng chứng khi vào ca',
                        style: TextStyle(fontSize: 11.5, color: cani.textMuted, fontStyle: FontStyle.italic),
                      ),
                    ),
                    ElevatedButton.icon(
                      onPressed: canInteract ? onCaptureEvidence : null,
                      icon: const Icon(Icons.camera_alt, size: 14, color: Colors.white),
                      label: const Text(
                        'Chụp ảnh',
                        style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: Colors.white),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: cani.brand,
                        disabledBackgroundColor: const Color(0xFF94A3B8),
                        visualDensity: VisualDensity.compact,
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Màn hình mở rộng toàn màn hình khi ca làm có nhiều đầu việc cần tập trung xử lý.
class ShiftChecklistFullScreen extends StatelessWidget {
  final String title;
  final bool canInteract;
  final ValueNotifier<List<ShiftChecklistItem>> itemsNotifier;
  final ValueNotifier<int?> uploadingIndexNotifier;
  final Future<void> Function(int index) onToggleItem;
  final Future<void> Function(int index) onCaptureEvidence;
  final void Function(String imageUrl, String title) onViewEvidence;

  const ShiftChecklistFullScreen({
    super.key,
    required this.title,
    this.canInteract = true,
    required this.itemsNotifier,
    required this.uploadingIndexNotifier,
    required this.onToggleItem,
    required this.onCaptureEvidence,
    required this.onViewEvidence,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cani = Theme.of(context).cani;

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: isDark ? const Color(0xFF1E293B) : Colors.white,
        elevation: 0.5,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Danh sách đầu việc trong ca', style: TextStyle(fontSize: 15.5, fontWeight: FontWeight.bold)),
            Text(title, style: TextStyle(fontSize: 11.5, color: cani.textMuted), maxLines: 1, overflow: TextOverflow.ellipsis),
          ],
        ),
      ),
      body: ValueListenableBuilder<List<ShiftChecklistItem>>(
        valueListenable: itemsNotifier,
        builder: (context, items, _) {
          final completed = items.where((i) => i.isCompleted).length;
          final total = items.length;
          final progress = total > 0 ? completed / total : 0.0;

          return Column(
            children: [
              Container(
                color: isDark ? const Color(0xFF1E293B) : Colors.white,
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 14),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('Tiến độ hoàn thành', style: TextStyle(fontSize: 12.5, color: cani.textMuted, fontWeight: FontWeight.w600)),
                        Text(
                          '$completed / $total Hoàn thành',
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: Color(0xFF0D9488)),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: LinearProgressIndicator(
                        value: progress,
                        minHeight: 7,
                        backgroundColor: cani.border,
                        valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF0D9488)),
                      ),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: ValueListenableBuilder<int?>(
                  valueListenable: uploadingIndexNotifier,
                  builder: (context, uploadingIdx, _) {
                    return ListView.separated(
                      padding: const EdgeInsets.all(16),
                      itemCount: items.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 10),
                      itemBuilder: (ctx, idx) => ChecklistTaskTile(
                        index: idx,
                        item: items[idx],
                        canInteract: canInteract,
                        isUploading: uploadingIdx == idx,
                        onToggle: () => onToggleItem(idx),
                        onCaptureEvidence: () => onCaptureEvidence(idx),
                        onViewEvidence: () => onViewEvidence(items[idx].evidenceImageUrl!, items[idx].title),
                      ),
                    );
                  },
                ),
              ),
            ],
          );
        },
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: SizedBox(
            height: 48,
            child: FilledButton(
              onPressed: () => Navigator.pop(context),
              style: FilledButton.styleFrom(
                backgroundColor: cani.brand,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              child: const Text('Quay lại màn vận hành ca', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
          ),
        ),
      ),
    );
  }
}

/// Hỗ trợ hiển thị ảnh từ cả URL http(s), đường dẫn tương đối (/api/...), hoặc chuỗi Base64 Data URI từ Web EO.
Widget buildResolvedImageWidget(
  String rawPath, {
  BoxFit fit = BoxFit.cover,
  double? height,
  double? width,
  String? errorText = 'Không thể tải ảnh chứng từ',
  WidgetBuilder? errorBuilder,
}) {
  final trimmed = rawPath.trim();
  if (trimmed.startsWith('data:image/')) {
    final commaIdx = trimmed.indexOf(',');
    if (commaIdx != -1) {
      try {
        final base64Str = trimmed.substring(commaIdx + 1).replaceAll(RegExp(r'\s+'), '');
        final Uint8List bytes = base64Decode(base64Str);
        return Image.memory(
          bytes,
          height: height,
          width: width,
          fit: fit,
          errorBuilder: (ctx, _, _) =>
              errorBuilder != null ? errorBuilder(ctx) : _imageFallbackBox(height: height, errorText: errorText),
        );
      } catch (_) {
        return Builder(
          builder: (ctx) =>
              errorBuilder != null ? errorBuilder(ctx) : _imageFallbackBox(height: height, errorText: errorText),
        );
      }
    }
  }

  String fullUrl;
  if (trimmed.startsWith('http://localhost:') || trimmed.startsWith('http://127.0.0.1:')) {
    // Chuẩn hóa URL localhost do Web EO lưu để thiết bị Android dùng đúng API_BASE_URL đang cấu hình
    final parsed = Uri.tryParse(trimmed);
    fullUrl = parsed != null ? '${ApiConstants.baseUrl}${parsed.path}${parsed.hasQuery ? '?${parsed.query}' : ''}' : trimmed;
  } else if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    fullUrl = trimmed;
  } else {
    final normalizedPath = trimmed.startsWith('/') ? trimmed : '/$trimmed';
    fullUrl = '${ApiConstants.baseUrl}$normalizedPath';
  }

  return Image.network(
    fullUrl,
    height: height,
    width: width,
    fit: fit,
    errorBuilder: (ctx, _, _) =>
        errorBuilder != null ? errorBuilder(ctx) : _imageFallbackBox(height: height, errorText: errorText),
  );
}

Widget _imageFallbackBox({double? height, String? errorText}) {
  return Container(
    height: height ?? 64,
    width: double.infinity,
    decoration: BoxDecoration(
      color: const Color(0xFFF1F5F9),
      borderRadius: BorderRadius.circular(10),
    ),
    child: Center(
      child: errorText == null
          ? const Icon(Icons.broken_image_outlined, size: 20, color: Color(0xFF94A3B8))
          : Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.image_not_supported_outlined, size: 18, color: Color(0xFF64748B)),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      errorText,
                      style: const TextStyle(fontSize: 12, color: Color(0xFF64748B), fontWeight: FontWeight.w500),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
    ),
  );
}
