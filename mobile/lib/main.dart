import 'package:flutter/material.dart';
import 'package:cani/screens/home_screen.dart';

void main() {
  runApp(const CaniApp());
}

class CaniApp extends StatelessWidget {
  const CaniApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CANi',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF3B82F6),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
