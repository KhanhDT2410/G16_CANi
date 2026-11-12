import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:file_picker/file_picker.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/rate_eo_dialog.dart';
import 'attendance_qr_scanner_screen.dart';
import '../../ekyc/screens/card_scanner_screen.dart';
import '../../../core/constants/api_constants.dart';

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
  int? _uploadingTaskIndex;
  final ScrollController _scrollController = ScrollController();

  String _resolveImageUrl(String? path) {
    if (path == null || path.isEmpty) return '';
    if (path.startsWith('http://') || path.startsWith('https://')) return path;
    return '${ApiConstants.baseUrl}$path';
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
    try {
      final items = await ref
          .read(proposalRepositoryProvider)
          .fetchChecklist(_assignment.assignmentId);
      if (mounted) {
        setState(() {
          _checklistItems = items;
          _isLoadingChecklist = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoadingChecklist = false);
      }
    }
  }

  Future<void> _toggleChecklistItem(int index) async {
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
      _saveChecklistAsync();
      return;
    }

    // Nếu chưa hoàn thành -> Kiểm tra xem có bắt buộc nộp bằng chứng không
    if (current.isRequiredEvidence && (current.evidenceImageUrl == null || current.evidenceImageUrl!.isEmpty)) {
      final takePhoto = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: const Row(
            children: [
              Icon(Icons.camera_alt, color: Color(0xFF0066FF), size: 22),
              SizedBox(width: 8),
              Text('Yêu cầu ảnh nghiệm thu', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            ],
          ),
          content: Text(
            'Đầu việc "${current.title}" yêu cầu chụp ảnh bằng chứng nghiệm thu tại hiện trường.\n\nBạn có muốn chụp ảnh ngay bây giờ không?',
            style: const TextStyle(fontSize: 13.5, height: 1.4),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Đánh dấu tạm'),
            ),
            FilledButton.icon(
              onPressed: () => Navigator.pop(ctx, true),
              icon: const Icon(Icons.camera_alt, size: 16),
              label: const Text('Chụp ảnh ngay'),
              style: FilledButton.styleFrom(backgroundColor: const Color(0xFF0066FF)),
            ),
          ],
        ),
      );

      if (takePhoto == true) {
        await _captureEvidenceForTask(index);
        return;
      }
    }

    // Đánh dấu hoàn thành không kèm ảnh
    final updated = current.copyWith(
      isCompleted: true,
      completedAt: DateTime.now(),
    );

    setState(() {
      _checklistItems[index] = updated;
    });
    _saveChecklistAsync();
  }

  Future<void> _saveChecklistAsync() async {
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
                leading: const Icon(Icons.camera_alt_rounded, color: Color(0xFF0066FF)),
                title: const Text('Chụp ảnh trực tiếp bằng Camera', style: TextStyle(fontWeight: FontWeight.w600)),
                onTap: () => Navigator.of(ctx).pop('camera'),
              ),
              ListTile(
                leading: const Icon(Icons.photo_library_rounded, color: Color(0xFF0066FF)),
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
          builder: (_) => const CardScannerScreen(scanType: ScanType.cccdFront),
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
    final item = _checklistItems[index];
    try {
      final picked = await _chooseOrCaptureImage();

      if (picked != null && (picked.filePath != null || picked.bytes != null)) {
        setState(() => _uploadingTaskIndex = index);

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

        // Lưu tiến độ lên backend
        await _saveChecklistAsync();

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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Không thể tải ảnh nghiệm thu: $e'),
            backgroundColor: const Color(0xFFDC2626),
          ),
        );
      }
    }
  }

  void _showEvidenceViewer(String imageUrl, String title) {
    final fullUrl = _resolveImageUrl(imageUrl);
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
                  child: Image.network(
                    fullUrl,
                    fit: BoxFit.contain,
                    height: 350,
                    loadingBuilder: (ctx, child, progress) {
                      if (progress == null) return child;
                      return SizedBox(
                        height: 250,
                        child: Center(
                          child: CircularProgressIndicator(
                            value: progress.expectedTotalBytes != null
                                ? progress.cumulativeBytesLoaded / progress.expectedTotalBytes!
                                : null,
                          ),
                        ),
                      );
                    },
                    errorBuilder: (ctx, err, stack) => Container(
                      height: 200,
                      color: Colors.grey.shade200,
                      child: const Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.broken_image, size: 40, color: Colors.grey),
                            SizedBox(height: 8),
                            Text('Không thể tải ảnh bằng chứng', style: TextStyle(color: Colors.grey, fontSize: 12)),
                          ],
                        ),
                      ),
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

  Future<void> _addNewChecklistItem() async {
    final titleController = TextEditingController();
    final instructionController = TextEditingController();

    final result = await showDialog<Map<String, String>>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Thêm đầu việc mới', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: titleController,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Tên công việc *',
                hintText: 'Ví dụ: Sắp xếp bàn ghế khu VIP',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: instructionController,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Hướng dẫn thực hiện (tùy chọn)',
                hintText: 'Ví dụ: Kê 10 bàn tròn, 6 ghế/bàn ngay ngắn',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Hủy')),
          FilledButton(
            onPressed: () {
              if (titleController.text.trim().isNotEmpty) {
                Navigator.pop(ctx, {
                  'title': titleController.text.trim(),
                  'instruction': instructionController.text.trim(),
                });
              }
            },
            child: const Text('Thêm'),
          ),
        ],
      ),
    );

    if (result != null && result['title'] != null && result['title']!.isNotEmpty) {
      final newItem = ShiftChecklistItem(
        id: 'custom-${DateTime.now().millisecondsSinceEpoch}',
        title: result['title']!,
        instruction: result['instruction']!.isNotEmpty ? result['instruction'] : 'Thực hiện theo chỉ đạo của quản lý ca.',
        isCompleted: false,
        isRequiredEvidence: true,
      );
      setState(() {
        _checklistItems.add(newItem);
      });
      await _saveChecklistAsync();
    }
  }

  Future<void> _pickProofImage() async {
    try {
      final picked = await _chooseOrCaptureImage();

      if (picked != null && (picked.filePath != null || picked.bytes != null)) {
        if (picked.filePath != null) {
          setState(() {
            _proofImageFile = File(picked.filePath!);
          });
        }

        // Tự động upload ảnh lên máy chủ
        setState(() => _isUploadingProof = true);
        final repo = ref.read(proposalRepositoryProvider);
        final url = picked.bytes != null
            ? await repo.uploadProofBytes(picked.bytes!, picked.fileName ?? 'proof.jpg')
            : await repo.uploadProofImage(picked.filePath!);
        if (mounted) {
          setState(() {
            _uploadedProofUrl = url;
            _isUploadingProof = false;
          });
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Tải ảnh bằng chứng nghiệm thu thành công!'),
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
            backgroundColor: const Color(0xFFDC2626),
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

  Future<void> _callHotline(String phoneNumber) async {
    final telUri = Uri.parse('tel:$phoneNumber');
    try {
      await launchUrl(telUri, mode: LaunchMode.externalApplication);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể gọi đến số $phoneNumber')),
        );
      }
    }
  }

  Future<void> _handleBottomAction() async {
    final isCheckedIn = _assignment.status == 'CHECKED_IN' ||
        (_assignment.checkinTime != null && _assignment.checkoutTime == null);
    final isCompleted = _assignment.status == 'COMPLETED' || _assignment.checkoutTime != null;

    if (isCompleted) {
      // Đã xong ca -> Mở Đánh giá Ban tổ chức
      await showDialog(
        context: context,
        builder: (_) => RateEoDialog(assignment: _assignment),
      );
      return;
    }

    if (!isCheckedIn) {
      // Check-in
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
        _refreshAssignment();
      }
    } else {
      // Check-out: Kiểm tra xem còn task nào chưa hoàn thành hoặc thiếu ảnh không
      final incompleteTasks = _checklistItems.where((i) => !i.isCompleted).toList();
      final missingEvidenceTasks = _checklistItems.where((i) => i.isRequiredEvidence && (i.evidenceImageUrl == null || i.evidenceImageUrl!.isEmpty)).toList();

      if (incompleteTasks.isNotEmpty || missingEvidenceTasks.isNotEmpty) {
        final proceed = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            title: const Row(
              children: [
                Icon(Icons.warning_amber_rounded, color: Color(0xFFEAB308), size: 24),
                SizedBox(width: 8),
                Text('Chưa hoàn tất checklist', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
              ],
            ),
            content: Text(
              'Bạn còn ${incompleteTasks.length} đầu việc chưa xong (${missingEvidenceTasks.length} việc chưa nộp ảnh chụp nghiệm thu).\n\nBạn có chắc chắn muốn Check-out kết thúc ca làm ngay bây giờ không?',
              style: const TextStyle(fontSize: 13.5, height: 1.4),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('Xem lại việc'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(ctx, true),
                style: FilledButton.styleFrom(backgroundColor: const Color(0xFFDC2626)),
                child: const Text('Vẫn Check-out'),
              ),
            ],
          ),
        );

        if (proceed != true) return;
      }

      // Check-out: Tìm ảnh bằng chứng của task đầu tiên có ảnh làm proofUrl nếu _uploadedProofUrl null
      String? proofUrl = _uploadedProofUrl;
      if (proofUrl == null) {
        final firstWithPhoto = _checklistItems.firstWhere(
          (t) => t.evidenceImageUrl != null && t.evidenceImageUrl!.isNotEmpty,
          orElse: () => const ShiftChecklistItem(id: '', title: ''),
        );
        if (firstWithPhoto.evidenceImageUrl != null && firstWithPhoto.evidenceImageUrl!.isNotEmpty) {
          proofUrl = firstWithPhoto.evidenceImageUrl;
        }
      }

      if (proofUrl == null && _proofImageFile != null) {
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
        _refreshAssignment();
      }
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

    final isCheckedIn = _assignment.status == 'CHECKED_IN' ||
        (_assignment.checkinTime != null && _assignment.checkoutTime == null);
    final isCompleted = _assignment.status == 'COMPLETED' || _assignment.checkoutTime != null;

    final completedCount = _checklistItems.where((i) => i.isCompleted).length;
    final totalCount = _checklistItems.length;
    final progress = totalCount > 0 ? completedCount / totalCount : 0.0;

    const hotline = '0988123456';

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: isDark ? const Color(0xFF1E293B) : Colors.white,
        elevation: 0.5,
        leading: IconButton(
          icon: Icon(Icons.arrow_back_ios_new, size: 20, color: isDark ? Colors.white : Colors.black87),
          onPressed: () => Navigator.pop(context, true),
        ),
        title: Column(
          children: [
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Sự kiện · ',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                  ),
                ),
                InkWell(
                  onTap: () => _callHotline(hotline),
                  child: Text(
                    'BTC $hotline',
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFFEF4444),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.help_outline, color: Color(0xFF0066FF)),
            onPressed: () {
              showDialog(
                context: context,
                builder: (ctx) => AlertDialog(
                  title: const Text('Quy trình làm việc'),
                  content: const Text(
                    '1. Có mặt tại địa điểm trước 15 phút.\n'
                    '2. Quét mã QR Check-in do Ban tổ chức cung cấp để vào ca.\n'
                    '3. Hoàn thành và tích chọn các đầu việc trong Checklist.\n'
                    '4. Chụp ảnh bằng chứng nghiệm thu kết quả công việc.\n'
                    '5. Quét mã QR Check-out để kết thúc ca và nhận xác nhận thù lao.',
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
            child: SingleChildScrollView(
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
                                  color: const Color(0xFF0066FF).withValues(alpha: 0.1),
                                  border: Border.all(color: const Color(0xFF0066FF).withValues(alpha: 0.2)),
                                ),
                                child: const Icon(Icons.directions, color: Color(0xFF0066FF), size: 22),
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
                              'Thù lao: ${_assignment.hourlyWage.toInt()} đ/giờ',
                              style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: Color(0xFF0D9488)),
                            ),
                            Text(
                              'Tổng ước tính: ${_assignment.totalPay.toInt()} đ',
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

                  // 3. Countdown & Note
                  Container(
                    color: isDark ? const Color(0xFF1E293B) : Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    child: Column(
                      children: [
                        Text(
                          _countdownText,
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                            color: isDark ? const Color(0xFF38BDF8) : const Color(0xFF0284C7),
                          ),
                        ),
                        const SizedBox(height: 4),
                        const Text(
                          '* Lưu ý: Tích chọn hoàn thành checklist và chụp ảnh bằng chứng nghiệm thu trước khi quét QR kết ca.',
                          style: TextStyle(fontSize: 11.5, color: Color(0xFFEA580C), height: 1.4),
                          textAlign: TextAlign.center,
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
                              ? const Color(0xFFDC2626)
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
                              foregroundColor: const Color(0xFF0066FF),
                              padding: const EdgeInsets.symmetric(horizontal: 8),
                            ),
                            child: const Text('Chỉ đường', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                          ),
                      ],
                    ),
                  ),

                  // 5. Checklist Công việc Chi tiết (Shopee style món ăn)
                  Container(
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
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              'Chi tiết công việc',
                              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                            ),
                            Text(
                              'Số lượng: $totalCount',
                              style: const TextStyle(fontSize: 13, color: Color(0xFF64748B)),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              '#Tên đầu việc',
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF94A3B8)),
                            ),
                            Row(
                              children: [
                                if (_isSavingChecklist)
                                  const Padding(
                                    padding: EdgeInsets.only(right: 6),
                                    child: SizedBox(
                                      width: 12,
                                      height: 12,
                                      child: CircularProgressIndicator(strokeWidth: 2),
                                    ),
                                  ),
                                Text(
                                  '$completedCount / $totalCount Hoàn thành',
                                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF0D9488)),
                                ),
                              ],
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: LinearProgressIndicator(
                            value: progress,
                            minHeight: 6,
                            backgroundColor: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                            valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF0D9488)),
                          ),
                        ),
                        const SizedBox(height: 14),

                        if (_isLoadingChecklist)
                          const Center(
                            child: Padding(
                              padding: EdgeInsets.all(16.0),
                              child: CircularProgressIndicator(),
                            ),
                          )
                        else if (_checklistItems.isEmpty)
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF1F5F9),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: const Center(
                              child: Text(
                                'Chưa có đầu việc cụ thể từ BTC. Bạn có thể tự thêm việc cần làm bên dưới.',
                                style: TextStyle(fontSize: 12.5, color: Color(0xFF64748B)),
                                textAlign: TextAlign.center,
                              ),
                            ),
                          )
                        else
                          ListView.builder(
                            shrinkWrap: true,
                            physics: const NeverScrollableScrollPhysics(),
                            itemCount: _checklistItems.length,
                            itemBuilder: (ctx, idx) {
                              final item = _checklistItems[idx];
                              final hasEvidence = item.evidenceImageUrl != null && item.evidenceImageUrl!.isNotEmpty;
                              final isCurrentUploading = _uploadingTaskIndex == idx;

                              return Container(
                                margin: const EdgeInsets.only(bottom: 12),
                                decoration: BoxDecoration(
                                  color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(
                                    color: item.isCompleted
                                        ? const Color(0xFF86EFAC)
                                        : (isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0)),
                                    width: item.isCompleted ? 1.5 : 1,
                                  ),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    // Phần đầu Task: Checkbox + Tiêu đề + Trạng thái
                                    Padding(
                                      padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
                                      child: Row(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          InkWell(
                                            onTap: () => _toggleChecklistItem(idx),
                                            borderRadius: BorderRadius.circular(6),
                                            child: Container(
                                              width: 24,
                                              height: 24,
                                              margin: const EdgeInsets.only(top: 1, right: 10),
                                              decoration: BoxDecoration(
                                                color: item.isCompleted ? const Color(0xFF16A34A) : Colors.transparent,
                                                borderRadius: BorderRadius.circular(6),
                                                border: Border.all(
                                                  color: item.isCompleted ? const Color(0xFF16A34A) : const Color(0xFF94A3B8),
                                                  width: 2,
                                                ),
                                              ),
                                              child: item.isCompleted
                                                  ? const Icon(Icons.check, size: 16, color: Colors.white)
                                                  : null,
                                            ),
                                          ),
                                          Expanded(
                                            child: Column(
                                              crossAxisAlignment: CrossAxisAlignment.start,
                                              children: [
                                                Text(
                                                  '${(idx + 1).toString().padLeft(2, '0')}. ${item.title}',
                                                  style: TextStyle(
                                                    fontSize: 14,
                                                    fontWeight: FontWeight.w700,
                                                    decoration: item.isCompleted ? TextDecoration.lineThrough : null,
                                                    color: item.isCompleted
                                                        ? const Color(0xFF64748B)
                                                        : (isDark ? Colors.white : const Color(0xFF0F172A)),
                                                  ),
                                                ),
                                                if (item.instruction != null && item.instruction!.isNotEmpty) ...[
                                                  const SizedBox(height: 5),
                                                  Container(
                                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                                    decoration: BoxDecoration(
                                                      color: isDark ? const Color(0xFF1E293B) : const Color(0xFFEEF2F6),
                                                      borderRadius: BorderRadius.circular(6),
                                                    ),
                                                    child: Row(
                                                      crossAxisAlignment: CrossAxisAlignment.start,
                                                      children: [
                                                        const Icon(Icons.info_outline, size: 13, color: Color(0xFF0284C7)),
                                                        const SizedBox(width: 5),
                                                        Expanded(
                                                          child: Text(
                                                            item.instruction!,
                                                            style: TextStyle(
                                                              fontSize: 11.5,
                                                              color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569),
                                                              height: 1.3,
                                                            ),
                                                          ),
                                                        ),
                                                      ],
                                                    ),
                                                  ),
                                                ],
                                              ],
                                            ),
                                          ),
                                          const SizedBox(width: 8),
                                          Container(
                                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                            decoration: BoxDecoration(
                                              color: item.isCompleted ? const Color(0xFFDCFCE7) : const Color(0xFFFEF3C7),
                                              borderRadius: BorderRadius.circular(6),
                                            ),
                                            child: Text(
                                              item.isCompleted ? '✓ Đã xong' : 'Chưa xong',
                                              style: TextStyle(
                                                fontSize: 10.5,
                                                fontWeight: FontWeight.bold,
                                                color: item.isCompleted ? const Color(0xFF16A34A) : const Color(0xFFD97706),
                                              ),
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),

                                    const Divider(height: 1),

                                    // Phần Nghiệm thu ảnh (Evidence) của Task
                                    Padding(
                                      padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
                                      child: Row(
                                        children: [
                                          if (hasEvidence) ...[
                                            GestureDetector(
                                              onTap: () => _showEvidenceViewer(item.evidenceImageUrl!, item.title),
                                              child: ClipRRect(
                                                borderRadius: BorderRadius.circular(8),
                                                child: Stack(
                                                  children: [
                                                    Image.network(
                                                      _resolveImageUrl(item.evidenceImageUrl!),
                                                      width: 46,
                                                      height: 46,
                                                      fit: BoxFit.cover,
                                                      errorBuilder: (c, e, s) => Container(
                                                        width: 46,
                                                        height: 46,
                                                        color: Colors.grey.shade300,
                                                        child: const Icon(Icons.broken_image, size: 20),
                                                      ),
                                                    ),
                                                    Positioned(
                                                      bottom: 0,
                                                      right: 0,
                                                      child: Container(
                                                        padding: const EdgeInsets.all(2),
                                                        decoration: const BoxDecoration(
                                                          color: Colors.black54,
                                                          borderRadius: BorderRadius.only(topLeft: Radius.circular(4)),
                                                        ),
                                                        child: const Icon(Icons.zoom_in, size: 12, color: Colors.white),
                                                      ),
                                                    ),
                                                  ],
                                                ),
                                              ),
                                            ),
                                            const SizedBox(width: 10),
                                            Expanded(
                                              child: Column(
                                                crossAxisAlignment: CrossAxisAlignment.start,
                                                children: [
                                                  const Row(
                                                    children: [
                                                      Icon(Icons.check_circle, size: 14, color: Color(0xFF16A34A)),
                                                      SizedBox(width: 4),
                                                      Text(
                                                        'Ảnh nghiệm thu đã nộp',
                                                        style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: Color(0xFF16A34A)),
                                                      ),
                                                    ],
                                                  ),
                                                  if (item.evidenceSubmittedAt != null)
                                                    Text(
                                                      'Lúc ${item.evidenceSubmittedAt!.hour.toString().padLeft(2, '0')}:${item.evidenceSubmittedAt!.minute.toString().padLeft(2, '0')}',
                                                      style: const TextStyle(fontSize: 10.5, color: Color(0xFF94A3B8)),
                                                    ),
                                                ],
                                              ),
                                            ),
                                            TextButton.icon(
                                              onPressed: () => _captureEvidenceForTask(idx),
                                              icon: const Icon(Icons.refresh, size: 14),
                                              label: const Text('Chụp lại', style: TextStyle(fontSize: 11)),
                                              style: TextButton.styleFrom(
                                                visualDensity: VisualDensity.compact,
                                                padding: const EdgeInsets.symmetric(horizontal: 8),
                                              ),
                                            ),
                                          ] else if (isCurrentUploading) ...[
                                            const SizedBox(
                                              width: 16,
                                              height: 16,
                                              child: CircularProgressIndicator(strokeWidth: 2),
                                            ),
                                            const SizedBox(width: 8),
                                            const Text(
                                              'Đang tải ảnh nghiệm thu...',
                                              style: TextStyle(fontSize: 12, color: Color(0xFF0284C7), fontWeight: FontWeight.w600),
                                            ),
                                          ] else ...[
                                            const Icon(Icons.camera_alt_outlined, size: 16, color: Color(0xFF0284C7)),
                                            const SizedBox(width: 6),
                                            const Expanded(
                                              child: Text(
                                                'Cần ảnh chụp nghiệm thu',
                                                style: TextStyle(fontSize: 11.5, color: Color(0xFF64748B), fontStyle: FontStyle.italic),
                                              ),
                                            ),
                                            ElevatedButton.icon(
                                              onPressed: () => _captureEvidenceForTask(idx),
                                              icon: const Icon(Icons.camera_alt, size: 14, color: Colors.white),
                                              label: const Text(
                                                'Chụp ảnh',
                                                style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: Colors.white),
                                              ),
                                              style: ElevatedButton.styleFrom(
                                                backgroundColor: const Color(0xFF0066FF),
                                                visualDensity: VisualDensity.compact,
                                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                                              ),
                                            ),
                                          ],
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                              );
                            },
                          ),

                        const SizedBox(height: 12),
                        Center(
                          child: TextButton.icon(
                            onPressed: _addNewChecklistItem,
                            icon: const Icon(Icons.add, size: 18),
                            label: const Text('Thêm đầu việc mới', style: TextStyle(fontWeight: FontWeight.bold)),
                          ),
                        ),
                      ],
                    ),
                  ),

                  // 6. Bằng chứng nghiệm thu ca làm (Proof of Work Image)
                  Container(
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
                            const Icon(Icons.camera_alt_outlined, size: 20, color: Color(0xFF0066FF)),
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
                        const Text(
                          'Chụp ảnh hiện trường sau khi hoàn tất công việc (khu vực sạch sẽ, sản phẩm đã bàn giao, trang phục đúng chuẩn...) để nộp cho Ban tổ chức khi Check-out.',
                          style: TextStyle(fontSize: 12, color: Color(0xFF64748B), height: 1.35),
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
                                    : Image.network(
                                        _resolveImageUrl(_uploadedProofUrl!),
                                        height: 170,
                                        width: double.infinity,
                                        fit: BoxFit.cover,
                                        errorBuilder: (ctx, err, stack) => Container(
                                          height: 170,
                                          color: Colors.grey.shade200,
                                          child: const Center(child: Icon(Icons.broken_image, size: 40)),
                                        ),
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
                                  color: const Color(0xFF0066FF).withValues(alpha: 0.35),
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
                                        color: const Color(0xFF0066FF).withValues(alpha: 0.1),
                                        shape: BoxShape.circle,
                                      ),
                                      child: const Icon(Icons.add_a_photo, color: Color(0xFF0066FF), size: 24),
                                    ),
                                    const SizedBox(height: 8),
                                    const Text(
                                      'Chụp hoặc tải ảnh bằng chứng nghiệm thu',
                                      style: TextStyle(
                                        color: Color(0xFF0066FF),
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

                  // 7. Quick Actions Row (Gọi điện, Chat, Chỉ đường)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        _buildQuickAction(
                          icon: Icons.phone_in_talk,
                          label: 'Điện thoại',
                          onTap: () => _callHotline(hotline),
                        ),
                        _buildQuickAction(
                          icon: Icons.chat_outlined,
                          label: 'Chat CSKH',
                          onTap: () {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Đang kết nối trung tâm trợ giúp CSKH CANi...')),
                            );
                          },
                        ),
                        _buildQuickAction(
                          icon: Icons.map_outlined,
                          label: 'Bản đồ',
                          onTap: _openGoogleMapsNavigation,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),

          // 8. Bottom Sticky Action Bar (ShopeeFood Driver Style)
          Container(
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
                    backgroundColor: isCompleted
                        ? const Color(0xFF0D9488)
                        : (isCheckedIn ? const Color(0xFFEA580C) : const Color(0xFF0066FF)),
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
                        child: const Icon(Icons.arrow_forward_ios, size: 14, color: Colors.white),
                      ),
                      const SizedBox(width: 10),
                      Text(
                        isCompleted
                            ? 'ĐÃ HOÀN THÀNH CA (ĐÁNH GIÁ BTC)'
                            : (isCheckedIn
                                ? 'QUÉT QR CHECK-OUT (NỘP NGHIỆM THU)'
                                : 'QUÉT QR CHECK-IN (VÀO CA)'),
                        style: const TextStyle(
                          fontSize: 14.5,
                          fontWeight: FontWeight.w900,
                          letterSpacing: 0.5,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
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
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 24, color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569)),
            const SizedBox(height: 4),
            Text(
              label,
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
