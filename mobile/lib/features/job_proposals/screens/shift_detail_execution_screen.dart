import 'dart:async';
import 'dart:typed_data';
import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/shift_chat_sheet.dart';
import '../widgets/shift_checklist_section.dart';
import '../widgets/shift_workflow_stepper.dart';
import 'attendance_qr_scanner_screen.dart';
import 'my_disputes_screen.dart';
import 'rate_eo_screen.dart';
import 'shift_payment_screen.dart';
import '../widgets/eo_public_profile_sheet.dart';
import '../../ekyc/screens/card_scanner_screen.dart';

class ShiftDetailExecutionScreen extends ConsumerStatefulWidget {
  final ShiftAssignmentItem assignment;

  const ShiftDetailExecutionScreen({super.key, required this.assignment});

  @override
  ConsumerState<ShiftDetailExecutionScreen> createState() => _ShiftDetailExecutionScreenState();
}

class _ShiftDetailExecutionScreenState extends ConsumerState<ShiftDetailExecutionScreen> {
  late ShiftAssignmentItem _assignment;
  List<ShiftChecklistItem> _checklistItems = [];
  bool _isLoadingChecklist = true;
  bool _isSavingChecklist = false;
  String? _checklistError;
  int? _uploadingTaskIndex;
  final ScrollController _scrollController = ScrollController();
  final GlobalKey _checklistSectionKey = GlobalKey();
  final ValueNotifier<List<ShiftChecklistItem>> _checklistNotifier = ValueNotifier([]);
  final ValueNotifier<int?> _uploadingIndexNotifier = ValueNotifier(null);

  void _scrollToSection(GlobalKey key) {
    final targetContext = key.currentContext;
    if (targetContext != null) {
      Scrollable.ensureVisible(
        targetContext,
        duration: const Duration(milliseconds: 450),
        curve: Curves.easeInOut,
        alignment: 0.1,
      );
    }
  }

  // GPS & Geofencing
  Position? _currentPosition;
  double? _distanceMeters;
  bool _isLoadingGps = true;
  String? _gpsError;

  // Overall proof URL synced from checklist task photos if needed at checkout
  String? _uploadedProofUrl;

  bool get _isCompleted =>
      _assignment.checkoutTime != null ||
      _assignment.status == 'COMPLETED';

  bool get _isCheckedIn =>
      !_isCompleted &&
      (_assignment.status == 'CHECKED_IN' || _assignment.checkinTime != null);

  bool get _isExpired =>
      !_isCheckedIn &&
      !_isCompleted &&
      (_assignment.status == 'NO_SHOW' ||
          _assignment.status == 'CANCELLED' ||
          DateTime.now().isAfter(_assignment.endTime));

  DateTime get _earliestCheckinTime => _assignment.arrivalTime ?? _assignment.startTime;

  bool get _isBeforeCheckinWindow =>
      !_isCheckedIn && !_isCompleted && !_isExpired && DateTime.now().isBefore(_earliestCheckinTime);

  bool get _isPaymentConfirmed => isAssignmentPaymentConfirmed(_assignment);

  bool get _isWorkReadyForEoConfirm {
    return _checklistItems.every((t) =>
        t.isCompleted &&
        (!t.isRequiredEvidence || (t.evidenceImageUrl != null && t.evidenceImageUrl!.isNotEmpty)));
  }

  // Countdown timer
  Timer? _countdownTimer;
  String _countdownText = '00:00:00';

  @override
  void initState() {
    super.initState();
    _assignment = widget.assignment;
    _uploadedProofUrl = _assignment.proofImageUrl;
    _loadChecklist();
    _fetchGpsLocation();
    _initCountdown();
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    _scrollController.dispose();
    _checklistNotifier.dispose();
    _uploadingIndexNotifier.dispose();
    super.dispose();
  }

