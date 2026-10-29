import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/widgets/skill_hub_theme.dart';

class AiVerificationWizardScreen extends ConsumerStatefulWidget {
  const AiVerificationWizardScreen({super.key});

  @override
  ConsumerState<AiVerificationWizardScreen> createState() => _AiVerificationWizardScreenState();
}

class _AiVerificationWizardScreenState extends ConsumerState<AiVerificationWizardScreen> with SingleTickerProviderStateMixin {
  int _step = 1; // 1: Upload, 2: Scanning, 3: Result, 4: Celebration
  Uint8List? _fileBytes;
  String? _fileName;
  String? _selectedSkillId;
  final TextEditingController _notesController = TextEditingController();

  late AnimationController _scannerAnimController;
  late Animation<double> _laserAnim;

  @override
  void initState() {
    super.initState();
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

    final success = await ref.read(skillVerificationProvider.notifier).submitVerification(
      fileBytes: _fileBytes!,
      fileName: _fileName!,
      skillId: _selectedSkillId,
      notes: _notesController.text,
    );

    if (mounted) {
      if (success) {
        setState(() => _step = 3);
      } else {
        setState(() => _step = 1);
        final err = ref.read(skillVerificationProvider).errorMessage ?? 'Lỗi thẩm định';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể gửi tài liệu: $err'), backgroundColor: Colors.redAccent),
        );
      }
    }
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
          'Thẩm Định Chứng Chỉ Bằng AI',
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
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: SkillHubColors.surface,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: SkillHubColors.borderSoft, width: 2),
              boxShadow: const [BoxShadow(color: Color(0x0A03045E), blurRadius: 12)],
            ),
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
                  decoration: InputDecoration(
                    labelText: 'Kỹ năng mục tiêu',
                    labelStyle: const TextStyle(fontSize: 12),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  ),
                  items: [
                    const DropdownMenuItem(value: null, child: Text('AI tự động nhận diện từ tài liệu')),
                    ...state.taxonomy.map((s) => DropdownMenuItem(value: s.skillId, child: Text(s.name))),
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
            child: const Text('BẮT ĐẦU QUÉT & THẨM ĐỊNH', style: TextStyle(fontWeight: FontWeight.w800, color: Colors.white)),
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
              'AI đang quét và đối soát tính hợp lệ...',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: SkillHubColors.textPrimary),
            ),
          ],
        ),
        const SizedBox(height: 6),
        const Text(
          'Đang trích xuất: Đơn vị cấp, Số hiệu chứng chỉ, Đối chiếu danh mục',
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
    final result = ref.watch(skillVerificationProvider).lastResult;
    final isAutoApprove = result?.routingDecision == 'AUTO_APPROVE';

    return Column(
      children: [
        Expanded(
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: SkillHubColors.surface,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: isAutoApprove ? Colors.green : Colors.orange, width: 1.5),
            ),
            child: SingleChildScrollView(
              child: Column(
                children: [
                  Container(
                    width: 58,
                    height: 58,
                    decoration: BoxDecoration(
                      color: const Color(0xFFCAF0F8),
                      shape: BoxShape.circle,
                      border: Border.all(color: isAutoApprove ? Colors.green : Colors.orange, width: 2),
                    ),
                    child: Icon(
                      isAutoApprove ? Icons.check : Icons.access_time,
                      color: isAutoApprove ? Colors.green : Colors.orange,
                      size: 32,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    isAutoApprove ? 'Chứng chỉ hợp lệ' : 'Đang chờ quản trị viên duyệt',
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: SkillHubColors.textPrimary),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                    decoration: BoxDecoration(
                      color: const Color(0xFFE8F9FD),
                      borderRadius: BorderRadius.circular(99),
                      border: Border.all(color: SkillHubColors.ctaPrimary),
                    ),
                    child: Text(
                      '+${result?.spAwarded ?? 25} SP đã cộng',
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: SkillHubColors.ctaHover),
                    ),
                  ),
                  const SizedBox(height: 16),
                  _buildResultRow('Kỹ năng nhận diện:', result?.skillName ?? 'Nghiệp vụ sự kiện'),
                  _buildResultRow('Đơn vị cấp bằng:', result?.institution ?? 'Cơ sở đào tạo uy tín'),
                  _buildResultRow('Họ tên người nhận:', result?.recipientName ?? 'Khớp thông tin CCCD'),
                  _buildResultRow('Độ tin cậy AI:', '${((result?.overallConfidence ?? 0.9) * 100).toStringAsFixed(0)}%'),
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
            onPressed: () => setState(() => _step = 4),
            style: ElevatedButton.styleFrom(
              backgroundColor: SkillHubColors.ctaPrimary,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: const Text('TIẾP TỤC CẬP NHẬT HỒ SƠ', style: TextStyle(fontWeight: FontWeight.w800, color: Colors.white)),
          ),
        ),
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
          'NÂNG HẠNG THÀNH CÔNG!',
          style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: SkillHubColors.textPrimary),
        ),
        const SizedBox(height: 8),
        const Text(
          'Kỹ năng của bạn đã được chứng thực chính thức với thời hạn 24 tháng.',
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
              Text(
                'Đã kích hoạt ưu tiên ghép nối các ca sự kiện thù lao cao',
                style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: SkillHubColors.textPrimary),
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
