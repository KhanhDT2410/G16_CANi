import 'package:flutter/material.dart';

/// Mức phát triển kỹ năng gộp để hiển thị thân thiện trên UI (từ 7 hạng DB).
enum SkillDevelopmentLevel {
  novice,
  reliable,
  proven,
}

/// Bảng màu và hàm ánh xạ duy nhất (Single Source of Truth) cho Kỹ năng & Hạng trên CANi Mobile.
class SkillHubColors {
  SkillHubColors._();

  // Primary Canvas & Surfaces
  static const Color canvas = Color(0xFFF8FAFC);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color surfaceTint = Color(0xFFF1F5F9);
  static const Color textPrimary = Color(0xFF0F172A);
  static const Color textSecondary = Color(0xFF64748B);
  static const Color ctaPrimary = Color(0xFF0284C7);
  static const Color ctaHover = Color(0xFF0369A1);
  static const Color accentBright = Color(0xFF36D1DC);
  static const Color borderSoft = Color(0xFFE2E8F0);
  static const Color borderSubtle = Color(0x66E2E8F0);

  // Semantic colors
  static const Color successText = Color(0xFF047857);
  static const Color successBg = Color(0xFFECFDF5);
  static const Color warningText = Color(0xFFB45309);
  static const Color warningBg = Color(0xFFFFFBEB);
  static const Color dangerText = Color(0xFFB91C1C);
  static const Color dangerBg = Color(0xFFFEF2F2);
  static const Color infoText = Color(0xFF0369A1);
  static const Color infoBg = Color(0xFFE0F2FE);

  // 7 SkillTier Colors
  static const Color starter = Color(0xFF0284C7);
  static const Color bronze = Color(0xFFCD7F32);
  static const Color silver = Color(0xFF64748B);
  static const Color gold = Color(0xFFD97706);
  static const Color platinum = Color(0xFF0D9488);
  static const Color diamond = Color(0xFF0891B2);
  static const Color master = Color(0xFF7C3AED);

  static const Map<String, int> _tierOrder = {
    'STARTER': 0,
    'BRONZE': 1,
    'SILVER': 2,
    'GOLD': 3,
    'PLATINUM': 4,
    'DIAMOND': 5,
    'MASTER': 6,
  };

  static int tierRank(String? tier) {
    return _tierOrder[(tier ?? 'STARTER').toUpperCase()] ?? 0;
  }

  /// Lấy hạng cao nhất hợp lệ trong danh sách kỹ năng (tránh dùng .first gây lệch theo thứ tự).
  static String resolveHighestTier(Iterable<dynamic> skillPoints) {
    String highest = 'STARTER';
    int highestRank = 0;
    for (final p in skillPoints) {
      final tierCode = (p.tier as String?) ?? 'STARTER';
      final rank = tierRank(tierCode);
      if (rank > highestRank) {
        highestRank = rank;
        highest = tierCode.toUpperCase();
      }
    }
    return highest;
  }

  static String tierFromSp(int totalSp) {
    if (totalSp >= 900) return 'MASTER';
    if (totalSp >= 700) return 'DIAMOND';
    if (totalSp >= 500) return 'PLATINUM';
    if (totalSp >= 350) return 'GOLD';
    if (totalSp >= 200) return 'SILVER';
    if (totalSp >= 100) return 'BRONZE';
    return 'STARTER';
  }

  /// Quy đổi 7 hạng chi tiết trong DB -> 3 Mức phát triển dễ hiểu cho người dùng Việt Nam.
  static SkillDevelopmentLevel getDevelopmentLevel(String? tier) {
    switch ((tier ?? 'STARTER').toUpperCase()) {
      case 'DIAMOND':
      case 'MASTER':
        return SkillDevelopmentLevel.proven;
      case 'SILVER':
      case 'GOLD':
      case 'PLATINUM':
        return SkillDevelopmentLevel.reliable;
      case 'STARTER':
      case 'BRONZE':
      default:
        return SkillDevelopmentLevel.novice;
    }
  }

  static String getDevelopmentLevelLabel(String? tier) {
    switch (getDevelopmentLevel(tier)) {
      case SkillDevelopmentLevel.novice:
        return 'Mới bắt đầu';
      case SkillDevelopmentLevel.reliable:
        return 'Có kinh nghiệm';
      case SkillDevelopmentLevel.proven:
        return 'Thành thạo';
    }
  }

  static Color getDevelopmentLevelColor(String? tier) {
    switch (getDevelopmentLevel(tier)) {
      case SkillDevelopmentLevel.novice:
        return const Color(0xFF0284C7);
      case SkillDevelopmentLevel.reliable:
        return const Color(0xFFD97706);
      case SkillDevelopmentLevel.proven:
        return const Color(0xFF047857);
    }
  }

  static Color getDevelopmentLevelBg(String? tier) {
    switch (getDevelopmentLevel(tier)) {
      case SkillDevelopmentLevel.novice:
        return const Color(0xFFE0F2FE);
      case SkillDevelopmentLevel.reliable:
        return const Color(0xFFFEF3C7);
      case SkillDevelopmentLevel.proven:
        return const Color(0xFFD1FAE5);
    }
  }

