import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cani/app/home_screen.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/screens/rate_eo_screen.dart';
import 'package:cani/features/job_proposals/screens/shift_payment_screen.dart';
import 'package:cani/features/job_proposals/widgets/shift_assignment_card.dart';

class _FakeProposalRepository extends ProposalRepository {
  final List<ShiftAssignmentItem> items;
  bool submitReviewCalled = false;
  _FakeProposalRepository(this.items) : super(Dio());

  @override
  Future<List<ShiftAssignmentItem>> fetchAssignments({String? token}) async {
    return items;
  }

  @override
  Future<bool> submitReview({
    required String assignmentId,
    required int rating,
    required int criteriaWorkEnvironment,
    required int criteriaSupport,
    required int criteriaPaymentTiming,
    String? comment,
    List<String>? tags,
    String? token,
  }) async {
    submitReviewCalled = true;
    return true;
  }
}

ShiftAssignmentItem _makeAssignment({
  String status = 'CHECKED_IN',
  bool eoConfirmed = true,
  String paymentStatus = 'DECLARED_PENDING_WORKER',
  double declaredAmount = 160000,
  String? workerConfirmStatus,
  String? paymentProofUrl,
  DateTime? startTime,
  DateTime? endTime,
  DateTime? checkinTime,
  DateTime? checkoutTime,
  bool? hasWorkerReviewed,
}) {
  final start = startTime ?? DateTime(2026, 11, 20, 12, 0);
  final end = endTime ?? DateTime(2026, 11, 20, 16, 0);
  return ShiftAssignmentItem(
    assignmentId: 'assign-1',
    shiftId: 'shift-1',
    eventId: 'evt-1',
    eventName: 'Nhà giáo Việt Nam',
    eoName: 'Ban tổ chức Sự kiện',
    venueAddress: 'Hòa Lạc, Hà Nội',
    jobType: 'Phục vụ bàn',
    startTime: start,
    endTime: end,
    hourlyWage: 40000,
    totalPay: 160000,
    status: status,
    eoConfirmed: eoConfirmed,
    paymentStatus: paymentStatus,
    declaredAmount: declaredAmount,
    paymentMethod: 'BANK_TRANSFER',
    checkinTime: checkinTime ?? start,
    checkoutTime: checkoutTime,
    workerConfirmStatus: workerConfirmStatus,
    paymentProofUrl: paymentProofUrl,
    hasWorkerReviewed: hasWorkerReviewed,
  );
}

