import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cani/app/app.dart';

void main() {
  testWidgets('CANi app smoke test', (WidgetTester tester) async {
    // Build the app and trigger a frame wrapped in ProviderScope
    await tester.pumpWidget(
      const ProviderScope(
        child: CaniApp(),
      ),
    );

    // Verify the app title is displayed
    expect(find.text('CANi'), findsWidgets);
    expect(find.text('Event Staffing Platform'), findsOneWidget);
  });
}
