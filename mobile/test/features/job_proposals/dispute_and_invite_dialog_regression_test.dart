import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cani/app/theme.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/dispute_models.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/screens/my_assignments_screen.dart';
import 'package:cani/features/job_proposals/screens/my_disputes_screen.dart';
import 'package:cani/features/job_proposals/screens/shift_payment_screen.dart';
import 'package:cani/features/job_radar/widgets/job_radar_proposals_sheet.dart';

class _FakeProposalRepository extends ProposalRepository {
  _FakeProposalRepository() : super(Dio());

  String? lastJoinedInviteCode;
  CreateDisputePayload? lastCreatedDispute;
  String? lastPaymentStatus;
  String? lastPaymentComment;

  @override
  Future<List<WorkerDisputeItem>> fetchMyDisputes() async => [];

  @override
  Future<List<DisputeEligibleAssignmentItem>> fetchEligibleAssignmentsForDispute() async => [
        DisputeEligibleAssignmentItem(
          assignmentId: 'a1',
          eventName: 'Tech Summit 2026',
          jobType: 'Lễ tân',
          counterpartyName: 'ABC Events',
          startTime: DateTime(2026, 11, 20, 8, 0),
        ),
      ];

  @override
  Future<WorkerDisputeItem> createDispute(CreateDisputePayload payload) async {
    lastCreatedDispute = payload;
    return WorkerDisputeItem(
      id: 'd1',
      assignmentId: payload.assignmentId,
      type: payload.type,
      status: 'OPEN',
      title: payload.title,
      description: payload.description,
      createdAt: DateTime.now(),
    );
  }

  @override
  Future<List<ShiftAssignmentItem>> fetchAssignments({String? token}) async => [];

  @override
  Future<WorkerIncomeSummary> fetchIncomeSummary({String? token}) async =>
      const WorkerIncomeSummary(
        completedShiftsCount: 0,
        totalHoursWorked: 0,
        totalEarnings: 0,
        pendingEarnings: 0,
      );

  @override
  Future<bool> joinViaInviteCode(String code, {String? token}) async {
    lastJoinedInviteCode = code;
    return true;
  }

  @override
  Future<bool> confirmPayment(String assignmentId, String status, String comment, {String? token}) async {
    lastPaymentStatus = status;
    lastPaymentComment = comment;
    return true;
  }
}

ShiftAssignmentItem _buildAssignment({
  String status = 'CHECKED_IN',
  bool eoConfirmed = false,
  String paymentStatus = 'NOT_DECLARED',
}) {
  return ShiftAssignmentItem(
    assignmentId: 'a1',
    shiftId: 's1',
    eventId: 'e1',
    eventName: 'Tech Summit 2026',
    venueAddress: '123 Lê Lợi, Q.1',
    jobType: 'Lễ tân',
    startTime: DateTime(2026, 11, 24, 8, 0),
    endTime: DateTime(2026, 11, 24, 17, 0),
    hourlyWage: 50000,
    totalPay: 450000,
    status: status,
    eoConfirmed: eoConfirmed,
    paymentStatus: paymentStatus,
    declaredAmount: 450000,
    paymentMethod: 'CASH',
  );
}

