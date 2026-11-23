import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';

import 'package:cani/app/theme.dart';
import 'package:cani/features/auth/data/auth_repository.dart';
import 'package:cani/features/auth/models/user_dto.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/data/ekyc_repository.dart';
import 'package:cani/features/ekyc/models/ekyc_models.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_radar/data/job_radar_repository.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/job_radar/screens/job_radar_screen.dart';
import 'package:cani/features/skill_verification/data/skill_verification_repository.dart';
import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';

class _FakeGeolocatorPlatform extends GeolocatorPlatform {
  @override
  Future<bool> isLocationServiceEnabled() async => true;

  @override
  Future<LocationPermission> checkPermission() async => LocationPermission.whileInUse;

  @override
  Future<LocationPermission> requestPermission() async => LocationPermission.whileInUse;

  @override
  Future<Position> getCurrentPosition({LocationSettings? locationSettings}) async => Position(
        longitude: 106.7009,
        latitude: 10.7769,
        timestamp: DateTime(2026, 1, 1),
        accuracy: 10,
        altitude: 0,
        altitudeAccuracy: 0,
        heading: 0,
        headingAccuracy: 0,
        speed: 0,
        speedAccuracy: 0,
      );
}

class _FakeAuthRepository implements AuthRepository {
  @override
  Future<UserDto?> getSavedUser() async => const UserDto(
        id: 'user-1',
        email: 'worker@cani.vn',
        fullName: 'Trần Minh Khang',
        role: 'WORKER',
        status: 'ACTIVE',
      );

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

class _FakeEkycRepository implements EkycRepository {
  @override
  Future<EkycConsentPolicy> getConsentPolicy({String? token}) async => EkycConsentPolicy.fallback;

  @override
  Future<EkycVerificationItem?> getMyEkycStatus({String? token}) async => EkycVerificationItem(
        verificationId: 'ekyc-1',
        workerId: 'w-1',
        workerName: 'Trần Minh Khang',
        cccdNumberMasked: '********1234',
        fullName: 'Trần Minh Khang',
        routingDecision: 'AUTO_APPROVED',
        routingReason: 'OK',
        status: 'APPROVED',
        createdAt: DateTime(2026, 1, 1),
      );

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

class _FakeSkillVerificationRepository implements SkillVerificationRepository {
  @override
  Future<List<SkillModel>> getTaxonomy() async => const [];

  @override
  Future<List<WorkerSkillPointModel>> getSkillPoints() async => const [
        WorkerSkillPointModel(
          id: 'sp-1',
          jobType: 'BARTENDER',
          spVerify: 50,
          spExperience: 150,
          spRating: 100,
          totalSp: 300,
          tier: 'GOLD',
          totalShiftsCompleted: 8,
          avgRating: 4.9,
        ),
      ];

  @override
  Future<List<SkillVerificationResult>> getHistory() async => const [];

  @override
  Future<List<WorkerSkillItem>> getMySkills() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

class _FakeProposalRepository implements ProposalRepository {
  List<JobProposalItem> proposals = const [];
  bool throwOnFetch = false;
  bool acceptCalled = false;
  bool rejectCalled = false;

  @override
  Future<WorkerPreferencesModel> fetchPreferences({String? token}) async => const WorkerPreferencesModel(
        baseAddress: 'Quận 1, TP.HCM',
        baseLat: 10.7769,
        baseLng: 106.7009,
        maxDistanceKm: 15,
        isOnline: true,
      );

  @override
  Future<List<JobProposalItem>> fetchProposals({String? token}) async {
    if (throwOnFetch) throw const RepositoryException('Network error');
    return proposals;
  }

  @override
  Future<bool> acceptProposal(String proposalId, {String? token}) async {
    acceptCalled = true;
    proposals = proposals.where((p) => p.proposalId != proposalId).toList();
    return true;
  }

