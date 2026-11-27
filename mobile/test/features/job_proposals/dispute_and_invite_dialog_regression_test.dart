import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cani/app/home_screen.dart';
import 'package:cani/app/theme.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/dispute_models.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/screens/invite_qr_scanner_screen.dart';
import 'package:cani/features/job_proposals/screens/my_assignments_screen.dart';
import 'package:cani/features/job_proposals/screens/my_disputes_screen.dart';
import 'package:cani/features/job_proposals/screens/shift_payment_screen.dart';
import 'package:cani/features/job_proposals/widgets/shift_assignment_card.dart';
import 'package:cani/features/job_radar/widgets/job_radar_proposals_sheet.dart';
import 'package:cani/shared/widgets/cani_snackbar.dart';

class _FakeProposalRepository extends ProposalRepository {
  _FakeProposalRepository() : super(Dio());

  String? lastJoinedInviteCode;
  CreateDisputePayload? lastCreatedDispute;
  String? lastPaymentStatus;
  String? lastPaymentComment;
  bool shouldFailDisputes = false;
  bool shouldFailAcceptProposal = false;
  List<WorkerDisputeItem> disputes = [];
  Map<String, DisputeChatData> chatsByDisputeId = {};

  @override
  Future<List<WorkerDisputeItem>> fetchMyDisputes() async {
    if (shouldFailDisputes) {
      throw const RepositoryException('Máy chủ đang bận, vui lòng thử lại.');
    }
    return disputes;
  }

  @override
  Future<DisputeChatData> fetchDisputeChat(String disputeId) async {
    return chatsByDisputeId[disputeId] ??
        DisputeChatData(
          disputeId: disputeId,
          disputeTitle: 'Hồ sơ $disputeId',
          disputeStatus: 'OPEN',
          messages: const [],
        );
  }

  @override
  Future<void> markDisputeChatRead(String disputeId) async {}

