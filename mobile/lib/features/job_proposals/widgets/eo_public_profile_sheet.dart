import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:cani/app/cani_tokens.dart';
import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';

class EoPublicProfileSheet extends ConsumerStatefulWidget {
  final String eoId;
  final String? fallbackName;

  const EoPublicProfileSheet({
    super.key,
    required this.eoId,
    this.fallbackName,
  });

  static void show(BuildContext context, {required String eoId, String? fallbackName}) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => EoPublicProfileSheet(eoId: eoId, fallbackName: fallbackName),
    );
  }

  @override
  ConsumerState<EoPublicProfileSheet> createState() => _EoPublicProfileSheetState();
}

class _EoPublicProfileSheetState extends ConsumerState<EoPublicProfileSheet> {
  late Future<EoPublicProfileData> _profileFuture;

  @override
  void initState() {
    super.initState();
    _profileFuture = ref.read(proposalRepositoryProvider).fetchEoPublicProfile(widget.eoId);
  }

  void _reload() {
    setState(() {
      _profileFuture = ref.read(proposalRepositoryProvider).fetchEoPublicProfile(widget.eoId);
    });
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.78),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E293B) : Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.withValues(alpha: 0.35),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 14),
          Flexible(
            child: FutureBuilder<EoPublicProfileData>(
              future: _profileFuture,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 36),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const CircularProgressIndicator(),
                        const SizedBox(height: 12),
                        Text(
                          'Đang tải hồ sơ ${widget.fallbackName ?? "Ban tổ chức"}...',
                          style: TextStyle(color: cani.textMuted, fontSize: 13),
                        ),
                      ],
                    ),
                  );
                }
                if (snapshot.hasError || !snapshot.hasData) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 28),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.business_outlined, size: 44, color: cani.textMuted),
                        const SizedBox(height: 10),
                        Text(
                          'Không tải được hồ sơ Ban tổ chức',
                          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: cani.textMain),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          '${snapshot.error ?? ""}'.replaceFirst('RepositoryException: ', ''),
                          textAlign: TextAlign.center,
                          style: TextStyle(fontSize: 12.5, color: cani.textMuted),
                        ),
                        const SizedBox(height: 14),
                        OutlinedButton.icon(
                          onPressed: _reload,
                          icon: const Icon(Icons.refresh, size: 16),
                          label: const Text('Thử lại'),
                        ),
                      ],
                    ),
                  );
                }

                final eo = snapshot.data!;
                final hasRatings = eo.ratingCount > 0;
                final ratingLabel = hasRatings ? '${eo.ratingAvg.toStringAsFixed(1)} ★ (${eo.ratingCount})' : 'Chưa có đánh giá';

                return SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        children: [
                          CircleAvatar(
                            radius: 30,
                            backgroundColor: cani.brandSoft,
                            backgroundImage: (eo.avatarUrl != null && eo.avatarUrl!.isNotEmpty)
                                ? NetworkImage(eo.avatarUrl!)
                                : null,
                            child: (eo.avatarUrl == null || eo.avatarUrl!.isEmpty)
                                ? Icon(Icons.apartment_rounded, size: 28, color: cani.brand)
                                : null,
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  eo.companyName,
                                  style: TextStyle(
                                    fontSize: 17,
                                    fontWeight: FontWeight.w800,
                                    color: cani.textMain,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                  decoration: BoxDecoration(
                                    color: eo.isVerified
                                        ? const Color(0xFFDCFCE7)
                                        : const Color(0xFFFEF3C7),
                                    borderRadius: BorderRadius.circular(8),
                                  ),
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Icon(
                                        eo.isVerified ? Icons.verified_rounded : Icons.schedule_rounded,
                                        size: 13,
                                        color: eo.isVerified ? const Color(0xFF16A34A) : const Color(0xFFD97706),
                                      ),
                                      const SizedBox(width: 4),
                                      Text(
                                        eo.isVerified ? 'BTC đã xác thực pháp lý' : 'Đang chờ xác thực',
                                        style: TextStyle(
                                          fontSize: 11,
                                          fontWeight: FontWeight.w700,
                                          color: eo.isVerified ? const Color(0xFF15803D) : const Color(0xFFB45309),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),

                      // Thống kê nhanh
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(color: cani.border),
                        ),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                children: [
                                  Text('Đánh giá từ nhân sự', style: TextStyle(fontSize: 11, color: cani.textMuted)),
                                  const SizedBox(height: 4),
                                  Text(
                                    ratingLabel,
                                    style: TextStyle(
                                      fontSize: 14,
                                      fontWeight: FontWeight.w800,
                                      color: hasRatings ? const Color(0xFFD97706) : cani.textMuted,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Container(width: 1, height: 30, color: cani.border),
                            Expanded(
                              child: Column(
                                children: [
                                  Text('Sự kiện đã tổ chức', style: TextStyle(fontSize: 11, color: cani.textMuted)),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${eo.totalEventsCompleted} sự kiện',
                                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800, color: cani.brand),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 14),

                      if (eo.description != null && eo.description!.isNotEmpty) ...[
                        Text('Giới thiệu đơn vị', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: cani.textMain)),
                        const SizedBox(height: 4),
                        Text(eo.description!, style: TextStyle(fontSize: 13, color: cani.textMuted, height: 1.4)),
                        const SizedBox(height: 14),
                      ],

                      if (eo.address != null && eo.address!.trim().isNotEmpty)
                        _buildInfoRow(Icons.location_on_outlined, 'Địa chỉ', eo.address!, cani),
                      if (eo.phone != null && eo.phone!.trim().isNotEmpty)
                        _buildInfoRow(
                          Icons.phone_outlined,
                          'Điện thoại liên hệ',
                          eo.phone!,
                          cani,
                          onTap: () => launchUrl(Uri.parse('tel:${eo.phone!.trim()}')),
                        ),
                      if (eo.email != null && eo.email!.trim().isNotEmpty)
                        _buildInfoRow(Icons.email_outlined, 'Email', eo.email!, cani),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(IconData icon, String label, String value, CaniTokens cani, {VoidCallback? onTap}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 18, color: cani.brand),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label, style: TextStyle(fontSize: 11, color: cani.textMuted)),
                  Text(
                    value,
                    style: TextStyle(
                      fontSize: 13.5,
                      fontWeight: FontWeight.w600,
                      color: onTap != null ? cani.brand : cani.textMain,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
