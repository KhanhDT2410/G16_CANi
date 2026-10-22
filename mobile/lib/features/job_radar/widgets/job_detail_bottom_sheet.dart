import 'package:flutter/material.dart';

import 'package:cani/features/job_radar/models/job_radar_models.dart';

class JobDetailBottomSheet extends StatelessWidget {
  final NearbyShiftItem shift;
  final VoidCallback onApply;

  const JobDetailBottomSheet({
    super.key,
    required this.shift,
    required this.onApply,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.15),
            blurRadius: 20,
            offset: const Offset(0, -5),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header kéo
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.shade400,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Tên sự kiện & Khoảng cách
          Row(
            children: [
              Expanded(
                child: Text(
                  shift.eventName,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.teal.shade50,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.teal.shade200),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.near_me, size: 14, color: Colors.teal.shade800),
                    const SizedBox(width: 4),
                    Text(
                      '${shift.distanceKm.toStringAsFixed(1)} km',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: Colors.teal.shade800,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Vị trí công việc (Job Type)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: theme.colorScheme.primaryContainer.withOpacity(0.6),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              shift.jobType,
              style: TextStyle(
                color: theme.colorScheme.onPrimaryContainer,
                fontWeight: FontWeight.bold,
                fontSize: 13,
              ),
            ),
          ),
          const SizedBox(height: 12),

          // Địa chỉ
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(Icons.location_on, size: 18, color: theme.colorScheme.primary),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  shift.locationAddress,
                  style: TextStyle(fontSize: 13, color: theme.colorScheme.onSurfaceVariant),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Thời gian
          if (shift.startTime != null && shift.endTime != null)
            Row(
              children: [
                Icon(Icons.access_time, size: 18, color: Colors.orange.shade700),
                const SizedBox(width: 6),
                Text(
                  '${shift.startTime!.hour.toString().padLeft(2, '0')}:${shift.startTime!.minute.toString().padLeft(2, '0')} - ${shift.endTime!.hour.toString().padLeft(2, '0')}:${shift.endTime!.minute.toString().padLeft(2, '0')} (${shift.startTime!.day}/${shift.startTime!.month})',
                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
                ),
              ],
            ),
          const SizedBox(height: 16),

          // Bảng lương & Quân số
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceVariant.withOpacity(0.4),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                Column(
                  children: [
                    const Text('Mức Lương', style: TextStyle(fontSize: 11, color: Colors.grey)),
                    const SizedBox(height: 4),
                    Text(
                      '${(shift.hourlyWage).toStringAsFixed(0)} đ/h',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: Colors.green.shade800,
                      ),
                    ),
                  ],
                ),
                Container(height: 30, width: 1, color: Colors.grey.shade300),
                Column(
                  children: [
                    const Text('Phụ Cấp', style: TextStyle(fontSize: 11, color: Colors.grey)),
                    const SizedBox(height: 4),
                    Text(
                      '+${(shift.allowance).toStringAsFixed(0)} đ',
                      style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
                Container(height: 30, width: 1, color: Colors.grey.shade300),
                Column(
                  children: [
                    const Text('Còn Thiếu', style: TextStyle(fontSize: 11, color: Colors.grey)),
                    const SizedBox(height: 4),
                    Text(
                      '${shift.remainingWorkers}/${shift.requiredWorkers} người',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: Colors.red.shade700,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Nút Ứng tuyển
          SizedBox(
            width: double.infinity,
            height: 48,
            child: FilledButton.icon(
              onPressed: onApply,
              icon: const Icon(Icons.check_circle_outline),
              label: const Text('ỨNG TUYỂN / NHẬN VIỆC NGAY', style: TextStyle(fontWeight: FontWeight.bold)),
              style: FilledButton.styleFrom(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
