import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:file_picker/file_picker.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/rate_eo_dialog.dart';
import '../widgets/shift_chat_sheet.dart';
import '../widgets/shift_checklist_section.dart';
import 'attendance_qr_scanner_screen.dart';
import 'my_disputes_screen.dart';
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
  final GlobalKey _proofSectionKey = GlobalKey();
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

  // Proof of work image
  File? _proofImageFile;
  String? _uploadedProofUrl;
  bool _isUploadingProof = false;
  bool _isConfirmingPayment = false;

  bool get _isCompleted =>
      _assignment.checkoutTime != null ||
      (_assignment.status == 'COMPLETED' && _assignment.checkinTime == null);

  bool get _isCheckedIn =>
      !_isCompleted &&
      (_assignment.status == 'CHECKED_IN' || _assignment.checkinTime != null);

  bool get _isPaymentConfirmed =>
      (_assignment.workerConfirmStatus != null && _assignment.workerConfirmStatus!.isNotEmpty) ||
      _assignment.paymentStatus == 'CONFIRMED_FULL' ||
      _assignment.paymentStatus == 'CONFIRMED_PARTIAL' ||
      _assignment.paymentStatus == 'DISPUTED';

  bool get _hasOverallProof =>
      (_uploadedProofUrl != null && _uploadedProofUrl!.isNotEmpty) || _proofImageFile != null;

  bool get _isWorkReadyForEoConfirm {
    final allTasksDone = _checklistItems.every((t) =>
        t.isCompleted &&
        (!t.isRequiredEvidence || (t.evidenceImageUrl != null && t.evidenceImageUrl!.isNotEmpty)));
    final hasAnyTaskPhoto = _checklistItems.any(
      (t) => t.evidenceImageUrl != null && t.evidenceImageUrl!.isNotEmpty,
    );
    return allTasksDone && (_hasOverallProof || hasAnyTaskPhoto);
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
    final isCheckedIn = _assignment.status == 'CHECKED_IN' ||
        (_assignment.checkinTime != null && _assignment.checkoutTime == null);

    final target = isCheckedIn ? _assignment.endTime : _assignment.startTime;
    final diff = target.difference(now);

    if (diff.isNegative) {
      if (mounted) {
        setState(() {
          _countdownText = isCheckedIn ? 'Đã quá giờ kết thúc' : 'Đang trong giờ ca làm';
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
          content: const Text('Vào ca (quét QR check-in) để bắt đầu tích chọn đầu việc!'),
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

  Future<({Uint8List? bytes, String? fileName, String? filePath})?> _chooseOrCaptureImage() async {
    final cani = Theme.of(context).cani;
    final source = await showModalBottomSheet<String>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: Icon(Icons.camera_alt_rounded, color: cani.brand),
                title: const Text('Chụp ảnh trực tiếp bằng Camera', style: TextStyle(fontWeight: FontWeight.w600)),
                onTap: () => Navigator.of(ctx).pop('camera'),
              ),
              ListTile(
                leading: Icon(Icons.photo_library_rounded, color: cani.brand),
                title: const Text('Chọn ảnh từ Thư viện thiết bị', style: TextStyle(fontWeight: FontWeight.w600)),
                onTap: () => Navigator.of(ctx).pop('gallery'),
              ),
            ],
          ),
        ),
      ),
    );

    if (source == null || !mounted) return null;

    if (source == 'camera') {
      final scanResult = await Navigator.of(context).push<CardScanResult>(
        MaterialPageRoute(
          builder: (_) => const CardScannerScreen(scanType: ScanType.workProof),
        ),
      );
      if (scanResult == null) return null;
      return (bytes: scanResult.bytes, fileName: scanResult.fileName, filePath: null);
    } else {
      final files = await FilePicker.pickFiles(type: FileType.image);
      if (files.isNotEmpty && files.first.path != null) {
        return (bytes: null, fileName: files.first.name, filePath: files.first.path!);
      }
      return null;
    }
  }

  Future<void> _captureEvidenceForTask(int index) async {
    final cani = Theme.of(context).cani;
    if (!_isCheckedIn) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Vào ca (quét QR check-in) để chụp ảnh nghiệm thu đầu việc!'),
          backgroundColor: cani.warning,
        ),
      );
      return;
    }
    final item = _checklistItems[index];
    try {
      final picked = await _chooseOrCaptureImage();

      if (picked != null && (picked.filePath != null || picked.bytes != null)) {
        setState(() => _uploadingTaskIndex = index);
        _uploadingIndexNotifier.value = index;

        // Upload ảnh lên backend
        final repo = ref.read(proposalRepositoryProvider);
        final uploadedUrl = picked.bytes != null
            ? await repo.uploadProofBytes(picked.bytes!, picked.fileName ?? 'task_proof.jpg')
            : await repo.uploadProofImage(picked.filePath!);

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
          itemsNotifier: _checklistNotifier,
          uploadingIndexNotifier: _uploadingIndexNotifier,
          onToggleItem: _toggleChecklistItem,
          onCaptureEvidence: _captureEvidenceForTask,
          onViewEvidence: _showEvidenceViewer,
        ),
      ),
    );
  }

  Future<void> _pickProofImage() async {
    final cani = Theme.of(context).cani;
    try {
      final picked = await _chooseOrCaptureImage();

      if (picked != null && (picked.filePath != null || picked.bytes != null)) {
        if (picked.filePath != null) {
          setState(() {
            _proofImageFile = File(picked.filePath!);
          });
        }

        // Tự động upload ảnh lên máy chủ và đồng bộ ngay xuống AttendanceLog để EO kiểm tra
        setState(() => _isUploadingProof = true);
        final repo = ref.read(proposalRepositoryProvider);
        final url = picked.bytes != null
            ? await repo.uploadProofBytes(picked.bytes!, picked.fileName ?? 'proof.jpg')
            : await repo.uploadProofImage(picked.filePath!);
        if (_isCheckedIn || _isCompleted) {
          await repo.submitAssignmentProof(_assignment.assignmentId, url);
        }
        if (mounted) {
          setState(() {
            _uploadedProofUrl = url;
            _isUploadingProof = false;
          });
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Đã nộp ảnh bằng chứng nghiệm thu cho BTC kiểm tra!'),
              backgroundColor: Color(0xFF16A34A),
            ),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isUploadingProof = false);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Không thể chọn ảnh: $e'),
              backgroundColor: cani.danger,
            ),
          );
      }
    }
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

    if (!_isCheckedIn) {
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

    // Đang trong ca: kiểm tra đúng trình tự (1) Checklist & Ảnh -> (2) EO Duyệt -> (3) Thanh toán & Xác nhận -> (4) Check-out -> (5) Rating
    if (!_isWorkReadyForEoConfirm) {
      final incompleteTasks = _checklistItems.where((i) => !i.isCompleted).toList();
      final missingPhotoTasks = _checklistItems
          .where((i) => i.isRequiredEvidence && (i.evidenceImageUrl == null || i.evidenceImageUrl!.isEmpty))
          .toList();

      if (incompleteTasks.isNotEmpty || missingPhotoTasks.isNotEmpty) {
        _scrollToSection(_checklistSectionKey);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              incompleteTasks.isNotEmpty
                  ? 'Hãy hoàn thành ${incompleteTasks.length} đầu việc còn lại trước khi gửi BTC duyệt công!'
                  : 'Còn ${missingPhotoTasks.length} đầu việc yêu cầu chụp ảnh nghiệm thu!',
            ),
            backgroundColor: cani.warning,
          ),
        );
      } else {
        _scrollToSection(_proofSectionKey);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Hãy chụp/tải ảnh bằng chứng nghiệm thu ca làm để gửi Ban tổ chức xác nhận!'),
            backgroundColor: cani.warning,
          ),
        );
      }
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

    if ((proofUrl == null || proofUrl.isEmpty) && _proofImageFile != null) {
      setState(() => _isUploadingProof = true);
      try {
        proofUrl = await ref
            .read(proposalRepositoryProvider)
            .uploadProofImage(_proofImageFile!.path);
        _uploadedProofUrl = proofUrl;
      } catch (e) {
        if (mounted) {
          setState(() => _isUploadingProof = false);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Lỗi upload ảnh bằng chứng: $e'), backgroundColor: Colors.red),
          );
          return;
        }
      } finally {
        if (mounted) {
          setState(() => _isUploadingProof = false);
        }
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

                  // 2. Banner Trạng thái màu xanh lá (Shopee Style)
                  Container(
                    margin: const EdgeInsets.only(top: 8),
                    color: const Color(0xFF16A34A),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    child: Center(
                      child: Text(
                        isCompleted
                            ? 'Ca làm việc đã hoàn thành thành công'
                            : (isCheckedIn ? 'Đang trong ca làm việc' : 'Ban tổ chức đã xác nhận nhận ca'),
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
                            color: isDark ? cani.accent : const Color(0xFF0284C7),
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          isCompleted
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
                                  : '* Bước 1: Quét mã QR Check-in khi có mặt tại địa điểm sự kiện.'),
                          style: TextStyle(
                            fontSize: 11.8,
                            fontWeight: FontWeight.w600,
                            color: isCompleted ? const Color(0xFF059669) : const Color(0xFFEA580C),
                            height: 1.4,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 12),
                        _buildWorkflowStepper(
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

                  // 4. GPS Geofencing Warning (Cảnh báo khoảng cách)
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

                  // 5. Checklist Công việc Chi tiết (Module hóa gọn nhẹ + Mở rộng toàn màn hình)
                  KeyedSubtree(
                    key: _checklistSectionKey,
                    child: ShiftChecklistSection(
                      items: _checklistItems,
                      isLoading: _isLoadingChecklist,
                      isSaving: _isSavingChecklist,
                      errorMessage: _checklistError,
                      canInteract: isCheckedIn || isCompleted,
                      uploadingTaskIndex: _uploadingTaskIndex,
                      onRetryLoad: _loadChecklist,
                      onToggleItem: _toggleChecklistItem,
                      onCaptureEvidence: _captureEvidenceForTask,
                      onViewEvidence: _showEvidenceViewer,
                      onOpenFullscreen: _openFullscreenChecklist,
                    ),
                  ),

                  // 6. Bằng chứng nghiệm thu ca làm (Proof of Work Image)
                  Container(
                    key: _proofSectionKey,
                    margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF1E293B) : Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.04),
                          blurRadius: 10,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(Icons.camera_alt_outlined, size: 20, color: cani.brand),
                            const SizedBox(width: 8),
                            const Text(
                              'Bằng chứng nghiệm thu ca làm',
                              style: TextStyle(fontSize: 14.5, fontWeight: FontWeight.bold),
                            ),
                            const Spacer(),
                            if (_uploadedProofUrl != null || _proofImageFile != null)
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFDCFCE7),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: const Text(
                                  'Đã đính kèm ảnh',
                                  style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFF16A34A)),
                                ),
                              ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'Chụp ảnh hiện trường sau khi hoàn tất công việc (khu vực sạch sẽ, sản phẩm đã bàn giao, trang phục đúng chuẩn...) để nộp cho Ban tổ chức khi Check-out.',
                          style: TextStyle(fontSize: 12, color: cani.textMuted, height: 1.35),
                        ),
                        const SizedBox(height: 12),

                        // Preview Image hoặc Nút Chụp
                        if (_proofImageFile != null || _uploadedProofUrl != null)
                          Stack(
                            children: [
                              ClipRRect(
                                borderRadius: BorderRadius.circular(12),
                                child: _proofImageFile != null
                                    ? Image.file(
                                        _proofImageFile!,
                                        height: 170,
                                        width: double.infinity,
                                        fit: BoxFit.cover,
                                      )
                                    : buildResolvedImageWidget(
                                        _uploadedProofUrl!,
                                        height: 170,
                                        width: double.infinity,
                                        fit: BoxFit.cover,
                                        errorText: 'Không thể tải ảnh nghiệm thu',
                                      ),
                              ),
                              if (_isUploadingProof)
                                Container(
                                  height: 170,
                                  decoration: BoxDecoration(
                                    color: Colors.black54,
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                  child: const Center(
                                    child: Column(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        CircularProgressIndicator(color: Colors.white),
                                        SizedBox(height: 8),
                                        Text('Đang tải ảnh lên...', style: TextStyle(color: Colors.white, fontSize: 12)),
                                      ],
                                    ),
                                  ),
                                ),
                              Positioned(
                                top: 8,
                                right: 8,
                                child: Row(
                                  children: [
                                    InkWell(
                                      onTap: _pickProofImage,
                                      child: Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                        decoration: BoxDecoration(
                                          color: Colors.black87,
                                          borderRadius: BorderRadius.circular(8),
                                        ),
                                        child: const Row(
                                          children: [
                                            Icon(Icons.cached, size: 14, color: Colors.white),
                                            SizedBox(width: 4),
                                            Text('Đổi ảnh', style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold)),
                                          ],
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 6),
                                    InkWell(
                                      onTap: () {
                                        setState(() {
                                          _proofImageFile = null;
                                          _uploadedProofUrl = null;
                                        });
                                        if (_isCheckedIn || _isCompleted) {
                                          ref.read(proposalRepositoryProvider).submitAssignmentProof(_assignment.assignmentId, '');
                                        }
                                      },
                                      child: Container(
                                        padding: const EdgeInsets.all(6),
                                        decoration: const BoxDecoration(
                                          color: Colors.red,
                                          shape: BoxShape.circle,
                                        ),
                                        child: const Icon(Icons.close, size: 14, color: Colors.white),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          )
                        else
                          InkWell(
                            onTap: _pickProofImage,
                            borderRadius: BorderRadius.circular(12),
                            child: Container(
                              height: 110,
                              decoration: BoxDecoration(
                                color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(
                                  color: cani.brand.withValues(alpha: 0.35),
                                  style: BorderStyle.solid,
                                  width: 1.5,
                                ),
                              ),
                              child: Center(
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.all(8),
                                      decoration: BoxDecoration(
                                        color: cani.brand.withValues(alpha: 0.1),
                                        shape: BoxShape.circle,
                                      ),
                                      child: Icon(Icons.add_a_photo, color: cani.brand, size: 24),
                                    ),
                                    const SizedBox(height: 8),
                                    Text(
                                      'Chụp hoặc tải ảnh bằng chứng nghiệm thu',
                                      style: TextStyle(
                                        color: cani.brand,
                                        fontWeight: FontWeight.bold,
                                        fontSize: 12.5,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),

                  // 6b. Xác nhận Thanh toán Thù lao (Hard Gate trước Check-out)
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
                                MaterialPageRoute(builder: (_) => const MyDisputesScreen()),
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

          // 8. Bottom Sticky Action Bar (Theo đúng 5 bước vận hành)
          _buildBottomStickyBar(
            context: context,
            isDark: isDark,
            cani: cani,
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
    required bool isCheckedIn,
    required bool isCompleted,
    required bool isReviewed,
  }) {
    Color bgColor;
    IconData iconData;
    String label;

    if (isCompleted) {
      bgColor = isReviewed ? const Color(0xFF16A34A) : const Color(0xFF0D9488);
      iconData = isReviewed ? Icons.verified_rounded : Icons.star_rate_rounded;
      label = isReviewed
          ? 'ĐÃ HOÀN THÀNH & ĐÁNH GIÁ CA LÀM'
          : 'ĐÁNH GIÁ BAN TỔ CHỨC (RATING)';
    } else if (!isCheckedIn) {
      bgColor = cani.brand;
      iconData = Icons.qr_code_scanner_rounded;
      label = 'QUÉT QR CHECK-IN (VÀO CA)';
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
            onPressed: _handleBottomAction,
            style: ElevatedButton.styleFrom(
              backgroundColor: bgColor,
              foregroundColor: Colors.white,
              elevation: 3,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.white.withValues(alpha: 0.2),
                  ),
                  child: Icon(iconData, size: 15, color: Colors.white),
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
    required bool isCheckedIn,
    required bool isWorkReady,
    required bool isEoConfirmed,
    required bool isPaymentConfirmed,
    required bool isCompleted,
    required bool isReviewed,
  }) {
    final steps = [
      (label: 'Vào ca', done: isCheckedIn || isCompleted, active: !isCheckedIn && !isCompleted),
      (label: 'Việc & Ảnh', done: isWorkReady || isEoConfirmed || isCompleted, active: isCheckedIn && !isWorkReady && !isEoConfirmed),
      (label: 'BTC duyệt', done: isEoConfirmed || isCompleted, active: isCheckedIn && isWorkReady && !isEoConfirmed),
      (label: 'Thù lao', done: isPaymentConfirmed, active: isCheckedIn && isEoConfirmed && !isPaymentConfirmed),
      (label: 'Kết ca', done: isCompleted && isReviewed, active: (isCheckedIn && isPaymentConfirmed) || (isCompleted && !isReviewed)),
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
                    color: steps[i].done
                        ? const Color(0xFF16A34A)
                        : (steps[i].active ? const Color(0xFF0284C7) : const Color(0xFFE2E8F0)),
                  ),
                  child: Center(
                    child: steps[i].done
                        ? const Icon(Icons.check, size: 13, color: Colors.white)
                        : Text(
                            '${i + 1}',
                            style: TextStyle(
                              fontSize: 10.5,
                              fontWeight: FontWeight.bold,
                              color: steps[i].active ? Colors.white : const Color(0xFF64748B),
                            ),
                          ),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  steps[i].label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 10,
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