void main() {
  testWidgets('ShiftAssignmentCard shows overdue pill, date and routes Step 4 vs Step 5', (tester) async {
    bool paymentTapped = false;
    bool qrTapped = false;

    final step4Assignment = _makeAssignment(
      status: 'CHECKED_IN',
      eoConfirmed: true,
      paymentStatus: 'DECLARED_PENDING_WORKER',
      startTime: DateTime.now().subtract(const Duration(hours: 6)),
      endTime: DateTime.now().subtract(const Duration(hours: 2)),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: ShiftAssignmentCard(
              assignment: step4Assignment,
              onNavigate: () {},
              onQrScan: () => qrTapped = true,
              onConfirmPayment: () => paymentTapped = true,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('QUÁ GIỜ · CHỜ KẾT CA'), findsOneWidget);
    expect(find.text('Thanh toán thù lao (Bước 4)'), findsOneWidget);
    await tester.tap(find.text('Thanh toán thù lao (Bước 4)'));
    expect(paymentTapped, isTrue);
    expect(qrTapped, isFalse);

    final step5Assignment = _makeAssignment(
      status: 'CHECKED_IN',
      eoConfirmed: true,
      paymentStatus: 'CONFIRMED_FULL',
      workerConfirmStatus: 'RECEIVED_FULL',
      startTime: DateTime.now().subtract(const Duration(hours: 6)),
      endTime: DateTime.now().subtract(const Duration(hours: 2)),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: ShiftAssignmentCard(
              assignment: step5Assignment,
              onNavigate: () {},
              onQrScan: () => qrTapped = true,
              onConfirmPayment: () {},
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Quét QR Check-out (Bước 5)'), findsOneWidget);
    await tester.tap(find.text('Quét QR Check-out (Bước 5)'));
    expect(qrTapped, isTrue);
  });

  testWidgets('ShiftPaymentScreen shows two-party evidence & Check-out next button when confirmed', (tester) async {
    final confirmedItem = _makeAssignment(
      status: 'CHECKED_IN',
      eoConfirmed: true,
      paymentStatus: 'CONFIRMED_FULL',
      workerConfirmStatus: 'RECEIVED_FULL',
      paymentProofUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          proposalRepositoryProvider.overrideWithValue(_FakeProposalRepository([confirmedItem])),
        ],
        child: MaterialApp(
          home: ShiftPaymentScreen(assignment: confirmedItem),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Thanh toán thù lao · Bước 4/6'), findsOneWidget);
    expect(find.text('Trạng thái xác nhận 2 bên'), findsOneWidget);
    expect(find.text('Chứng từ thanh toán từ BTC'), findsOneWidget);
    expect(find.text('TIẾP TỤC: QUÉT QR CHECK-OUT (BƯỚC 5)'), findsOneWidget);
  });

  testWidgets('RateEoScreen renders dedicated Step 6 rating page', (tester) async {
    final completedItem = _makeAssignment(
      status: 'COMPLETED',
      eoConfirmed: true,
      paymentStatus: 'CONFIRMED_FULL',
      workerConfirmStatus: 'RECEIVED_FULL',
      checkoutTime: DateTime(2026, 11, 20, 16, 5),
      hasWorkerReviewed: false,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          proposalRepositoryProvider.overrideWithValue(_FakeProposalRepository([completedItem])),
        ],
        child: MaterialApp(
          home: RateEoScreen(assignment: completedItem, checkoutHoursWorked: 4.1),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Đánh giá Ban tổ chức · Bước 6/6'), findsOneWidget);
    expect(find.text('4.1 giờ'), findsOneWidget);
    expect(find.text('Gửi Đánh Giá & Hoàn Tất'), findsOneWidget);
  });

  testWidgets('RateEoScreen hides submit button when already reviewed', (tester) async {
    final reviewedItem = _makeAssignment(
      status: 'COMPLETED',
      eoConfirmed: true,
      paymentStatus: 'CONFIRMED_FULL',
      workerConfirmStatus: 'RECEIVED_FULL',
      checkoutTime: DateTime(2026, 11, 20, 16, 5),
      hasWorkerReviewed: true,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          proposalRepositoryProvider.overrideWithValue(_FakeProposalRepository([reviewedItem])),
        ],
        child: MaterialApp(
          home: RateEoScreen(assignment: reviewedItem, checkoutHoursWorked: 4.1),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Bạn đã hoàn thành ca và gửi đánh giá Ban tổ chức rồi.'), findsOneWidget);
    expect(find.text('Gửi Đánh Giá & Hoàn Tất'), findsNothing);
    expect(find.text('Để sau'), findsNothing);
  });

  testWidgets('RateEoScreen returns directly to shifts list (root route + tab 1) after submitting review', (tester) async {
    final completedItem = _makeAssignment(
      status: 'COMPLETED',
      eoConfirmed: true,
      paymentStatus: 'CONFIRMED_FULL',
      workerConfirmStatus: 'RECEIVED_FULL',
      checkoutTime: DateTime(2026, 11, 20, 16, 5),
      hasWorkerReviewed: false,
    );

    final fakeRepo = _FakeProposalRepository([completedItem]);
    final container = ProviderContainer(
      overrides: [
        proposalRepositoryProvider.overrideWithValue(fakeRepo),
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          home: Builder(
            builder: (rootCtx) => Scaffold(
              body: ElevatedButton(
                onPressed: () {
                  Navigator.push(
                    rootCtx,
                    MaterialPageRoute(
                      builder: (detailCtx) => Scaffold(
                        body: ElevatedButton(
                          onPressed: () {
                            Navigator.push(
                              detailCtx,
                              MaterialPageRoute(
                                builder: (_) => RateEoScreen(
                                  assignment: completedItem,
                                  checkoutHoursWorked: 4.0,
                                ),
                              ),
                            );
                          },
                          child: const Text('Mở đánh giá'),
                        ),
                      ),
                    ),
                  );
                },
                child: const Text('Trang danh sách ca'),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Trang danh sách ca'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Mở đánh giá'));
    await tester.pumpAndSettle();

    expect(find.text('Đánh giá Ban tổ chức · Bước 6/6'), findsOneWidget);
    await tester.tap(find.text('Gửi Đánh Giá & Hoàn Tất'));
    await tester.pumpAndSettle();

    expect(fakeRepo.submitReviewCalled, isTrue);
    expect(container.read(homeTabIndexProvider), 1);
    expect(find.text('Trang danh sách ca'), findsOneWidget);
    expect(find.text('Đánh giá Ban tổ chức · Bước 6/6'), findsNothing);
    expect(find.text('Mở đánh giá'), findsNothing);
  });
}
