import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cani/features/help_center/models/help_center_models.dart';
import 'package:cani/features/help_center/providers/help_center_providers.dart';
import 'package:cani/features/help_center/screens/my_tickets_screen.dart';
import 'package:cani/features/profile/models/worker_bank_model.dart';

void main() {
  test('TicketMessageModel parses backend body field and staff role cleanly', () {
    final staffMsg = TicketMessageModel.fromJson({
      'id': 'm1',
      'body': 'Hệ thống lỗi vui lòng đợi chút',
      'senderRole': 'CUSTOMER_CARE',
      'senderName': 'Chuyên viên hỗ trợ',
      'isStaffReply': true,
      'createdAt': '2026-11-25T00:00:00Z',
    });

    expect(staffMsg.message, 'Hệ thống lỗi vui lòng đợi chút');
    expect(staffMsg.isStaffReply, isTrue);
    expect(staffMsg.authorName, 'Chuyên viên hỗ trợ');
    expect(staffMsg.authorRole, 'Chuyên viên hỗ trợ');

    final userMsg = TicketMessageModel.fromJson({
      'id': 'm2',
      'body': 'Có vấn đề khi xác thực ekyc',
      'senderRole': 'USER',
      'isStaffReply': false,
      'createdAt': '2026-11-25T00:00:00Z',
    });

    expect(userMsg.message, 'Có vấn đề khi xác thực ekyc');
    expect(userMsg.isStaffReply, isFalse);
    expect(userMsg.authorName, 'Tôi');
    expect(userMsg.authorRole, 'Người gửi');
  });

  test('WorkerBankModel parses masked bank account number for Decree 13/2023 compliance', () {
    final model = WorkerBankModel.fromJson({
      'bankCode': 'VCB',
      'bankAccountNumber': '1234567890',
      'bankAccountNumberMasked': '••••••7890',
      'bankAccountName': 'NGUYEN VAN A',
      'vietQrUrl': 'https://img.vietqr.io/image/VCB-1234567890-compact2.png',
    });

    expect(model.isConfigured, isTrue);
    expect(model.bankAccountNumberMasked, '••••••7890');
    expect(model.bankCode, 'VCB');
  });

  testWidgets('MyTicketsScreen renders CLOSED status and locked detail modal', (tester) async {
    final closedTicket = SupportTicketModel(
      id: 't1',
      referenceNo: 'TKT-20261124-C52F7A',
      category: 'ACCOUNT',
      priority: 'HIGH',
      subject: 'Có vấn đề khi ekyc',
      description: 'Có vấn đề khi cố gắng xác thực ekyc',
      status: 'CLOSED',
      createdAt: DateTime(2026, 11, 24, 23, 56),
      messages: [
        TicketMessageModel(
          id: 'm1',
          authorName: 'Người gửi',
          authorRole: 'Người gửi',
          message: 'Có vấn đề khi cố gắng xác thực ekyc',
          isStaffReply: false,
          createdAt: DateTime(2026, 11, 24, 23, 56),
        ),
        TicketMessageModel(
          id: 'm2',
          authorName: 'Chuyên viên hỗ trợ',
          authorRole: 'Chuyên viên hỗ trợ',
          message: 'Đóng ticket: Hệ thống đã khắc phục xong',
          isStaffReply: true,
          createdAt: DateTime(2026, 11, 25, 0, 20),
        ),
      ],
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          myTicketsProvider.overrideWith((ref) async => [closedTicket]),
          ticketDetailProvider('t1').overrideWith((ref) async => closedTicket),
        ],
        child: const MaterialApp(home: MyTicketsScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('TKT-20261124-C52F7A'), findsOneWidget);
    expect(find.text('Đã đóng'), findsOneWidget);

    await tester.tap(find.text('Có vấn đề khi ekyc'));
    await tester.pumpAndSettle();

    expect(find.text('Đóng ticket: Hệ thống đã khắc phục xong'), findsOneWidget);
    expect(find.text('Phiếu hỗ trợ đã được đóng và xử lý hoàn tất'), findsOneWidget);
    expect(find.text('Nhập phản hồi trao đổi...'), findsNothing);
  });
}
