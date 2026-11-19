import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'package:cani/core/constants/banks.dart';
import 'package:cani/core/network/dio_client.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/profile/providers/worker_bank_provider.dart';

class WorkerBankSettingsScreen extends ConsumerStatefulWidget {
  const WorkerBankSettingsScreen({super.key});

  @override
  ConsumerState<WorkerBankSettingsScreen> createState() => _WorkerBankSettingsScreenState();
}

class _WorkerBankSettingsScreenState extends ConsumerState<WorkerBankSettingsScreen> {
  final _storage = const FlutterSecureStorage();
  final _formKey = GlobalKey<FormState>();

  String _selectedBankCode = 'MB';
  final TextEditingController _accountNumberCtrl = TextEditingController();
  final TextEditingController _accountNameCtrl = TextEditingController();

  bool _isLoading = true;
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    _loadSavedBankInfo();
  }

  @override
  void dispose() {
    _accountNumberCtrl.dispose();
    _accountNameCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadSavedBankInfo() async {
    try {
      final user = ref.read(authStateProvider).user;
      final savedBank = await _storage.read(key: 'cani_worker_bank_code');
      final savedAccount = await _storage.read(key: 'cani_worker_bank_account');
      final savedName = await _storage.read(key: 'cani_worker_bank_owner');

      if (mounted) {
        setState(() {
          if (savedBank != null && kSupportedBanks.any((b) => b.code == savedBank)) {
            _selectedBankCode = savedBank;
          }
          _accountNumberCtrl.text = savedAccount ?? '';
          _accountNameCtrl.text = (savedName != null && savedName.isNotEmpty)
              ? savedName
              : (user?.fullName.toUpperCase() ?? '');
          _isLoading = false;
        });
      }

      // Tải dữ liệu chính xác từ Backend API để luôn đồng bộ trạng thái đám mây
      final dio = ref.read(dioProvider);
      final response = await dio.get('/v1/workers/bank-account');
      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final data = body is Map<String, dynamic> && body.containsKey('data')
            ? body['data'] as Map<String, dynamic>?
            : body as Map<String, dynamic>?;

        if (data != null) {
          final serverBank = data['bankCode'] as String?;
          final serverAccount = data['bankAccountNumber'] as String?;
          final serverName = data['bankAccountName'] as String?;

          if (serverAccount != null && serverAccount.isNotEmpty && mounted) {
            setState(() {
              if (serverBank != null && kSupportedBanks.any((b) => b.code == serverBank)) {
                _selectedBankCode = serverBank;
              }
              _accountNumberCtrl.text = serverAccount;
              if (serverName != null && serverName.isNotEmpty) {
                _accountNameCtrl.text = serverName;
              }
            });

            // Đồng bộ ngược vào cache thiết bị
            await Future.wait([
              if (serverBank != null) _storage.write(key: 'cani_worker_bank_code', value: serverBank),
              _storage.write(key: 'cani_worker_bank_account', value: serverAccount),
              if (serverName != null) _storage.write(key: 'cani_worker_bank_owner', value: serverName),
            ]);
          }
        }
      }
    } catch (e) {
      debugPrint('Thông tin ngân hàng load từ cache cục bộ (Backend offline hoặc chưa có): $e');
    } finally {
      if (mounted && _isLoading) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _saveBankInfo() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSaving = true);
    final bankCode = _selectedBankCode;
    final accountNum = _accountNumberCtrl.text.trim();
    final accountName = _accountNameCtrl.text.trim().toUpperCase();

    try {
      // 1. Gửi lên Backend Database để Event Organizer thấy mã VietQR thanh toán
      final dio = ref.read(dioProvider);
      await dio.put(
        '/v1/workers/bank-account',
        data: {
          'bankCode': bankCode,
          'bankAccountNumber': accountNum,
          'bankAccountName': accountName,
        },
      );

      // 2. Lưu vào FlutterSecureStorage của thiết bị để dùng offline & hiển thị tức thì
      await Future.wait([
        _storage.write(key: 'cani_worker_bank_code', value: bankCode),
        _storage.write(key: 'cani_worker_bank_account', value: accountNum),
        _storage.write(key: 'cani_worker_bank_owner', value: accountName),
      ]);

      ref.read(workerBankProvider.notifier).loadBankAccount();

      if (!mounted) return;
      setState(() => _isSaving = false);

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Đã lưu thông tin tài khoản ngân hàng thành công lên hệ thống'),
          backgroundColor: Color(0xFF059669),
        ),
      );
    } catch (e) {
      debugPrint('Lỗi lưu thông tin ngân hàng lên máy chủ: $e');
      if (!mounted) return;
      setState(() => _isSaving = false);

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Lỗi cập nhật tài khoản ngân hàng: ${e.toString()}'),
          backgroundColor: const Color(0xFFDC2626),
        ),
      );
    }
  }

  String _getVietQrUrl() {
    final accountNum = _accountNumberCtrl.text.trim();
    final accountName = _accountNameCtrl.text.trim().toUpperCase();
    if (accountNum.isEmpty || accountName.isEmpty) return '';
    return 'https://img.vietqr.io/image/$_selectedBankCode-$accountNum-compact2.png?accountName=${Uri.encodeComponent(accountName)}';
  }

  String _formatMaskedAccount(String raw) {
    if (raw.length <= 4) return raw;
    final last4 = raw.substring(raw.length - 4);
    return '•••• •••• •••• $last4';
  }

  @override
  Widget build(BuildContext context) {
    final bankObj = kSupportedBanks.firstWhere(
      (b) => b.code == _selectedBankCode,
      orElse: () => kSupportedBanks.first,
    );
    final qrUrl = _getVietQrUrl();

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20, color: Color(0xFF0F172A)),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text(
          'Tài Khoản Nhận Thù Lao',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: Color(0xFF0F172A)),
        ),
        centerTitle: false,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // MÔ PHỎNG THẺ NGÂN HÀNG THỤ HƯỞNG (VIRTUAL CARD)
                    Container(
                      width: double.infinity,
                      height: 190,
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        ),
                        borderRadius: BorderRadius.circular(20),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF0F172A).withValues(alpha: 0.25),
                            blurRadius: 16,
                            offset: const Offset(0, 8),
                          ),
                        ],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                bankObj.shortName.toUpperCase(),
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w800,
                                  fontSize: 15,
                                  letterSpacing: 0.8,
                                ),
                              ),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                decoration: BoxDecoration(
                                  color: const Color(0xFF059669),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: const Text(
                                  'VIETQR 247',
                                  style: TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w800),
                                ),
                              ),
                            ],
                          ),
                          Text(
                            _accountNumberCtrl.text.isNotEmpty
                                ? _formatMaskedAccount(_accountNumberCtrl.text)
                                : '•••• •••• •••• ••••',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              letterSpacing: 2.5,
                              fontWeight: FontWeight.w700,
                              fontFamily: 'monospace',
                            ),
                          ),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text(
                                    'CHỦ TÀI KHOẢN',
                                    style: TextStyle(color: Color(0xFF94A3B8), fontSize: 9.5, fontWeight: FontWeight.w600),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    _accountNameCtrl.text.isNotEmpty
                                        ? _accountNameCtrl.text.toUpperCase()
                                        : 'NGUYEN VAN A',
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w700,
                                      fontSize: 13,
                                      letterSpacing: 0.5,
                                    ),
                                  ),
                                ],
                              ),
                              const Icon(Icons.contactless, color: Color(0xFF94A3B8), size: 24),
                            ],
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 16),

                    // THÔNG TIN BẢO MẬT & THANH TOÁN
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFFEFF6FF),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFFBFDBFE)),
                      ),
                      child: const Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(Icons.info_outline, size: 18, color: Color(0xFF1D4ED8)),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Thông tin tài khoản ngân hàng sẽ được hiển thị cho Ban tổ chức dưới dạng mã VietQR 247 để chuyển thù lao trực tiếp cho bạn ngay sau khi hoàn thành ca làm việc.',
                              style: TextStyle(fontSize: 12, color: Color(0xFF1E40AF), height: 1.4),
                            ),
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 20),

                    // FORM NHẬP LIỆU
                    const Text(
                      'Thông tin tài khoản ngân hàng',
                      style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: Color(0xFF0F172A)),
                    ),
                    const SizedBox(height: 12),

                    // 1. CHỌN NGÂN HÀNG
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFFCBD5E1)),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          value: _selectedBankCode,
                          isExpanded: true,
                          items: kSupportedBanks.map((b) {
                            return DropdownMenuItem<String>(
                              value: b.code,
                              child: Text(
                                b.name,
                                style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: Color(0xFF0F172A)),
                              ),
                            );
                          }).toList(),
                          onChanged: (val) {
                            if (val != null) {
                              setState(() => _selectedBankCode = val);
                            }
                          },
                        ),
                      ),
                    ),

                    const SizedBox(height: 12),

                    // 2. SỐ TÀI KHOẢN
                    TextFormField(
                      controller: _accountNumberCtrl,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      decoration: InputDecoration(
                        labelText: 'Số tài khoản ngân hàng *',
                        hintText: 'Ví dụ: 038291823901',
                        filled: true,
                        fillColor: Colors.white,
                        prefixIcon: const Icon(Icons.credit_card, size: 20, color: Color(0xFF64748B)),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                        ),
                      ),
                      onChanged: (_) => setState(() {}),
                      validator: (v) {
                        if (v == null || v.trim().isEmpty) return 'Vui lòng nhập số tài khoản';
                        if (v.trim().length < 6) return 'Số tài khoản không hợp lệ (tối thiểu 6 số)';
                        return null;
                      },
                    ),

                    const SizedBox(height: 12),

                    // 3. TÊN CHỦ TÀI KHOẢN (IN HOA KHÔNG DẤU)
                    TextFormField(
                      controller: _accountNameCtrl,
                      textCapitalization: TextCapitalization.characters,
                      decoration: InputDecoration(
                        labelText: 'Tên chủ tài khoản (In hoa không dấu) *',
                        hintText: 'Ví dụ: NGUYEN VAN A',
                        filled: true,
                        fillColor: Colors.white,
                        prefixIcon: const Icon(Icons.person_outline, size: 20, color: Color(0xFF64748B)),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                        ),
                      ),
                      onChanged: (_) => setState(() {}),
                      validator: (v) {
                        if (v == null || v.trim().isEmpty) return 'Vui lòng nhập tên chủ tài khoản';
                        return null;
                      },
                    ),

                    const SizedBox(height: 20),

                    // PREVIEW MÃ VIETQR NHẬN TIỀN
                    if (qrUrl.isNotEmpty) ...[
                      const Text(
                        'Mã VietQR Nhận Thù Lao Trực Tiếp',
                        style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: Color(0xFF0F172A)),
                      ),
                      const SizedBox(height: 10),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: const Color(0xFFE2E8F0)),
                        ),
                        child: Column(
                          children: [
                            ClipRRect(
                              borderRadius: BorderRadius.circular(8),
                              child: Image.network(
                                qrUrl,
                                height: 260,
                                fit: BoxFit.contain,
                                loadingBuilder: (ctx, child, progress) {
                                  if (progress == null) return child;
                                  return const SizedBox(
                                    height: 260,
                                    child: Center(child: CircularProgressIndicator()),
                                  );
                                },
                                errorBuilder: (ctx, err, stack) {
                                  return const SizedBox(
                                    height: 160,
                                    child: Center(
                                      child: Text(
                                        'Không thể tải ảnh mã QR. Vui lòng kiểm tra kết nối mạng.',
                                        style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                                      ),
                                    ),
                                  );
                                },
                              ),
                            ),
                            const SizedBox(height: 10),
                            Text(
                              '${bankObj.shortName} • ${_accountNumberCtrl.text.trim()} • ${_accountNameCtrl.text.trim().toUpperCase()}',
                              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12, color: Color(0xFF334155)),
                              textAlign: TextAlign.center,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 20),
                    ],

                    // NÚT LƯU THÔNG TIN
                    SizedBox(
                      width: double.infinity,
                      height: 48,
                      child: FilledButton(
                        onPressed: _isSaving ? null : _saveBankInfo,
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF0066FF),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                        child: _isSaving
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                              )
                            : const Text(
                                'Lưu Thông Tin Ngân Hàng',
                                style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                              ),
                      ),
                    ),
                    const SizedBox(height: 24),
                  ],
                ),
              ),
            ),
    );
  }
}