void main() {
  testWidgets('MyDisputesScreen submits in-shift dispute without controller disposal crash', (tester) async {
    final repo = _FakeProposalRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [proposalRepositoryProvider.overrideWithValue(repo)],
        child: MaterialApp(
          theme: AppTheme.light,
          home: MyDisputesScreen(assignment: _buildAssignment()),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Tạo khiếu nại cho ca này'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).at(0), 'Thiếu phụ cấp ăn trưa');
    await tester.enterText(
      find.byType(TextField).at(1),
      'Ban tổ chức đã cam kết hỗ trợ tiền ăn trưa nhưng khi thanh toán chưa cộng thêm.',
    );
    await tester.pump();
    await tester.ensureVisible(find.text('GỬI KHIẾU NẠI NGAY'));
    await tester.tap(find.text('GỬI KHIẾU NẠI NGAY'));
    await tester.pumpAndSettle();

    expect(repo.lastCreatedDispute?.title, 'Thiếu phụ cấp ăn trưa');
    expect(find.textContaining('Đã gửi khiếu nại thành công'), findsOneWidget);
  });

  testWidgets('MyDisputesScreen without preset assignment submits dispute cleanly', (tester) async {
    final repo = _FakeProposalRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [proposalRepositoryProvider.overrideWithValue(repo)],
        child: MaterialApp(
          theme: AppTheme.light,
          home: const MyDisputesScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.add_circle_outline_rounded));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).at(0), 'Sai số giờ làm việc');
    await tester.enterText(
      find.byType(TextField).at(1),
      'Hệ thống ghi nhận thiếu 1 giờ làm việc thực tế so với thời gian check-in và check-out.',
    );
    await tester.pump();
    await tester.ensureVisible(find.text('GỬI KHIẾU NẠI NGAY'));
    await tester.tap(find.text('GỬI KHIẾU NẠI NGAY'));
    await tester.pumpAndSettle();

    expect(repo.lastCreatedDispute?.assignmentId, 'a1');
  });

  testWidgets('MyAssignmentsScreen QR/invite code dialog closes cleanly after submit', (tester) async {
    final repo = _FakeProposalRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [proposalRepositoryProvider.overrideWithValue(repo)],
        child: MaterialApp(
          theme: AppTheme.light,
          home: const MyAssignmentsScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Khi chưa có ca active, nút Quét QR mở dialog nhập mã mời vào ca
    await tester.tap(find.text('Quét QR'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'CANI-8XK2PW');
    await tester.tap(find.text('Tham Gia'));
    await tester.pumpAndSettle();

    expect(repo.lastJoinedInviteCode, 'CANI-8XK2PW');
    expect(find.text('Tham gia ca làm việc thành công!'), findsOneWidget);
  });

  testWidgets('JobRadar invite code dialog closes cleanly after submit', (tester) async {
    final repo = _FakeProposalRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [proposalRepositoryProvider.overrideWithValue(repo)],
        child: MaterialApp(
          theme: AppTheme.light,
          home: Consumer(
            builder: (context, ref, _) => Scaffold(
              body: ElevatedButton(
                onPressed: () => showJobRadarInviteCodeDialog(context, ref),
                child: const Text('Mở mã mời'),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Mở mã mời'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'CANI-999AAA');
    await tester.tap(find.text('Tham Gia'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 4));

    expect(repo.lastJoinedInviteCode, 'CANI-999AAA');
  });

  testWidgets('ShiftPaymentScreen report not-received dialog closes cleanly after submit', (tester) async {
    final repo = _FakeProposalRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [proposalRepositoryProvider.overrideWithValue(repo)],
        child: MaterialApp(
          theme: AppTheme.light,
          home: ShiftPaymentScreen(
            assignment: _buildAssignment(
              eoConfirmed: true,
              paymentStatus: 'DECLARED_PENDING_WORKER',
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final notReceivedBtn = find.text('Chưa nhận');
    await tester.ensureVisible(notReceivedBtn);
    await tester.tap(notReceivedBtn);
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'Chưa thấy BTC chuyển khoản');
    await tester.tap(find.text('Xác nhận gửi'));
    await tester.pumpAndSettle();

    expect(repo.lastPaymentStatus, 'NOT_RECEIVED');
    expect(repo.lastPaymentComment, 'Chưa thấy BTC chuyển khoản');
  });

  testWidgets('ShiftPaymentScreen report partial-payment dialog closes cleanly after submit', (tester) async {
    final repo = _FakeProposalRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [proposalRepositoryProvider.overrideWithValue(repo)],
        child: MaterialApp(
          theme: AppTheme.light,
          home: ShiftPaymentScreen(
            assignment: _buildAssignment(
              eoConfirmed: true,
              paymentStatus: 'DECLARED_PENDING_WORKER',
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final partialBtn = find.text('Nhận 1 phần');
    await tester.ensureVisible(partialBtn);
    await tester.tap(partialBtn);
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).at(0), '100000');
    await tester.enterText(find.byType(TextField).at(1), 'Thiếu 50k tiền mặt');
    await tester.tap(find.text('Xác nhận gửi'));
    await tester.pumpAndSettle();

    expect(repo.lastPaymentStatus, 'RECEIVED_PARTIAL');
    expect(repo.lastPaymentComment, contains('Thiếu 50k tiền mặt'));
  });
}
