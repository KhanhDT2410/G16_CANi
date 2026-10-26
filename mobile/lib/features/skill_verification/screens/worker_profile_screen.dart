import 'package:flutter/material.dart';

import '../widgets/worker_profile_tab.dart';

class WorkerProfileScreen extends StatelessWidget {
  const WorkerProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: const Text(
          'Hồ Sơ & Cài Đặt',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: Color(0xFF0F172A)),
        ),
      ),
      body: const WorkerProfileTab(),
    );
  }
}
