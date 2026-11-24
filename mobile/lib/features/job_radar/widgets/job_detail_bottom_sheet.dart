import 'package:cani/app/cani_tokens.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/widgets/eo_public_profile_sheet.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

class JobDetailBottomSheet extends StatelessWidget {
  final NearbyShiftItem shift;
  final VoidCallback onApply;
  final VoidCallback? onReject;
  final double userLat;
  final double userLng;

  const JobDetailBottomSheet({
    super.key,
    required this.shift,
    required this.onApply,
    this.onReject,
    this.userLat = 10.7769,
    this.userLng = 106.7009,
  });

  Future<void> _launchGoogleMapsNavigation(BuildContext context) async {
    final destLat = shift.locationLat;
    final destLng = shift.locationLng;
    final uri = Uri.parse(
      'https://www.google.com/maps/dir/?api=1&origin=$userLat,$userLng&destination=$destLat,$destLng&travelmode=driving',
    );

    try {
      final launched = await launchUrl(
        uri,
        mode: LaunchMode.externalApplication,
      );
      if (!launched) {
        await launchUrl(uri, mode: LaunchMode.platformDefault);
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Không thể mở Google Maps: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cani = theme.cani;

    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.85,
        ),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.15),
                blurRadius: 20,
                offset: const Offset(0, -5),
              ),
            ],
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header kéo
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: Colors.grey.shade400,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Ảnh minh họa ca làm / đồng phục (nếu EO có cung cấp)
                if (shift.imageUrl != null &&
                    shift.imageUrl!.trim().isNotEmpty) ...[
                  ClipRRect(
                    borderRadius: BorderRadius.circular(14),
                    child: Stack(
                      children: [
                        Image.network(
                          shift.imageUrl!.trim(),
                          height: 140,
                          width: double.infinity,
                          fit: BoxFit.cover,
                          errorBuilder: (ctx, err, stack) =>
                              const SizedBox.shrink(),
                        ),
                        Positioned(
                          bottom: 8,
                          left: 8,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 3,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.65),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: const Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.checkroom_rounded,
                                  size: 12,
                                  color: Colors.white,
                                ),
                                SizedBox(width: 4),
                                Text(
                                  'Ảnh ca làm / Đồng phục yêu cầu',
                                  style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 10.5,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),
                ],

                // Tên sự kiện & Khoảng cách
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        shift.eventName,
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.teal.shade50,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: Colors.teal.shade200),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.near_me,
                            size: 14,
                            color: Colors.teal.shade800,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            '${shift.distanceKm.toStringAsFixed(1)} km',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              color: Colors.teal.shade800,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),

                // Vị trí công việc (Job Type) & Ban tổ chức (EO Profile)
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.primaryContainer.withValues(
                          alpha: 0.6,
                        ),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        shift.jobType,
                        style: TextStyle(
                          color: theme.colorScheme.onPrimaryContainer,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                    ),
                    if (shift.eoId != null && shift.eoId!.isNotEmpty)
                      InkWell(
                        onTap: () => EoPublicProfileSheet.show(
                          context,
                          eoId: shift.eoId!,
                          fallbackName: shift.eoName,
                        ),
                        borderRadius: BorderRadius.circular(8),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: cani.brandSoft,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: cani.brand.withValues(alpha: 0.25)),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                shift.eoVerified ? Icons.verified_rounded : Icons.apartment_rounded,
                                size: 14,
                                color: cani.brand,
                              ),
                              const SizedBox(width: 4),
                              Text(
                                '${shift.eoName ?? "Ban tổ chức"} · Xem hồ sơ',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                  color: cani.brand,
                                ),
                              ),
                              Icon(Icons.chevron_right_rounded, size: 15, color: cani.brand),
                            ],
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 12),

                // Địa chỉ
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      Icons.location_on,
                      size: 18,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        shift.locationAddress,
                        style: TextStyle(
                          fontSize: 13,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),

                // Thời gian
                if (shift.startTime != null && shift.endTime != null)
                  Row(
                    children: [
                      Icon(
                        Icons.access_time,
                        size: 18,
                        color: Colors.orange.shade700,
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          '${shift.startTime!.hour.toString().padLeft(2, '0')}:${shift.startTime!.minute.toString().padLeft(2, '0')} - ${shift.endTime!.hour.toString().padLeft(2, '0')}:${shift.endTime!.minute.toString().padLeft(2, '0')} (${shift.startTime!.day}/${shift.startTime!.month})',
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),

                // Giờ yêu cầu có mặt / mở điểm danh (nếu khác giờ bắt đầu)
                if (shift.arrivalTime != null &&
                    shift.startTime != null &&
                    shift.startTime!
                            .difference(shift.arrivalTime!)
                            .inMinutes
                            .abs() >=
                        1)
                  Padding(
                    padding: const EdgeInsets.only(top: 6, left: 24),
                    child: Row(
                      children: [
                        Icon(
                          Icons.fact_check,
                          size: 15,
                          color: Colors.amber.shade800,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            shift.arrivalTime!.isBefore(shift.startTime!)
                                ? 'Có mặt từ ${shift.arrivalTime!.hour.toString().padLeft(2, '0')}:${shift.arrivalTime!.minute.toString().padLeft(2, '0')} (trước ca ${shift.startTime!.difference(shift.arrivalTime!).inMinutes} phút)'
                                : 'Mở điểm danh từ ${shift.arrivalTime!.hour.toString().padLeft(2, '0')}:${shift.arrivalTime!.minute.toString().padLeft(2, '0')}',
                            style: TextStyle(
                              fontSize: 12,
                              color: Colors.amber.shade800,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                // Kỹ năng bắt buộc & ưu tiên
                if (shift.requiredSkillNames.isNotEmpty || shift.preferredSkillNames.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 8, left: 24),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          Icons.workspace_premium,
                          size: 18,
                          color: Colors.blue.shade700,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Wrap(
                            spacing: 6,
                            runSpacing: 4,
                            children: [
                              ...shift.requiredSkillNames.map(
                                (n) => Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                  decoration: BoxDecoration(
                                    color: Colors.blue.shade50,
                                    borderRadius: BorderRadius.circular(8),
                                    border: Border.all(color: Colors.blue.shade200),
                                  ),
                                  child: Text(
                                    'Bắt buộc: $n',
                                    style: const TextStyle(fontSize: 12, color: Colors.blueAccent, fontWeight: FontWeight.w600),
                                  ),
                                ),
                              ),
                              ...shift.preferredSkillNames.map(
                                (n) => Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                  decoration: BoxDecoration(
                                    color: Colors.green.shade50,
                                    borderRadius: BorderRadius.circular(8),
                                    border: Border.all(color: Colors.green.shade200),
                                  ),
                                  child: Text(
                                    'Ưu tiên: $n',
                                    style: TextStyle(fontSize: 12, color: Colors.green.shade700, fontWeight: FontWeight.w600),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                const SizedBox(height: 16),

                // Bảng lương & Quân số
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerHighest.withValues(
                      alpha: 0.4,
                    ),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          children: [
                            const Text(
                              'Mức Lương',
                              style: TextStyle(
                                fontSize: 11,
                                color: Colors.grey,
                              ),
                            ),
                            const SizedBox(height: 4),
                            FittedBox(
                              fit: BoxFit.scaleDown,
                              child: Text(
                                '${(shift.hourlyWage).toStringAsFixed(0)} đ/h',
                                style: TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.green.shade800,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      Container(
                        height: 30,
                        width: 1,
                        color: Colors.grey.shade300,
                      ),
                      Expanded(
                        child: Column(
                          children: [
                            const Text(
                              'Phụ Cấp',
                              style: TextStyle(
                                fontSize: 11,
                                color: Colors.grey,
                              ),
                            ),
                            const SizedBox(height: 4),
                            FittedBox(
                              fit: BoxFit.scaleDown,
                              child: Text(
                                '+${(shift.allowance).toStringAsFixed(0)} đ',
                                style: const TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      Container(
                        height: 30,
                        width: 1,
                        color: Colors.grey.shade300,
                      ),
                      Expanded(
                        child: Column(
                          children: [
                            const Text(
                              'Còn Thiếu',
                              style: TextStyle(
                                fontSize: 11,
                                color: Colors.grey,
                              ),
                            ),
                            const SizedBox(height: 4),
                            FittedBox(
                              fit: BoxFit.scaleDown,
                              child: Text(
                                '${shift.remainingWorkers}/${shift.requiredWorkers} người',
                                style: TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.red.shade700,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),

                // Ghi chú thanh toán Non-Escrow (Minh bạch phương thức thanh toán)
                Row(
                  children: [
                    Icon(Icons.info_outline, size: 13, color: cani.textMuted),
                    const SizedBox(width: 5),
                    Expanded(
                      child: Text(
                        '* Tiền thù lao do Ban tổ chức thanh toán trực tiếp cho bạn sau ca (không qua CANi ví điện tử).',
                        style: TextStyle(
                          fontSize: 11,
                          fontStyle: FontStyle.italic,
                          color: theme.colorScheme.onSurfaceVariant.withValues(
                            alpha: 0.8,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Hàng nút thao tác: Chỉ đường Google Maps + Từ chối + Nhận ca
                Row(
                  children: [
                    // Nút Chỉ đường Google Maps
                    if (onReject != null) ...[
                      SizedBox(
                        height: 48,
                        child: OutlinedButton(
                          onPressed: () => _launchGoogleMapsNavigation(context),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: cani.brand,
                            side: BorderSide(color: cani.brand, width: 1.5),
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: const Icon(Icons.navigation_rounded, size: 20),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        flex: 4,
                        child: SizedBox(
                          height: 48,
                          child: OutlinedButton(
                            onPressed: onReject,
                            style: OutlinedButton.styleFrom(
                              foregroundColor: cani.danger,
                              side: BorderSide(color: cani.danger.withValues(alpha: 0.6), width: 1.4),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                            child: const Text(
                              'Từ chối',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontWeight: FontWeight.w800,
                                fontSize: 13,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ] else
                      Expanded(
                        flex: 4,
                        child: SizedBox(
                          height: 48,
                          child: OutlinedButton.icon(
                            onPressed: () => _launchGoogleMapsNavigation(context),
                            icon: const Icon(Icons.navigation_rounded, size: 18),
                            label: const Text(
                              'Chỉ đường',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 13,
                              ),
                            ),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: cani.brand,
                              side: BorderSide(color: cani.brand, width: 1.5),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                          ),
                        ),
                      ),
                    const SizedBox(width: 8),
                    // Nút Nhận ca (Chỉ mở khi ca đang ở chế độ Khẩn cấp / Emergency)
                    Expanded(
                      flex: 6,
                      child: SizedBox(
                        height: 48,
                        child: shift.emergency
                            ? FilledButton.icon(
                                onPressed: onApply,
                                icon: const Icon(
                                  Icons.flash_on_rounded,
                                  size: 18,
                                ),
                                label: const Text(
                                  'Nhận ca',
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 13,
                                  ),
                                ),
                                style: FilledButton.styleFrom(
                                  backgroundColor: cani.danger,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                              )
                            : Container(
                                alignment: Alignment.center,
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 10,
                                ),
                                decoration: BoxDecoration(
                                  color: cani.border,
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(
                                    color: const Color(0xFFCBD5E1),
                                  ),
                                ),
                                child: Row(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    Icon(
                                      Icons.schedule_send_rounded,
                                      size: 16,
                                      color: cani.textMuted,
                                    ),
                                    const SizedBox(width: 6),
                                    const Flexible(
                                      child: Text(
                                        'Chờ lời mời (Matching)',
                                        style: TextStyle(
                                          fontWeight: FontWeight.w700,
                                          fontSize: 12,
                                          color: Color(0xFF475569),
                                        ),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class JobProposalDetailBottomSheet extends StatelessWidget {
  final JobProposalItem proposal;
  final VoidCallback onAccept;
  final VoidCallback onReject;

  const JobProposalDetailBottomSheet({
    super.key,
    required this.proposal,
    required this.onAccept,
    required this.onReject,
  });

  String _formatCurrency(double value) {
    return value.toInt().toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]}.',
        );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cani = theme.cani;
    final p = proposal;

    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.85,
        ),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.15),
                blurRadius: 20,
                offset: const Offset(0, -5),
              ),
            ],
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: Colors.grey.shade400,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                if (p.imageUrl != null && p.imageUrl!.trim().isNotEmpty) ...[
                  ClipRRect(
                    borderRadius: BorderRadius.circular(14),
                    child: Image.network(
                      p.imageUrl!.trim(),
                      height: 140,
                      width: double.infinity,
                      fit: BoxFit.cover,
                      errorBuilder: (ctx, err, stack) => const SizedBox.shrink(),
                    ),
                  ),
                  const SizedBox(height: 14),
                ],

                Text(
                  p.eventName,
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),

                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.primaryContainer.withValues(alpha: 0.6),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        p.jobType,
                        style: TextStyle(
                          color: theme.colorScheme.onPrimaryContainer,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                    ),
                    if (p.eoId != null && p.eoId!.isNotEmpty)
                      InkWell(
                        onTap: () => EoPublicProfileSheet.show(
                          context,
                          eoId: p.eoId!,
                          fallbackName: p.eoName,
                        ),
                        borderRadius: BorderRadius.circular(8),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: cani.brandSoft,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: cani.brand.withValues(alpha: 0.25)),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                p.eoVerified ? Icons.verified_rounded : Icons.apartment_rounded,
                                size: 14,
                                color: cani.brand,
                              ),
                              const SizedBox(width: 4),
                              Text(
                                '${p.eoName ?? "Ban tổ chức"} · Xem hồ sơ',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                  color: cani.brand,
                                ),
                              ),
                              Icon(Icons.chevron_right_rounded, size: 15, color: cani.brand),
                            ],
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 12),

                if (p.venueAddress.trim().isNotEmpty) ...[
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.location_on, size: 18, color: theme.colorScheme.primary),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          p.venueAddress,
                          style: TextStyle(fontSize: 13, color: theme.colorScheme.onSurfaceVariant),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                ],

                Row(
                  children: [
                    Icon(Icons.access_time, size: 18, color: Colors.orange.shade700),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        '${p.startTime.hour.toString().padLeft(2, '0')}:${p.startTime.minute.toString().padLeft(2, '0')} - ${p.endTime.hour.toString().padLeft(2, '0')}:${p.endTime.minute.toString().padLeft(2, '0')} (${p.startTime.day}/${p.startTime.month}/${p.startTime.year})',
                        style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
                      ),
                    ),
                  ],
                ),

                if (p.recommendationReasonVi != null && p.recommendationReasonVi!.trim().isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    decoration: BoxDecoration(
                      color: const Color(0xFFECFDF5),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: const Color(0xFFA7F3D0)),
                    ),
                    child: Text(
                      p.recommendationReasonVi!,
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF047857),
                      ),
                    ),
                  ),
                ],

                if (p.description.trim().isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Text(
                    p.description,
                    style: TextStyle(fontSize: 13, color: theme.colorScheme.onSurfaceVariant, height: 1.4),
                  ),
                ],

                if (p.requiredSkillNames.isNotEmpty || p.preferredSkillNames.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    children: [
                      ...p.requiredSkillNames.map(
                        (s) => Chip(
                          label: Text('Bắt buộc: $s', style: const TextStyle(fontSize: 11)),
                          visualDensity: VisualDensity.compact,
                        ),
                      ),
                      ...p.preferredSkillNames.map(
                        (s) => Chip(
                          label: Text('Ưu tiên: $s', style: const TextStyle(fontSize: 11)),
                          visualDensity: VisualDensity.compact,
                        ),
                      ),
                    ],
                  ),
                ],

                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: cani.brandSoft.withValues(alpha: 0.45),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: cani.brand.withValues(alpha: 0.2)),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Tổng ước tính',
                              style: TextStyle(fontSize: 11, color: cani.textMuted, fontWeight: FontWeight.w600),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '${_formatCurrency(p.totalEstimatedPay)} đ',
                              style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: cani.success),
                            ),
                          ],
                        ),
                      ),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            '${_formatCurrency(p.hourlyWage)} đ/giờ',
                            style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: cani.textMain),
                          ),
                          if (p.allowance > 0)
                            Text(
                              '+ ${_formatCurrency(p.allowance)} đ phụ cấp',
                              style: TextStyle(fontSize: 11, color: cani.textMuted, fontWeight: FontWeight.w600),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Icon(Icons.info_outline, size: 13, color: cani.textMuted),
                    const SizedBox(width: 5),
                    Expanded(
                      child: Text(
                        '* Tiền thù lao do Ban tổ chức thanh toán trực tiếp cho bạn sau ca (không qua CANi ví điện tử).',
                        style: TextStyle(
                          fontSize: 11,
                          fontStyle: FontStyle.italic,
                          color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.8),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                Row(
                  children: [
                    Expanded(
                      flex: 4,
                      child: SizedBox(
                        height: 48,
                        child: OutlinedButton(
                          onPressed: onReject,
                          style: OutlinedButton.styleFrom(
                            foregroundColor: cani.danger,
                            side: BorderSide(color: cani.danger.withValues(alpha: 0.65), width: 1.4),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                          ),
                          child: const Text(
                            'Từ chối',
                            style: TextStyle(fontWeight: FontWeight.w800, fontSize: 13.5),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      flex: 6,
                      child: SizedBox(
                        height: 48,
                        child: Container(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(colors: cani.brandGradient),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: ElevatedButton(
                            onPressed: onAccept,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.transparent,
                              shadowColor: Colors.transparent,
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                            ),
                            child: const Text(
                              'Nhận ca',
                              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 13.5, color: Colors.white),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
