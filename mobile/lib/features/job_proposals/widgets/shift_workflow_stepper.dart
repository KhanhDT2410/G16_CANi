import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';

import '../models/proposal_models.dart';
import 'shift_checklist_section.dart';

String formatVndCurrency(num value) {
  return '${value.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ';
}

bool isAssignmentPaymentConfirmed(ShiftAssignmentItem a) {
  return (a.workerConfirmStatus != null && a.workerConfirmStatus!.isNotEmpty) ||
      a.paymentStatus == 'CONFIRMED_FULL' ||
      a.paymentStatus == 'CONFIRMED_PARTIAL' ||
      a.paymentStatus == 'DISPUTED';
}

void showEvidenceViewerDialog(
  BuildContext context, {
  required String imageUrl,
  required String title,
}) {
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

class ShiftWorkflowStepper extends StatelessWidget {
  final bool isExpired;
  final bool isCheckedIn;
  final bool isWorkReady;
  final bool isEoConfirmed;
  final bool isPaymentConfirmed;
  final bool isCompleted;
  final bool isReviewed;
  final int? forcedCurrentStep; // 1..6 optional override for focused pages

  const ShiftWorkflowStepper({
    super.key,
    required this.isExpired,
    required this.isCheckedIn,
    required this.isWorkReady,
    required this.isEoConfirmed,
    required this.isPaymentConfirmed,
    required this.isCompleted,
    required this.isReviewed,
    this.forcedCurrentStep,
  });

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final step1Done = isCheckedIn || isCompleted;
    final step2Done = (isCheckedIn && isWorkReady) || isEoConfirmed || isCompleted;
    final step3Done = isEoConfirmed || isCompleted;
    final step4Done = step3Done && isPaymentConfirmed;
    final step5Done = isCompleted;
    final step6Done = isCompleted && isReviewed;

    final steps = [
      (
        label: 'Vào ca',
        done: step1Done,
        active: (!step1Done && !isExpired) || forcedCurrentStep == 1,
        failed: isExpired,
      ),
      (
        label: 'Đầu việc',
        done: step2Done,
        active: (isCheckedIn && !step2Done) || forcedCurrentStep == 2,
        failed: false,
      ),
      (
        label: 'Duyệt',
        done: step3Done,
        active: (isCheckedIn && step2Done && !step3Done) || forcedCurrentStep == 3,
        failed: false,
      ),
      (
        label: 'Thù lao',
        done: step4Done,
        active: (isCheckedIn && step3Done && !step4Done) || (forcedCurrentStep == 4 && !step4Done),
        failed: false,
      ),
      (
        label: 'Kết ca',
        done: step5Done,
        active: (isCheckedIn && step4Done && !isCompleted) || (forcedCurrentStep == 5 && !step5Done),
        failed: false,
      ),
      (
        label: 'Đánh giá',
        done: step6Done,
        active: (isCompleted && !isReviewed) || (forcedCurrentStep == 6 && !step6Done),
        failed: false,
      ),
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
                        ? cani.danger
                        : (steps[i].done
                            ? cani.success
                            : (steps[i].active ? cani.brand : cani.border)),
                  ),
                  child: Center(
                    child: steps[i].failed
                        ? const Icon(Icons.close_rounded, size: 13, color: Colors.white)
                        : (steps[i].done
                            ? const Icon(Icons.check_rounded, size: 13, color: Colors.white)
                            : Text(
                                '${i + 1}',
                                style: TextStyle(
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w800,
                                  color: steps[i].active ? Colors.white : cani.textMuted,
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
                    fontSize: 9.2,
                    fontWeight: (steps[i].active || steps[i].done) ? FontWeight.w700 : FontWeight.w500,
                    color: steps[i].failed
                        ? cani.danger
                        : (steps[i].done
                            ? cani.success
                            : (steps[i].active ? cani.brand : cani.textMuted)),
                  ),
                ),
              ],
            ),
          ),
          if (i < steps.length - 1)
            SizedBox(
              width: 10,
              child: Container(
                height: 2,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: steps[i].done ? cani.success.withValues(alpha: 0.5) : cani.border,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
        ],
      ],
    );
  }
}
