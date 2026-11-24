import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/widgets/skill_hub_theme.dart';

class AiVerificationWizardScreen extends ConsumerStatefulWidget {
  final String? initialVerificationId;
  final String? initialSkillId;

  const AiVerificationWizardScreen({
    super.key,
    this.initialVerificationId,
    this.initialSkillId,
  });

  @override
  ConsumerState<AiVerificationWizardScreen> createState() => _AiVerificationWizardScreenState();
}

class _AiVerificationWizardScreenState extends ConsumerState<AiVerificationWizardScreen> with SingleTickerProviderStateMixin {
  int _step = 1; // 1: Upload, 2: Scanning, 3: Result, 4: Celebration
  Uint8List? _fileBytes;
  String? _fileName;
  String? _selectedSkillId;
  String? _resubmitVerificationId;
  final TextEditingController _notesController = TextEditingController();

  late AnimationController _scannerAnimController;
  late Animation<double> _laserAnim;

  @override
  void initState() {
    super.initState();
    _resubmitVerificationId = widget.initialVerificationId;
    _selectedSkillId = widget.initialSkillId;
    _scannerAnimController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    )..repeat(reverse: true);
    _laserAnim = Tween<double>(begin: 0.15, end: 0.85).animate(
      CurvedAnimation(parent: _scannerAnimController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _scannerAnimController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _pickFile() async {
    try {
      final result = await FilePickerPlatform.instance.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'jpeg', 'png', 'pdf'],
      );
      if (result.isNotEmpty) {
        final file = result.first;
        final bytes = await file.readAsBytes();
        setState(() {
          _fileBytes = bytes;
          _fileName = file.name;
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to select file: $e')),
        );
      }
    }
  }

  Future<void> _startScanningAndSubmit() async {
    if (_fileBytes == null || _fileName == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng chọn ảnh chứng chỉ hoặc tệp PDF trước!')),
      );
      return;
    }

    setState(() => _step = 2);

    final notifier = ref.read(skillVerificationProvider.notifier);
    final bool success;
    if (_resubmitVerificationId != null && _resubmitVerificationId!.isNotEmpty) {
      success = await notifier.resubmitVerification(
        verificationId: _resubmitVerificationId!,
        fileBytes: _fileBytes!,
        fileName: _fileName!,
        skillId: _selectedSkillId,
        notes: _notesController.text,
      );
    } else {
      success = await notifier.submitVerification(
        fileBytes: _fileBytes!,
        fileName: _fileName!,
        skillId: _selectedSkillId,
        notes: _notesController.text,
      );
    }

    if (mounted) {
      if (success) {
        setState(() {
          _resubmitVerificationId = null;
          _step = 3;
        });
      } else {
        setState(() => _step = 1);
        final err = ref.read(skillVerificationProvider).errorMessage ?? 'Lỗi thẩm định';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể gửi tài liệu: $err'), backgroundColor: Colors.redAccent),
        );
      }
    }
  }

  Future<void> _retryFailedVerification(String verificationId) async {
    setState(() => _step = 2);
    final success = await ref.read(skillVerificationProvider.notifier).retryVerification(verificationId);
    if (!mounted) return;
    setState(() => _step = 3);
    if (!success) {
      final err = ref.read(skillVerificationProvider).errorMessage ?? 'Lỗi khi thử lại xác minh';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(err), backgroundColor: Colors.redAccent),
      );
    }
  }

  void _prepareResubmit(SkillVerificationResult result) {
    setState(() {
      _resubmitVerificationId = result.id;
      _selectedSkillId = result.skillId ?? _selectedSkillId;
      _fileBytes = null;
      _fileName = null;
      _step = 1;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: SkillHubColors.canvas,
      appBar: AppBar(
        backgroundColor: SkillHubColors.canvas,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: SkillHubColors.textPrimary),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: const Text(
          'Nộp Chứng Chỉ Xác Minh Kỹ Năng',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: SkillHubColors.textPrimary),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 16.0),
            child: Center(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                decoration: BoxDecoration(
                  color: SkillHubColors.surface,
                  borderRadius: BorderRadius.circular(99),
                  border: Border.all(color: SkillHubColors.borderSoft),
                ),
                child: Text(
                  'Bước $_step / 4',
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: SkillHubColors.textSecondary),
                ),
              ),
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: _step == 1
              ? _buildStep1Upload()
              : _step == 2
                  ? _buildStep2Scanner()
                  : _step == 3
                      ? _buildStep3Result()
                      : _buildStep4Celebration(),
        ),
      ),
    );
  }

  Widget _buildStep1Upload() {
    final state = ref.watch(skillVerificationProvider);

    return Column(
      children: [
        Expanded(
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: SkillHubColors.surface,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: SkillHubColors.borderSoft, width: 2),
              boxShadow: const [BoxShadow(color: Color(0x0A03045E), blurRadius: 12)],
            ),
            child: SingleChildScrollView(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                Container(
                  width: 64,
                  height: 64,
                  decoration: BoxDecoration(
                    color: const Color(0xFFCAF0F8),
                    shape: BoxShape.circle,
                    border: Border.all(color: SkillHubColors.ctaPrimary, width: 2),
                  ),
                  child: const Icon(Icons.camera_alt_outlined, color: SkillHubColors.ctaHover, size: 28),
                ),
                const SizedBox(height: 16),
                const Text(
                  'Tải lên chứng chỉ hoặc giấy chứng nhận nghề',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: SkillHubColors.textPrimary),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 6),
                const Text(
                  'Hỗ trợ: An toàn thực phẩm, Pha chế Bartender, Lễ tân - Bàn tiệc, Ngoại ngữ... (PNG, JPG, PDF - Tối đa 5MB)',
                  style: TextStyle(fontSize: 11.5, color: SkillHubColors.textSecondary, height: 1.4),
                  textAlign: TextAlign.center,
                ),
                if (_resubmitVerificationId != null) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFFBEB),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: const Color(0xFFFCD34D)),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.refresh_rounded, size: 16, color: Color(0xFFB45309)),
                        SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Đang nộp lại tệp mới để cập nhật đè vào hồ sơ trước đó.',
                            style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: Color(0xFF92400E)),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                const SizedBox(height: 20),
                if (_fileName != null)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    decoration: BoxDecoration(
                      color: const Color(0xFFE8F9FD),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: SkillHubColors.ctaPrimary),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.check_circle, color: Colors.green, size: 18),
                        const SizedBox(width: 8),
                        Flexible(
                          child: Text(
                            _fileName!,
                            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: SkillHubColors.textPrimary),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                const SizedBox(height: 20),
                DropdownButtonFormField<String>(
                  initialValue: _selectedSkillId,
                  isExpanded: true,
                  decoration: InputDecoration(
                    labelText: 'Kỹ năng mục tiêu',
                    labelStyle: const TextStyle(fontSize: 12),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  ),
                  items: [
                    const DropdownMenuItem(value: null, child: Text('Để trống để AI tự nhận diện kỹ năng', overflow: TextOverflow.ellipsis)),
                    ...state.taxonomy.map((s) => DropdownMenuItem(value: s.skillId, child: Text(s.name, overflow: TextOverflow.ellipsis))),
                  ],
                  onChanged: (val) => setState(() => _selectedSkillId = val),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  height: 44,
                  child: OutlinedButton.icon(
                    onPressed: _pickFile,
                    icon: const Icon(Icons.folder_open, size: 18),
                    label: Text(_fileName != null ? 'Chọn tệp khác' : 'Chọn ảnh hoặc PDF từ thiết bị'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: SkillHubColors.ctaHover,
                      side: const BorderSide(color: SkillHubColors.borderSoft, width: 1.5),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                  ),
                ),
              ],
            ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton(
            onPressed: _fileBytes != null ? _startScanningAndSubmit : null,
            style: ElevatedButton.styleFrom(
              backgroundColor: SkillHubColors.ctaPrimary,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: const Text('KIỂM TRA & XÁC MINH TỰ ĐỘNG', style: TextStyle(fontWeight: FontWeight.w800, color: Colors.white)),
          ),
        ),
      ],
    );
  }

  Widget _buildStep2Scanner() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          width: 220,
          height: 220,
          decoration: BoxDecoration(
            color: SkillHubColors.surfaceTint,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: SkillHubColors.borderSoft, width: 1.5),
          ),
          child: Stack(
            children: [
              _buildCorner(Alignment.topLeft, top: 10, left: 10),
              _buildCorner(Alignment.topRight, top: 10, right: 10),
              _buildCorner(Alignment.bottomLeft, bottom: 10, left: 10),
              _buildCorner(Alignment.bottomRight, bottom: 10, right: 10),
              AnimatedBuilder(
                animation: _laserAnim,
                builder: (context, child) {
                  return Positioned(
                    top: 220 * _laserAnim.value,
                    left: 20,
                    right: 20,
                    child: Container(
                      height: 3,
                      decoration: BoxDecoration(
                        color: SkillHubColors.ctaPrimary,
                        boxShadow: [
                          BoxShadow(
                            color: SkillHubColors.ctaPrimary.withValues(alpha: 0.8),
                            blurRadius: 10,
                            spreadRadius: 2,
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
              const Center(
                child: Icon(Icons.document_scanner_outlined, size: 64, color: SkillHubColors.borderSoft),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        const Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            SizedBox(
              width: 14,
              height: 14,
              child: CircularProgressIndicator(strokeWidth: 2, color: SkillHubColors.ctaPrimary),
            ),
            SizedBox(width: 10),
            Text(
              'Đang quét & phân tích chứng chỉ bằng AI...',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: SkillHubColors.textPrimary),
            ),
          ],
        ),
        const SizedBox(height: 6),
        const Text(
          'Hệ thống đang đọc nội dung, con dấu và đối chiếu thông tin trên chứng chỉ...',
          style: TextStyle(fontSize: 11, color: SkillHubColors.textSecondary),
        ),
      ],
    );
  }

  Widget _buildCorner(Alignment align, {double? top, double? bottom, double? left, double? right}) {
    return Positioned(
      top: top,
      bottom: bottom,
      left: left,
      right: right,
      child: Container(
        width: 20,
        height: 20,
        decoration: BoxDecoration(
          border: Border(
            top: top != null ? const BorderSide(color: SkillHubColors.ctaPrimary, width: 3) : BorderSide.none,
            bottom: bottom != null ? const BorderSide(color: SkillHubColors.ctaPrimary, width: 3) : BorderSide.none,
            left: left != null ? const BorderSide(color: SkillHubColors.ctaPrimary, width: 3) : BorderSide.none,
            right: right != null ? const BorderSide(color: SkillHubColors.ctaPrimary, width: 3) : BorderSide.none,
          ),
        ),
      ),
    );
  }

  Widget _buildStep3Result() {
    final skillState = ref.watch(skillVerificationProvider);
    final result = skillState.lastResult;
    final isApproved = result?.isApproved ?? false;
    final isTempFailure = result?.isTempFailure ?? false;
    final isUnsupported = result?.isUnsupportedIssuer ?? false;
    final isEkycRequired = result?.isEkycRequired ?? false;
    final isSelfDeclaredCv = result?.isSelfDeclaredCv ?? false;

    final accentColor = isApproved
        ? Colors.green
        : ((isTempFailure || isEkycRequired) ? const Color(0xFFD97706) : Colors.red);

    final String headingText;
    final String badgeText;
    final String statusSummary;
    final String primaryButtonLabel;
    final VoidCallback? primaryAction;

    if (isApproved) {
      headingText = 'Đã xác minh tự động thành công';
      badgeText = '+${result?.spAwarded ?? 0} SP đã cộng';
      statusSummary = 'Xác minh tự động hợp lệ';
      primaryButtonLabel = 'TIẾP TỤC CẬP NHẬT HỒ SƠ';
      primaryAction = () => setState(() => _step = 4);
    } else if (isTempFailure && result != null) {
      headingText = 'Nguồn tra cứu gặp lỗi tạm thời';
      badgeText = 'Cho phép thử lại · 0 SP';
      statusSummary = 'Lỗi nguồn tra cứu tạm thời — thử lại';
      primaryButtonLabel = 'THỬ LẠI';
      primaryAction = () => _retryFailedVerification(result.id);
    } else if (isUnsupported) {
      headingText = 'CANi chưa hỗ trợ tra cứu chứng chỉ của đơn vị này';
      badgeText = 'Chưa cộng điểm SP';
      statusSummary = 'Đơn vị cấp chưa được tích hợp tra cứu';
      primaryButtonLabel = 'QUAY VỀ KỸ NĂNG';
      primaryAction = () => Navigator.of(context).pop();
    } else if (isEkycRequired) {
      headingText = 'Cần hoàn tất xác thực eKYC';
      badgeText = 'Chưa cộng điểm SP';
      statusSummary = 'Chưa có danh tính eKYC để đối chiếu';
      primaryButtonLabel = 'XÁC THỰC EKYC NGAY';
      primaryAction = () {
        Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
        );
      };
    } else {
      headingText = isSelfDeclaredCv
          ? 'Tài liệu là CV, chưa phải chứng chỉ'
          : 'Chưa đạt xác minh tự động';
      badgeText = 'Chưa cộng điểm SP';
      statusSummary = isSelfDeclaredCv
          ? 'AI nhận diện tài liệu là CV tự khai'
          : 'Tệp hoặc thông tin chưa hợp lệ — nộp lại';
      primaryButtonLabel = 'NỘP LẠI';
      primaryAction = result != null
          ? () => _prepareResubmit(result)
          : () => setState(() => _step = 1);
    }

    final String detailMessage;
    if (isUnsupported) {
      detailMessage = (result?.routingReason != null && result!.routingReason.isNotEmpty)
          ? result.routingReason
          : 'CANi chưa hỗ trợ tra cứu chứng chỉ của đơn vị này.';
    } else {
      detailMessage = result?.routingReason ?? '';
    }

    return Column(
      children: [
        Expanded(
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: SkillHubColors.surface,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: accentColor, width: 1.5),
            ),
            child: SingleChildScrollView(
              child: Column(
                children: [
                  Container(
                    width: 58,
                    height: 58,
                    decoration: BoxDecoration(
                      color: isApproved
                          ? const Color(0xFFECFDF5)
                          : ((isTempFailure || isEkycRequired)
                              ? const Color(0xFFFFFBEB)
                              : const Color(0xFFFEF2F2)),
                      shape: BoxShape.circle,
                      border: Border.all(color: accentColor, width: 2),
                    ),
                    child: Icon(
                      isApproved
                          ? Icons.check_circle_outline
                          : (isTempFailure
                              ? Icons.sync_problem_rounded
                              : (isEkycRequired ? Icons.badge_outlined : Icons.info_outline_rounded)),
                      color: accentColor,
                      size: 32,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    headingText,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: isApproved
                          ? const Color(0xFF047857)
                          : ((isTempFailure || isEkycRequired)
                              ? const Color(0xFFB45309)
                              : const Color(0xFFB91C1C)),
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                    decoration: BoxDecoration(
                      color: isApproved
                          ? const Color(0xFFE8F9FD)
                          : ((isTempFailure || isEkycRequired)
                              ? const Color(0xFFFEF3C7)
                              : const Color(0xFFFEE2E2)),
                      borderRadius: BorderRadius.circular(99),
                      border: Border.all(
                        color: isApproved
                            ? SkillHubColors.ctaPrimary
                            : ((isTempFailure || isEkycRequired)
                                ? const Color(0xFFF59E0B)
                                : Colors.redAccent),
                      ),
                    ),
                    child: Text(
                      badgeText,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                        color: isApproved
                            ? SkillHubColors.ctaHover
                            : ((isTempFailure || isEkycRequired)
                                ? const Color(0xFF92400E)
                                : const Color(0xFFDC2626)),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (!isApproved && detailMessage.isNotEmpty) ...[
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      margin: const EdgeInsets.only(bottom: 14),
                      decoration: BoxDecoration(
                        color: (isTempFailure || isEkycRequired)
                            ? const Color(0xFFFFFBEB)
                            : const Color(0xFFFEF2F2),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: (isTempFailure || isEkycRequired)
                              ? const Color(0xFFFDE68A)
                              : const Color(0xFFFECACA),
                        ),
                      ),
                      child: Text(
                        detailMessage,
                        style: TextStyle(
                          fontSize: 12,
                          color: (isTempFailure || isEkycRequired)
                              ? const Color(0xFF92400E)
                              : const Color(0xFF991B1B),
                          height: 1.4,
                        ),
                      ),
                    ),
                  ],
                  _buildResultRow('Kỹ năng đăng ký:', result?.skillName ?? result?.jobType ?? 'Chưa xác định'),
                  _buildResultRow('Đơn vị cấp bằng:', result?.institution ?? 'Chưa có dữ liệu tra cứu'),
                  _buildResultRow('Họ tên người nhận:', result?.recipientName ?? 'Chưa đối chiếu được'),
                  if (result?.certificateNumber != null && result!.certificateNumber!.isNotEmpty)
                    _buildResultRow('Mã chứng chỉ:', result.certificateNumber!),
                  _buildResultRow('Kết quả xử lý:', statusSummary),
                  if (result != null && result.extractedSkills.isNotEmpty) ...[
                    const SizedBox(height: 6),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        'Kỹ năng AI đọc được từ tệp:',
                        style: const TextStyle(fontSize: 11.5, color: SkillHubColors.textSecondary),
                      ),
                    ),
                    const SizedBox(height: 6),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Wrap(
                        spacing: 6,
                        runSpacing: 6,
                        children: result.extractedSkills
                            .map((s) => Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: const Color(0xFFE8F9FD),
                                    borderRadius: BorderRadius.circular(99),
                                    border: Border.all(color: SkillHubColors.borderSoft),
                                  ),
                                  child: Text(
                                    s.skillName,
                                    style: const TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w600,
                                      color: SkillHubColors.textPrimary,
                                    ),
                                  ),
                                ))
                            .toList(),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton(
            onPressed: skillState.isSubmitting ? null : primaryAction,
            style: ElevatedButton.styleFrom(
              backgroundColor: isApproved
                  ? SkillHubColors.ctaPrimary
                  : (isUnsupported
                      ? SkillHubColors.textPrimary
                      : ((isTempFailure || isEkycRequired)
                          ? const Color(0xFFD97706)
                          : const Color(0xFFEF4444))),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: Text(
              primaryButtonLabel,
              style: const TextStyle(fontWeight: FontWeight.w800, color: Colors.white),
            ),
          ),
        ),
        if (isEkycRequired) ...[
          const SizedBox(height: 8),
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text(
              'Quay về kỹ năng',
              style: TextStyle(fontWeight: FontWeight.w700, color: SkillHubColors.textSecondary),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildResultRow(String k, String v) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(k, style: const TextStyle(fontSize: 11.5, color: SkillHubColors.textSecondary)),
          Flexible(
            child: Text(v, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: SkillHubColors.textPrimary)),
          ),
        ],
      ),
    );
  }

  Widget _buildStep4Celebration() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          width: 72,
          height: 72,
          decoration: BoxDecoration(
            color: const Color(0xFFE8F9FD),
            shape: BoxShape.circle,
            border: Border.all(color: SkillHubColors.ctaPrimary, width: 2),
          ),
          child: const Icon(Icons.verified, size: 38, color: SkillHubColors.ctaHover),
        ),
        const SizedBox(height: 16),
        const Text(
          'XÁC THỰC TÀI LIỆU HOÀN TẤT',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: SkillHubColors.textPrimary),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        const Text(
          'Tài liệu của bạn đã được hệ thống phê duyệt ở mức "Đã xác thực tài liệu" (hiệu lực tối đa 24 tháng).',
          style: TextStyle(fontSize: 12.5, color: SkillHubColors.textSecondary, height: 1.4),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          decoration: BoxDecoration(
            color: SkillHubColors.surface,
            borderRadius: BorderRadius.circular(99),
            border: Border.all(color: SkillHubColors.borderSoft),
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.workspace_premium, size: 18, color: Colors.amber),
              SizedBox(width: 8),
              Flexible(
                child: Text(
                  'Đã kích hoạt ưu tiên ghép nối các ca sự kiện thù lao cao',
                  style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: SkillHubColors.textPrimary),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 32),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton(
            onPressed: () => Navigator.of(context).pop(),
            style: ElevatedButton.styleFrom(
              backgroundColor: SkillHubColors.ctaPrimary,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: const Text('HOÀN TẤT & VỀ TRUNG TÂM KỸ NĂNG', style: TextStyle(fontWeight: FontWeight.w800, color: Colors.white)),
          ),
        ),
      ],
    );
  }
}