  /// Nhãn tiếng Việt chuẩn cho 3 Verification Tier (hoàn toàn độc lập với Skill Rank).
  static String getVerificationTierLabel(String? verificationTier) {
    switch ((verificationTier ?? 'SELF_DECLARED').toUpperCase()) {
      case 'PLATFORM_PROVEN':
        return 'Đã chứng minh qua ca làm';
      case 'AI_DOCUMENT':
        return 'Đã xác thực tài liệu';
      case 'SELF_DECLARED':
      default:
        return 'Tự khai báo';
    }
  }

  static String getVerificationTierDescription(String? verificationTier) {
    switch ((verificationTier ?? 'SELF_DECLARED').toUpperCase()) {
      case 'PLATFORM_PROVEN':
        return 'Đạt từ tối thiểu 3 ca hoàn thành tốt (≥ 85%), đánh giá thật ≥ 4.5 sao & uy tín ≥ 70.';
      case 'AI_DOCUMENT':
        return 'Chứng chỉ/tài liệu đã được xác minh tự động từ nguồn tra cứu chính thức.';
      case 'SELF_DECLARED':
      default:
        return 'Bạn tự thêm kỹ năng vào hồ sơ cá nhân.';
    }
  }

  static Color getVerificationTierColor(String? verificationTier, {bool isExpired = false}) {
    if (isExpired) return dangerText;
    switch ((verificationTier ?? 'SELF_DECLARED').toUpperCase()) {
      case 'PLATFORM_PROVEN':
        return successText;
      case 'AI_DOCUMENT':
        return ctaPrimary;
      case 'SELF_DECLARED':
      default:
        return textSecondary;
    }
  }

  static Color getVerificationTierBg(String? verificationTier, {bool isExpired = false}) {
    if (isExpired) return dangerBg;
    switch ((verificationTier ?? 'SELF_DECLARED').toUpperCase()) {
      case 'PLATFORM_PROVEN':
        return successBg;
      case 'AI_DOCUMENT':
        return infoBg;
      case 'SELF_DECLARED':
      default:
        return surfaceTint;
    }
  }

  static Color getTierColor(String tier) {
    switch (tier.toUpperCase()) {
      case 'MASTER':
        return master;
      case 'DIAMOND':
        return diamond;
      case 'PLATINUM':
        return platinum;
      case 'GOLD':
        return gold;
      case 'SILVER':
        return silver;
      case 'BRONZE':
        return bronze;
      case 'STARTER':
      default:
        return starter;
    }
  }

  static IconData getTierIcon(String tier) {
    switch (tier.toUpperCase()) {
      case 'MASTER':
        return Icons.auto_awesome;
      case 'DIAMOND':
        return Icons.diamond_outlined;
      case 'PLATINUM':
        return Icons.workspace_premium;
      case 'GOLD':
        return Icons.military_tech;
      case 'SILVER':
        return Icons.military_tech_outlined;
      case 'BRONZE':
        return Icons.shield;
      case 'STARTER':
      default:
        return Icons.shield_outlined;
    }
  }

  static int getNextTierThreshold(int totalSp) {
    if (totalSp < 100) return 100; // BRONZE
    if (totalSp < 200) return 200; // SILVER
    if (totalSp < 350) return 350; // GOLD
    if (totalSp < 500) return 500; // PLATINUM
    if (totalSp < 700) return 700; // DIAMOND
    return 900; // MASTER
  }

  static String getNextTierName(int totalSp) {
    if (totalSp < 100) return 'Hạng Đồng';
    if (totalSp < 200) return 'Hạng Bạc';
    if (totalSp < 350) return 'Hạng Vàng';
    if (totalSp < 500) return 'Hạng Bạch Kim';
    if (totalSp < 700) return 'Hạng Kim Cương';
    return 'Hạng Tinh Anh';
  }

  static String getTierTitle(String tier) {
    switch (tier.toUpperCase()) {
      case 'MASTER':
        return 'Tinh anh';
      case 'DIAMOND':
        return 'Kim cương';
      case 'PLATINUM':
        return 'Bạch kim';
      case 'GOLD':
        return 'Vàng';
      case 'SILVER':
        return 'Bạc';
      case 'BRONZE':
        return 'Đồng';
      case 'STARTER':
      default:
        return 'Khởi đầu';
    }
  }

  static String getDevelopmentAndTierLabel(String? tier) {
    final safeTier = (tier ?? 'STARTER').toUpperCase();
    return '${getDevelopmentLevelLabel(safeTier)} (${getTierTitle(safeTier)})';
  }

  static String getTierBenefit(String tier) {
    switch (tier.toUpperCase()) {
      case 'MASTER':
        return 'Thành thạo cao nhất — ưu tiên hàng đầu cho các ca sự kiện yêu cầu cao';
      case 'DIAMOND':
        return 'Thành thạo — hồ sơ nổi bật và độ tin cậy thực chiến vượt trội';
      case 'PLATINUM':
        return 'Có kinh nghiệm vững vàng — nhận đề xuất ca khẩn cấp và sự kiện quy mô lớn';
      case 'GOLD':
        return 'Có kinh nghiệm — ưu tiên đề xuất các ca có thù lao cạnh tranh';
      case 'SILVER':
        return 'Có kinh nghiệm — tăng tỷ lệ ghép nối với nhiều nhà tổ chức uy tín';
      case 'BRONZE':
        return 'Mới bắt đầu — đủ nền tảng nhận các ca sự kiện tiêu chuẩn';
      case 'STARTER':
      default:
        return 'Mới bắt đầu — tích lũy điểm qua ca hoàn thành và xác thực chứng chỉ';
    }
  }
}
