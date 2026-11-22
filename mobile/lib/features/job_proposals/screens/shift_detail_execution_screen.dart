import 'dart:async';
import 'dart:typed_data';
import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/rate_eo_dialog.dart';
import '../widgets/shift_chat_sheet.dart';
import '../widgets/shift_checklist_section.dart';
import 'attendance_qr_scanner_screen.dart';
import 'my_disputes_screen.dart';
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
  final GlobalKey _paymentSectionKey = GlobalKey();
  final ValueNotifier<List<ShiftChecklistItem>> _checklistNotifier = ValueNotifier([]);
  final ValueNotifier<int?> _uploadingIndexNotifier = ValueNotifier(null);

  String _formatCurrency(num value) {
    return '${value.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ';
  }

  String _paymentStatusLabel(String status) {
    if (_isPaymentConfirmed) {
      if (status == 'CONFIRMED_PARTIAL' || _assignment.workerConfirmStatus == 'RECEIVED_PARTIAL') {
        return 'Nhận một phần';
      }
      if (status == 'DISPUTED' || _assignment.workerConfirmStatus == 'NOT_RECEIVED') {
        return 'Đang khiếu nại';
      }
      return 'Đã xác nhận';
    }
    return switch (status) {
      'DECLARED_PENDING_WORKER' => 'Chờ bạn xác nhận',
      'NOT_DECLARED' => _assignment.eoConfirmed ? 'Chờ BTC thanh toán' : 'Chưa khai báo',
      'CONFIRMED_FULL' => 'Đã nhận đủ',
      'CONFIRMED_PARTIAL' => 'Nhận một phần',
      'DISPUTED' => 'Đang tranh chấp',
      _ => status,
    };
  }

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
  bool _isConfirmingPayment = false;

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

  bool get _isPaymentConfirmed =>
      (_assignment.workerConfirmStatus != null && _assignment.workerConfirmStatus!.isNotEmpty) ||
      _assignment.paymentStatus == 'CONFIRMED_FULL' ||
      _assignment.paymentStatus == 'CONFIRMED_PARTIAL' ||
      _assignment.paymentStatus == 'DISPUTED';

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
                  ? 'Đã ghi nhận vắng mặt (No-show)'
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
            content: Text('✅ Đã nộp ảnh nghiệm thu task #${index + 1}: ${item.title}'),
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
                content: Text('Tiếp tục với đầu việc #${nextIndex + 1}! 🚀'),
                backgroundColor: const Color(0xFF0284C7),
                duration: const Duration(seconds: 2),
              ),
            );
          }
        } else {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('🎉 Xuất sắc! Bạn đã hoàn thành tất cả các đầu việc của ca làm.'),
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
    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: const EdgeInsets.all(16),
        child: Container(
          decoration: BoxDecoration(
            color: Theme.of(context).scaffoldBackgroundColor,
            borderRadius: BorderRadius.circular(20),
            boxShadow: const [
              BoxShadow(color: Colors.black26, blurRadius: 20, offset: Offset(0, 10)),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
                child: Row(
                  children: [
                    const Icon(Icons.verified, color: Color(0xFF16A34A), size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Bằng chứng: $title',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close, size: 20),
                      onPressed: () => Navigator.pop(ctx),
                      visualDensity: VisualDensity.compact,
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              ClipRRect(
                borderRadius: const BorderRadius.vertical(bottom: Radius.circular(20)),
                child: InteractiveViewer(
                  maxScale: 4.0,
                  child: SizedBox(
                    height: 350,
                    child: buildResolvedImageWidget(
                      imageUrl,
                      fit: BoxFit.contain,
                      height: 350,
                      errorText: 'Không thể tải ảnh bằng chứng',
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
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

  Future<void> _handleConfirmPayment(String status) async {
    final cani = Theme.of(context).cani;
    String comment = 'Đã nhận đủ thù lao theo thỏa thuận.';
    if (status == 'RECEIVED_PARTIAL' || status == 'NOT_RECEIVED') {
      final ctrl = TextEditingController();
      final dialogResult = await showDialog<String>(
        context: context,
        builder: (ctx) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: Text(
            status == 'RECEIVED_PARTIAL' ? 'Xác nhận nhận một phần' : 'Báo cáo chưa nhận thù lao',
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
          content: TextField(
            controller: ctrl,
            maxLines: 3,
            autofocus: true,
            decoration: InputDecoration(
              labelText: 'Chi tiết số tiền / Lý do khiếu nại (*)',
              hintText: status == 'RECEIVED_PARTIAL'
                  ? 'Ví dụ: Đã nhận 150.000đ tiền mặt, còn thiếu 50.000đ phụ cấp...'
                  : 'Ví dụ: Đã kết thúc ca làm việc nhưng Ban tổ chức chưa thanh toán thù lao...',
              border: const OutlineInputBorder(),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Hủy')),
            FilledButton(
              onPressed: () {
                if (ctrl.text.trim().length >= 4) {
                  Navigator.pop(ctx, ctrl.text.trim());
                }
              },
              style: FilledButton.styleFrom(
                backgroundColor: status == 'NOT_RECEIVED' ? cani.danger : const Color(0xFFD97706),
              ),
              child: const Text('Xác nhận gửi'),
            ),
          ],
        ),
      );
      if (dialogResult == null || dialogResult.isEmpty) return;
      comment = dialogResult;
    }

    try {
      setState(() => _isConfirmingPayment = true);
      await ref.read(proposalRepositoryProvider).confirmPayment(
            _assignment.assignmentId,
            status,
            comment,
          );
      await _refreshAssignment();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              status == 'NOT_RECEIVED'
                  ? 'Đã ghi nhận khiếu nại chưa nhận tiền và mở hồ sơ tranh chấp cho CSKH — bạn có thể kết ca!'
                  : 'Đã xác nhận thanh toán thành công — bạn có thể kết ca!',
            ),
            backgroundColor: const Color(0xFF16A34A),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Lỗi xác nhận thanh toán: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: cani.danger,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isConfirmingPayment = false);
      }
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
      // Đã check-out xong ca -> Mở Đánh giá Ban tổ chức (Rating)
      final rated = await showDialog<bool>(
        context: context,
        builder: (_) => RateEoDialog(assignment: _assignment),
      );
      if (rated == true && mounted) {
        _refreshAssignment();
      }
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

    // Đang trong ca: kiểm tra đúng trình tự (1) Checklist & Ảnh task -> (2) EO Duyệt -> (3) Thanh toán & Xác nhận -> (4) Check-out -> (5) Rating
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

    if (!_assignment.eoConfirmed && !_isPaymentConfirmed && _assignment.paymentStatus == 'NOT_DECLARED') {
      await _refreshAssignment();
      if (!mounted) return;
      if (!_assignment.eoConfirmed) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Đã làm mới! Vui lòng chờ Ban tổ chức kiểm tra ảnh nghiệm thu và bấm Duyệt công.'),
            backgroundColor: Color(0xFF0284C7),
          ),
        );
        return;
      }
    }

    if (!_isPaymentConfirmed) {
      if (_assignment.paymentStatus == 'NOT_DECLARED' && _assignment.declaredAmount <= 0) {
        await _refreshAssignment();
        if (!mounted) return;
        if (_assignment.paymentStatus == 'NOT_DECLARED' && _assignment.declaredAmount <= 0) {
          _scrollToSection(_paymentSectionKey);
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('BTC đã duyệt công — vui lòng chờ BTC khai báo thanh toán thù lao!'),
              backgroundColor: Color(0xFF0284C7),
            ),
          );
          return;
        }
      }

      _scrollToSection(_paymentSectionKey);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Vui lòng xác nhận "Đã nhận đủ / Nhận 1 phần / Chưa nhận" ở mục Thanh toán bên trên trước khi Check-out!'),
          backgroundColor: cani.warning,
        ),
      );
      return;
    }

    // Bước cuối trước Rating: Quét QR Check-out kết thúc ca
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
    final isReviewed = isCompleted && _assignment.hasWorkerReviewed == true;

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: isDark ? const Color(0xFF1E293B) : Colors.white,
        elevation: 0.5,
        leading: IconButton(
          icon: Icon(Icons.arrow_back_ios_new, size: 20, color: isDark ? Colors.white : Colors.black87),
          onPressed: () => Navigator.pop(context, true),
        ),
        title: Text(
          '${_assignment.eventName} · ${_assignment.jobType}',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.bold,
            color: cani.textMain,
          ),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: Icon(Icons.help_outline, color: cani.brand),
            onPressed: () {
              showDialog(
                context: context,
                builder: (ctx) => AlertDialog(
                  title: const Text('Quy trình vận hành ca làm'),
                  content: const Text(
                    '1. Có mặt tại địa điểm và quét QR Check-in để vào ca.\n'
                    '2. Hoàn thành Checklist đầu việc & chụp ảnh nghiệm thu ca làm.\n'
                    '3. Chờ Ban tổ chức kiểm tra & duyệt công hoàn thành.\n'
                    '4. Ban tổ chức thanh toán thù lao -> Bạn kiểm tra & xác nhận đã nhận đủ tiền.\n'
                    '5. Quét mã QR Check-out để kết thúc ca.\n'
                    '6. Gửi đánh giá (Rating) Ban tổ chức sau khi kết ca.',
                    style: TextStyle(height: 1.5, fontSize: 13),
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
                  // 1. Venue & Job Card (Shopee Driver Style)
                  Container(
                    color: isDark ? const Color(0xFF1E293B) : Colors.white,
                    padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '${_assignment.jobType} – ${_assignment.eventName}',
                                    style: TextStyle(
                                      fontSize: 15.5,
                                      fontWeight: FontWeight.bold,
                                      color: isDark ? Colors.white : const Color(0xFF0F172A),
                                      height: 1.3,
                                    ),
                                  ),
                                  const SizedBox(height: 6),
                                  Text(
                                    _assignment.venueAddress,
                                    style: TextStyle(
                                      fontSize: 13,
                                      color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569),
                                      height: 1.3,
                                    ),
                                  ),
                                  const SizedBox(height: 6),
                                  Row(
                                    children: [
                                      Icon(Icons.access_time_rounded, size: 14, color: cani.brand),
                                      const SizedBox(width: 5),
                                      Expanded(
                                        child: Text(
                                          '${_assignment.startTime.hour.toString().padLeft(2, '0')}:${_assignment.startTime.minute.toString().padLeft(2, '0')} - ${_assignment.endTime.hour.toString().padLeft(2, '0')}:${_assignment.endTime.minute.toString().padLeft(2, '0')} · ${_assignment.startTime.day.toString().padLeft(2, '0')}/${_assignment.startTime.month.toString().padLeft(2, '0')}/${_assignment.startTime.year}',
                                          style: TextStyle(
                                            fontSize: 12,
                                            fontWeight: FontWeight.w600,
                                            color: isDark ? const Color(0xFFCBD5E1) : const Color(0xFF334155),
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                  if (_assignment.eoId != null && _assignment.eoId!.isNotEmpty) ...[
                                    const SizedBox(height: 8),
                                    InkWell(
                                      onTap: () => EoPublicProfileSheet.show(
                                        context,
                                        eoId: _assignment.eoId!,
                                        fallbackName: _assignment.eoName,
                                      ),
                                      borderRadius: BorderRadius.circular(8),
                                      child: Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                                        decoration: BoxDecoration(
                                          color: cani.brandSoft,
                                          borderRadius: BorderRadius.circular(8),
                                          border: Border.all(color: cani.brand.withValues(alpha: 0.25)),
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
                                                  fontSize: 11.5,
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
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            InkWell(
                              onTap: _openGoogleMapsNavigation,
                              borderRadius: BorderRadius.circular(999),
                              child: Container(
                                width: 44,
                                height: 44,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: cani.brand.withValues(alpha: 0.1),
                                  border: Border.all(color: cani.brand.withValues(alpha: 0.2)),
                                ),
                                child: Icon(Icons.directions, color: cani.brand, size: 22),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        const Divider(height: 1),
                        const SizedBox(height: 10),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'Thù lao: ${_formatCurrency(_assignment.hourlyWage)}/giờ',
                              style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: Color(0xFF0D9488)),
                            ),
                            Text(
                              'Tổng ước tính: ${_formatCurrency(_assignment.totalPay)}',
                              style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.bold, color: Color(0xFFEA580C)),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),

                  // 2. Banner Trạng thái
                  Container(
                    margin: const EdgeInsets.only(top: 8),
                    color: _isExpired
                        ? cani.danger
                        : const Color(0xFF16A34A),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    child: Center(
                      child: Text(
                        _isExpired
                            ? (_assignment.status == 'CANCELLED'
                                ? 'Ca làm việc đã bị hủy'
                                : (_assignment.status == 'NO_SHOW'
                                    ? 'Vắng mặt không điểm danh (No-show)'
                                    : 'Ca làm việc đã quá hạn điểm danh vào ca'))
                            : (isCompleted
                                ? 'Ca làm việc đã hoàn thành thành công'
                                : (isCheckedIn ? 'Đang trong ca làm việc' : 'Ban tổ chức đã xác nhận nhận ca')),
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 14.5,
                        ),
                      ),
                    ),
                  ),

                  // 3. Countdown & Quy trình 5 bước vận hành
                  Container(
                    color: isDark ? const Color(0xFF1E293B) : Colors.white,
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
                    child: Column(
                      children: [
                        Text(
                          isCompleted ? 'Ca làm việc đã kết thúc' : _countdownText,
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                            color: _isExpired
                                ? cani.danger
                                : (isDark ? cani.accent : const Color(0xFF0284C7)),
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          _isExpired
                              ? 'Đã qua thời gian kết thúc ca mà chưa có Check-in. Bạn vẫn có thể xem thông tin ca, checklist hoặc gửi khiếu nại nếu có vấn đề.'
                              : (isCompleted
                                  ? (isReviewed
                                      ? 'Cảm ơn bạn đã hoàn thành ca và gửi đánh giá Ban tổ chức.'
                                      : 'Bạn đã Check-out thành công. Hãy gửi đánh giá (Rating) Ban tổ chức bên dưới!')
                                  : (isCheckedIn
                                      ? (!_isWorkReadyForEoConfirm
                                          ? '* Bước 2: Hoàn tất checklist và chụp ảnh nghiệm thu để Ban tổ chức duyệt công.'
                                          : (!_assignment.eoConfirmed
                                              ? '* Bước 3: Chờ Ban tổ chức kiểm tra & bấm Duyệt công.'
                                              : (!_isPaymentConfirmed
                                                  ? '* Bước 4: Xác nhận nhận đủ thù lao từ Ban tổ chức trước khi quét QR Check-out.'
                                                  : '* Bước 5: Đã xác nhận thanh toán! Vui lòng quét QR Check-out để kết thúc ca.')))
                                      : '* Bước 1: Quét mã QR Check-in khi có mặt tại địa điểm sự kiện.')),
                          style: TextStyle(
                            fontSize: 11.8,
                            fontWeight: FontWeight.w600,
                            color: _isExpired
                                ? cani.danger
                                : (isCompleted ? const Color(0xFF059669) : const Color(0xFFEA580C)),
                            height: 1.4,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 12),
                        _buildWorkflowStepper(
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

                  // 4. GPS Geofencing Warning (Chỉ hiện khi ca chưa hoàn thành và chưa hết hạn)
                  if (!isCompleted && !_isExpired)
                    Container(
                      margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: _distanceMeters != null && _distanceMeters! > 200
                            ? const Color(0xFFFEF2F2)
                            : const Color(0xFFF0FDF4),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: _distanceMeters != null && _distanceMeters! > 200
                              ? const Color(0xFFFCA5A5)
                              : const Color(0xFF86EFAC),
                        ),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            _distanceMeters != null && _distanceMeters! > 200
                                ? Icons.warning_amber_rounded
                                : Icons.check_circle_outline,
                              color: _distanceMeters != null && _distanceMeters! > 200
                                  ? cani.danger
                                  : const Color(0xFF16A34A),
                            size: 24,
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  _distanceMeters != null && _distanceMeters! > 200
                                      ? 'Cảnh báo khoảng cách (> 200m)'
                                      : 'Vị trí điểm danh hợp lệ',
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 13,
                                    color: _distanceMeters != null && _distanceMeters! > 200
                                        ? const Color(0xFF991B1B)
                                        : const Color(0xFF166534),
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  _distanceMeters != null
                                      ? (_distanceMeters! > 200
                                          ? 'Bạn đang cách địa điểm ${_distanceMeters! >= 1000 ? "${(_distanceMeters! / 1000).toStringAsFixed(1)}km" : "${_distanceMeters!.toInt()}m"}. Hãy di chuyển đến gần địa điểm trước khi quét mã!'
                                          : 'Bạn đang cách địa điểm ${_distanceMeters!.toInt()}m (nằm trong bán kính 200m cho phép).')
                                      : (_isLoadingGps ? 'Đang xác định khoảng cách GPS...' : (_gpsError ?? 'Không có dữ liệu GPS')),
                                  style: TextStyle(
                                    fontSize: 11.5,
                                    color: _distanceMeters != null && _distanceMeters! > 200
                                        ? const Color(0xFFB91C1C)
                                        : const Color(0xFF15803D),
                                    height: 1.3,
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
                              child: const Text('Chỉ đường', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                            ),
                        ],
                      ),
                    ),

                  // 5. Checklist Công việc Chi tiết (Luôn hiển thị để xem trước, chỉ cho tương tác khi đã Check-in vào ca)
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

                  // 6. Xác nhận Thanh toán Thù lao (Hard Gate trước Check-out)
                  if (isCheckedIn || isCompleted)
                    Container(
                      key: _paymentSectionKey,
                      margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                      decoration: BoxDecoration(
                        color: isDark ? const Color(0xFF1E293B) : Colors.white,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: _isPaymentConfirmed ? const Color(0xFF86EFAC) : const Color(0xFFBFDBFE),
                          width: 1.5,
                        ),
                      ),
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(Icons.payments_outlined, size: 20, color: cani.brand),
                              const SizedBox(width: 8),
                              const Text(
                                'THANH TOÁN THÙ LAO',
                                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800),
                              ),
                              const Spacer(),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                decoration: BoxDecoration(
                                  color: _isPaymentConfirmed
                                      ? const Color(0xFFDCFCE7)
                                      : (_assignment.eoConfirmed && _assignment.paymentStatus == 'NOT_DECLARED'
                                          ? const Color(0xFFE0F2FE)
                                          : const Color(0xFFFEF3C7)),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  _paymentStatusLabel(_assignment.paymentStatus),
                                  style: TextStyle(
                                    fontSize: 10.5,
                                    fontWeight: FontWeight.bold,
                                    color: _isPaymentConfirmed
                                        ? const Color(0xFF16A34A)
                                        : (_assignment.eoConfirmed && _assignment.paymentStatus == 'NOT_DECLARED'
                                            ? const Color(0xFF0284C7)
                                            : const Color(0xFFD97706)),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 10),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text('Thù lao thỏa thuận:', style: TextStyle(fontSize: 12.5, color: cani.textMuted)),
                              Text(
                                _formatCurrency(_assignment.totalPay),
                                style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text('BTC khai báo thanh toán:', style: TextStyle(fontSize: 12.5, color: cani.textMuted)),
                              Text(
                                _assignment.declaredAmount > 0
                                    ? _formatCurrency(_assignment.declaredAmount)
                                    : 'Chưa khai báo',
                                style: TextStyle(
                                  fontSize: 13.5,
                                  fontWeight: FontWeight.w800,
                                  color: _assignment.declaredAmount > 0 ? const Color(0xFF059669) : const Color(0xFF94A3B8),
                                ),
                              ),
                            ],
                          ),
                          if (_assignment.paymentProofUrl != null && _assignment.paymentProofUrl!.isNotEmpty) ...[
                            const SizedBox(height: 10),
                            GestureDetector(
                              onTap: () => _showEvidenceViewer(_assignment.paymentProofUrl!, 'Chứng từ thanh toán từ BTC'),
                              child: ClipRRect(
                                borderRadius: BorderRadius.circular(10),
                                child: buildResolvedImageWidget(
                                  _assignment.paymentProofUrl!,
                                  height: 130,
                                  width: double.infinity,
                                  fit: BoxFit.cover,
                                  errorText: 'Không thể tải ảnh chứng từ',
                                ),
                              ),
                            ),
                          ],
                          const SizedBox(height: 12),
                          if (_isPaymentConfirmed)
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                              decoration: BoxDecoration(
                                color: const Color(0xFFDCFCE7),
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(color: const Color(0xFF86EFAC)),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.check_circle, size: 18, color: Color(0xFF16A34A)),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      isCompleted
                                          ? 'Đã xác nhận thanh toán và hoàn thành Check-out.'
                                          : 'Đã xác nhận thanh toán — hãy bấm Quét QR Check-out để kết thúc ca!',
                                      style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Color(0xFF15803D)),
                                    ),
                                  ),
                                ],
                              ),
                            )
                          else if (_assignment.paymentStatus == 'NOT_DECLARED' && _assignment.declaredAmount <= 0)
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                              decoration: BoxDecoration(
                                color: _assignment.eoConfirmed ? const Color(0xFFE0F2FE) : const Color(0xFFF1F5F9),
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(
                                  color: _assignment.eoConfirmed ? const Color(0xFFBAE6FD) : const Color(0xFFCBD5E1),
                                ),
                              ),
                              child: Row(
                                children: [
                                  Icon(
                                    _assignment.eoConfirmed ? Icons.verified_outlined : Icons.schedule,
                                    size: 18,
                                    color: _assignment.eoConfirmed ? const Color(0xFF0284C7) : const Color(0xFF475569),
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      _assignment.eoConfirmed
                                          ? 'BTC đã xác nhận hoàn thành công việc! Vui lòng chờ BTC khai báo thanh toán thù lao.'
                                          : 'Chờ bạn hoàn tất đầu việc & nộp ảnh nghiệm thu để BTC duyệt công trước khi thanh toán.',
                                      style: TextStyle(
                                        fontSize: 12,
                                        fontWeight: FontWeight.w600,
                                        color: _assignment.eoConfirmed ? const Color(0xFF0369A1) : const Color(0xFF475569),
                                      ),
                                    ),
                                  ),
                                  IconButton(
                                    onPressed: _refreshAssignment,
                                    tooltip: 'Làm mới trạng thái',
                                    icon: const Icon(Icons.refresh, size: 18),
                                    visualDensity: VisualDensity.compact,
                                  ),
                                ],
                              ),
                            )
                          else if (_isConfirmingPayment)
                            const Center(child: Padding(padding: EdgeInsets.all(8), child: CircularProgressIndicator()))
                          else
                            Row(
                              children: [
                                Expanded(
                                  child: FilledButton(
                                    onPressed: () => _handleConfirmPayment('RECEIVED_FULL'),
                                    style: FilledButton.styleFrom(
                                      backgroundColor: const Color(0xFF16A34A),
                                      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
                                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                    ),
                                    child: const Text('Đã nhận đủ', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w800)),
                                  ),
                                ),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: FilledButton(
                                    onPressed: () => _handleConfirmPayment('RECEIVED_PARTIAL'),
                                    style: FilledButton.styleFrom(
                                      backgroundColor: const Color(0xFFD97706),
                                      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
                                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                    ),
                                    child: const Text('Đã nhận 1 phần', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w800)),
                                  ),
                                ),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: FilledButton(
                                    onPressed: () => _handleConfirmPayment('NOT_RECEIVED'),
                                     style: FilledButton.styleFrom(
                                       backgroundColor: cani.danger,
                                       padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
                                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                    ),
                                    child: const Text('Chưa nhận', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w800)),
                                  ),
                                ),
                              ],
                            ),
                        ],
                      ),
                    ),

                  // 7. Quick Actions Row (Chat BTC & Khiếu nại — bỏ nút Bản đồ thừa do đã có chỉ đường ở trên)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 16, 12, 0),
                    child: Row(
                      children: [
                        Expanded(
                          child: _buildQuickAction(
                            icon: Icons.chat_outlined,
                            label: 'Chat BTC',
                            onTap: _openChatSheet,
                          ),
                        ),
                        Expanded(
                          child: _buildQuickAction(
                            icon: Icons.gavel_rounded,
                            label: 'Khiếu nại',
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

          // 8. Bottom Sticky Action Bar (Theo đúng 5 bước vận hành; ẩn QR nếu ca đã hết hạn)
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
    Color bgColor;
    IconData iconData;
    String label;
    bool isActionDisabled = false;

    if (isExpired) {
      bgColor = const Color(0xFF64748B);
      iconData = Icons.event_busy_rounded;
      label = _assignment.status == 'CANCELLED'
          ? 'CA LÀM VIỆC ĐÃ BỊ HỦY'
          : (_assignment.status == 'NO_SHOW'
              ? 'ĐÃ GHI NHẬN VẮNG MẶT (NO-SHOW)'
              : 'ĐÃ HẾT HẠN ĐIỂM DANH VÀO CA');
      isActionDisabled = true;
    } else if (isCompleted) {
      bgColor = isReviewed ? const Color(0xFF16A34A) : const Color(0xFF0D9488);
      iconData = isReviewed ? Icons.verified_rounded : Icons.star_rate_rounded;
      label = isReviewed
          ? 'ĐÃ HOÀN THÀNH & ĐÁNH GIÁ CA LÀM'
          : 'ĐÁNH GIÁ BAN TỔ CHỨC (RATING)';
    } else if (!isCheckedIn) {
      if (_isBeforeCheckinWindow) {
        final gate = _earliestCheckinTime;
        final gateTime = '${gate.hour.toString().padLeft(2, '0')}:${gate.minute.toString().padLeft(2, '0')}';
        bgColor = const Color(0xFF64748B);
        iconData = Icons.lock_clock_rounded;
        label = 'CHƯA ĐẾN GIỜ CHECK-IN (MỞ LÚC $gateTime)';
      } else {
        bgColor = cani.brand;
        iconData = Icons.qr_code_scanner_rounded;
        label = 'QUÉT QR CHECK-IN (VÀO CA)';
      }
    } else if (!_isWorkReadyForEoConfirm) {
      bgColor = const Color(0xFF0284C7);
      iconData = Icons.checklist_rounded;
      label = 'HOÀN TẤT ĐẦU VIỆC & ẢNH NGHIỆM THU';
    } else if (!_assignment.eoConfirmed &&
        !_isPaymentConfirmed &&
        _assignment.paymentStatus == 'NOT_DECLARED') {
      bgColor = const Color(0xFF475569);
      iconData = Icons.hourglass_top_rounded;
      label = 'CHỜ BAN TỔ CHỨC XÁC NHẬN CÔNG';
    } else if (!_isPaymentConfirmed) {
      final isWaitingDeclare =
          _assignment.paymentStatus == 'NOT_DECLARED' && _assignment.declaredAmount <= 0;
      bgColor = isWaitingDeclare ? const Color(0xFF0369A1) : const Color(0xFFD97706);
      iconData = isWaitingDeclare ? Icons.refresh_rounded : Icons.payments_outlined;
      label = isWaitingDeclare
          ? 'CHỜ BAN TỔ CHỨC THANH TOÁN THÙ LAO'
          : 'XÁC NHẬN ĐÃ NHẬN THÙ LAO';
    } else {
      bgColor = const Color(0xFFEA580C);
      iconData = Icons.qr_code_scanner_rounded;
      label = 'QUÉT QR CHECK-OUT (KẾT THÚC CA)';
    }

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E293B) : Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.08),
            blurRadius: 10,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 52,
          width: double.infinity,
          child: ElevatedButton(
            onPressed: isActionDisabled ? null : _handleBottomAction,
            style: ElevatedButton.styleFrom(
              backgroundColor: bgColor,
              disabledBackgroundColor: isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1),
              foregroundColor: Colors.white,
              disabledForegroundColor: isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569),
              elevation: isActionDisabled ? 0 : 3,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: (isActionDisabled ? Colors.black : Colors.white).withValues(alpha: 0.16),
                  ),
                  child: Icon(
                    iconData,
                    size: 15,
                    color: isActionDisabled
                        ? (isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569))
                        : Colors.white,
                  ),
                ),
                const SizedBox(width: 10),
                Flexible(
                  child: Text(
                    label,
                    style: const TextStyle(
                      fontSize: 13.5,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 0.3,
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

  Widget _buildWorkflowStepper({
    required bool isExpired,
    required bool isCheckedIn,
    required bool isWorkReady,
    required bool isEoConfirmed,
    required bool isPaymentConfirmed,
    required bool isCompleted,
    required bool isReviewed,
  }) {
    final steps = [
      (label: 'Vào ca', done: isCheckedIn || isCompleted, active: !isCheckedIn && !isCompleted && !isExpired, failed: isExpired),
      (label: 'Đầu việc', done: (isCheckedIn && isWorkReady) || isEoConfirmed || isCompleted, active: isCheckedIn && !isWorkReady && !isEoConfirmed, failed: false),
      (label: 'Duyệt công', done: isEoConfirmed || isCompleted, active: isCheckedIn && isWorkReady && !isEoConfirmed, failed: false),
      (label: 'Thù lao', done: isPaymentConfirmed, active: isCheckedIn && isEoConfirmed && !isPaymentConfirmed, failed: false),
      (label: 'Kết ca', done: isCompleted && isReviewed, active: (isCheckedIn && isPaymentConfirmed) || (isCompleted && !isReviewed), failed: false),
    ];

    return Row(
      children: [
        for (int i = 0; i < steps.length; i++) ...[
          Expanded(
            child: Column(
              children: [
                Container(
                  width: 22,
                  height: 22,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: steps[i].failed
                        ? const Color(0xFFDC2626)
                        : (steps[i].done
                            ? const Color(0xFF16A34A)
                            : (steps[i].active ? const Color(0xFF0284C7) : const Color(0xFFE2E8F0))),
                  ),
                  child: Center(
                    child: steps[i].failed
                        ? const Icon(Icons.close, size: 13, color: Colors.white)
                        : (steps[i].done
                            ? const Icon(Icons.check, size: 13, color: Colors.white)
                            : Text(
                                '${i + 1}',
                                style: TextStyle(
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.bold,
                                  color: steps[i].active ? Colors.white : const Color(0xFF64748B),
                                ),
                              )),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  steps[i].label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 9.5,
                    fontWeight: (steps[i].active || steps[i].done) ? FontWeight.w700 : FontWeight.w500,
                    color: steps[i].done
                        ? const Color(0xFF16A34A)
                        : (steps[i].active ? const Color(0xFF0284C7) : const Color(0xFF94A3B8)),
                  ),
                ),
              ],
            ),
          ),
          if (i < steps.length - 1)
            Expanded(
              child: Container(
                height: 2,
                margin: const EdgeInsets.only(bottom: 16),
                color: steps[i].done ? const Color(0xFF86EFAC) : const Color(0xFFE2E8F0),
              ),
            ),
        ],
      ],
    );
  }

  Widget _buildQuickAction({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 24, color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569)),
            const SizedBox(height: 4),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
