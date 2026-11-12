import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cani/app/app.dart';
import 'package:cani/features/auth/data/auth_repository.dart';
import 'package:cani/features/auth/models/user_dto.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';

/// Deterministic stub: bypasses FlutterSecureStorage / Supabase plugin calls
/// (which throw MissingPluginException in tests).
class _FakeAuthRepository implements AuthRepository {
  @override
  Future<UserDto?> getSavedUser() async => null;

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

void main() {
  testWidgets('CANi app smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authRepositoryProvider.overrideWith((ref) => _FakeAuthRepository()),
          // HomeScreen watches proposalsCountProvider unconditionally; stub it
          // so no real Dio HTTP timer is left pending at test teardown.
          proposalsCountProvider.overrideWith((ref) async => 0),
        ],
        child: const CaniApp(),
      ),
    );

    // Deterministic auth check: stub returns no saved user → unauthenticated
    // → LoginScreen renders. Bounded pumps: the loading spinner would never
    // settle on its own.
    for (var i = 0; i < 5; i++) {
      await tester.pump();
    }

    expect(find.text('Đăng Nhập'), findsOneWidget);
    expect(find.text('Đăng ký ngay'), findsOneWidget);
  });
}