import 'package:cani/app/cani_tokens.dart';
import 'package:cani/features/profile/providers/worker_bank_provider.dart';
import 'package:cani/features/profile/screens/worker_bank_settings_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/shift_chat_sheet.dart';
import '../widgets/shift_checklist_section.dart';
import '../widgets/shift_workflow_stepper.dart';
import 'attendance_qr_scanner_screen.dart';
import 'my_disputes_screen.dart';
import 'rate_eo_screen.dart';

/// Bước 4: Trang Thanh toán thù lao tách riêng.
/// Sau khi có bằng chứng từ BTC và 2 bên xác nhận xong -> chuyển sang trang Check-out (Bước 5) -> Rating (Bước 6).
class ShiftPaymentScreen extends ConsumerStatefulWidget {
  final ShiftAssignmentItem assignment;
  final String? fallbackProofImageUrl;

  const ShiftPaymentScreen({
    super.key,
    required this.assignment,
    this.fallbackProofImageUrl,
  });

  @override
  ConsumerState<ShiftPaymentScreen> createState() => _ShiftPaymentScreenState();
}

class _ShiftPaymentScreenState extends ConsumerState<ShiftPaymentScreen> {
  late ShiftAssignmentItem _assignment;
  bool _isConfirming = false;
  bool _isRefreshing = false;

  @override
  void initState() {
    super.initState();
    _assignment = widget.assignment;
    _refreshAssignment(silent: true);
  }

  bool get _isCompleted => _assignment.checkoutTime != null || _assignment.status == 'COMPLETED';

  bool get _isPaymentConfirmed => isAssignmentPaymentConfirmed(_assignment);

  bool get _isEoDeclared =>
      _assignment.paymentStatus != 'NOT_DECLARED' || _assignment.declaredAmount > 0;

  bool get _hasEvidence =>
      _assignment.paymentProofUrl != null && _assignment.paymentProofUrl!.trim().isNotEmpty;

  Future<void> _refreshAssignment({bool silent = false}) async {
    if (!silent && mounted) {
      setState(() => _isRefreshing = true);
    }
    try {
      ref.invalidate(assignmentsListProvider);
      final list = await ref.read(assignmentsListProvider.future);
      final idx = list.indexWhere((a) => a.assignmentId == _assignment.assignmentId);
      if (mounted) {
        setState(() {
          if (idx != -1) {
            _assignment = list[idx];
          }
          _isRefreshing = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isRefreshing = false);
      }
    }
  }

  String _paymentStatusLabel(String status) {
    if (_isPaymentConfirmed) {
      if (status == 'CONFIRMED_PARTIAL' || _assignment.workerConfirmStatus == 'RECEIVED_PARTIAL') {
        return 'Nhận một phần';
      }
      if (status == 'DISPUTED' || _assignment.workerConfirmStatus == 'NOT_RECEIVED') {
        return 'Đang khiếu nại';
      }
      return '2 bên đã xác nhận';
    }
    return switch (status) {
      'DECLARED_PENDING_WORKER' => 'BTC đã khai báo · Chờ bạn xác nhận',
      'NOT_DECLARED' => _assignment.eoConfirmed ? 'Chờ BTC thanh toán' : 'Chưa duyệt công (Bước 3)',
      'CONFIRMED_FULL' => 'Đã nhận đủ',
      'CONFIRMED_PARTIAL' => 'Nhận một phần',
      'DISPUTED' => 'Đang tranh chấp',
      _ => status,
    };
  }

  Future<void> _handleConfirmPayment(String status) async {
    final cani = Theme.of(context).cani;
    if (!_assignment.eoConfirmed && !_isCompleted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Ban tổ chức cần Duyệt công (Bước 3) trước khi xác nhận thanh toán.'),
          backgroundColor: cani.warning,
        ),
      );
      return;
    }

    String comment = 'Đã nhận đủ thù lao theo thỏa thuận.';
    if (status == 'RECEIVED_PARTIAL' || status == 'NOT_RECEIVED') {
      final dialogResult = await showDialog<String>(
        context: context,
        builder: (_) => _PaymentIssueDialog(isPartial: status == 'RECEIVED_PARTIAL'),
      );
      if (dialogResult == null || dialogResult.isEmpty) return;
      comment = dialogResult;
    }

