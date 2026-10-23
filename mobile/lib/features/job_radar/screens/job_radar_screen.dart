import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/job_radar/models/job_radar_models.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/job_radar/widgets/job_detail_bottom_sheet.dart';
import 'package:cani/features/job_radar/widgets/job_radar_map_painter.dart';

class JobRadarScreen extends ConsumerWidget {
  const JobRadarScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(jobRadarProvider);
    final notifier = ref.read(jobRadarProvider.notifier);
    final theme = Theme.of(context);

    return Scaffold(
      body: Column(
        children: [
          // 1. Header: Switch Bắt đầu kiếm việc (Online / Offline)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: state.isOnline ? Colors.teal.shade50 : Colors.grey.shade100,
              border: Border(bottom: BorderSide(color: Colors.grey.shade200)),
            ),
            child: Row(
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: state.isOnline ? Colors.green : Colors.grey,
                    boxShadow: state.isOnline
                        ? [BoxShadow(color: Colors.green.withOpacity(0.5), blurRadius: 8, spreadRadius: 2)]
                        : null,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        state.isOnline ? 'CHẾ ĐỘ TÌM VIỆC ĐANG BẬT' : 'CHẾ ĐỘ NGHỈ (OFFLINE)',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                          color: state.isOnline ? Colors.teal.shade900 : Colors.grey.shade700,
                        ),
                      ),
                      Text(
                        state.isOnline
                            ? 'Đang phát sóng GPS & nhận đề xuất việc trong ${state.searchRadiusKm.toInt()}km'
                            : 'Gạt công tắc để bắt đầu quét việc quanh bạn',
                        style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: state.isOnline,
                  activeColor: Colors.teal,
                  onChanged: (val) => notifier.toggleOnlineStatus(),
                ),
              ],
            ),
          ),

          // 2. Bộ lọc bán kính & Nút chuyển đổi View Mode (Bản đồ / Danh sách)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 6),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  // Chọn bán kính
                  const Text('Bán kính:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                  const SizedBox(width: 6),
                  Wrap(
                    spacing: 4,
                    children: [5.0, 10.0, 15.0].map((r) {
                      final isSel = state.searchRadiusKm == r;
                      return ChoiceChip(
                        label: Text('${r.toInt()}km', style: TextStyle(fontSize: 11, fontWeight: isSel ? FontWeight.bold : FontWeight.normal)),
                        selected: isSel,
                        onSelected: (_) => notifier.setRadius(r),
                        visualDensity: VisualDensity.compact,
                      );
                    }).toList(),
                  ),
                  const SizedBox(width: 12),

                  // Toggle Bản đồ / Danh sách
                  SegmentedButton<bool>(
                    segments: const [
                      ButtonSegment(value: true, icon: Icon(Icons.map_outlined, size: 16), label: Text('Bản đồ', style: TextStyle(fontSize: 11))),
                      ButtonSegment(value: false, icon: Icon(Icons.view_list, size: 16), label: Text('Danh sách', style: TextStyle(fontSize: 11))),
                    ],
                    selected: {state.isRadarView},
                    onSelectionChanged: (set) => notifier.toggleViewMode(set.first),
                  ),
                ],
              ),
            ),
          ),

          // 3. Body: Radar Map View hoặc Danh sách công việc
          Expanded(
            child: state.isLoading
                ? const Center(child: CircularProgressIndicator())
                : state.isRadarView
                    ? _buildRadarView(context, state, notifier)
                    : _buildListView(context, state, notifier),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => notifier.scanNearbyShifts(),
        icon: const Icon(Icons.my_location),
        label: const Text('Quét lại', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
    );
  }

  Widget _buildRadarView(BuildContext context, JobRadarState state, JobRadarNotifier notifier) {
    return Column(
      children: [
        const SizedBox(height: 6),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              Icon(Icons.info_outline, size: 16, color: Colors.indigo.shade400),
              const SizedBox(width: 6),
              Text(
                'Tìm thấy ${state.nearbyShifts.length} ca làm việc phù hợp xung quanh',
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
              ),
            ],
          ),
        ),
        const SizedBox(height: 6),
        Expanded(
          child: JobRadarMapWidget(
            shifts: state.nearbyShifts,
            selectedShift: state.selectedShift,
            maxRadiusKm: state.searchRadiusKm,
            isOnline: state.isOnline,
            userLat: state.userLat,
            userLng: state.userLng,
            onSelectShift: (shift) {
              notifier.selectShift(shift);
              _showShiftDetails(context, shift);
            },
          ),
        ),
      ],
    );
  }

  Widget _buildListView(BuildContext context, JobRadarState state, JobRadarNotifier notifier) {
    if (state.nearbyShifts.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.location_off, size: 48, color: Colors.grey.shade400),
            const SizedBox(height: 12),
            const Text('Không có ca làm việc nào trong khu vực này'),
            const SizedBox(height: 8),
            TextButton(
              onPressed: () => notifier.setRadius(15.0),
              child: const Text('Mở rộng bán kính 15km'),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
      itemCount: state.nearbyShifts.length,
      itemBuilder: (context, index) {
        final shift = state.nearbyShifts[index];
        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          elevation: 2,
          child: InkWell(
            borderRadius: BorderRadius.circular(14),
            onTap: () {
              notifier.selectShift(shift);
              _showShiftDetails(context, shift);
            },
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          shift.eventName,
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: Colors.teal.shade50,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: Colors.teal.shade200),
                        ),
                        child: Text(
                          '${shift.distanceKm.toStringAsFixed(1)} km',
                          style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.teal.shade800),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    shift.jobType,
                    style: TextStyle(fontWeight: FontWeight.w600, color: Colors.indigo.shade700, fontSize: 13),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    shift.locationAddress,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const Divider(height: 18),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${shift.hourlyWage.toStringAsFixed(0)} đ/h',
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: Colors.green.shade800),
                      ),
                      Text(
                        'Cần: ${shift.remainingWorkers}/${shift.requiredWorkers} người',
                        style: TextStyle(fontSize: 12, color: Colors.red.shade700, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  void _showShiftDetails(BuildContext context, NearbyShiftItem shift) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => JobDetailBottomSheet(
        shift: shift,
        onApply: () {
          Navigator.pop(ctx);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Đã gửi yêu cầu ứng tuyển ca: ${shift.jobType}'),
              backgroundColor: Colors.green,
            ),
          );
        },
      ),
    );
  }
}