  @override
  Future<bool> rejectProposal(String proposalId, String reason, {String? token}) async {
    rejectCalled = true;
    proposals = proposals.where((p) => p.proposalId != proposalId).toList();
    return true;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

class _FakeJobRadarRepository implements JobRadarRepository {
  List<NearbyShiftItem> shifts = const [];
  bool throwOnScan = false;
  bool onlineStatus = true;

  @override
  Future<List<NearbyShiftItem>> getNearbyShifts({
    double lat = 10.7769,
    double lng = 106.7009,
    double radiusKm = 15.0,
  }) async {
    if (throwOnScan) throw Exception('Radar offline');
    return shifts;
  }

  @override
  Future<bool> setOnlineStatus({required bool isOnline, double? lat, double? lng}) async {
    onlineStatus = isOnline;
    return isOnline;
  }

  @override
  Future<void> applyForShift(String shiftId) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

JobProposalItem _makeProposal(
  String id, {
  String eventName = 'Gala Công Nghệ',
  String jobType = 'Lễ tân VIP',
  DateTime? expiresAt,
  String status = 'PENDING',
}) {
  final now = DateTime.now();
  return JobProposalItem(
    proposalId: id,
    shiftId: 'shift-prop-$id',
    eventId: 'evt-$id',
    eventName: eventName,
    venueAddress: '123 Lê Lợi, Q.1',
    jobType: jobType,
    description: 'Đón tiếp khách mời',
    startTime: now.add(const Duration(hours: 3)),
    endTime: now.add(const Duration(hours: 7)),
    expiresAt: expiresAt ?? now.add(const Duration(minutes: 25)),
    hourlyWage: 80000,
    allowance: 20000,
    totalEstimatedPay: 340000,
    matchScore: 95,
    status: status,
  );
}

NearbyShiftItem _makeShift(
  String id, {
  String eventName = 'Sự Kiện Âm Nhạc',
  String jobType = 'Điều phối viên',
  bool emergency = true,
  bool conflicting = false,
}) {
  final now = DateTime.now();
  return NearbyShiftItem(
    shiftId: id,
    eventId: 'evt-$id',
    eventName: eventName,
    jobType: jobType,
    locationAddress: 'Quận 7, TP.HCM',
    locationLat: 10.732,
    locationLng: 106.721,
    hourlyWage: 70000,
    allowance: 0,
    startTime: now.add(const Duration(hours: 4)),
    endTime: now.add(const Duration(hours: 8)),
    distanceKm: 2.3,
    requiredWorkers: 5,
    confirmedWorkers: 2,
    remainingWorkers: 3,
    emergency: emergency,
    isConflicting: conflicting,
  );
}

Future<ProviderContainer> _pumpRadarScreen(
  WidgetTester tester, {
  required _FakeProposalRepository proposalRepo,
  required _FakeJobRadarRepository radarRepo,
  Size viewport = const Size(412, 840),
  double textScale = 1.0,
  ThemeMode themeMode = ThemeMode.light,
}) async {
  tester.view.physicalSize = viewport;
  tester.view.devicePixelRatio = 1.0;

  final container = ProviderContainer(
    overrides: [
      authRepositoryProvider.overrideWith((ref) => _FakeAuthRepository()),
      ekycRepositoryProvider.overrideWith((ref) => _FakeEkycRepository()),
      skillVerificationRepositoryProvider.overrideWith((ref) => _FakeSkillVerificationRepository()),
      proposalRepositoryProvider.overrideWith((ref) => proposalRepo),
      jobRadarRepositoryProvider.overrideWith((ref) => radarRepo),
    ],
  );

  // Trigger auth & ekyc check cleanly
  await container.read(authStateProvider.notifier).checkAuthStatus();
  await container.read(ekycProvider.notifier).checkStatus();
  await container.read(skillVerificationProvider.notifier).loadAll();

  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: MaterialApp(
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        themeMode: themeMode,
        builder: (ctx, child) => MediaQuery(
          data: MediaQuery.of(ctx).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: const JobRadarScreen(),
      ),
    ),
  );

  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 40));
  }
  return container;
}

void main() {
  setUpAll(() {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform();
  });

  tearDown(() {
    TestWidgetsFlutterBinding.ensureInitialized().platformDispatcher.views.first.resetPhysicalSize();
    TestWidgetsFlutterBinding.ensureInitialized().platformDispatcher.views.first.resetDevicePixelRatio();
  });

  testWidgets('Empty state shows compact header, filter row, and single Đang tìm ca quanh bạn line', (tester) async {
    final proposalRepo = _FakeProposalRepository();
    final radarRepo = _FakeJobRadarRepository();

    final container = await _pumpRadarScreen(tester, proposalRepo: proposalRepo, radarRepo: radarRepo);
    addTearDown(container.dispose);

    // Compact header shows Worker name (without long greeting) and Nhận việc toggle
    expect(find.text('Trần Minh Khang'), findsOneWidget);
    expect(find.textContaining('Chào buổi'), findsNothing);
    expect(find.text('Nhận việc'), findsOneWidget);
    expect(find.textContaining('Hạng'), findsNothing);
    expect(find.textContaining('SP'), findsNothing);

    // Filter toolbar chips present cleanly
    expect(find.text('Mã mời'), findsOneWidget);
    expect(find.text('DS (0)'), findsOneWidget);

    // Empty state single line on map
    expect(find.text('Đang tìm ca quanh bạn'), findsOneWidget);
    expect(find.text('Lời mời làm việc'), findsNothing);
    expect(find.text('Ca gần bạn'), findsNothing);
  });

  testWidgets('Nearby shifts only -> shows single Ca gần bạn card with Xem chi tiết and Ứng tuyển', (tester) async {
    final proposalRepo = _FakeProposalRepository();
    final radarRepo = _FakeJobRadarRepository()..shifts = [_makeShift('s1', emergency: true)];

    final container = await _pumpRadarScreen(tester, proposalRepo: proposalRepo, radarRepo: radarRepo);
    addTearDown(container.dispose);

    expect(find.text('Đang tìm ca quanh bạn'), findsNothing);
    expect(find.text('Ca gần bạn'), findsOneWidget);
    expect(find.text('Sự Kiện Âm Nhạc'), findsOneWidget);
    expect(find.text('Xem chi tiết'), findsOneWidget);
    expect(find.text('Ứng tuyển'), findsOneWidget);
    expect(find.text('DS (1)'), findsOneWidget);
  });

  testWidgets('Both invites & nearby shifts -> prioritizes single Lời mời làm việc card with Xem tất cả (N), marker tap switches card', (tester) async {
    final proposalRepo = _FakeProposalRepository()
      ..proposals = [
        _makeProposal('p1', eventName: 'Gala Công Nghệ'),
        _makeProposal('p2', eventName: 'Hội Nghị AI'),
      ];
    final radarRepo = _FakeJobRadarRepository()
      ..shifts = [_makeShift('s1', eventName: 'Show Ca Nhạc', emergency: false)];

    final container = await _pumpRadarScreen(tester, proposalRepo: proposalRepo, radarRepo: radarRepo);
    addTearDown(container.dispose);

    // Priority 1: Single invite card with Xem tất cả (2)
    expect(find.text('Lời mời làm việc'), findsOneWidget);
    expect(find.text('Gala Công Nghệ'), findsOneWidget);
    expect(find.text('Xem tất cả (2)'), findsOneWidget);
    expect(find.text('Đang tìm ca quanh bạn'), findsNothing);
    expect(find.text('Ca gần bạn'), findsNothing);

    // Simulate Worker tapping shift marker -> bottom card updates to that shift while keeping invites accessible
    container.read(jobRadarProvider.notifier).selectShift(radarRepo.shifts.first);
    await tester.pump();

    expect(find.text('Ca gần bạn'), findsOneWidget);
    expect(find.text('Show Ca Nhạc'), findsWidgets); // route banner + bottom card
    expect(find.text('Chờ lời mời'), findsOneWidget);
    expect(find.text('Lời mời (2)'), findsOneWidget);

    // If selected shift disappears from latest scan -> recomputes back to active invite!
    radarRepo.shifts = const [];
    await container.read(jobRadarProvider.notifier).scanNearbyShifts();
    await tester.pump();

    expect(find.text('Lời mời làm việc'), findsOneWidget);
    expect(find.text('Ca gần bạn'), findsNothing);

    // Rejecting/removing proposals recalculates down to empty line when both empty
    await tester.tap(find.text('Từ chối'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(proposalRepo.rejectCalled, isTrue);
    expect(find.text('Hội Nghị AI'), findsOneWidget);
  });

  testWidgets('API error shows failure state with Thử lại button and recovers cleanly', (tester) async {
    final proposalRepo = _FakeProposalRepository()..throwOnFetch = true;
    final radarRepo = _FakeJobRadarRepository()..throwOnScan = true;

    final container = await _pumpRadarScreen(tester, proposalRepo: proposalRepo, radarRepo: radarRepo);
    addTearDown(container.dispose);

    expect(find.text('Thử lại'), findsOneWidget);
    expect(find.text('Đang tìm ca quanh bạn'), findsNothing);

    // Retry when API recovers
    proposalRepo.throwOnFetch = false;
    radarRepo.throwOnScan = false;
    proposalRepo.proposals = [_makeProposal('p-recovered', eventName: 'Hội Chợ Xuân')];
    await tester.tap(find.text('Thử lại'));
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 40));
    }

    expect(find.text('Lời mời làm việc'), findsOneWidget);
    expect(find.text('Hội Chợ Xuân'), findsOneWidget);
  });

