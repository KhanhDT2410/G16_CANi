import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/help_center/providers/help_center_providers.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/profile/providers/worker_experience_provider.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/worker_groups/providers/worker_group_provider.dart';

/// Điều phối làm mới (invalidate / silent refetch) các Riverpod provider
/// dùng chung giữa FCM foreground/tap (`main.dart`) và fallback polling (`home_screen.dart`).
class NotificationSyncRouter {
  NotificationSyncRouter._();

  static void syncFromContainer(
    ProviderContainer container,
    List<Map<String, dynamic>> events, {
    bool isNotificationTap = false,
  }) {
    _sync(
      read: container.read,
      invalidate: container.invalidate,
      events: events,
      isNotificationTap: isNotificationTap,
    );
  }

  static void syncFromRef(
    WidgetRef ref,
    List<Map<String, dynamic>> events, {
    bool isNotificationTap = false,
  }) {
    _sync(
      read: ref.read,
      invalidate: ref.invalidate,
      events: events,
      isNotificationTap: isNotificationTap,
    );
  }

  static void _sync({
    required T Function<T>(ProviderListenable<T> provider) read,
    required void Function(ProviderOrFamily provider) invalidate,
    required List<Map<String, dynamic>> events,
    required bool isNotificationTap,
  }) {
    final isOnline = read(jobRadarProvider).isOnline;
    var refreshAssignments = isNotificationTap;
    var refreshProposals = false;
    var refreshRadar = false;
    var refreshEkyc = false;
    var refreshSkills = false;
    var refreshDisputes = false;
    var refreshGroups = false;
    var refreshExperience = false;
    var refreshTickets = false;
    final ticketIds = <String>{};

    for (final raw in events) {
      final rawPayload = raw['payload'];
      final type = (raw['type'] ?? (rawPayload is Map ? rawPayload['type'] : null))
              ?.toString()
              .toUpperCase() ??
          '';
      final refType = raw['referenceType']?.toString().toUpperCase() ?? '';
      final refId = raw['referenceId']?.toString();

      if (type == 'PROPOSAL' ||
          type == 'NEW_PROPOSAL' ||
          type == 'EMERGENCY_PROPOSAL' ||
          type == 'GROUP_PROPOSAL' ||
          type == 'EMERGENCY_GROUP_PROPOSAL' ||
          refType == 'PROPOSAL') {
        refreshProposals = true;
        refreshRadar = true;
        if (type.contains('GROUP')) {
          refreshGroups = true;
          refreshAssignments = true;
        }
      } else if (type.contains('EKYC') || refType.contains('EKYC')) {
        refreshEkyc = true;
      } else if (type.contains('SKILL') || refType.contains('SKILL')) {
        refreshSkills = true;
      } else if (type.contains('DISPUTE') || refType == 'DISPUTE') {
        refreshDisputes = true;
        refreshAssignments = true;
      } else if (type.contains('GROUP') || refType == 'WORKER_GROUP' || refType == 'GROUP') {
        refreshGroups = true;
        refreshAssignments = true;
      } else if (type.contains('REVIEW') || refType == 'REVIEW') {
        refreshExperience = true;
        refreshAssignments = true;
      } else if (type.contains('SUPPORT') || refType == 'SUPPORT_TICKET') {
        refreshTickets = true;
        if (refId != null && refId.isNotEmpty) {
          ticketIds.add(refId);
        }
      } else {
        refreshAssignments = true;
        if (type.contains('SHIFT') || refType == 'SHIFT') {
          refreshExperience = true;
          refreshProposals = true;
        }
      }
    }

    if (events.isEmpty) {
      refreshAssignments = true;
    }

    if ((refreshProposals || isNotificationTap) && isOnline) {
      invalidate(proposalsListProvider);
    }
    if (refreshAssignments) {
      invalidate(assignmentsListProvider);
      invalidate(incomeSummaryProvider);
    }
    if (refreshEkyc) {
      read(ekycProvider.notifier).checkStatus();
    }
    if (refreshSkills) {
      read(skillVerificationProvider.notifier).loadAll();
    }
    if (refreshDisputes) {
      invalidate(myDisputesListProvider);
    }
    if (refreshGroups) {
      invalidate(workerGroupsProvider);
    }
    if (refreshExperience) {
      read(workerExperienceProvider.notifier).loadExperience();
    }
    if (refreshTickets) {
      invalidate(myTicketsProvider);
      for (final id in ticketIds) {
        invalidate(ticketDetailProvider(id));
      }
    }
    if ((refreshRadar || isNotificationTap) &&
        isOnline &&
        read(ekycProvider).result?.isApproved == true) {
      read(jobRadarProvider.notifier).scanNearbyShifts(silent: true);
    }
  }
}
