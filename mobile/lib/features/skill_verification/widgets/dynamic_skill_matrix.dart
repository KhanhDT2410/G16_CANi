import 'package:flutter/material.dart';

import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'skill_hub_theme.dart';

/// Danh sách kỹ năng thống nhất của Worker: hiển thị tách bạch Mức phát triển (SP)
/// và Mức xác minh (Tự khai báo / Đã xác thực tài liệu / Đã chứng minh qua ca làm),
/// số ca thực tế, đánh giá thật (không giả 5.0) và bottom-sheet Xem tiến trình.
class DynamicSkillMatrix extends StatelessWidget {
  final List<WorkerSkillItem> mySkills;
  final List<WorkerSkillPointModel> skillPoints;
  final VoidCallback onDeclareSkills;
  final VoidCallback onStartAiVerification;

  const DynamicSkillMatrix({
    super.key,
    required this.mySkills,
    this.skillPoints = const [],
    required this.onDeclareSkills,
    required this.onStartAiVerification,
  });

  WorkerSkillPointModel? _findMatchingPoint(WorkerSkillItem item) {
    for (final p in skillPoints) {
      if (p.skillId != null && p.skillId == item.skillId) {
        return p;
      }
    }
    final targetName = item.skillName.trim().toLowerCase();
    for (final p in skillPoints) {
      if (p.jobType.trim().toLowerCase() == targetName) {
        return p;
      }
    }
    return null;
  }

