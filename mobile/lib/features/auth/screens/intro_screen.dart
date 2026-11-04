import 'package:flutter/material.dart';

import 'package:cani/features/auth/screens/login_screen.dart';
import 'package:cani/features/auth/screens/register_screen.dart';
import 'package:cani/shared/widgets/cani_brand_logo.dart';
import 'package:cani/shared/widgets/worker_illustration_card.dart';

/// IntroScreen - Màn hình Khởi động / Giới thiệu ứng dụng CANi Worker
/// Thiết kế chuẩn theo OpenDesign mockup (cani-intro):
/// - Logo thương hiệu CANi Event Platform
/// - Slogan: "Nơi bạn nhận ca sự kiện linh hoạt"
/// - Worker Illustration Card với ánh sáng festival sân khấu
/// - Nút "Đăng nhập ->" (Gradient Cyan-Blue)
/// - Nút "Đăng ký ngay" (Outlined Border Blue)
/// - Footer: "Vào việc nhanh · Check-in QR · Nhận lương minh bạch"
class IntroScreen extends StatelessWidget {
  const IntroScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;

    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            return SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  minHeight: constraints.maxHeight - 32,
                ),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const SizedBox(height: 8),

                    // 1. Brand Header
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        CaniBrandLogo(
                          width: (screenWidth * 0.72).clamp(190.0, 270.0),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          'Nơi bạn nhận ca sự kiện linh hoạt',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w500,
                            color: Color(0xFF64748B),
                            height: 1.4,
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 16),

                    // 2. Worker Illustration Card (Hero Art)
                    const WorkerIllustrationCard(
                      height: 205,
                      margin: EdgeInsets.symmetric(vertical: 12),
                    ),

                    const SizedBox(height: 16),

                    // 3. CTA Actions & Footer
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // Nút Đăng nhập (Primary Gradient)
                        Container(
                          height: 52,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(12),
                            gradient: const LinearGradient(
                              colors: [Color(0xFF00D2FF), Color(0xFF0066FF)],
                              begin: Alignment.centerLeft,
                              end: Alignment.centerRight,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: const Color(0xFF0077FF).withValues(alpha: 0.32),
                                blurRadius: 14,
                                offset: const Offset(0, 5),
                              ),
                            ],
                          ),
                          child: ElevatedButton(
                            onPressed: () {
                              Navigator.of(context).push(
                                MaterialPageRoute<void>(
                                  builder: (_) => const LoginScreen(),
                                ),
                              );
                            },
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.transparent,
                              shadowColor: Colors.transparent,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                            child: const Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text(
                                  'Đăng nhập',
                                  style: TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.w700,
                                    color: Colors.white,
                                    letterSpacing: -0.2,
                                  ),
                                ),
                                SizedBox(width: 8),
                                Icon(
                                  Icons.arrow_forward_rounded,
                                  color: Colors.white,
                                  size: 20,
                                ),
                              ],
                            ),
                          ),
                        ),

                        const SizedBox(height: 12),

                        // Nút Đăng ký ngay (Secondary Outlined)
                        SizedBox(
                          height: 52,
                          child: OutlinedButton(
                            onPressed: () {
                              Navigator.of(context).push(
                                MaterialPageRoute<void>(
                                  builder: (_) => const RegisterScreen(),
                                ),
                              );
                            },
                            style: OutlinedButton.styleFrom(
                              side: const BorderSide(
                                color: Color(0xFF0077FF),
                                width: 1.5,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                            child: const Text(
                              'Đăng ký ngay',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: Color(0xFF0077FF),
                                letterSpacing: -0.2,
                              ),
                            ),
                          ),
                        ),

                        const SizedBox(height: 22),

                        // 4. Footer Keywords
                        const Text(
                          'Vào việc nhanh  ·  Check-in QR  ·  Nhận lương minh bạch',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                            color: Color(0xFF64748B),
                            letterSpacing: 0.1,
                          ),
                        ),
                        const SizedBox(height: 4),
                      ],
                    ),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