  void _initCountdown() {
    _updateCountdown();
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (_) => _updateCountdown());
  }

  void _updateCountdown() {
    final now = DateTime.now();
    if (_isExpired) {
      if (mounted) {
        setState(() {
          _countdownText = _assignment.status == 'CANCELLED'
              ? 'Ca làm việc đã bị hủy'
              : (_assignment.status == 'NO_SHOW'
                  ? 'Đã ghi nhận vắng mặt'
                  : 'Ca làm việc đã quá hạn điểm danh');
        });
      }
      return;
    }

    final isCheckedIn = _assignment.status == 'CHECKED_IN' ||
        (_assignment.checkinTime != null && _assignment.checkoutTime == null);

    final target = isCheckedIn ? _assignment.endTime : _assignment.startTime;
    final diff = target.difference(now);

    if (diff.isNegative) {
      if (mounted) {
        String text;
        if (isCheckedIn) {
          text = 'Đã quá giờ kết thúc';
        } else {
          final remDiff = _assignment.endTime.difference(now);
          final remHours = remDiff.inHours;
          final remMins = remDiff.inMinutes % 60;
          text = remHours > 0
              ? 'Đang trong giờ ca (còn ${remHours}g ${remMins}p)'
              : 'Đang trong giờ ca (còn $remMins phút)';
        }
        setState(() {
          _countdownText = text;
        });
      }
      return;
    }

    final hours = diff.inHours;
    final minutes = diff.inMinutes % 60;
    final seconds = diff.inSeconds % 60;

    String text;
    if (hours > 0) {
      text = 'còn $hours giờ $minutes phút để ${isCheckedIn ? "kết thúc ca" : "vào ca"}';
    } else if (minutes > 0) {
      text = 'còn $minutes phút $seconds giây để ${isCheckedIn ? "kết thúc ca" : "vào ca"}';
    } else {
      text = 'còn $seconds giây để ${isCheckedIn ? "kết thúc ca" : "vào ca"}';
    }

    if (mounted) {
      setState(() => _countdownText = text);
    }
  }

  Future<void> _fetchGpsLocation() async {
    setState(() => _isLoadingGps = true);
    final vLat = _assignment.venueLat;
    final vLng = _assignment.venueLng;

    if (vLat == null || vLng == null) {
      if (mounted) {
        setState(() {
          _isLoadingGps = false;
          _distanceMeters = null;
        });
      }
      return;
    }

    try {
      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 6),
        ),
      );
      final dist = Geolocator.distanceBetween(
        pos.latitude,
        pos.longitude,
        vLat,
        vLng,
      );
      if (mounted) {
        setState(() {
          _currentPosition = pos;
          _distanceMeters = dist;
          _isLoadingGps = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _gpsError = 'Không lấy được GPS ($e)';
          _isLoadingGps = false;
        });
      }
    }
  }

  Future<void> _loadChecklist() async {
    if (mounted) {
      setState(() {
        _isLoadingChecklist = true;
        _checklistError = null;
      });
    }
    try {
      final items = await ref
          .read(proposalRepositoryProvider)
          .fetchChecklist(_assignment.assignmentId);
      if (mounted) {
        setState(() {
          _checklistItems = items;
          _isLoadingChecklist = false;
        });
        _checklistNotifier.value = List<ShiftChecklistItem>.from(items);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoadingChecklist = false;
          _checklistError = e.toString().replaceFirst('Exception: ', '');
        });
      }
    }
  }

  Future<void> _toggleChecklistItem(int index) async {
    final cani = Theme.of(context).cani;
    if (!_isCheckedIn) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _isExpired
                ? 'Ca làm việc đã quá hạn — danh sách đầu việc chỉ ở chế độ xem.'
                : 'Vào ca (quét QR check-in) để bắt đầu tích chọn đầu việc!',
          ),
          backgroundColor: cani.warning,
        ),
      );
      return;
    }
    final current = _checklistItems[index];

    // Nếu đang hoàn thành mà muốn bỏ hoàn thành
    if (current.isCompleted) {
      final updated = current.copyWith(
        isCompleted: false,
        completedAt: null,
      );
      setState(() {
        _checklistItems[index] = updated;
      });
      _checklistNotifier.value = List<ShiftChecklistItem>.from(_checklistItems);
      _saveChecklistAsync();
      return;
    }

    // Nếu chưa hoàn thành -> Kiểm tra xem BTC có yêu cầu chụp ảnh nghiệm thu không
    if (current.isRequiredEvidence && (current.evidenceImageUrl == null || current.evidenceImageUrl!.isEmpty)) {
      final takePhoto = await showDialog<bool>(
        context: context,
        builder: (ctx) {
          final ctxCani = Theme.of(ctx).cani;
          return AlertDialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            title: Row(
              children: [
                Icon(Icons.camera_alt, color: ctxCani.brand, size: 22),
                const SizedBox(width: 8),
                const Text('Yêu cầu ảnh nghiệm thu', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
              ],
            ),
            content: Text(
              'Đầu việc "${current.title}" yêu cầu chụp ảnh bằng chứng nghiệm thu tại hiện trường.\n\nBạn cần chụp ảnh để hoàn tất đầu việc này.',
              style: const TextStyle(fontSize: 13.5, height: 1.4),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('Để sau'),
              ),
              FilledButton.icon(
                onPressed: () => Navigator.pop(ctx, true),
                icon: const Icon(Icons.camera_alt, size: 16),
                label: const Text('Chụp ảnh ngay'),
                style: FilledButton.styleFrom(backgroundColor: ctxCani.brand),
              ),
            ],
          );
        },
      );

      if (takePhoto == true) {
        await _captureEvidenceForTask(index);
      }
      return;
    }

    // Đánh dấu hoàn thành đầu việc
    final updated = current.copyWith(
      isCompleted: true,
      completedAt: DateTime.now(),
    );

    setState(() {
      _checklistItems[index] = updated;
    });
    _checklistNotifier.value = List<ShiftChecklistItem>.from(_checklistItems);
    _saveChecklistAsync();
  }

  Future<void> _saveChecklistAsync() async {
    if (!mounted) return;
    try {
      setState(() => _isSavingChecklist = true);
      await ref
          .read(proposalRepositoryProvider)
          .updateChecklist(_assignment.assignmentId, _checklistItems);
    } catch (_) {
    } finally {
      if (mounted) {
        setState(() => _isSavingChecklist = false);
      }
    }
  }

  /// Chỉ cho phép chụp ảnh trực tiếp từ Camera trong app, tuyệt đối không chọn từ thư viện ảnh.
  Future<({Uint8List? bytes, String? fileName})?> _captureImageFromCamera() async {
    if (!mounted) return null;
    final scanResult = await Navigator.of(context).push<CardScanResult>(
      MaterialPageRoute(
        builder: (_) => const CardScannerScreen(scanType: ScanType.workProof),
      ),
    );
    if (scanResult == null) return null;
    return (bytes: scanResult.bytes, fileName: scanResult.fileName);
  }

  Future<void> _captureEvidenceForTask(int index) async {
    final cani = Theme.of(context).cani;
    if (!_isCheckedIn) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _isExpired
                ? 'Ca làm việc đã quá hạn — không thể chụp ảnh nghiệm thu.'
                : 'Vào ca (quét QR check-in) để chụp ảnh nghiệm thu đầu việc!',
          ),
          backgroundColor: cani.warning,
        ),
      );
      return;
    }
    final item = _checklistItems[index];
    try {
      final captured = await _captureImageFromCamera();

      if (captured != null && captured.bytes != null) {
        setState(() => _uploadingTaskIndex = index);
        _uploadingIndexNotifier.value = index;

        // Upload ảnh chụp camera trực tiếp lên backend
        final repo = ref.read(proposalRepositoryProvider);
        final uploadedUrl = await repo.uploadProofBytes(
          captured.bytes!,
          captured.fileName ?? 'task_proof.jpg',
        );

        if (!mounted) return;

        final now = DateTime.now();
        final updated = item.copyWith(
          isCompleted: true,
          completedAt: now,
          evidenceImageUrl: uploadedUrl,
          evidenceSubmittedAt: now,
        );

        setState(() {
          _checklistItems[index] = updated;
          _uploadingTaskIndex = null;
          _uploadedProofUrl ??= uploadedUrl;
        });
        _checklistNotifier.value = List<ShiftChecklistItem>.from(_checklistItems);
        _uploadingIndexNotifier.value = null;

        // Lưu tiến độ lên backend
        await _saveChecklistAsync();
        try {
          await repo.submitAssignmentProof(_assignment.assignmentId, _uploadedProofUrl);
        } catch (_) {}

        if (!mounted) return;

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Đã nộp ảnh nghiệm thu task #${index + 1}: ${item.title}'),
            backgroundColor: const Color(0xFF16A34A),
            duration: const Duration(seconds: 2),
          ),
        );

        // Tự động tìm và cuộn sang task kế tiếp chưa hoàn thành
        int nextIndex = -1;
        for (int i = index + 1; i < _checklistItems.length; i++) {
          if (!_checklistItems[i].isCompleted) {
            nextIndex = i;
            break;
          }
        }
        if (nextIndex == -1) {
          for (int i = 0; i < index; i++) {
            if (!_checklistItems[i].isCompleted) {
              nextIndex = i;
              break;
            }
          }
        }

        if (nextIndex != -1) {
          if (_scrollController.hasClients) {
            final targetOffset = 650.0 + (nextIndex * 150.0);
            _scrollController.animateTo(
              targetOffset.clamp(0.0, _scrollController.position.maxScrollExtent),
              duration: const Duration(milliseconds: 600),
              curve: Curves.easeInOutCubic,
            );
          }
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('Tiếp tục với đầu việc #${nextIndex + 1}!'),
                backgroundColor: const Color(0xFF0284C7),
                duration: const Duration(seconds: 2),
              ),
            );
          }
        } else {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Xuất sắc! Bạn đã hoàn thành tất cả các đầu việc của ca làm.'),
                backgroundColor: Color(0xFF16A34A),
              ),
            );
          }
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() => _uploadingTaskIndex = null);
        _uploadingIndexNotifier.value = null;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Không thể tải ảnh nghiệm thu: $e'),
            backgroundColor: cani.danger,
          ),
        );
      }
    }
  }

  void _showEvidenceViewer(String imageUrl, String title) {
    showEvidenceViewerDialog(context, imageUrl: imageUrl, title: title);
  }

  void _openFullscreenChecklist() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ShiftChecklistFullScreen(
          title: '${_assignment.jobType} • ${_assignment.eventName}',
          canInteract: _isCheckedIn,
          itemsNotifier: _checklistNotifier,
          uploadingIndexNotifier: _uploadingIndexNotifier,
          onToggleItem: _toggleChecklistItem,
          onCaptureEvidence: _captureEvidenceForTask,
          onViewEvidence: _showEvidenceViewer,
        ),
      ),
    );
  }

  Future<void> _openGoogleMapsNavigation() async {
    final destLat = _assignment.venueLat;
    final destLng = _assignment.venueLng;

    Uri mapUri;
    if (destLat != null && destLng != null) {
      if (_currentPosition != null) {
        mapUri = Uri.parse(
          'https://www.google.com/maps/dir/?api=1&origin=${_currentPosition!.latitude},${_currentPosition!.longitude}&destination=$destLat,$destLng&travelmode=driving',
        );
      } else {
        mapUri = Uri.parse('https://www.google.com/maps/search/?api=1&query=$destLat,$destLng');
      }
    } else {
      mapUri = Uri.parse('https://www.google.com/maps/search/?api=1&query=${Uri.encodeComponent(_assignment.venueAddress)}');
    }

    try {
      final launched = await launchUrl(mapUri, mode: LaunchMode.externalApplication);
      if (!launched) {
        await launchUrl(mapUri, mode: LaunchMode.platformDefault);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể mở Google Maps: $e')),
        );
      }
    }
  }

  void _openChatSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ShiftChatSheet(assignment: _assignment),
    );
  }

  String? _resolveCheckoutProofUrl() {
    String? proofUrl = _uploadedProofUrl;
    if (proofUrl == null || proofUrl.isEmpty) {
      final firstWithPhoto = _checklistItems.firstWhere(
        (t) => t.evidenceImageUrl != null && t.evidenceImageUrl!.isNotEmpty,
        orElse: () => const ShiftChecklistItem(id: '', title: ''),
      );
      if (firstWithPhoto.evidenceImageUrl != null && firstWithPhoto.evidenceImageUrl!.isNotEmpty) {
        proofUrl = firstWithPhoto.evidenceImageUrl;
      }
    }
    return proofUrl;
  }

  Future<void> _openPaymentScreen() async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => ShiftPaymentScreen(
          assignment: _assignment,
          fallbackProofImageUrl: _resolveCheckoutProofUrl(),
        ),
      ),
    );
    if (changed == true && mounted) {
      await _refreshAssignment();
    }
  }

  Future<void> _openRatingScreen() async {
    final rated = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => RateEoScreen(assignment: _assignment),
      ),
    );
    if (rated == true && mounted) {
      await _refreshAssignment();
    }
  }

  Future<void> _handleBottomAction() async {
    final cani = Theme.of(context).cani;

    if (_isCompleted) {
      if (_assignment.hasWorkerReviewed == true) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Bạn đã hoàn thành ca và gửi đánh giá Ban tổ chức rồi!'),
            backgroundColor: Color(0xFF0D9488),
          ),
        );
        return;
      }
      await _openRatingScreen();
      return;
    }

    if (_isExpired) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Ca làm việc đã quá hạn điểm danh, không thể quét QR vào ca.'),
          backgroundColor: cani.danger,
        ),
      );
      return;
    }

    if (!_isCheckedIn) {
      if (_isBeforeCheckinWindow) {
        final gate = _earliestCheckinTime;
        final gateStr =
            '${gate.hour.toString().padLeft(2, '0')}:${gate.minute.toString().padLeft(2, '0')} ${gate.day.toString().padLeft(2, '0')}/${gate.month.toString().padLeft(2, '0')}';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Chưa đến giờ điểm danh ca làm (Mở lúc $gateStr). Vui lòng quay lại đúng giờ!'),
            backgroundColor: cani.warning,
          ),
        );
        return;
      }
      // Bước 1: Check-in vào ca
      final success = await Navigator.push<bool>(
        context,
        MaterialPageRoute(
          builder: (_) => AttendanceQrScannerScreen(
            assignment: _assignment,
            isCheckin: true,
          ),
        ),
      );
      if (success == true) {
        await _refreshAssignment();
        await _loadChecklist();
      }
      return;
    }

    // Bước 2: Hoàn tất Checklist & Ảnh nghiệm thu
    if (!_isWorkReadyForEoConfirm) {
      final incompleteTasks = _checklistItems.where((i) => !i.isCompleted).toList();
      final missingPhotoTasks = _checklistItems
          .where((i) => i.isRequiredEvidence && (i.evidenceImageUrl == null || i.evidenceImageUrl!.isEmpty))
          .toList();

      _scrollToSection(_checklistSectionKey);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            missingPhotoTasks.isNotEmpty
                ? 'Còn ${missingPhotoTasks.length} đầu việc yêu cầu chụp ảnh nghiệm thu tại chỗ!'
                : 'Hãy hoàn thành ${incompleteTasks.length} đầu việc còn lại trước khi gửi BTC duyệt công!',
          ),
          backgroundColor: cani.warning,
        ),
      );
      return;
    }

    // Bước 3: Chờ EO Duyệt công
    if (!_assignment.eoConfirmed) {
      await _refreshAssignment();
      if (!mounted) return;
      if (!_assignment.eoConfirmed) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Đã làm mới! Vui lòng chờ Ban tổ chức kiểm tra ảnh nghiệm thu và bấm Duyệt công (Bước 3).'),
            backgroundColor: cani.brand,
          ),
        );
        return;
      }
    }

    // Bước 4: Tách sang trang Thanh toán thù lao riêng
    if (!_isPaymentConfirmed) {
      await _openPaymentScreen();
      return;
    }

    // Bước 5: Quét QR Check-out kết thúc ca (tự chuyển tiếp Bước 6 Rating)
    final proofUrl = _resolveCheckoutProofUrl();
    if (!mounted) return;

    final success = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => AttendanceQrScannerScreen(
          assignment: _assignment,
          isCheckin: false,
          proofImageUrl: proofUrl,
        ),
      ),
    );
    if (success == true) {
      await _refreshAssignment();
    }
  }

  Future<void> _refreshAssignment() async {
    try {
      final list = await ref.read(proposalRepositoryProvider).fetchAssignments();
      final updated = list.firstWhere(
        (a) => a.assignmentId == _assignment.assignmentId,
        orElse: () => _assignment,
      );
      if (mounted) {
        setState(() {
          _assignment = updated;
          if (updated.proofImageUrl != null) {
            _uploadedProofUrl = updated.proofImageUrl;
          }
        });
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final cani = theme.cani;

    final isCheckedIn = _isCheckedIn;
    final isCompleted = _isCompleted;
    final isOverdueCheckout = isCheckedIn && DateTime.now().isAfter(_assignment.endTime);
    final isReviewed = isCompleted && _assignment.hasWorkerReviewed == true;

    return Scaffold(
      backgroundColor: cani.canvas,
      appBar: AppBar(
        backgroundColor: cani.surface,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        surfaceTintColor: Colors.transparent,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(1),
          child: Container(height: 1, color: cani.border),
        ),
        leading: IconButton(
          icon: Icon(Icons.arrow_back_ios_new_rounded, size: 19, color: cani.textMain),
          onPressed: () => Navigator.pop(context, true),
        ),
        title: Text(
          '${_assignment.eventName} · ${_assignment.jobType}',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w800,
            color: cani.textMain,
          ),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: Icon(Icons.help_outline_rounded, color: cani.brand, size: 22),
            onPressed: () {
              showDialog(
                context: context,
                builder: (ctx) => AlertDialog(
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
                  title: const Text('Quy trình vận hành ca làm', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
                  content: const Text(
                    '1. Có mặt tại địa điểm và quét QR Check-in để vào ca.\n'
                    '2. Hoàn thành Checklist đầu việc & chụp ảnh nghiệm thu ca làm.\n'
                    '3. Chờ Ban tổ chức kiểm tra & duyệt công hoàn thành.\n'
                    '4. Ban tổ chức thanh toán thù lao → Bạn kiểm tra & xác nhận đã nhận đủ tiền.\n'
                    '5. Quét mã QR Check-out để kết thúc ca.\n'
                    '6. Gửi đánh giá Ban tổ chức sau khi kết ca.',
                    style: TextStyle(height: 1.55, fontSize: 13),
                  ),
                  actions: [
                    TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Đã hiểu')),
                  ],
                ),
              );
            },
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: RefreshIndicator(
              color: cani.brand,
              onRefresh: () async {
                await Future.wait([
                  _refreshAssignment(),
                  _loadChecklist(),
                  _fetchGpsLocation(),
                ]);
              },
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                controller: _scrollController,
                padding: const EdgeInsets.only(bottom: 24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                  // 1. Hero Shift Info Card (CANi Clean Workforce Style)
                  Container(
                    margin: const EdgeInsets.fromLTRB(16, 14, 16, 0),
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: cani.surface,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: cani.border),
                      boxShadow: [
                        BoxShadow(
                          color: isDark
                              ? Colors.black.withValues(alpha: 0.16)
                              : const Color(0xFF0F172A).withValues(alpha: 0.03),
                          blurRadius: 12,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Status pill + nút Chỉ đường
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                              decoration: BoxDecoration(
                                color: _isExpired
                                    ? cani.dangerBg
                                    : (isCompleted
                                        ? cani.successBg
                                        : (isOverdueCheckout
                                            ? cani.warningBg
                                            : (isCheckedIn ? cani.successBg : cani.brandSoft))),
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Container(
                                    width: 7,
                                    height: 7,
                                    decoration: BoxDecoration(
                                      shape: BoxShape.circle,
                                      color: _isExpired
                                          ? cani.danger
                                          : (isOverdueCheckout
                                              ? cani.warning
                                              : (isCompleted || isCheckedIn ? cani.success : cani.brand)),
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  Text(
                                    _isExpired
                                        ? (_assignment.status == 'CANCELLED'
                                            ? 'Ca đã bị hủy'
                                            : (_assignment.status == 'NO_SHOW'
                                                ? 'Vắng mặt'
                                                : 'Quá hạn điểm danh'))
                                        : (isCompleted
                                            ? 'Đã hoàn thành ca'
                                            : (isOverdueCheckout
                                                ? 'Quá giờ · Chờ kết ca'
                                                : (isCheckedIn ? 'Đang trong ca làm việc' : 'Đã xác nhận nhận ca'))),
                                    style: TextStyle(
                                      fontSize: 11.5,
                                      fontWeight: FontWeight.w700,
                                      color: _isExpired
                                          ? cani.danger
                                          : (isOverdueCheckout
                                              ? cani.warning
                                              : (isCompleted || isCheckedIn ? cani.success : cani.brand)),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const Spacer(),
                            InkWell(
                              onTap: _openGoogleMapsNavigation,
                              borderRadius: BorderRadius.circular(999),
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                decoration: BoxDecoration(
                                  color: cani.brandSoft,
                                  borderRadius: BorderRadius.circular(999),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(Icons.near_me_rounded, color: cani.brand, size: 14),
                                    const SizedBox(width: 4),
                                    Text(
                                      'Chỉ đường',
                                      style: TextStyle(
                                        fontSize: 11.5,
                                        fontWeight: FontWeight.w700,
                                        color: cani.brand,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Text(
                          '${_assignment.jobType} – ${_assignment.eventName}',
                          style: TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.w800,
                            color: cani.textMain,
                            height: 1.3,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Icon(Icons.location_on_outlined, size: 15, color: cani.textMuted),
                            ),
                            const SizedBox(width: 5),
                            Expanded(
                              child: Text(
                                _assignment.venueAddress,
                                style: TextStyle(
                                  fontSize: 12.8,
                                  color: cani.textMuted,
                                  height: 1.35,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 5),
                        Row(
                          children: [
                            Icon(Icons.schedule_rounded, size: 15, color: cani.textMuted),
                            const SizedBox(width: 5),
                            Expanded(
                              child: Text(
                                '${_assignment.startTime.hour.toString().padLeft(2, '0')}:${_assignment.startTime.minute.toString().padLeft(2, '0')} - ${_assignment.endTime.hour.toString().padLeft(2, '0')}:${_assignment.endTime.minute.toString().padLeft(2, '0')} · ${_assignment.startTime.day.toString().padLeft(2, '0')}/${_assignment.startTime.month.toString().padLeft(2, '0')}/${_assignment.startTime.year}',
                                style: TextStyle(
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w600,
                                  color: cani.textBody,
                                ),
                              ),
                            ),
                          ],
                        ),
                        if (_assignment.eoId != null && _assignment.eoId!.isNotEmpty) ...[
                          const SizedBox(height: 10),
                          InkWell(
                            onTap: () => EoPublicProfileSheet.show(
                              context,
                              eoId: _assignment.eoId!,
                              fallbackName: _assignment.eoName,
                            ),
                            borderRadius: BorderRadius.circular(10),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                              decoration: BoxDecoration(
                                color: cani.brandSoft,
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(color: cani.brand.withValues(alpha: 0.2)),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    _assignment.eoVerified ? Icons.verified_rounded : Icons.apartment_rounded,
                                    size: 14,
                                    color: cani.brand,
                                  ),
                                  const SizedBox(width: 5),
                                  Flexible(
                                    child: Text(
                                      '${_assignment.eoName ?? "Ban tổ chức"} · Xem hồ sơ BTC',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        fontSize: 11.8,
                                        fontWeight: FontWeight.w700,
                                        color: cani.brand,
                                      ),
                                    ),
                                  ),
                                  Icon(Icons.chevron_right_rounded, size: 15, color: cani.brand),
                                ],
                              ),
                            ),
                          ),
                        ],
                        const SizedBox(height: 14),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                          decoration: BoxDecoration(
                            color: cani.canvas,
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: cani.border),
                          ),
                          child: Row(
                            children: [
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text('Đơn giá thù lao', style: TextStyle(fontSize: 11, color: cani.textMuted)),
                                    const SizedBox(height: 2),
                                    Text(
                                      '${formatVndCurrency(_assignment.hourlyWage)}/giờ',
                                      style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
                                    ),
                                  ],
                                ),
                              ),
                              Container(width: 1, height: 28, color: cani.border),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    Text('Tổng ước tính ca', style: TextStyle(fontSize: 11, color: cani.textMuted)),
                                    const SizedBox(height: 2),
                                    Text(
                                      formatVndCurrency(_assignment.totalPay),
                                      style: TextStyle(fontSize: 14.5, fontWeight: FontWeight.w900, color: cani.brand),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),

                  // 2. Countdown & Quy trình 5 bước vận hành
                  Container(
                    margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: cani.surface,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: cani.border),
                      boxShadow: [
                        BoxShadow(
                          color: isDark
                              ? Colors.black.withValues(alpha: 0.16)
                              : const Color(0xFF0F172A).withValues(alpha: 0.03),
                          blurRadius: 12,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: Column(
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              isCompleted
                                  ? Icons.task_alt_rounded
                                  : (_isExpired ? Icons.event_busy_rounded : Icons.timelapse_rounded),
                              size: 18,
                              color: _isExpired
                                  ? cani.danger
                                  : (isCompleted ? cani.success : cani.brand),
                            ),
                            const SizedBox(width: 6),
                            Flexible(
                              child: Text(
                                isCompleted ? 'Ca làm việc đã kết thúc' : _countdownText,
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w800,
                                  color: _isExpired
                                      ? cani.danger
                                      : (isCompleted ? cani.success : cani.textMain),
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                          decoration: BoxDecoration(
                            color: _isExpired
                                ? cani.dangerBg
                                : (isCompleted ? cani.successBg : cani.brandSoft),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Text(
                            _isExpired
                                ? 'Đã qua thời gian kết thúc ca mà chưa có Check-in. Bạn vẫn có thể xem thông tin ca, checklist hoặc gửi khiếu nại nếu có vấn đề.'
                                : (isCompleted
                                    ? (isReviewed
                                        ? 'Cảm ơn bạn đã hoàn thành ca và gửi đánh giá Ban tổ chức.'
                                        : 'Bạn đã Check-out thành công. Hãy gửi đánh giá Ban tổ chức bên dưới!')
                                    : (isCheckedIn
                                        ? (!_isWorkReadyForEoConfirm
                                            ? 'Bước 2: Hoàn tất checklist và chụp ảnh nghiệm thu để Ban tổ chức duyệt công.'
                                            : (!_assignment.eoConfirmed
                                                ? 'Bước 3: Đã hoàn tất đầu việc — đang chờ Ban tổ chức kiểm tra & bấm Duyệt công.'
                                                : (!_isPaymentConfirmed
                                                    ? 'Bước 4: Ban tổ chức đã duyệt công. Chuyển sang trang Thanh toán thù lao để đối soát 2 bên.'
                                                    : 'Bước 5: Đã xác nhận thanh toán! Quét QR Check-out để hoàn tất và chuyển sang Đánh giá.')))
                                        : 'Bước 1: Quét mã QR Check-in khi có mặt tại địa điểm sự kiện.')),
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: _isExpired
                                  ? cani.danger
                                  : (isCompleted ? cani.success : cani.brand),
                              height: 1.4,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        ),
                        const SizedBox(height: 14),
                        ShiftWorkflowStepper(
                          isExpired: _isExpired,
                          isCheckedIn: isCheckedIn,
                          isWorkReady: _isWorkReadyForEoConfirm,
                          isEoConfirmed: _assignment.eoConfirmed,
                          isPaymentConfirmed: _isPaymentConfirmed,
                          isCompleted: isCompleted,
                          isReviewed: isReviewed,
                        ),
                      ],
                    ),
                  ),

                  // 3. GPS Geofencing Card (Chỉ hiện khi ca chưa hoàn thành và chưa hết hạn)
                  if (!isCompleted && !_isExpired)
                    Container(
                      margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      decoration: BoxDecoration(
                        color: _distanceMeters != null && _distanceMeters! > 200
                            ? cani.dangerBg
                            : cani.successBg,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: _distanceMeters != null && _distanceMeters! > 200
                              ? cani.danger.withValues(alpha: 0.35)
                              : cani.success.withValues(alpha: 0.3),
                        ),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            _distanceMeters != null && _distanceMeters! > 200
                                ? Icons.warning_amber_rounded
                                : Icons.verified_user_outlined,
                            color: _distanceMeters != null && _distanceMeters! > 200
                                ? cani.danger
                                : cani.success,
                            size: 22,
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  _distanceMeters != null && _distanceMeters! > 200
                                      ? 'Ngoài bán kính điểm danh (> 200m)'
                                      : 'Vị trí điểm danh hợp lệ',
                                  style: TextStyle(
                                    fontWeight: FontWeight.w700,
                                    fontSize: 13,
                                    color: _distanceMeters != null && _distanceMeters! > 200
                                        ? cani.danger
                                        : cani.success,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  _distanceMeters != null
                                      ? (_distanceMeters! > 200
                                          ? 'Cách địa điểm ${_distanceMeters! >= 1000 ? "${(_distanceMeters! / 1000).toStringAsFixed(1)}km" : "${_distanceMeters!.toInt()}m"}. Di chuyển lại gần trước khi quét mã.'
                                          : 'Cách địa điểm ${_distanceMeters!.toInt()}m (trong bán kính 200m cho phép).')
                                      : (_isLoadingGps ? 'Đang xác định khoảng cách GPS...' : (_gpsError ?? 'Không có dữ liệu GPS')),
                                  style: TextStyle(
                                    fontSize: 11.8,
                                    color: cani.textBody,
                                    height: 1.35,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          if (_distanceMeters != null && _distanceMeters! > 200)
                            TextButton(
                              onPressed: _openGoogleMapsNavigation,
                              style: TextButton.styleFrom(
                                foregroundColor: cani.brand,
                                padding: const EdgeInsets.symmetric(horizontal: 8),
                              ),
                              child: const Text('Chỉ đường', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                            ),
                        ],
                      ),
                    ),

                  // 4. Checklist Công việc Chi tiết
                  KeyedSubtree(
                    key: _checklistSectionKey,
                    child: ShiftChecklistSection(
                      items: _checklistItems,
                      isLoading: _isLoadingChecklist,
                      isSaving: _isSavingChecklist,
                      errorMessage: _checklistError,
                      canInteract: isCheckedIn,
                      isExpired: _isExpired,
                      uploadingTaskIndex: _uploadingTaskIndex,
                      onRetryLoad: _loadChecklist,
                      onToggleItem: _toggleChecklistItem,
                      onCaptureEvidence: _captureEvidenceForTask,
                      onViewEvidence: _showEvidenceViewer,
                      onOpenFullscreen: _openFullscreenChecklist,
                    ),
                  ),

                  // 5. Điều hướng sang Bước 4 (Trang Thanh toán thù lao tách riêng)
                  if ((isCheckedIn && _assignment.eoConfirmed) || isCompleted)
                    Container(
                      margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: cani.surface,
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(
                          color: _isPaymentConfirmed
                              ? cani.success.withValues(alpha: 0.35)
                              : cani.brand.withValues(alpha: 0.35),
                        ),
                      ),
                      child: Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(9),
                            decoration: BoxDecoration(
                              color: _isPaymentConfirmed ? cani.successBg : cani.brandSoft,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Icon(
                              Icons.payments_outlined,
                              size: 20,
                              color: _isPaymentConfirmed ? cani.success : cani.brand,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Bước 4 · Thanh toán thù lao',
                                  style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  _isPaymentConfirmed
                                      ? 'Hai bên đã đối soát xong (${formatVndCurrency(_assignment.declaredAmount > 0 ? _assignment.declaredAmount : _assignment.totalPay)}).'
                                      : 'Mở trang thanh toán để kiểm tra bằng chứng và xác nhận với BTC.',
                                  style: TextStyle(fontSize: 11.8, color: cani.textMuted, height: 1.35),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 8),
                          FilledButton(
                            onPressed: _openPaymentScreen,
                            style: FilledButton.styleFrom(
                              backgroundColor: _isPaymentConfirmed ? cani.success : cani.brand,
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                            ),
                            child: Text(
                              _isPaymentConfirmed ? 'Xem' : 'Mở trang',
                              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800),
                            ),
                          ),
                        ],
                      ),
                    ),

                  // 6. Quick Actions Row (Surface Cards gọn đẹp đồng bộ)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                    child: Row(
                      children: [
                        Expanded(
                          child: _buildQuickAction(
                            icon: Icons.chat_bubble_outline_rounded,
                            label: 'Chat với BTC',
                            onTap: _openChatSheet,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _buildQuickAction(
                            icon: Icons.gavel_rounded,
                            label: 'Khiếu nại ca làm',
                            onTap: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => MyDisputesScreen(assignment: _assignment),
                                ),
                              );
                            },
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),

          // 7. Bottom Sticky Action Bar (CANi Brand Style)
          _buildBottomStickyBar(
            context: context,
            isDark: isDark,
            cani: cani,
            isExpired: _isExpired,
            isCheckedIn: isCheckedIn,
            isCompleted: isCompleted,
            isReviewed: isReviewed,
          ),
        ],
      ),
    );
  }

  Widget _buildBottomStickyBar({
    required BuildContext context,
    required bool isDark,
    required CaniTokens cani,
    required bool isExpired,
    required bool isCheckedIn,
    required bool isCompleted,
    required bool isReviewed,
  }) {
    Color? solidColor;
    bool useBrandGradient = false;
    IconData iconData;
    String label;
    bool isActionDisabled = false;

    if (isExpired) {
      solidColor = const Color(0xFF64748B);
      iconData = Icons.event_busy_rounded;
      label = _assignment.status == 'CANCELLED'
          ? 'CA LÀM VIỆC ĐÃ BỊ HỦY'
          : (_assignment.status == 'NO_SHOW'
              ? 'ĐÃ GHI NHẬN VẮNG MẶT'
              : 'ĐÃ HẾT HẠN ĐIỂM DANH VÀO CA');
      isActionDisabled = true;
    } else if (isCompleted) {
      if (isReviewed) {
        return const SizedBox.shrink();
      }
      useBrandGradient = true;
      iconData = Icons.star_rate_rounded;
      label = 'ĐÁNH GIÁ BAN TỔ CHỨC';
    } else if (!isCheckedIn) {
      if (_isBeforeCheckinWindow) {
        final gate = _earliestCheckinTime;
        final gateTime = '${gate.hour.toString().padLeft(2, '0')}:${gate.minute.toString().padLeft(2, '0')}';
        solidColor = const Color(0xFF64748B);
        iconData = Icons.lock_clock_rounded;
        label = 'CHƯA ĐẾN GIỜ CHECK-IN (MỞ LÚC $gateTime)';
      } else {
        useBrandGradient = true;
        iconData = Icons.qr_code_scanner_rounded;
        label = 'QUÉT QR CHECK-IN (VÀO CA)';
      }
    } else if (!_isWorkReadyForEoConfirm) {
      useBrandGradient = true;
      iconData = Icons.checklist_rounded;
      label = 'HOÀN TẤT ĐẦU VIỆC & ẢNH NGHIỆM THU';
    } else if (!_assignment.eoConfirmed) {
      solidColor = const Color(0xFF475569);
      iconData = Icons.hourglass_top_rounded;
      label = 'CHỜ BAN TỔ CHỨC DUYỆT CÔNG (BƯỚC 3)';
    } else if (!_isPaymentConfirmed) {
      useBrandGradient = true;
      iconData = Icons.payments_outlined;
      label = 'TIẾP TỤC: THANH TOÁN THÙ LAO (BƯỚC 4)';
    } else {
      useBrandGradient = true;
      iconData = Icons.qr_code_scanner_rounded;
      label = 'QUÉT QR CHECK-OUT (BƯỚC 5)';
    }

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
      decoration: BoxDecoration(
        color: cani.surface,
        border: Border(top: BorderSide(color: cani.border)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.25 : 0.05),
            blurRadius: 12,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Container(
          height: 50,
          width: double.infinity,
          decoration: BoxDecoration(
            gradient: (useBrandGradient && !isActionDisabled)
                ? LinearGradient(
                    colors: cani.brandGradient,
                    begin: Alignment.centerLeft,
                    end: Alignment.centerRight,
                  )
                : null,
            color: isActionDisabled
                ? (isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0))
                : (useBrandGradient ? null : solidColor),
            borderRadius: BorderRadius.circular(14),
            boxShadow: (useBrandGradient && !isActionDisabled)
                ? [
                    BoxShadow(
                      color: cani.brand.withValues(alpha: 0.25),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ]
                : null,
          ),
          child: ElevatedButton(
            onPressed: isActionDisabled ? null : _handleBottomAction,
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.transparent,
              disabledBackgroundColor: Colors.transparent,
              shadowColor: Colors.transparent,
              foregroundColor: Colors.white,
              disabledForegroundColor: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
              elevation: 0,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  iconData,
                  size: 18,
                  color: isActionDisabled
                      ? (isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B))
                      : Colors.white,
                ),
                const SizedBox(width: 8),
                Flexible(
                  child: Text(
                    label,
                    style: const TextStyle(
                      fontSize: 13.5,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 0.2,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildQuickAction({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    final cani = Theme.of(context).cani;
    return Material(
      color: cani.surface,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: cani.border),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 18, color: cani.brand),
              const SizedBox(width: 8),
              Flexible(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w700,
                    color: cani.textMain,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
