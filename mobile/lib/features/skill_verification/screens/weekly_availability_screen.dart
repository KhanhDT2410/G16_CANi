import 'package:flutter/material.dart';
import 'worker_settings_screen.dart';

/// Lớp chuyển tiếp bảo toàn tính tương thích ngược (Backward-Compatible Forwarder).
/// Theo kiến trúc mới, màn hình Lịch rảnh tuần và Tiêu chí tìm việc đã được hợp nhất
/// thành một màn hình trực quan duy nhất: [WorkerSettingsScreen].
class WeeklyAvailabilityScreen extends StatelessWidget {
  const WeeklyAvailabilityScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const WorkerSettingsScreen();
  }
}