  testWidgets('Expired proposal filtered out cleanly and small screen + large text + dark mode renders without overflow', (tester) async {
    final proposalRepo = _FakeProposalRepository()
      ..proposals = [
        _makeProposal(
          'p-expired',
          eventName: 'Đã Hết Hạn',
          expiresAt: DateTime.now().subtract(const Duration(minutes: 2)),
        ),
        _makeProposal('p-live', eventName: 'Sự Kiện Còn Hạn'),
      ];
    final radarRepo = _FakeJobRadarRepository()..shifts = [_makeShift('s1')];

    final container = await _pumpRadarScreen(
      tester,
      proposalRepo: proposalRepo,
      radarRepo: radarRepo,
      viewport: const Size(360, 640),
      textScale: 1.4,
      themeMode: ThemeMode.dark,
    );
    addTearDown(container.dispose);

    expect(find.text('Đã Hết Hạn'), findsNothing);
    expect(find.text('Sự Kiện Còn Hạn'), findsOneWidget);
    // Zoom and GPS recenter controls remain visible and tappable above bottom card
    expect(find.byIcon(Icons.my_location_rounded), findsWidgets);
    expect(find.byIcon(Icons.add_rounded), findsOneWidget);
    expect(find.byIcon(Icons.remove_rounded), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Toggle Nhận việc off updates switch, new invite updates live via proposalsListProvider, and list view keeps invites accessible', (tester) async {
    final proposalRepo = _FakeProposalRepository();
    final radarRepo = _FakeJobRadarRepository()..shifts = [_makeShift('s1', eventName: 'Ca Mở Radar')];

    final container = await _pumpRadarScreen(tester, proposalRepo: proposalRepo, radarRepo: radarRepo);
    addTearDown(container.dispose);

    // Initially online with nearby shift card
    expect(find.text('Đang bật'), findsOneWidget);
    expect(find.text('Ca gần bạn'), findsOneWidget);

    // Tap Nhận việc switch -> Tạm tắt
    await tester.tap(find.text('Nhận việc'));
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 40));
    }
    expect(radarRepo.onlineStatus, isFalse);
    expect(find.text('Tạm tắt'), findsOneWidget);

    // Simulate new invite arriving while screen is still open
    proposalRepo.proposals = [_makeProposal('p-live-push', eventName: 'Sự Kiện Mới Tới')];
    container.invalidate(proposalsListProvider);
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 40));
    }
    expect(find.text('Lời mời làm việc'), findsOneWidget);
    expect(find.text('Sự Kiện Mới Tới'), findsOneWidget);

    // Switch to List View -> both active invite and nearby shift are visible
    await tester.tap(find.text('DS (1)'));
    await tester.pump();
    expect(find.text('Lời mời làm việc (1)'), findsOneWidget);
    expect(find.text('Ca quanh bạn (1)'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