  List<WorkerSkillPointModel> _findUnlinkedPoints() {
    final linkedIds = <String>{};
    final linkedNames = <String>{};
    for (final s in mySkills) {
      linkedIds.add(s.skillId);
      linkedNames.add(s.skillName.trim().toLowerCase());
    }
    return skillPoints.where((p) {
      if (p.skillId != null && linkedIds.contains(p.skillId)) return false;
      if (linkedNames.contains(p.jobType.trim().toLowerCase())) return false;
      return true;
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final unlinkedPoints = _findUnlinkedPoints();

    if (mySkills.isEmpty && unlinkedPoints.isEmpty) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: SkillHubColors.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: SkillHubColors.borderSoft),
        ),
        child: Column(
          children: [
            const Icon(Icons.workspace_premium_outlined, size: 40, color: SkillHubColors.ctaPrimary),
            const SizedBox(height: 10),
            const Text(
              'Bạn chưa khai báo kỹ năng nào',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800, color: SkillHubColors.textPrimary),
            ),
            const SizedBox(height: 4),
            const Text(
              'Hãy chọn các kỹ năng bạn tự tin hoặc tải lên chứng chỉ để hệ thống gợi ý ca làm phù hợp hơn.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: SkillHubColors.textSecondary, height: 1.4),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: onDeclareSkills,
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: SkillHubColors.ctaPrimary),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                    child: const Text('Khai báo ngay', style: TextStyle(fontWeight: FontWeight.w700)),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton(
                    onPressed: onStartAiVerification,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: SkillHubColors.ctaPrimary,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                    child: const Text('Tải chứng chỉ', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                  ),
                ),
              ],
            ),
          ],
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.only(bottom: 8),
          child: Text(
            'Kỹ năng chuyên môn & Tiến trình phát triển',
            style: TextStyle(
              fontSize: 13.5,
              fontWeight: FontWeight.w800,
              color: SkillHubColors.textPrimary,
            ),
          ),
        ),
        ...mySkills.map((item) => _buildSkillCard(context, item, _findMatchingPoint(item))),
        if (unlinkedPoints.isNotEmpty) ...[
          const SizedBox(height: 6),
          const Text(
            'Điểm kỹ năng đã tích lũy trước đó',
            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: SkillHubColors.textSecondary),
          ),
          const SizedBox(height: 6),
          ...unlinkedPoints.map((sp) => _buildOrphanPointCard(context, sp)),
        ],
      ],
    );
  }

  Widget _buildSkillCard(BuildContext context, WorkerSkillItem skill, WorkerSkillPointModel? sp) {
    final tierCode = sp?.tier ?? 'STARTER';
    final devLabel = SkillHubColors.getDevelopmentLevelLabel(tierCode);
    final devColor = SkillHubColors.getDevelopmentLevelColor(tierCode);
    final devBg = SkillHubColors.getDevelopmentLevelBg(tierCode);
    final verLabel = skill.tierBadgeLabel;
    final verColor = SkillHubColors.getVerificationTierColor(skill.verificationTier, isExpired: skill.isExpired);
    final verBg = SkillHubColors.getVerificationTierBg(skill.verificationTier, isExpired: skill.isExpired);

    final totalSp = sp?.totalSp ?? skill.spVerifyAwarded;
    final nextThreshold = SkillHubColors.getNextTierThreshold(totalSp);
    final progress = (totalSp / nextThreshold).clamp(0.0, 1.0);
    final shiftsCount = sp?.totalShiftsCompleted ?? 0;

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: SkillHubColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: SkillHubColors.borderSoft, width: 1.2),
        boxShadow: const [
          BoxShadow(color: Color(0x0603045E), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      skill.skillName,
                      style: const TextStyle(
                        fontSize: 14.5,
                        fontWeight: FontWeight.w800,
                        color: SkillHubColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Mức tự đánh giá: ${skill.proficiencyLabel}',
                      style: const TextStyle(fontSize: 11, color: SkillHubColors.textSecondary),
                    ),
                  ],
                ),
              ),
              TextButton(
                onPressed: () => _showSkillProgressSheet(context, skill, sp),
                style: TextButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  minimumSize: Size.zero,
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
                child: const Text(
                  'Xem tiến trình',
                  style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: SkillHubColors.ctaPrimary),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          // Hai nhãn tách bạch: Nhóm phát triển vs Mức xác minh
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: [
              _buildChip(
                label: devLabel,
                textColor: devColor,
                bgColor: devBg,
                icon: Icons.trending_up_rounded,
              ),
              _buildChip(
                label: verLabel,
                textColor: verColor,
                bgColor: verBg,
                icon: skill.isTier3PlatformProven
                    ? Icons.verified_rounded
                    : (skill.isTier2AiVerified && !skill.isExpired ? Icons.fact_check_rounded : Icons.edit_note_rounded),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(99),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 6,
              backgroundColor: const Color(0xFFF1F5F9),
              valueColor: AlwaysStoppedAnimation<Color>(devColor),
            ),
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '$totalSp / $nextThreshold SP · $shiftsCount ca hoàn thành',
                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: SkillHubColors.textSecondary),
              ),
              Text(
                (sp != null && sp.hasRealRating)
                    ? '${sp.avgRating.toStringAsFixed(1)} ★ (${sp.ratingCount} lượt)'
                    : 'Chưa có đánh giá',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: (sp != null && sp.hasRealRating) ? SkillHubColors.gold : SkillHubColors.textSecondary,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildOrphanPointCard(BuildContext context, WorkerSkillPointModel sp) {
    final devLabel = SkillHubColors.getDevelopmentLevelLabel(sp.tier);
    final devColor = SkillHubColors.getDevelopmentLevelColor(sp.tier);
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: SkillHubColors.surfaceTint,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: SkillHubColors.borderSoft),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  sp.jobType,
                  style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: SkillHubColors.textPrimary),
                ),
                const SizedBox(height: 3),
                Text(
                  '$devLabel · ${sp.totalSp} SP · ${sp.totalShiftsCompleted} ca hoàn thành',
                  style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: devColor),
                ),
              ],
            ),
          ),
          TextButton(
            onPressed: onDeclareSkills,
            child: const Text('Khai báo lại', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }

  Widget _buildChip({
    required String label,
    required Color textColor,
    required Color bgColor,
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 13, color: textColor),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: textColor),
          ),
        ],
      ),
    );
  }

  void _showSkillProgressSheet(BuildContext context, WorkerSkillItem skill, WorkerSkillPointModel? sp) {
    final tierCode = sp?.tier ?? 'STARTER';
    final devLabel = SkillHubColors.getDevelopmentAndTierLabel(tierCode);
    final totalSp = sp?.totalSp ?? skill.spVerifyAwarded;
    final spVerify = sp?.spVerify ?? skill.spVerifyAwarded;
    final spExp = sp?.spExperience ?? 0;
    final spRate = sp?.spRating ?? 0;
    final completed = sp?.totalShiftsCompleted ?? 0;
    final ratingLabel = (sp != null && sp.hasRealRating)
        ? '${sp.avgRating.toStringAsFixed(1)} sao (${sp.ratingCount} đánh giá thật)'
        : 'Chưa có đánh giá thật từ Ban tổ chức';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 14, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(color: SkillHubColors.borderSoft, borderRadius: BorderRadius.circular(2)),
              ),
            ),
            const SizedBox(height: 14),
            Text(
              'Tiến trình kỹ năng: ${skill.skillName}',
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: SkillHubColors.textPrimary),
            ),
            const SizedBox(height: 12),
            _detailRow('Mức phát triển:', devLabel),
            _detailRow('Mức xác minh:', skill.tierBadgeLabel),
            _detailRow('Tổng điểm kỹ năng:', '$totalSp SP'),
            _detailRow('• Điểm xác minh:', '$spVerify SP'),
            _detailRow('• Điểm kinh nghiệm:', '$spExp SP ($completed ca hoàn thành, +20 SP/ca)'),
            _detailRow('• Điểm đánh giá:', '$spRate SP'),
            _detailRow('Đánh giá thực tế:', ratingLabel),
            if (skill.expiresAt != null)
              _detailRow('Hạn chứng chỉ:', skill.expiresAt!.split('T').first),
            const Divider(height: 20),
            const Text(
              'Điều kiện đạt mức "Đã chứng minh qua ca làm":',
              style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w800, color: SkillHubColors.textPrimary),
            ),
            const SizedBox(height: 6),
            const Text(
              '• Hoàn thành tối thiểu 3 ca với kỹ năng này\n'
              '• Tỷ lệ hoàn thành ≥ 85%, tỷ lệ vắng mặt ≤ 5%\n'
              '• Điểm uy tín ≥ 70 và điểm đánh giá thật ≥ 4.5 sao',
              style: TextStyle(fontSize: 11.5, color: SkillHubColors.textSecondary, height: 1.5),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              height: 44,
              child: ElevatedButton(
                onPressed: () => Navigator.of(ctx).pop(),
                style: ElevatedButton.styleFrom(
                  backgroundColor: SkillHubColors.ctaPrimary,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
                child: const Text('Đóng', style: TextStyle(fontWeight: FontWeight.w700, color: Colors.white)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _detailRow(String k, String v) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 7),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 145,
            child: Text(k, style: const TextStyle(fontSize: 12, color: SkillHubColors.textSecondary)),
          ),
          Expanded(
            child: Text(v, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: SkillHubColors.textPrimary)),
          ),
        ],
      ),
    );
  }
}