    try {
      setState(() => _isConfirming = true);
      await ref.read(proposalRepositoryProvider).confirmPayment(
            _assignment.assignmentId,
            status,
            comment,
          );
      await _refreshAssignment(silent: true);
      ref.invalidate(assignmentsListProvider);
      ref.invalidate(incomeSummaryProvider);
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            status == 'NOT_RECEIVED'
                ? 'Đã ghi nhận phản hồi chưa nhận tiền — bạn có thể tiếp tục Check-out kết ca.'
                : (status == 'RECEIVED_PARTIAL'
                    ? 'Đã ghi nhận phản hồi nhận một phần — bạn có thể tiếp tục Check-out kết ca.'
                    : 'Hai bên đã xác nhận thanh toán! Chuyển sang trang Check-out.'),
          ),
          backgroundColor: status == 'NOT_RECEIVED' ? cani.warning : cani.success,
        ),
      );

      if (!_isCompleted) {
        _goToCheckoutScreen();
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
        setState(() => _isConfirming = false);
      }
    }
  }

  void _goToCheckoutScreen() {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (_) => AttendanceQrScannerScreen(
          assignment: _assignment,
          isCheckin: false,
          proofImageUrl: widget.fallbackProofImageUrl ?? _assignment.proofImageUrl,
        ),
      ),
    );
  }

  void _goToRatingScreen() {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (_) => RateEoScreen(assignment: _assignment),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final liveList = ref.watch(assignmentsListProvider).valueOrNull;
    if (liveList != null) {
      final liveIdx = liveList.indexWhere((item) => item.assignmentId == _assignment.assignmentId);
      if (liveIdx != -1) {
        _assignment = liveList[liveIdx];
      }
    }

    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final a = _assignment;
    final dateStr =
        '${a.startTime.day.toString().padLeft(2, '0')}/${a.startTime.month.toString().padLeft(2, '0')}/${a.startTime.year}';
    final timeStr =
        '${a.startTime.hour.toString().padLeft(2, '0')}:${a.startTime.minute.toString().padLeft(2, '0')} - ${a.endTime.hour.toString().padLeft(2, '0')}:${a.endTime.minute.toString().padLeft(2, '0')}';

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
          'Thanh toán thù lao · Bước 4/6',
          style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: cani.textMain),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            onPressed: _isRefreshing ? null : () => _refreshAssignment(),
            tooltip: 'Làm mới trạng thái thanh toán',
            icon: _isRefreshing
                ? SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2, color: cani.brand),
                  )
                : Icon(Icons.refresh_rounded, color: cani.brand),
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: RefreshIndicator(
              color: cani.brand,
              onRefresh: () => _refreshAssignment(),
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // 1. Stepper tiến độ quy trình
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: cani.surface,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: cani.border),
                      ),
                      child: Column(
                        children: [
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            decoration: BoxDecoration(
                              color: _isPaymentConfirmed ? cani.successBg : cani.brandSoft,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Text(
                              _isPaymentConfirmed
                                  ? (_isCompleted
                                      ? 'Hai bên đã hoàn tất đối soát thù lao cho ca làm này.'
                                      : 'Đã xác nhận thanh toán! Bấm tiếp tục để sang trang Check-out (Bước 5).')
                                  : (_isEoDeclared
                                      ? 'Kiểm tra số tiền & chứng từ từ BTC, sau đó xác nhận để sang Check-out.'
                                      : 'Chờ BTC khai báo thanh toán kèm chứng từ trước khi bạn xác nhận.'),
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: _isPaymentConfirmed ? cani.success : cani.brand,
                              ),
                            ),
                          ),
                          const SizedBox(height: 12),
                          ShiftWorkflowStepper(
                            isExpired: false,
                            isCheckedIn: true,
                            isWorkReady: true,
                            isEoConfirmed: a.eoConfirmed || _isCompleted,
                            isPaymentConfirmed: _isPaymentConfirmed,
                            isCompleted: _isCompleted,
                            isReviewed: a.hasWorkerReviewed == true,
                            forcedCurrentStep: 4,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),

                    // 2. Chi tiết thù lao & bằng chứng đối soát 2 bên
                    Container(
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
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(7),
                                decoration: BoxDecoration(
                                  color: cani.brandSoft,
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: Icon(Icons.payments_outlined, size: 18, color: cani.brand),
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      '${a.jobType} · ${a.eventName}',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(fontSize: 14.5, fontWeight: FontWeight.w800, color: cani.textMain),
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      '$timeStr · $dateStr',
                                      style: TextStyle(fontSize: 11.5, color: cani.textMuted, fontWeight: FontWeight.w600),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),

                          // Pill trạng thái đối soát
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                            decoration: BoxDecoration(
                              color: _isPaymentConfirmed
                                  ? cani.successBg
                                  : (_isEoDeclared ? cani.warningBg : cani.brandSoft),
                              borderRadius: BorderRadius.circular(999),
                            ),
                            child: Text(
                              _paymentStatusLabel(a.paymentStatus),
                              style: TextStyle(
                                fontSize: 11.5,
                                fontWeight: FontWeight.w700,
                                color: _isPaymentConfirmed
                                    ? cani.success
                                    : (_isEoDeclared ? cani.warning : cani.brand),
                              ),
                            ),
                          ),
                          const SizedBox(height: 12),

                          // Số tiền thỏa thuận vs BTC khai báo
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: cani.canvas,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: cani.border),
                            ),
                            child: Column(
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text('Thù lao thỏa thuận', style: TextStyle(fontSize: 12.5, color: cani.textMuted)),
                                    Text(
                                      formatVndCurrency(a.totalPay),
                                      style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 6),
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text('BTC khai báo thanh toán', style: TextStyle(fontSize: 12.5, color: cani.textMuted)),
                                    Text(
                                      a.declaredAmount > 0 ? formatVndCurrency(a.declaredAmount) : 'Chưa khai báo',
                                      style: TextStyle(
                                        fontSize: 14,
                                        fontWeight: FontWeight.w900,
                                        color: a.declaredAmount > 0 ? cani.success : cani.textMuted,
                                      ),
                                    ),
                                  ],
                                ),
                                if (a.paymentMethod != null && a.paymentMethod!.isNotEmpty) ...[
                                  const SizedBox(height: 6),
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text('Hình thức thanh toán', style: TextStyle(fontSize: 12.5, color: cani.textMuted)),
                                      Text(
                                        a.paymentMethod == 'BANK_TRANSFER' ? 'Chuyển khoản ngân hàng' : 'Tiền mặt trực tiếp',
                                        style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: cani.textMain),
                                      ),
                                    ],
                                  ),
                                ],
                                if (a.externalRef != null && a.externalRef!.trim().isNotEmpty) ...[
                                  const SizedBox(height: 6),
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text('Mã giao dịch', style: TextStyle(fontSize: 12.5, color: cani.textMuted)),
                                      Flexible(
                                        child: Text(
                                          a.externalRef!.trim(),
                                          style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: cani.textMain),
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                    ],
                                  ),
                                ],
                                if (a.declaredAt != null) ...[
                                  const SizedBox(height: 6),
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text('Thời điểm khai báo', style: TextStyle(fontSize: 12.5, color: cani.textMuted)),
                                      Text(
                                        '${a.declaredAt!.hour.toString().padLeft(2, '0')}:${a.declaredAt!.minute.toString().padLeft(2, '0')} · ${a.declaredAt!.day.toString().padLeft(2, '0')}/${a.declaredAt!.month.toString().padLeft(2, '0')}',
                                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: cani.textMain),
                                      ),
                                    ],
                                  ),
                                ],
                                if (a.workerComment != null && a.workerComment!.trim().isNotEmpty) ...[
                                  const SizedBox(height: 6),
                                  Row(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text('Phản hồi của bạn', style: TextStyle(fontSize: 12.5, color: cani.textMuted)),
                                      const SizedBox(width: 12),
                                      Flexible(
                                        child: Text(
                                          a.workerComment!.trim(),
                                          textAlign: TextAlign.right,
                                          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: cani.textMain),
                                        ),
                                      ),
                                    ],
                                  ),
                                ],
                              ],
                            ),
                          ),
                          const SizedBox(height: 12),

                          // Khối thông tin nhận tiền: Chuyển khoản (VietQR Napas 247) hoặc Tiền mặt trực tiếp
                          Builder(builder: (ctx) {
                            final savedBank = ref.watch(workerBankProvider).bankAccount;
                            final rawBankCode = (a.bankCode != null && a.bankCode!.trim().isNotEmpty)
                                ? a.bankCode!
                                : (savedBank?.bankCode ?? '');
                            final rawAccountNumber = (a.bankAccountNumber != null && a.bankAccountNumber!.trim().isNotEmpty)
                                ? a.bankAccountNumber!
                                : (savedBank?.bankAccountNumber ?? '');
                            final rawAccountName = (a.bankAccountName != null && a.bankAccountName!.trim().isNotEmpty)
                                ? a.bankAccountName!
                                : (savedBank?.bankAccountName ?? '');

                            final cleanBank = rawBankCode.trim().toUpperCase();
                            final cleanAcc = rawAccountNumber.trim().replaceAll(RegExp(r'\s+'), '');
                            final cleanName = rawAccountName.trim().toUpperCase();
                            final hasBank = cleanBank.isNotEmpty && cleanAcc.isNotEmpty;
                            final isCashMethod = a.paymentMethod == 'CASH';

                            if (isCashMethod) {
                              return Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFF0FDF4),
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(color: const Color(0xFFBBF7D0)),
                                ),
                                child: Row(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const Icon(Icons.payments_rounded, size: 22, color: Color(0xFF15803D)),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          const Text(
                                            'Nhận tiền mặt trực tiếp',
                                            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: Color(0xFF15803D)),
                                          ),
                                          const SizedBox(height: 3),
                                          const Text(
                                            'Vui lòng gặp Ban tổ chức tại khu vực tập trung / điều phối sự kiện để nhận tiền mặt trực tiếp.',
                                            style: TextStyle(fontSize: 11.8, color: Color(0xFF166534), height: 1.35),
                                          ),
                                          const SizedBox(height: 4),
                                          Text(
                                            'Số tiền: ${formatVndCurrency(a.declaredAmount > 0 ? a.declaredAmount : a.totalPay)}',
                                            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: Color(0xFF15803D)),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                              );
                            }

                            if (!hasBank) {
                              return Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: cani.warningBg,
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(color: cani.warning.withValues(alpha: 0.35)),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      children: [
                                        Icon(Icons.qr_code_2_rounded, size: 20, color: cani.warning),
                                        const SizedBox(width: 8),
                                        Expanded(
                                          child: Text(
                                            'Chưa có mã QR nhận tiền',
                                            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: cani.warning),
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      'Bạn chưa liên kết tài khoản ngân hàng nên chưa thể tạo mã VietQR. Thiết lập ngay để hiện mã QR nhận tiền hoặc nhận tiền mặt trực tiếp từ BTC.',
                                      style: TextStyle(fontSize: 11.8, color: cani.textBody, height: 1.35),
                                    ),
                                    const SizedBox(height: 10),
                                    SizedBox(
                                      width: double.infinity,
                                      child: OutlinedButton.icon(
                                        onPressed: () async {
                                          await Navigator.push(
                                            context,
                                            MaterialPageRoute(builder: (_) => const WorkerBankSettingsScreen()),
                                          );
                                          await ref.read(workerBankProvider.notifier).loadBankAccount();
                                          await _refreshAssignment(silent: true);
                                        },
                                        icon: Icon(Icons.account_balance_rounded, size: 17, color: cani.brand),
                                        label: Text(
                                          'Cài đặt tài khoản ngân hàng & Hiện QR',
                                          style: TextStyle(fontSize: 12.2, fontWeight: FontWeight.w700, color: cani.brand),
                                        ),
                                        style: OutlinedButton.styleFrom(
                                          backgroundColor: cani.surface,
                                          side: BorderSide(color: cani.brand.withValues(alpha: 0.4)),
                                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              );
                            }

                            final qrAmount = (a.declaredAmount > 0 ? a.declaredAmount : a.totalPay).toInt();
                            final queryParts = <String>[
                              if (qrAmount > 0) 'amount=$qrAmount',
                              if (cleanName.isNotEmpty) 'accountName=${Uri.encodeComponent(cleanName)}',
                            ];
                            final fallbackQrUrl =
                                'https://img.vietqr.io/image/$cleanBank-$cleanAcc-compact2.png${queryParts.isNotEmpty ? '?${queryParts.join('&')}' : ''}';
                            final vietQrUrl = (a.vietQrUrl != null && a.vietQrUrl!.trim().isNotEmpty)
                                ? a.vietQrUrl!
                                : fallbackQrUrl;

                            return Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: cani.canvas,
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: cani.brand.withValues(alpha: 0.3)),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Icon(Icons.qr_code_2_rounded, size: 18, color: cani.brand),
                                      const SizedBox(width: 8),
                                      Expanded(
                                        child: Text(
                                          'Mã QR nhận tiền của bạn (VietQR Napas 247)',
                                          style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w800, color: cani.textMain),
                                        ),
                                      ),
                                      InkWell(
                                        onTap: () async {
                                          await Navigator.push(
                                            context,
                                            MaterialPageRoute(builder: (_) => const WorkerBankSettingsScreen()),
                                          );
                                          await ref.read(workerBankProvider.notifier).loadBankAccount();
                                          await _refreshAssignment(silent: true);
                                        },
                                        child: Text(
                                          'Đổi TK',
                                          style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: cani.brand),
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 10),
                                  Row(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      InkWell(
                                        onTap: () => showEvidenceViewerDialog(
                                          context,
                                          imageUrl: vietQrUrl,
                                          title: 'Mã VietQR nhận thù lao ($cleanBank)',
                                        ),
                                        borderRadius: BorderRadius.circular(8),
                                        child: ClipRRect(
                                          borderRadius: BorderRadius.circular(8),
                                          child: Container(
                                            color: Colors.white,
                                            padding: const EdgeInsets.all(4),
                                            child: Image.network(
                                              vietQrUrl,
                                              width: 116,
                                              height: 116,
                                              fit: BoxFit.contain,
                                              loadingBuilder: (context, child, progress) {
                                                if (progress == null) return child;
                                                return SizedBox(
                                                  width: 116,
                                                  height: 116,
                                                  child: Center(child: CircularProgressIndicator(strokeWidth: 2, color: cani.brand)),
                                                );
                                              },
                                              errorBuilder: (context, error, stackTrace) => Container(
                                                width: 116,
                                                height: 116,
                                                color: cani.brandSoft,
                                                alignment: Alignment.center,
                                                padding: const EdgeInsets.all(6),
                                                child: Column(
                                                  mainAxisAlignment: MainAxisAlignment.center,
                                                  children: [
                                                    Icon(Icons.qr_code_scanner, color: cani.brand, size: 32),
                                                    const SizedBox(height: 4),
                                                    Text(
                                                      'Chạm làm mới mạng để tải QR',
                                                      textAlign: TextAlign.center,
                                                      style: TextStyle(fontSize: 9.5, color: cani.textMuted),
                                                    ),
                                                  ],
                                                ),
                                              ),
                                            ),
                                          ),
                                        ),
                                      ),
                                      const SizedBox(width: 12),
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Text('Ngân hàng: $cleanBank', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: cani.textMain)),
                                            const SizedBox(height: 3),
                                            Row(
                                              children: [
                                                Flexible(
                                                  child: Text(
                                                    'STK: $cleanAcc',
                                                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: cani.brand),
                                                    overflow: TextOverflow.ellipsis,
                                                  ),
                                                ),
                                                const SizedBox(width: 6),
                                                InkWell(
                                                  onTap: () {
                                                    Clipboard.setData(ClipboardData(text: cleanAcc));
                                                    ScaffoldMessenger.of(context).showSnackBar(
                                                      const SnackBar(content: Text('Đã sao chép số tài khoản')),
                                                    );
                                                  },
                                                  child: Icon(Icons.copy_rounded, size: 14, color: cani.brand),
                                                ),
                                              ],
                                            ),
                                            if (cleanName.isNotEmpty) ...[
                                              const SizedBox(height: 3),
                                              Text('Chủ TK: $cleanName', style: TextStyle(fontSize: 11.5, color: cani.textMuted)),
                                            ],
                                            const SizedBox(height: 6),
                                            Text(
                                              'Chạm mã QR để phóng to cho Ban tổ chức quét chuyển khoản.',
                                              style: TextStyle(fontSize: 11, color: cani.textMuted, fontStyle: FontStyle.italic),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            );
                          }),
                          const SizedBox(height: 14),

                          // Trạng thái xác nhận 2 bên
                          Text(
                            'Trạng thái xác nhận 2 bên',
                            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: cani.textMain),
                          ),
                          const SizedBox(height: 8),
                          _PartyConfirmRow(
                            cani: cani,
                            partyLabel: '1. Ban tổ chức (BTC)',
                            statusText: _isEoDeclared
                                ? 'Đã khai báo chi trả thù lao'
                                : (a.eoConfirmed
                                    ? 'Đã duyệt công · Đang chờ khai báo thanh toán'
                                    : 'Chưa duyệt công (Bước 3)'),
                            isDone: _isEoDeclared,
                          ),
                          const SizedBox(height: 6),
                          _PartyConfirmRow(
                            cani: cani,
                            partyLabel: '2. Nhân sự (Bạn)',
                            statusText: _isPaymentConfirmed
                                ? _paymentStatusLabel(a.paymentStatus)
                                : 'Chờ bạn kiểm tra và xác nhận thực nhận',
                            isDone: _isPaymentConfirmed,
                          ),

                          // Bằng chứng thanh toán từ BTC
                          if (_hasEvidence) ...[
                            const SizedBox(height: 14),
                            Text(
                              'Bằng chứng thanh toán từ BTC',
                              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: cani.textMain),
                            ),
                            const SizedBox(height: 8),
                            InkWell(
                              onTap: () => showEvidenceViewerDialog(
                                context,
                                imageUrl: a.paymentProofUrl!,
                                title: 'Chứng từ thanh toán từ BTC',
                              ),
                              borderRadius: BorderRadius.circular(12),
                              child: Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: cani.canvas,
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(color: cani.border),
                                ),
                                child: Row(
                                  children: [
                                    ClipRRect(
                                      borderRadius: BorderRadius.circular(8),
                                      child: SizedBox(
                                        width: 60,
                                        height: 60,
                                        child: buildResolvedImageWidget(
                                          a.paymentProofUrl!,
                                          height: 60,
                                          width: 60,
                                          fit: BoxFit.cover,
                                          errorText: null,
                                          errorBuilder: (ctx) => Container(
                                            color: cani.brandSoft,
                                            child: Icon(Icons.receipt_long_outlined, size: 24, color: cani.brand),
                                          ),
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            'Chứng từ thanh toán từ BTC',
                                            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: cani.textMain),
                                          ),
                                          const SizedBox(height: 2),
                                          Text(
                                            'Chạm để xem ảnh gốc & phóng to đối soát',
                                            style: TextStyle(fontSize: 11.5, color: cani.textMuted),
                                          ),
                                        ],
                                      ),
                                    ),
                                    Icon(Icons.open_in_new_rounded, size: 18, color: cani.brand),
                                  ],
                                ),
                              ),
                            ),
                          ] else if (_isEoDeclared && !_isPaymentConfirmed) ...[
                            const SizedBox(height: 12),
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: cani.warningBg,
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(color: cani.warning.withValues(alpha: 0.35)),
                              ),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Icon(Icons.info_outline_rounded, size: 18, color: cani.warning),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      'BTC chưa đính kèm ảnh chứng từ giao dịch. Hãy kiểm tra kỹ số dư/tiền mặt trước khi bấm xác nhận.',
                                      style: TextStyle(fontSize: 11.8, fontWeight: FontWeight.w600, color: cani.warning),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],

                          // Vùng hành động xác nhận phía Worker
                          const SizedBox(height: 16),
                          if (!a.eoConfirmed && !_isCompleted && !_isPaymentConfirmed)
                            Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: cani.warningBg,
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Text(
                                'Ca làm chưa được Ban tổ chức Duyệt công (Bước 3). Vui lòng quay lại màn đầu việc để hoàn tất và chờ duyệt công trước.',
                                style: TextStyle(fontSize: 12.2, fontWeight: FontWeight.w600, color: cani.warning),
                              ),
                            )
                          else if (!_isEoDeclared && !_isPaymentConfirmed)
                            Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: cani.brandSoft,
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: cani.brand.withValues(alpha: 0.2)),
                              ),
                              child: Row(
                                children: [
                                  Icon(Icons.schedule_rounded, size: 20, color: cani.brand),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Text(
                                      'BTC đã duyệt công! Đang chờ BTC khai báo thanh toán kèm chứng từ.',
                                      style: TextStyle(fontSize: 12.2, fontWeight: FontWeight.w600, color: cani.brand),
                                    ),
                                  ),
                                ],
                              ),
                            )
                          else if (_isPaymentConfirmed)
                            Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: cani.successBg,
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Row(
                                children: [
                                  Icon(Icons.check_circle_rounded, size: 20, color: cani.success),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Text(
                                      _isCompleted
                                          ? 'Đã hoàn tất đối soát thanh toán và Check-out ca làm.'
                                          : 'Hai bên đã xác nhận thanh toán! Hãy bấm nút bên dưới để sang trang Check-out.',
                                      style: TextStyle(fontSize: 12.2, fontWeight: FontWeight.w700, color: cani.success),
                                    ),
                                  ),
                                ],
                              ),
                            )
                          else if (_isConfirming)
                            Center(
                              child: Padding(
                                padding: const EdgeInsets.all(12),
                                child: CircularProgressIndicator(color: cani.brand),
                              ),
                            )
                          else
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                FilledButton.icon(
                                  onPressed: () => _handleConfirmPayment('RECEIVED_FULL'),
                                  icon: const Icon(Icons.check_circle_outline_rounded, size: 18),
                                  label: const Text(
                                    'Xác nhận đã nhận đủ thù lao',
                                    style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800),
                                  ),
                                  style: FilledButton.styleFrom(
                                    backgroundColor: cani.success,
                                    padding: const EdgeInsets.symmetric(vertical: 13),
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Row(
                                  children: [
                                    Expanded(
                                      child: OutlinedButton(
                                        onPressed: () => _handleConfirmPayment('RECEIVED_PARTIAL'),
                                        style: OutlinedButton.styleFrom(
                                          foregroundColor: cani.warning,
                                          side: BorderSide(color: cani.warning.withValues(alpha: 0.5)),
                                          padding: const EdgeInsets.symmetric(vertical: 11),
                                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                        ),
                                        child: const Text('Nhận 1 phần', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: OutlinedButton(
                                        onPressed: () => _handleConfirmPayment('NOT_RECEIVED'),
                                        style: OutlinedButton.styleFrom(
                                          foregroundColor: cani.danger,
                                          side: BorderSide(color: cani.danger.withValues(alpha: 0.5)),
                                          padding: const EdgeInsets.symmetric(vertical: 11),
                                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                        ),
                                        child: const Text('Chưa nhận', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),

                    // 3. Hỗ trợ nhanh: Chat & Khiếu nại
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: () {
                              showModalBottomSheet(
                                context: context,
                                isScrollControlled: true,
                                backgroundColor: Colors.transparent,
                                builder: (_) => ShiftChatSheet(assignment: _assignment),
                              );
                            },
                            icon: Icon(Icons.chat_bubble_outline_rounded, size: 17, color: cani.brand),
                            label: Text('Chat với BTC', style: TextStyle(fontWeight: FontWeight.w700, color: cani.textMain)),
                            style: OutlinedButton.styleFrom(
                              backgroundColor: cani.surface,
                              side: BorderSide(color: cani.border),
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(builder: (_) => MyDisputesScreen(assignment: _assignment)),
                              );
                            },
                            icon: Icon(Icons.gavel_rounded, size: 17, color: cani.brand),
                            label: Text('Khiếu nại', style: TextStyle(fontWeight: FontWeight.w700, color: cani.textMain)),
                            style: OutlinedButton.styleFrom(
                              backgroundColor: cani.surface,
                              side: BorderSide(color: cani.border),
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),

          // Sticky bottom CTA điều hướng sang Bước 5 (Check-out) / Bước 6 (Rating)
          _buildBottomBar(cani, isDark),
        ],
      ),
    );
  }

  Widget _buildBottomBar(CaniTokens cani, bool isDark) {
    final bool hasWorkerResponded = _assignment.workerConfirmStatus != null || _isPaymentConfirmed;
    final bool canGoCheckout = !_isCompleted &&
        _assignment.eoConfirmed &&
        (_isEoDeclared ? hasWorkerResponded : true);
    final bool canGoRating = _isCompleted && _assignment.hasWorkerReviewed != true;

    String label;
    IconData icon;
    VoidCallback? onPressed;
    bool useGradient = false;
    Color? solidColor;

    if (canGoCheckout) {
      useGradient = true;
      icon = Icons.qr_code_scanner_rounded;
      label = 'TIẾP TỤC: QUÉT QR CHECK-OUT (BƯỚC 5)';
      onPressed = _goToCheckoutScreen;
    } else if (canGoRating) {
      useGradient = true;
      icon = Icons.star_rate_rounded;
      label = 'TIẾP TỤC: ĐÁNH GIÁ BTC (BƯỚC 6)';
      onPressed = _goToRatingScreen;
    } else if (_isCompleted) {
      solidColor = cani.success;
      icon = Icons.check_circle_rounded;
      label = _assignment.hasWorkerReviewed == true ? 'ĐÃ HOÀN TẤT & ĐÃ ĐÁNH GIÁ' : 'HOÀN TẤT & QUAY LẠI';
      onPressed = () => Navigator.pop(context, true);
    } else {
      solidColor = cani.brand;
      icon = Icons.refresh_rounded;
      label = _isEoDeclared ? 'LÀM MỚI TRẠNG THÁI' : 'KIỂM TRA THANH TOÁN TỪ BTC';
      onPressed = () => _refreshAssignment();
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
            gradient: useGradient
                ? LinearGradient(
                    colors: cani.brandGradient,
                    begin: Alignment.centerLeft,
                    end: Alignment.centerRight,
                  )
                : null,
            color: useGradient ? null : solidColor,
            borderRadius: BorderRadius.circular(14),
          ),
          child: ElevatedButton.icon(
            onPressed: onPressed,
            icon: Icon(icon, size: 18, color: Colors.white),
            label: Text(
              label,
              style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: Colors.white),
              overflow: TextOverflow.ellipsis,
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.transparent,
              shadowColor: Colors.transparent,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
          ),
        ),
      ),
    );
  }
}

