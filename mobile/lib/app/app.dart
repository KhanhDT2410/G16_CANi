import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/home_screen.dart';
import 'package:cani/app/theme.dart';
import 'package:cani/app/theme_provider.dart';

final GlobalKey<NavigatorState> rootNavigatorKey = GlobalKey<NavigatorState>();

/// Root application widget.
///
/// Configures MaterialApp with theme, darkTheme, themeMode and initial route.
class CaniApp extends ConsumerWidget {
  const CaniApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeProvider);

    return MaterialApp(
      navigatorKey: rootNavigatorKey,
      title: 'CANi',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: themeMode,
      home: const HomeScreen(),
    );
  }
}
