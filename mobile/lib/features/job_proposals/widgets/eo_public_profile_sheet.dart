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

  String _formatDate(DateTime dt) {
    final l = dt.toLocal();
    return '${l.day.toString().padLeft(2, '0')}/${l.month.toString().padLeft(2, '0')}/${l.year}';
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.84),
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
                final ratingLabel = hasRatings ? '${eo.ratingAvg.toStringAsFixed(1)} ★ (${eo.ratingCount})' : 'Chưa có';

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
                                Wrap(
                                  spacing: 6,
                                  runSpacing: 4,
                                  crossAxisAlignment: WrapCrossAlignment.center,
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                      decoration: BoxDecoration(
                                        color: eo.isVerified ? const Color(0xFFDCFCE7) : const Color(0xFFFEF3C7),
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
                                            eo.isVerified ? 'Đã xác thực pháp lý' : 'Chờ xác thực',
                                            style: TextStyle(
                                              fontSize: 11,
                                              fontWeight: FontWeight.w700,
                                              color: eo.isVerified ? const Color(0xFF15803D) : const Color(0xFFB45309),
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                    if (eo.verifiedAt != null)
                                      Text(
                                        'Từ ${_formatDate(eo.verifiedAt!)}',
                                        style: TextStyle(fontSize: 11, color: cani.textMuted),
                                      ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),

                      // Thống kê thực tế từ DB
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
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
                                  Text('Đánh giá', style: TextStyle(fontSize: 11, color: cani.textMuted)),
                                  const SizedBox(height: 4),
                                  Text(
                                    ratingLabel,
                                    textAlign: TextAlign.center,
                                    style: TextStyle(
                                      fontSize: 13.5,
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
                                  Text('Sự kiện đã mở', style: TextStyle(fontSize: 11, color: cani.textMuted)),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${eo.totalEventsCompleted} sự kiện',
                                    textAlign: TextAlign.center,
                                    style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.brand),
                                  ),
                                ],
                              ),
                            ),
                            Container(width: 1, height: 30, color: cani.border),
                            Expanded(
                              child: Column(
                                children: [
                                  Text('Nhân sự đã nhận', style: TextStyle(fontSize: 11, color: cani.textMuted)),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${eo.totalConfirmedWorkers} lượt',
                                    textAlign: TextAlign.center,
                                    style: const TextStyle(
                                      fontSize: 13.5,
                                      fontWeight: FontWeight.w800,
                                      color: Color(0xFF0D9488),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 16),

                      if (eo.description != null && eo.description!.trim().isNotEmpty) ...[
                        Text('Giới thiệu đơn vị', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: cani.textMain)),
                        const SizedBox(height: 4),
                        Text(eo.description!.trim(), style: TextStyle(fontSize: 13, color: cani.textMuted, height: 1.4)),
                        const SizedBox(height: 14),
                      ],

                      if (eo.representativeName != null && eo.representativeName!.trim().isNotEmpty)
                        _buildInfoRow(Icons.person_outline_rounded, 'Người phụ trách', eo.representativeName!.trim(), cani),
                      if (eo.address != null && eo.address!.trim().isNotEmpty)
                        _buildInfoRow(Icons.location_on_outlined, 'Địa chỉ', eo.address!.trim(), cani),
                      if (eo.phone != null && eo.phone!.trim().isNotEmpty)
                        _buildInfoRow(
                          Icons.phone_outlined,
                          'Điện thoại liên hệ',
                          eo.phone!.trim(),
                          cani,
                          onTap: () => launchUrl(Uri.parse('tel:${eo.phone!.trim()}')),
                        ),
                      if (eo.email != null && eo.email!.trim().isNotEmpty)
                        _buildInfoRow(Icons.email_outlined, 'Email', eo.email!.trim(), cani),

                      const SizedBox(height: 8),
                      const Divider(height: 1),
                      const SizedBox(height: 14),
                      Text(
                        'Nhận xét từ nhân sự (${eo.reviews.length})',
                        style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
                      ),
                      const SizedBox(height: 10),
                      if (eo.reviews.isEmpty)
                        Container(
                          padding: const EdgeInsets.all(14),
                          decoration: BoxDecoration(
                            color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: cani.border),
                          ),
                          child: Text(
                            'Nhà tổ chức này chưa có đánh giá nào từ nhân sự sau ca làm.',
                            textAlign: TextAlign.center,
                            style: TextStyle(fontSize: 12.5, color: cani.textMuted),
                          ),
                        )
                      else
                        ...eo.reviews.map((r) => _buildReviewCard(r, cani, isDark)),
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

  Widget _buildReviewCard(EoPublicReviewItem r, CaniTokens cani, bool isDark) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: cani.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  r.reviewerWorkerName,
                  style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: cani.textMain),
                ),
              ),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.star_rounded, size: 15, color: Color(0xFFF59E0B)),
                  const SizedBox(width: 2),
                  Text(
                    '${r.rating}.0',
                    style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w800, color: Color(0xFFD97706)),
                  ),
                ],
              ),
            ],
          ),
          if (r.eventName != null || r.shiftName != null || r.createdAt != null) ...[
            const SizedBox(height: 3),
            Text(
              [
                if (r.shiftName != null) r.shiftName!,
                if (r.eventName != null) r.eventName!,
                if (r.createdAt != null) _formatDate(r.createdAt!),
              ].join(' · '),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 11, color: cani.textMuted),
            ),
          ],
          if (r.comment != null && r.comment!.trim().isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              r.comment!.trim(),
              style: TextStyle(fontSize: 12.5, color: cani.textMain, height: 1.4),
            ),
          ],
          if (r.tags.isNotEmpty) ...[
            const SizedBox(height: 6),
            Wrap(
              spacing: 6,
              runSpacing: 4,
              children: r.tags
                  .map((tag) => Container(
                        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                        decoration: BoxDecoration(
                          color: cani.brandSoft,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(tag, style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: cani.brand)),
                      ))
                  .toList(),
            ),
          ],
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