class _PartyConfirmRow extends StatelessWidget {
  final CaniTokens cani;
  final String partyLabel;
  final String statusText;
  final bool isDone;

  const _PartyConfirmRow({
    required this.cani,
    required this.partyLabel,
    required this.statusText,
    required this.isDone,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: isDone ? cani.successBg : cani.canvas,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: isDone ? cani.success.withValues(alpha: 0.3) : cani.border),
      ),
      child: Row(
        children: [
          Icon(
            isDone ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
            size: 18,
            color: isDone ? cani.success : cani.textMuted,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  partyLabel,
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: cani.textMain),
                ),
                const SizedBox(height: 1),
                Text(
                  statusText,
                  style: TextStyle(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w600,
                    color: isDone ? cani.success : cani.textMuted,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _PaymentIssueDialog extends StatefulWidget {
  final bool isPartial;

  const _PaymentIssueDialog({required this.isPartial});

  @override
  State<_PaymentIssueDialog> createState() => _PaymentIssueDialogState();
}

class _PaymentIssueDialogState extends State<_PaymentIssueDialog> {
  final TextEditingController _amountCtrl = TextEditingController();
  final TextEditingController _reasonCtrl = TextEditingController();

  @override
  void dispose() {
    _amountCtrl.dispose();
    _reasonCtrl.dispose();
    super.dispose();
  }

  void _submit() {
    if (widget.isPartial) {
      final amtText = _amountCtrl.text.replaceAll(RegExp(r'[^0-9]'), '');
      final amt = double.tryParse(amtText) ?? 0.0;
      final rsn = _reasonCtrl.text.trim();
      if (amt > 0 && rsn.length >= 3) {
        Navigator.pop(context, 'Thực nhận: ${formatVndCurrency(amt)}. Lý do: $rsn');
      }
      return;
    }

    final reason = _reasonCtrl.text.trim();
    if (reason.length >= 4) {
      Navigator.pop(context, reason);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;

    return AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      title: Text(
        widget.isPartial
            ? 'Xác nhận chỉ nhận một phần thù lao'
            : 'Báo cáo chưa nhận thù lao',
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
      ),
      content: widget.isPartial
          ? SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Vui lòng nhập số tiền thực tế bạn đã nhận và lý do để hệ thống đối soát:',
                    style: TextStyle(fontSize: 12.5),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _amountCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Số tiền thực nhận (VNĐ) *',
                      hintText: 'Ví dụ: 100000',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _reasonCtrl,
                    maxLines: 2,
                    decoration: const InputDecoration(
                      labelText: 'Lý do / Ghi chú thiếu hụt (*)',
                      hintText: 'Ví dụ: BTC mới thanh toán tiền mặt 100.000đ, còn thiếu 60.000đ...',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ],
              ),
            )
          : TextField(
              controller: _reasonCtrl,
              maxLines: 3,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Chi tiết lý do khiếu nại (*)',
                hintText: 'Ví dụ: Ca làm đã kết thúc nhưng chưa nhận được chuyển khoản hoặc tiền mặt...',
                border: OutlineInputBorder(),
              ),
            ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed: _submit,
          style: FilledButton.styleFrom(
            backgroundColor: widget.isPartial ? const Color(0xFFD97706) : cani.danger,
          ),
          child: const Text('Xác nhận gửi'),
        ),
      ],
    );
  }
}
