import 'package:flutter/material.dart';

/// Clean Sky color system and design tokens for Skill Mastery Hub.
class SkillHubColors {
  SkillHubColors._();

  // Primary Canvas & Surfaces
  static const Color canvas = Color(0xFFF4FCFE);       // Clean Sky soft ice blue background
  static const Color surface = Color(0xFFFFFFFF);      // Pure white card surfaces
  static const Color surfaceTint = Color(0xFFF0FAFD);  // Container contrast
  static const Color textPrimary = Color(0xFF03045E);  // Deep navy for high readability & contrast
  static const Color textSecondary = Color(0xFF0077B6);// Subtitles, metadata, captions
  static const Color ctaPrimary = Color(0xFF00B4D8);   // Sky cyan-blue active states & highlights
  static const Color ctaHover = Color(0xFF0077B6);     // Darker cyan/blue hover/active
  static const Color borderSoft = Color(0xFF90E0EF);   // Soft cyan borders
  static const Color borderSubtle = Color(0x6690E0EF); // 40% alpha border

  // Tier Colors (Single Source of Truth)
  static const Color starter = Color(0xFF00B4D8);
  static const Color bronze = Color(0xFFCD7F32);
  static const Color silver = Color(0xFF94A3B8);
  static const Color gold = Color(0xFFF59E0B);
  static const Color platinum = Color(0xFF14B8A6);
  static const Color diamond = Color(0xFF06B6D4);
  static const Color master = Color(0xFF8B5CF6);

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
    return 900;                    // MASTER
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
        return 'Hạng Tinh Anh (Master)';
      case 'DIAMOND':
        return 'Hạng Kim Cương';
      case 'PLATINUM':
        return 'Hạng Bạch Kim';
      case 'GOLD':
        return 'Hạng Vàng';
      case 'SILVER':
        return 'Hạng Bạc';
      case 'BRONZE':
        return 'Hạng Đồng';
      case 'STARTER':
      default:
        return 'Hạng Khởi Đầu';
    }
  }

  static String getTierBenefit(String tier) {
    switch (tier.toUpperCase()) {
      case 'MASTER':
        return 'Chuyên gia sự kiện hàng đầu, ưu tiên tuyệt đối mọi ca làm';
      case 'DIAMOND':
        return 'Hồ sơ nổi bật ưu tiên cao nhất trên bản đồ Radar việc làm';
      case 'PLATINUM':
        return 'Nhận thông báo ca khẩn cấp sớm hơn 15 phút';
      case 'GOLD':
        return 'Ưu tiên ứng tuyển các ca VIP với mức thù lao cao nhất';
      case 'SILVER':
        return 'Tự động ghép nối nhanh với các nhà tổ chức uy tín';
      case 'BRONZE':
        return 'Đủ điều kiện nhận các ca sự kiện tiêu chuẩn tại địa phương';
      case 'STARTER':
      default:
        return 'Tích lũy SP qua ca làm và chứng chỉ để thăng hạng';
    }
  }
}
