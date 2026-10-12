import 'package:flutter_test/flutter_test.dart';
import 'package:cani/main.dart';

void main() {
  testWidgets('CANi app smoke test', (WidgetTester tester) async {
    // Build the app and trigger a frame
    await tester.pumpWidget(const CaniApp());

    // Verify the app title is displayed
    expect(find.text('CANi'), findsWidgets);
    expect(find.text('Event Staffing Platform'), findsOneWidget);
  });
}
