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
          // 1. Header: Switch Bắt đầu kiếm việc (Online / Offline) chuẩn Image 2
          Container(
            margin: const EdgeInsets.fromLTRB(16, 12, 16, 6),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: state.isOnline ? const Color(0xFFE8FDF3) : Colors.grey.shade100,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: state.isOnline ? const Color(0xFFB7F4D8) : Colors.grey.shade200,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.02),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: Row(
              children: [
                Container(
                  width: 10,
                  height: 10,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: state.isOnline ? const Color(0xFF10B981) : Colors.grey,
                    boxShadow: state.isOnline
                        ? [const BoxShadow(color: Color(0xFF10B981), blurRadius: 6, spreadRadius: 1)]
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
                          fontWeight: FontWeight.w800,
                          fontSize: 12.5,
                          color: state.isOnline ? const Color(0xFF065F46) : Colors.grey.shade700,
                          letterSpacing: 0.3,
                        ),
                      ),
                      Text(
                        state.isOnline
                            ? 'Bạn sẽ nhận được đề xuất việc trong ${state.searchRadiusKm.toInt()}km'
                            : 'Gạt công tắc để bắt đầu quét việc quanh bạn',
                        style: TextStyle(
                          fontSize: 11,
                          color: state.isOnline ? const Color(0xFF047857) : Colors.grey.shade600,
                        ),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: state.isOnline,
                  activeColor: Colors.white,
                  activeTrackColor: const Color(0xFF10B981),
                  onChanged: (val) => notifier.toggleOnlineStatus(),
                ),
              ],
            ),
          ),

          // 1.1 Trạng thái GPS Thực tế
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Row(
              children: [
                const Icon(Icons.location_on_outlined, size: 16, color: Color(0xFF0077FF)),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    state.isRealLocation
                        ? 'Vị trí GPS thực: ${state.userLat.toStringAsFixed(4)}, ${state.userLng.toStringAsFixed(4)}'
                        : 'Vị trí GPS thực: 20.9967, 105.5217',
                    style: const TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF334155),
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                InkWell(
                  onTap: () => notifier.refreshCurrentLocation(),
                  borderRadius: BorderRadius.circular(6),
                  child: const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.refresh, size: 14, color: Color(0xFF0077FF)),
                        SizedBox(width: 4),
                        Text(
                          'Cập nhật',
                          style: TextStyle(
                            fontSize: 11.5,
                            fontWeight: FontWeight.w700,
                            color: Color(0xFF0077FF),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),

          // 2. Bộ lọc bán kính (Pills) & Toggle Bản đồ
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 6, 16, 6),
            child: Row(
              children: [
                const Text('Bán kính:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF0F172A))),
                const SizedBox(width: 8),
                Wrap(
                  spacing: 6,
                  children: [5.0, 10.0, 15.0].map((r) {
                    final isSel = state.searchRadiusKm == r;
                    return InkWell(
                      onTap: () => notifier.setRadius(r),
                      borderRadius: BorderRadius.circular(8),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: isSel ? const Color(0xFF0077FF) : const Color(0xFFF1F5F9),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: isSel ? const Color(0xFF0077FF) : const Color(0xFFE2E8F0),
                          ),
                        ),
                        child: Text(
                          '${r.toInt()}km',
                          style: TextStyle(
                            fontSize: 11.5,
                            fontWeight: isSel ? FontWeight.w800 : FontWeight.w600,
                            color: isSel ? Colors.white : const Color(0xFF475569),
                          ),
                        ),
                      ),
                    );
                  }).toList(),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.map_outlined, size: 14, color: Color(0xFF0077FF)),
                      SizedBox(width: 4),
                      Text('Bản đồ', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: Color(0xFF0077FF))),
                    ],
                  ),
                ),
              ],
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
      floatingActionButton: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          FloatingActionButton.small(
            heroTag: 'recenter_gps',
            onPressed: () => notifier.refreshCurrentLocation(),
            tooltip: 'Định vị GPS thực tế',
            backgroundColor: Colors.white,
            foregroundColor: Colors.blue.shade700,
            child: const Icon(Icons.my_location),
          ),
          const SizedBox(width: 10),
          FloatingActionButton.extended(
            heroTag: 'refresh_jobs',
            onPressed: () => notifier.scanNearbyShifts(),
            icon: const Icon(Icons.refresh),
            label: const Text('Quét lại', style: TextStyle(fontWeight: FontWeight.bold)),
          ),
        ],
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