  @override
  Future<bool> acceptProposal(String proposalId, {String? token}) async {
    if (shouldFailAcceptProposal) {
      throw const RepositoryException('Ca làm việc đã đủ nhân sự.');
    }
    return true;
  }

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
  String assignmentId = 'a1',
  String status = 'CHECKED_IN',
  bool eoConfirmed = false,
  String paymentStatus = 'NOT_DECLARED',
}) {
  return ShiftAssignmentItem(
    assignmentId: assignmentId,
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
  tearDown(() {
    TestWidgetsFlutterBinding.ensureInitialized().platformDispatcher.views.first.resetPhysicalSize();
    TestWidgetsFlutterBinding.ensureInitialized().platformDispatcher.views.first.resetDevicePixelRatio();
  });

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

    await tester.tap(find.text('Tạo khiếu nại'));
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

    await tester.tap(find.text('Tạo khiếu nại'));
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

  testWidgets('Nghiệm thu 1: Trên màn hình 360px sáng/tối, chữ không tràn và nút chính vẫn bấm được khi thông báo xuất hiện', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1.0;

    final repo = _FakeProposalRepository();
    for (final mode in [ThemeMode.light, ThemeMode.dark]) {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [proposalRepositoryProvider.overrideWithValue(repo)],
          child: MaterialApp(
            theme: AppTheme.light,
            darkTheme: AppTheme.dark,
            themeMode: mode,
            home: const MyDisputesScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final ctx = tester.element(find.byType(MyDisputesScreen));
      CaniSnackBar.show(
        ctx,
        message: 'Chưa gửi được ảnh. Vui lòng thử lại.',
        tone: CaniNoticeTone.error,
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('Chưa gửi được ảnh. Vui lòng thử lại.'), findsOneWidget);
      expect(tester.takeException(), isNull);

      // Nút chính Tạo khiếu nại ở đáy màn hình vẫn bấm được ngay khi SnackBar đang hiển thị
      await tester.tap(find.text('Tạo khiếu nại'));
      await tester.pumpAndSettle();
      expect(find.text('GỬI KHIẾU NẠI NGAY'), findsOneWidget);

      // Đóng bottom sheet
      Navigator.of(ctx).pop();
      await tester.pumpAndSettle();
    }
  });

  testWidgets('Nghiệm thu 2: Khi nhận ca thành công, nút Xem ca mở đúng mục Ca làm; khi lỗi nêu rõ thao tác thất bại', (tester) async {
    final repo = _FakeProposalRepository();
    final container = ProviderContainer(
      overrides: [proposalRepositoryProvider.overrideWithValue(repo)],
    );
    addTearDown(container.dispose);

    final proposal = JobProposalItem(
      proposalId: 'p-100',
      shiftId: 's-100',
      eventId: 'e-100',
      eventName: 'Hội Nghị Công Nghệ',
      venueAddress: 'Quận 1, TP.HCM',
      jobType: 'Điều phối sự kiện',
      description: 'Hỗ trợ khách mời',
      startTime: DateTime.now().add(const Duration(hours: 4)),
      endTime: DateTime.now().add(const Duration(hours: 8)),
      hourlyWage: 60000,
      allowance: 0,
      totalEstimatedPay: 240000,
      matchScore: 95,
      status: 'PENDING',
    );

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: AppTheme.light,
          home: Consumer(
            builder: (context, ref, _) => Scaffold(
              body: ElevatedButton(
                onPressed: () => acceptProposalOnRadar(context, ref, proposal),
                child: const Text('Nhận ca thử'),
              ),
            ),
          ),
        ),
      ),
    );

    // 1. Thành công -> hiện thông báo + nút Xem ca mở đúng tab Ca làm (tab 1, subtab 0)
    container.read(homeTabIndexProvider.notifier).state = 0;
    container.read(assignmentsSubTabProvider.notifier).state = 1;

    await tester.tap(find.text('Nhận ca thử'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.textContaining('Đã nhận ca "Điều phối sự kiện"'), findsOneWidget);
    expect(find.text('Xem ca'), findsOneWidget);

    await tester.tap(find.text('Xem ca'));
    await tester.pumpAndSettle();

    expect(container.read(homeTabIndexProvider), 1);
    expect(container.read(assignmentsSubTabProvider), 0);

    // 2. Khi lỗi -> thông báo nêu rõ thao tác nhận ca thất bại
    repo.shouldFailAcceptProposal = true;
    await tester.tap(find.text('Nhận ca thử'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.textContaining('Chưa nhận được ca "Điều phối sự kiện"'), findsOneWidget);
    expect(find.textContaining('Ca làm việc đã đủ nhân sự'), findsOneWidget);
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('Nghiệm thu 3: Bộ lọc hiển thị đúng nhóm (kể cả hồ sơ kháng nghị), đưa Đang xử lý lên đầu, mở chi tiết, minh chứng và trao đổi', (tester) async {
    tester.view.physicalSize = const Size(412, 1000);
    tester.view.devicePixelRatio = 1.0;

    final repo = _FakeProposalRepository()
      ..disputes = [
        WorkerDisputeItem(
          id: 'd-resolved',
          assignmentId: 'a1',
          eventTitle: 'Tech Expo',
          shiftTitle: 'Lễ tân',
          type: 'PAYMENT',
          status: 'RESOLVED',
          outcome: 'EO_FAULT',
          title: 'Hồ sơ đã phân xử xong',
          description: 'Mô tả chi tiết hồ sơ đã có kết quả phân xử.',
          decisionRationale: 'BTC thanh toán bổ sung 100.000đ cho nhân sự.',
          createdAt: DateTime(2026, 11, 25, 10, 0),
        ),
        WorkerDisputeItem(
          id: 'd-open',
          assignmentId: 'a1',
          eventTitle: 'Music Fest',
          shiftTitle: 'An ninh',
          type: 'ATTENDANCE',
          status: 'OPEN',
          title: 'Hồ sơ mới gửi đang chờ',
          description: 'Mô tả chi tiết lỗi máy quét QR tại cổng B.',
          evidences: const [
            DisputeEvidenceItem(
              id: 'ev-1',
              fileType: 'TEXT',
              fileUrl: 'Log check-in cổng B lúc 07:55',
            ),
          ],
          slaDeadline: DateTime(2026, 11, 29),
          createdAt: DateTime(2026, 11, 24, 9, 0),
        ),
        WorkerDisputeItem(
          id: 'd-appeal',
          assignmentId: 'a2',
          eventTitle: 'AI Forum',
          shiftTitle: 'Hậu cần',
          type: 'WORK_QUALITY',
          status: 'ESCALATED',
          title: 'Hồ sơ đang kháng nghị Admin',
          description: 'Kháng nghị quyết định sơ thẩm lên Admin.',
          escalationReason: 'Bổ sung video camera hiện trường.',
          createdAt: DateTime(2026, 11, 26, 11, 0),
        ),
      ]
      ..chatsByDisputeId = {
        'd-resolved': DisputeChatData(
          disputeId: 'd-resolved',
          disputeTitle: 'Hồ sơ đã phân xử xong',
          disputeStatus: 'RESOLVED',
          messages: [
            DisputeChatMessageItem(
              id: 'm1',
              conversationId: 'c1',
              senderId: 'cc1',
              senderName: 'Trọng tài CSKH',
              senderRole: 'CUSTOMER_CARE',
              messageText: 'Đã đối soát chứng từ chuyển khoản cho bạn.',
              createdAt: DateTime(2026, 11, 25, 12, 0),
            ),
          ],
        ),
      };

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

    // 1. Trong mục Tất cả: hồ sơ đang xử lý (bao gồm cả kháng nghị ESCALATED) nằm trên hồ sơ đã có kết quả
    final appealPos = tester.getTopLeft(find.text('Hồ sơ đang kháng nghị Admin')).dy;
    final openPos = tester.getTopLeft(find.text('Hồ sơ mới gửi đang chờ')).dy;
    final resolvedPos = tester.getTopLeft(find.text('Hồ sơ đã phân xử xong')).dy;
    expect(appealPos, lessThan(resolvedPos));
    expect(openPos, lessThan(resolvedPos));

    // Nhãn nút trao đổi phân biệt rõ hồ sơ còn trao đổi được vs đã đóng
    expect(find.text('Trao đổi với CSKH'), findsNWidgets(2));
    expect(find.text('Xem trao đổi'), findsOneWidget);

    // 2. Chuyển sang bộ lọc "Đang xử lý" -> chỉ hiển thị OPEN + ESCALATED
    await tester.tap(find.text('Đang xử lý'));
    await tester.pumpAndSettle();
    expect(find.text('Hồ sơ đang kháng nghị Admin'), findsOneWidget);
    expect(find.text('Hồ sơ mới gửi đang chờ'), findsOneWidget);
    expect(find.text('Hồ sơ đã phân xử xong'), findsNothing);

    // Mở "Xem chi tiết" của hồ sơ có minh chứng
    await tester.tap(find.text('Xem chi tiết').last);
    await tester.pumpAndSettle();
    expect(find.text('Chi tiết hồ sơ khiếu nại'), findsOneWidget);
    expect(find.text('Mô tả chi tiết lỗi máy quét QR tại cổng B.'), findsOneWidget);
    expect(find.textContaining('Log check-in cổng B lúc 07:55'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.close_rounded));
    await tester.pumpAndSettle();

    // 3. Chuyển sang bộ lọc "Đã có kết quả" -> chỉ hiển thị RESOLVED
    await tester.tap(find.text('Đã có kết quả'));
    await tester.pumpAndSettle();
    expect(find.text('Hồ sơ đã phân xử xong'), findsOneWidget);
    expect(find.text('Hồ sơ mới gửi đang chờ'), findsNothing);
    expect(find.text('Hồ sơ đang kháng nghị Admin'), findsNothing);

    // Xem chi tiết quyết định của hồ sơ đã có kết quả
    await tester.tap(find.text('Xem chi tiết'));
    await tester.pumpAndSettle();
    expect(find.text('BTC thanh toán bổ sung 100.000đ cho nhân sự.'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.close_rounded));
    await tester.pumpAndSettle();

    // Mở "Xem trao đổi" của hồ sơ đã đóng -> xem được lịch sử chat với CSKH
    await tester.tap(find.text('Xem trao đổi'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.text('Đã đối soát chứng từ chuyển khoản cho bạn.'), findsOneWidget);
    expect(find.text('Vụ việc đã đóng, kênh trao đổi đã được khép lại.'), findsOneWidget);
  });

  testWidgets('Nghiệm thu 4: Khi API lỗi hiển thị lỗi và nút Thử lại; khi mở từ chi tiết một ca chỉ hiện hồ sơ của ca đó', (tester) async {
    final repo = _FakeProposalRepository()
      ..shouldFailDisputes = true
      ..disputes = [
        WorkerDisputeItem(
          id: 'd-shift-a1',
          assignmentId: 'a1',
          eventTitle: 'Tech Summit 2026',
          shiftTitle: 'Lễ tân',
          type: 'PAYMENT',
          status: 'OPEN',
          title: 'Khiếu nại của ca A1',
          description: 'Nội dung ca A1',
        ),
        WorkerDisputeItem(
          id: 'd-shift-a2',
          assignmentId: 'a2',
          eventTitle: 'Sự kiện khác',
          shiftTitle: 'Bảo vệ',
          type: 'OTHER',
          status: 'OPEN',
          title: 'Khiếu nại của ca A2',
          description: 'Nội dung ca A2',
        ),
      ];

    await tester.pumpWidget(
      ProviderScope(
        overrides: [proposalRepositoryProvider.overrideWithValue(repo)],
        child: MaterialApp(
          theme: AppTheme.light,
          home: MyDisputesScreen(assignment: _buildAssignment(assignmentId: 'a1')),
        ),
      ),
    );
    await tester.pumpAndSettle();

    // 1. Khi API lỗi -> hiện trạng thái lỗi và nút Thử lại
    expect(find.text('Không thể tải danh sách khiếu nại'), findsOneWidget);
    expect(find.text('Thử lại'), findsOneWidget);

    // 2. Bấm Thử lại sau khi API phục hồi -> chỉ hiển thị hồ sơ của ca a1
    repo.shouldFailDisputes = false;
    await tester.tap(find.text('Thử lại'));
    await tester.pumpAndSettle();

    expect(find.text('Khiếu nại của ca A1'), findsOneWidget);
    expect(find.text('Khiếu nại của ca A2'), findsNothing);
  });

  testWidgets('MyAssignmentsScreen invite code button opens dialog cleanly & Quét QR opens QR scanner', (tester) async {
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

    await tester.tap(find.byTooltip('Nhập mã mời ca làm'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'CANI-8XK2PW');
    await tester.tap(find.text('Tham Gia'));
    await tester.pumpAndSettle();

    expect(repo.lastJoinedInviteCode, 'CANI-8XK2PW');
    expect(find.text('Tham gia ca làm việc thành công!'), findsOneWidget);

    await tester.tap(find.text('Quét QR'));
    await tester.pumpAndSettle();

    expect(find.text('Quét QR Tham Gia Ca'), findsOneWidget);
    expect(find.text('Nhập mã thủ công'), findsOneWidget);
  });

  test('parseInviteCodeFromQr extracts code from CANI_INVITE, URL, and raw code while rejecting checkin/checkout', () {
    expect(parseInviteCodeFromQr('CANI_INVITE:CANI-J6VEQ5'), 'CANI-J6VEQ5');
    expect(parseInviteCodeFromQr('cani-8xk2pw'), 'CANI-8XK2PW');
    expect(parseInviteCodeFromQr('http://localhost:5173/join?code=CANI-J6VEQ5'), 'CANI-J6VEQ5');
    expect(parseInviteCodeFromQr('CANI_CHECKIN:123'), isNull);
    expect(parseInviteCodeFromQr('CANI_CHECKOUT:123'), isNull);
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

  testWidgets('ShiftAssignmentCard places Chi tiết & Checklist công việc in primary top slot above Quét QR Check-in', (tester) async {
    var checklistOpened = false;
    var qrOpened = false;
    final upcomingAssignment = ShiftAssignmentItem(
      assignmentId: 'a-upcoming',
      shiftId: 's-upcoming',
      eventId: 'e-upcoming',
      eventName: 'Tech Summit 2026',
      venueAddress: '123 Lê Lợi, Q.1',
      jobType: 'Lễ tân',
      startTime: DateTime.now().add(const Duration(hours: 2)),
      endTime: DateTime.now().add(const Duration(hours: 6)),
      hourlyWage: 50000,
      totalPay: 200000,
      status: 'CONFIRMED',
      eoConfirmed: false,
      paymentStatus: 'NOT_DECLARED',
      declaredAmount: 0,
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light,
        home: Scaffold(
          body: SingleChildScrollView(
            child: ShiftAssignmentCard(
              assignment: upcomingAssignment,
              onTap: () {},
              onNavigate: () {},
              onQrScan: () => qrOpened = true,
              onConfirmPayment: () {},
              onOpenChecklist: () => checklistOpened = true,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final checklistFinder = find.text('Chi tiết & Checklist công việc');
    final qrFinder = find.text('Quét QR Check-in');
    expect(checklistFinder, findsOneWidget);
    expect(qrFinder, findsOneWidget);
    expect(tester.getTopLeft(checklistFinder).dy, lessThan(tester.getTopLeft(qrFinder).dy));

    await tester.tap(checklistFinder);
    await tester.pump();
    expect(checklistOpened, isTrue);

    await tester.tap(qrFinder);
    await tester.pump();
    expect(qrOpened, isTrue);
  });
}
