import 'package:flutter/material.dart';

import 'package:cani/app/home_screen.dart';
import 'package:cani/app/theme.dart';

final GlobalKey<NavigatorState> rootNavigatorKey = GlobalKey<NavigatorState>();

/// Root application widget.
///
/// Configures MaterialApp with theme and initial route.
class CaniApp extends StatelessWidget {
  const CaniApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: rootNavigatorKey,
      title: 'CANi',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      home: const HomeScreen(),
    );
  }
}
