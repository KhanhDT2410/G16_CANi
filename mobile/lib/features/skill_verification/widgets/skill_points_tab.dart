import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/widgets/my_skills_section.dart';

class SkillPointsTab extends ConsumerWidget {
  const SkillPointsTab({super.key});

  int _calculateNextTierThreshold(int totalSp) {
    if (totalSp < 100) return 100; // BRONZE
    if (totalSp < 200) return 200; // SILVER (BR-64: 200)
    if (totalSp < 350) return 350; // GOLD (BR-64: 350)
    if (totalSp < 500) return 500; // PLATINUM (BR-64: 500)
    if (totalSp < 700) return 700; // DIAMOND (BR-64: 700)
    return 900; // MASTER (BR-64: 900+)
  }

  String _getNextTierName(int totalSp) {
    if (totalSp < 100) return 'BRONZE';
    if (totalSp < 200) return 'SILVER';
    if (totalSp < 350) return 'GOLD';
    if (totalSp < 500) return 'PLATINUM';
    if (totalSp < 700) return 'DIAMOND';
    return 'MASTER';
  }

  Color _getTierColor(String tier) {
    switch (tier.toUpperCase()) {
      case 'MASTER':
        return Colors.deepPurple;
      case 'DIAMOND':
        return Colors.cyan.shade700;
      case 'PLATINUM':
        return Colors.teal;
      case 'GOLD':
        return Colors.amber.shade800;
      case 'SILVER':
        return Colors.blueGrey;
      case 'BRONZE':
        return Colors.brown;
      default:
        return Colors.blue;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(skillVerificationProvider);
    final theme = Theme.of(context);

    if (state.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    final pointsList = state.skillPoints;
    final totalAllSp = pointsList.fold<int>(0, (sum, p) => sum + p.totalSp);
    final nextThreshold = _calculateNextTierThreshold(totalAllSp);
    final nextTierName = _getNextTierName(totalAllSp);
    final progress = (totalAllSp / nextThreshold).clamp(0.0, 1.0);

    return RefreshIndicator(
      onRefresh: () => ref.read(skillVerificationProvider.notifier).loadAll(),
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Overall Rank Banner
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [Colors.indigo.shade800, Colors.purple.shade700],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: Colors.indigo.withOpacity(0.3),
                    blurRadius: 15,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'TỔNG ĐIỂM KỸ NĂNG (TOTAL SP)',
                              style: TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 1),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              '$totalAllSp SP',
                              style: const TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold),
                            ),
                          ],
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: Colors.white.withOpacity(0.3)),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.workspace_premium, color: Colors.amber, size: 22),
                            const SizedBox(width: 6),
                            Text(
                              pointsList.isNotEmpty ? pointsList.first.tier : 'STARTER',
                              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Tiến độ lên hạng $nextTierName',
                        style: const TextStyle(color: Colors.white70, fontSize: 13),
                      ),
                      Text(
                        '$totalAllSp / $nextThreshold SP',
                        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: LinearProgressIndicator(
                      value: progress,
                      minHeight: 10,
                      backgroundColor: Colors.white.withOpacity(0.2),
                      valueColor: const AlwaysStoppedAnimation<Color>(Colors.amberAccent),
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 24),

            // Kỹ Năng Đã Kích Hoạt & Bậc Xác Thực (A-14 / UC-45)
            const MySkillsSection(),

            const SizedBox(height: 24),

            Text(
              'Bảng Điểm Theo Vị Trí Nghề Nghiệp',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),

            if (pointsList.isEmpty)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceVariant.withOpacity(0.4),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
                child: Column(
                  children: [
                    Icon(Icons.workspace_premium_outlined, size: 48, color: theme.colorScheme.primary),
                    const SizedBox(height: 12),
                    const Text(
                      'Chưa có điểm kỹ năng nào',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'Hãy sang tab "Thẩm Định AI" và nộp bằng cấp/chứng chỉ để nhận ngay +25 SP đầu tiên!',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey, fontSize: 13),
                    ),
                  ],
                ),
              )
            else
              ...pointsList.map((p) => _buildJobTypeCard(context, p)),

            const SizedBox(height: 28),

            // Quy tắc tính điểm SP
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.blue.withOpacity(0.06),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: Colors.blue.withOpacity(0.2)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.help_outline, size: 18, color: Colors.blue),
                      const SizedBox(width: 8),
                      Text(
                        'Cơ chế tích lũy Điểm Kỹ Năng (SP):',
                        style: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.bold, color: Colors.blue.shade900),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  const Text('• SP_verify (+25 SP): Tự động cộng khi AI hoặc Admin duyệt chứng chỉ hợp lệ.', style: TextStyle(fontSize: 12.5)),
                  const SizedBox(height: 4),
                  const Text('• SP_experience (+2 SP/ca): Tích lũy khi hoàn thành xuất sắc ca làm thực tế.', style: TextStyle(fontSize: 12.5)),
                  const SizedBox(height: 4),
                  const Text('• SP_rating (Đánh giá): Điểm thưởng khi nhận đánh giá 5 sao từ Event Organizer.', style: TextStyle(fontSize: 12.5)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildJobTypeCard(BuildContext context, WorkerSkillPointModel p) {
    final theme = Theme.of(context);
    final tierColor = _getTierColor(p.tier);

    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(18.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Row(
                    children: [
                      CircleAvatar(
                        radius: 18,
                        backgroundColor: tierColor.withOpacity(0.15),
                        child: Icon(Icons.star, color: tierColor, size: 20),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          p.jobType,
                          style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: tierColor.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    p.tier,
                    style: TextStyle(color: tierColor, fontWeight: FontWeight.bold, fontSize: 12),
                  ),
                ),
              ],
            ),
            const Divider(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildMetricCol('SP Xác Thực', '+${p.spVerify}', Colors.green),
                _buildMetricCol('SP Thâm Niên', '+${p.spExperience}', Colors.blue),
                _buildMetricCol('SP Đánh Giá', '+${p.spRating}', Colors.orange),
                _buildMetricCol('Tổng Điểm', '${p.totalSp} SP', theme.colorScheme.primary, isBold: true),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceVariant.withOpacity(0.3),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Ca hoàn thành: ${p.totalShiftsCompleted}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
                  Row(
                    children: [
                      const Icon(Icons.star, size: 14, color: Colors.amber),
                      const SizedBox(width: 4),
                      Text('${p.avgRating.toStringAsFixed(1)} / 5.0', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMetricCol(String label, String value, Color color, {bool isBold = false}) {
    return Column(
      children: [
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
        const SizedBox(height: 4),
        Text(
          value,
          style: TextStyle(
            fontSize: isBold ? 16 : 14,
            fontWeight: isBold ? FontWeight.bold : FontWeight.w600,
            color: color,
          ),
        ),
      ],
    );
  }
}
